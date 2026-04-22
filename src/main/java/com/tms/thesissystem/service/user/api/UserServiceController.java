package com.tms.thesissystem.service.user.api;

import com.tms.thesissystem.api.ApiDtos;
import com.tms.thesissystem.api.ApiResponseMapper;
import com.tms.thesissystem.application.service.AuthService;
import com.tms.thesissystem.application.service.WorkflowQueryService;
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
public class UserServiceController {
    private final WorkflowQueryService queryService;
    private final ApiResponseMapper apiResponseMapper;
    private final AuthService authService;

    public UserServiceController(WorkflowQueryService queryService, ApiResponseMapper apiResponseMapper, AuthService authService) {
        this.queryService = queryService;
        this.apiResponseMapper = apiResponseMapper;
        this.authService = authService;
    }

    @GetMapping
    public List<ApiDtos.UserDto> users() {
        return queryService.getDashboard().users().stream()
                .map(apiResponseMapper::toUserDto)
                .toList();
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public ApiDtos.LoginResponse login(@RequestBody LoginRequest request) {
        ApiDtos.LoginResponse response = authService.login(request.username(), request.password());
        if (!response.ok()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, response.message());
        }
        return response;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.RegistrationResponse register(@RequestBody RegistrationRequest request) {
        ApiDtos.RegistrationResponse response = authService.register(
                request.username(),
                request.password(),
                request.confirmPassword()
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

    public record LoginRequest(String username, String password) {
    }

    public record RegistrationRequest(String username, String password, String confirmPassword) {
    }

    public record ForgotPasswordRequest(String username) {
    }
}
