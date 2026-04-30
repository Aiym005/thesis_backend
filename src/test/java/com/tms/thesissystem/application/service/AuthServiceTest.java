package com.tms.thesissystem.application.service;

import com.tms.thesissystem.application.port.WorkflowRepository;
import com.tms.thesissystem.application.service.security.JwtTokenService;
import com.tms.thesissystem.api.ApiDtos;
import com.tms.thesissystem.domain.User;
import com.tms.thesissystem.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final WorkflowRepository workflowRepository = mock(WorkflowRepository.class);
    private final AuthAccountStore authAccountStore = mock(AuthAccountStore.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final AuthService authService = new AuthService(workflowRepository, authAccountStore, passwordEncoder, jwtTokenService);

    @Test
    void returnsValidationMessageWhenCredentialsBlank() {
        ApiDtos.LoginResponse response = authService.login(" ", "");

        assertThat(response.ok()).isFalse();
        assertThat(response.message()).contains("Нэвтрэх нэр");
        assertThat(response.user()).isNull();
        assertThat(response.token()).isNull();
    }

    @Test
    void authenticatesUsingEmailAliasAndNormalizesUsername() {
        when(authAccountStore.findByUsername("22b1num0027")).thenReturn(Optional.of(
                new AuthAccountStore.AuthAccount(100001L, "22b1num0027", passwordEncoder.encode("secret"), "student", "Ану", null, null)
        ));
        when(jwtTokenService.issueToken(org.mockito.ArgumentMatchers.any())).thenReturn("jwt-token");

        ApiDtos.LoginResponse response = authService.login("  22b1num0027  ", "secret");

        assertThat(response.ok()).isTrue();
        assertThat(response.user().id()).isEqualTo(100001L);
        assertThat(response.user().username()).isEqualTo("22b1num0027");
        assertThat(response.user().role()).isEqualTo("student");
        assertThat(response.token()).isEqualTo("jwt-token");
    }

    @Test
    void authenticatesDepartmentUserViaDepartmentAlias() {
        when(authAccountStore.findByUsername("sisi-admin")).thenReturn(Optional.of(
                new AuthAccountStore.AuthAccount(300001L, "sisi-admin", passwordEncoder.encode("secret"), "department", "Department Admin", null, null)
        ));
        when(jwtTokenService.issueToken(org.mockito.ArgumentMatchers.any())).thenReturn("jwt-token");

        ApiDtos.LoginResponse response = authService.login("sisi-admin", "secret");

        assertThat(response.ok()).isTrue();
        assertThat(response.user().id()).isEqualTo(300001L);
        assertThat(response.user().role()).isEqualTo("department");
        assertThat(response.token()).isEqualTo("jwt-token");
    }

    @Test
    void returnsErrorForWrongPassword() {
        when(authAccountStore.findByUsername("anu")).thenReturn(Optional.of(
                new AuthAccountStore.AuthAccount(100001L, "anu", passwordEncoder.encode("secret"), "student", "Ану", null, null)
        ));

        ApiDtos.LoginResponse response = authService.login("anu", "wrong");

        assertThat(response.ok()).isFalse();
        assertThat(response.message()).contains("буруу");
        assertThat(response.token()).isNull();
    }

    @Test
    void resetPasswordReturnsTemporaryPasswordForKnownIdentifier() {
        when(authAccountStore.findByUsername("22b1num0027")).thenReturn(Optional.of(
                new AuthAccountStore.AuthAccount(100001L, "22b1num0027", passwordEncoder.encode("secret"), "student", "Ану", null, null)
        ));

        ApiDtos.PasswordResetResponse response = authService.resetPassword("22b1num0027");

        assertThat(response.ok()).isTrue();
        assertThat(response.username()).isEqualTo("22b1num0027");
        assertThat(response.message()).contains("токен");
        assertThat(response.resetToken()).isNotBlank();
    }

    @Test
    void registersUserDirectlyIntoAuthStore() {
        when(authAccountStore.findByUsername("new-user")).thenReturn(Optional.empty());
        when(workflowRepository.createUserAccount("new-user", UserRole.STUDENT))
                .thenReturn(new User(900001L, UserRole.STUDENT, "new-user", "new-user", "User", "new-user@tms.mn", "Software Engineering", "B.SE"));

        ApiDtos.RegistrationResponse response = authService.register("new-user", "secret1", "secret1");

        assertThat(response.ok()).isTrue();
        assertThat(response.username()).isEqualTo("new-user");
        verify(authAccountStore).save(eq(900001L), eq("new-user"), anyString(), eq("student"), eq("new-user"));
    }

}
