package com.tms.thesissystem.service.user.api;

import com.tms.thesissystem.api.ApiDtos;
import com.tms.thesissystem.api.ApiResponseMapper;
import com.tms.thesissystem.application.service.AuthAccountStore;
import com.tms.thesissystem.application.service.AuthService;
import com.tms.thesissystem.application.service.WorkflowQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserServiceController {
    private final WorkflowQueryService queryService;
    private final ApiResponseMapper apiResponseMapper;
    private final AuthService authService;
    private final AuthAccountStore authAccountStore;

    @GetMapping
    public List<ApiDtos.UserDto> users() {
        return queryService.getDashboard().users().stream()
                .map(apiResponseMapper::toUserDto)
                .toList();
    }

    @GetMapping("/login-enabled-teachers")
    public List<ApiDtos.UserDto> loginEnabledTeachers() {
        List<Long> teacherIds = authAccountStore.findAllByRole("teacher").stream()
                .map(AuthAccountStore.AuthAccount::userId)
                .toList();
        return queryService.getDashboard().users().stream()
                .filter(user -> user.role().name().equals("TEACHER"))
                .filter(user -> teacherIds.contains(user.id()))
                .map(apiResponseMapper::toUserDto)
                .toList();
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public ApiDtos.LoginResponse login(@Valid @RequestBody LoginRequest request) {
        ApiDtos.LoginResponse response = authService.login(request.username(), request.password());
        if (!response.ok()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, response.message());
        }
        return response;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.RegistrationResponse register(@Valid @RequestBody RegistrationRequest request) {
        ApiDtos.RegistrationResponse response = authService.register(
                request.username(),
                request.password(),
                request.confirmPassword(),
                request.firstName(),
                request.lastName(),
                request.phoneNumber()
        );
        if (!response.ok()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, response.message());
        }
        return response;
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.OK)
    public ApiDtos.PasswordResetResponse forgotPassword(@RequestBody ForgotPasswordRequest request) {
        ApiDtos.PasswordResetResponse response = authService.resetPassword(request.username());
        if (!response.ok()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, response.message());
        }
        return response;
    }

    public record LoginRequest(
            @NotBlank
            @Size(min = 3, max = 64)
            @Pattern(regexp = "^[a-zA-Z0-9._@-]{3,64}$")
            String username,
            @NotBlank
            @Size(min = 6, max = 128)
            String password
    ) {
    }

    public record RegistrationRequest(
            @NotBlank
            @Size(min = 3, max = 64)
            @Pattern(regexp = "^[a-zA-Z0-9._@-]{3,64}$")
            String username,
            @NotBlank
            @Size(min = 6, max = 128)
            String password,
            @NotBlank
            @Size(min = 6, max = 128)
            String confirmPassword,
            @NotBlank
            @Size(min = 2, max = 50)
            @Pattern(regexp = "^[\\p{L} .'-]{2,50}$")
            String firstName,
            @NotBlank
            @Size(min = 2, max = 50)
            @Pattern(regexp = "^[\\p{L} .'-]{2,50}$")
            String lastName,
            @NotNull
            @Pattern(regexp = "^[0-9+()\\-\\s]{8,20}$")
            String phoneNumber
    ) {
    }

    public record ForgotPasswordRequest(String username) {
    }
}
