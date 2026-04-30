package com.tms.thesissystem.application.service;

import com.tms.thesissystem.application.port.WorkflowRepository;
import com.tms.thesissystem.application.service.security.JwtTokenService;
import com.tms.thesissystem.api.ApiDtos;
import com.tms.thesissystem.domain.User;
import com.tms.thesissystem.domain.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._@-]{3,64}$");
    private final WorkflowRepository workflowRepository;
    private final AuthAccountStore authAccountStore;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public ApiDtos.LoginResponse login(String username, String password) {
        String normalizedUsername = normalize(username);
        String normalizedPassword = password == null ? "" : password.trim();

        if (normalizedUsername.isBlank() || normalizedPassword.isBlank()) {
            return new ApiDtos.LoginResponse(false, "Нэвтрэх нэр болон нууц үгээ оруулна уу.", null, null);
        }
        if (!isSafeUsername(normalizedUsername)) {
            return new ApiDtos.LoginResponse(false, "Нэвтрэх нэрийн формат буруу байна.", null, null);
        }

        Optional<AuthAccountStore.AuthAccount> account = authAccountStore.findByUsername(normalizedUsername);
        if (account.isEmpty()) {
            return new ApiDtos.LoginResponse(false, "Нэвтрэх нэр эсвэл нууц үг буруу байна.", null, null);
        }
        if (!passwordEncoder.matches(normalizedPassword, account.get().passwordHash())) {
            return new ApiDtos.LoginResponse(false, "Нэвтрэх нэр эсвэл нууц үг буруу байна.", null, null);
        }
        String role = normalizeStoredRole(account.get().role(), normalizedUsername);
        Long resolvedUserId = resolveWorkflowUserId(account.get(), normalizedUsername, role);
        ApiDtos.AuthUserDto authUser = new ApiDtos.AuthUserDto(
                resolvedUserId,
                account.get().username(),
                account.get().displayName() == null || account.get().displayName().isBlank()
                        ? account.get().username()
                        : account.get().displayName(),
                role
        );
        return new ApiDtos.LoginResponse(true, "Амжилттай нэвтэрлээ.", authUser, jwtTokenService.issueToken(authUser));
    }

    public ApiDtos.RegistrationResponse register(String username, String password, String confirmPassword) {
        String normalizedUsername = normalize(username);
        String normalizedPassword = password == null ? "" : password.trim();
        String normalizedConfirm = confirmPassword == null ? "" : confirmPassword.trim();

        if (normalizedUsername.isBlank() || normalizedPassword.isBlank() || normalizedConfirm.isBlank()) {
            return new ApiDtos.RegistrationResponse(false, "Нэвтрэх нэр, нууц үг, давтан нууц үгээ бүрэн оруулна уу.", null, null, null);
        }
        if (!isSafeUsername(normalizedUsername)) {
            return new ApiDtos.RegistrationResponse(false, "Нэвтрэх нэр зөвхөн үсэг, тоо, цэг, зураас, @, underscore агуулна.", null, null, null);
        }
        if (normalizedPassword.length() < 6) {
            return new ApiDtos.RegistrationResponse(false, "Нууц үг хамгийн багадаа 6 тэмдэгт байна.", null, null, null);
        }
        if (normalizedPassword.length() > 128) {
            return new ApiDtos.RegistrationResponse(false, "Нууц үг хэт урт байна.", null, null, null);
        }
        if (!normalizedPassword.equals(normalizedConfirm)) {
            return new ApiDtos.RegistrationResponse(false, "Нууц үг таарахгүй байна.", null, null, null);
        }
        if (authAccountStore.findByUsername(normalizedUsername).isPresent()) {
            return new ApiDtos.RegistrationResponse(false, "Энэ хэрэглэгч аль хэдийн бүртгүүлсэн байна.", normalizedUsername, null, null);
        }

        UserRole role = inferRole(normalizedUsername);
        User workflowUser = workflowRepository.createUserAccount(normalizedUsername, role);
        authAccountStore.save(
                workflowUser.id(),
                normalizedUsername,
                passwordEncoder.encode(normalizedPassword),
                role.name().toLowerCase(Locale.ROOT),
                normalizedUsername
        );
        ApiDtos.AuthUserDto authUser = new ApiDtos.AuthUserDto(
                workflowUser.id(),
                normalizedUsername,
                normalizedUsername,
                role.name().toLowerCase(Locale.ROOT)
        );
        return new ApiDtos.RegistrationResponse(
                true,
                "Бүртгэл амжилттай үүслээ.",
                normalizedUsername,
                authUser,
                jwtTokenService.issueToken(authUser)
        );
    }

    public ApiDtos.PasswordResetResponse resetPassword(String username) {
        String normalizedUsername = normalize(username);
        if (normalizedUsername.isBlank()) {
            return new ApiDtos.PasswordResetResponse(false, "СИСИ эрх эсвэл нэвтрэх нэрээ оруулна уу.", null, null);
        }
        if (!isSafeUsername(normalizedUsername)) {
            return new ApiDtos.PasswordResetResponse(false, "Нэвтрэх нэрийн формат буруу байна.", null, null);
        }
        Optional<AuthAccountStore.AuthAccount> account = authAccountStore.findByUsername(normalizedUsername);
        if (account.isEmpty()) {
            return new ApiDtos.PasswordResetResponse(false, "Ийм хэрэглэгч олдсонгүй.", null, null);
        }
        String normalizedRole = normalizeStoredRole(account.get().role(), account.get().username());
        String resetToken = jwtTokenService.issuePasswordResetToken(
                account.get().userId(),
                account.get().username(),
                normalizedRole
        );
        return new ApiDtos.PasswordResetResponse(
                true,
                "Нууц үг сэргээх токен үүслээ.",
                account.get().username(),
                resetToken
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isSafeUsername(String username) {
        return USERNAME_PATTERN.matcher(username).matches();
    }

    private UserRole inferRole(String normalizedUsername) {
        if (normalizedUsername.contains("@tms.mn")) {
            return UserRole.TEACHER;
        }
        if (normalizedUsername.contains("admin") || normalizedUsername.contains("dept")) {
            return UserRole.DEPARTMENT;
        }
        return UserRole.STUDENT;
    }

    private String normalizeStoredRole(String storedRole, String username) {
        String normalizedRole = normalize(storedRole);
        if (normalizedRole.equals("teacher") || normalizedRole.equals("department") || normalizedRole.equals("student")) {
            return normalizedRole;
        }
        return inferRole(username).name().toLowerCase(Locale.ROOT);
    }

    private Long resolveWorkflowUserId(AuthAccountStore.AuthAccount account, String normalizedUsername, String normalizedRole) {
        UserRole role = UserRole.valueOf(normalizedRole.toUpperCase(Locale.ROOT));
        Optional<User> directMatch = workflowRepository.findUserById(account.userId())
                .filter(user -> user.role() == role);
        if (directMatch.isPresent()) {
            return directMatch.get().id();
        }
        return workflowRepository.findAllUsers().stream()
                .filter(user -> user.role() == role)
                .filter(user -> normalizedUsername.equals(normalize(user.loginId())))
                .map(User::id)
                .findFirst()
                .orElse(account.userId());
    }
}
