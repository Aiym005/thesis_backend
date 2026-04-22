package com.tms.thesissystem.application.service;

import com.tms.thesissystem.api.ApiDtos;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Optional;
import java.util.HexFormat;
import java.security.SecureRandom;
import java.util.regex.Pattern;

@Service
public class AuthService {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._@-]{3,64}$");
    private final WorkflowQueryService queryService;
    private final AuthAccountStore authAccountStore;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(WorkflowQueryService queryService, AuthAccountStore authAccountStore) {
        this.queryService = queryService;
        this.authAccountStore = authAccountStore;
    }

    public ApiDtos.LoginResponse login(String username, String password) {
        String normalizedUsername = normalize(username);
        String normalizedPassword = password == null ? "" : password.trim();

        if (normalizedUsername.isBlank() || normalizedPassword.isBlank()) {
            return new ApiDtos.LoginResponse(false, "Нэвтрэх нэр болон нууц үгээ оруулна уу.", null);
        }
        if (!isSafeUsername(normalizedUsername)) {
            return new ApiDtos.LoginResponse(false, "Нэвтрэх нэрийн формат буруу байна.", null);
        }

        Optional<AuthAccountStore.AuthAccount> account = authAccountStore.findByUsername(normalizedUsername);
        if (account.isEmpty()) {
            return new ApiDtos.LoginResponse(false, "Нэвтрэх нэр эсвэл нууц үг буруу байна.", null);
        }
        if (!account.get().passwordHash().equals(hashPassword(normalizedPassword))) {
            return new ApiDtos.LoginResponse(false, "Нэвтрэх нэр эсвэл нууц үг буруу байна.", null);
        }
        String role = normalizeStoredRole(account.get().role(), normalizedUsername);
        ApiDtos.AuthUserDto authUser = new ApiDtos.AuthUserDto(
                account.get().userId(),
                account.get().username(),
                account.get().displayName() == null || account.get().displayName().isBlank()
                        ? account.get().username()
                        : account.get().displayName(),
                role
        );
        return new ApiDtos.LoginResponse(true, "Амжилттай нэвтэрлээ.", authUser);
    }

    public ApiDtos.RegistrationResponse register(String username, String password, String confirmPassword) {
        String normalizedUsername = normalize(username);
        String normalizedPassword = password == null ? "" : password.trim();
        String normalizedConfirm = confirmPassword == null ? "" : confirmPassword.trim();

        if (normalizedUsername.isBlank() || normalizedPassword.isBlank() || normalizedConfirm.isBlank()) {
            return new ApiDtos.RegistrationResponse(false, "Нэвтрэх нэр, нууц үг, давтан нууц үгээ бүрэн оруулна уу.", null);
        }
        if (!isSafeUsername(normalizedUsername)) {
            return new ApiDtos.RegistrationResponse(false, "Нэвтрэх нэр зөвхөн үсэг, тоо, цэг, зураас, @, underscore агуулна.", null);
        }
        if (normalizedPassword.length() < 6) {
            return new ApiDtos.RegistrationResponse(false, "Нууц үг хамгийн багадаа 6 тэмдэгт байна.", null);
        }
        if (normalizedPassword.length() > 128) {
            return new ApiDtos.RegistrationResponse(false, "Нууц үг хэт урт байна.", null);
        }
        if (!normalizedPassword.equals(normalizedConfirm)) {
            return new ApiDtos.RegistrationResponse(false, "Нууц үг таарахгүй байна.", null);
        }
        if (authAccountStore.findByUsername(normalizedUsername).isPresent()) {
            return new ApiDtos.RegistrationResponse(false, "Энэ хэрэглэгч аль хэдийн бүртгүүлсэн байна.", normalizedUsername);
        }

        Long userId = authAccountStore.nextUserId();
        authAccountStore.save(
                userId,
                normalizedUsername,
                hashPassword(normalizedPassword),
                inferRole(normalizedUsername),
                normalizedUsername
        );
        return new ApiDtos.RegistrationResponse(true, "Бүртгэл амжилттай үүслээ. Одоо нэвтэрнэ үү.", normalizedUsername);
    }

    public ApiDtos.PasswordResetResponse resetPassword(String username) {
        String normalizedUsername = normalize(username);
        if (normalizedUsername.isBlank()) {
            return new ApiDtos.PasswordResetResponse(false, "СИСИ эрх эсвэл нэвтрэх нэрээ оруулна уу.", null);
        }
        if (!isSafeUsername(normalizedUsername)) {
            return new ApiDtos.PasswordResetResponse(false, "Нэвтрэх нэрийн формат буруу байна.", null);
        }
        Optional<AuthAccountStore.AuthAccount> account = authAccountStore.findByUsername(normalizedUsername);
        if (account.isEmpty()) {
            return new ApiDtos.PasswordResetResponse(false, "Ийм хэрэглэгч олдсонгүй.", null);
        }
        String temporaryPassword = generateTemporaryPassword();
        authAccountStore.save(
                account.get().userId(),
                account.get().username(),
                hashPassword(temporaryPassword),
                normalizeStoredRole(account.get().role(), account.get().username()),
                account.get().displayName()
        );
        return new ApiDtos.PasswordResetResponse(
                true,
                "Нууц үг амжилттай сэргээгдлээ. Түр нууц үг: " + temporaryPassword,
                account.get().username()
        );
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (Exception exception) {
            throw new IllegalStateException("Нууц үг боловсруулахад алдаа гарлаа.", exception);
        }
    }

    private String generateTemporaryPassword() {
        byte[] bytes = new byte[4];
        secureRandom.nextBytes(bytes);
        return "tmp-" + HexFormat.of().formatHex(bytes);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isSafeUsername(String username) {
        return USERNAME_PATTERN.matcher(username).matches();
    }

    private String inferRole(String normalizedUsername) {
        if (normalizedUsername.startsWith("tch")) {
            return "teacher";
        }
        if (normalizedUsername.contains("admin") || normalizedUsername.contains("dept")) {
            return "department";
        }
        return "student";
    }

    private String normalizeStoredRole(String storedRole, String username) {
        String normalizedRole = normalize(storedRole);
        if (normalizedRole.equals("teacher") || normalizedRole.equals("department") || normalizedRole.equals("student")) {
            return normalizedRole;
        }
        return inferRole(username);
    }
}
