package com.tms.thesissystem.application.service;

import com.tms.thesissystem.api.ApiDtos;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final WorkflowQueryService queryService = mock(WorkflowQueryService.class);
    private final AuthAccountStore authAccountStore = mock(AuthAccountStore.class);
    private final AuthService authService = new AuthService(queryService, authAccountStore);

    @Test
    void returnsValidationMessageWhenCredentialsBlank() {
        ApiDtos.LoginResponse response = authService.login(" ", "");

        assertThat(response.ok()).isFalse();
        assertThat(response.message()).contains("Нэвтрэх нэр");
        assertThat(response.user()).isNull();
    }

    @Test
    void authenticatesUsingEmailAliasAndNormalizesUsername() {
        when(authAccountStore.findByUsername("22b1num0027")).thenReturn(Optional.of(
                new AuthAccountStore.AuthAccount(100001L, "22b1num0027", sha256("secret"), "student", "Ану", null, null)
        ));

        ApiDtos.LoginResponse response = authService.login("  22b1num0027  ", "secret");

        assertThat(response.ok()).isTrue();
        assertThat(response.user().id()).isEqualTo(100001L);
        assertThat(response.user().username()).isEqualTo("22b1num0027");
        assertThat(response.user().role()).isEqualTo("student");
    }

    @Test
    void authenticatesDepartmentUserViaDepartmentAlias() {
        when(authAccountStore.findByUsername("sisi-admin")).thenReturn(Optional.of(
                new AuthAccountStore.AuthAccount(300001L, "sisi-admin", sha256("secret"), "department", "Department Admin", null, null)
        ));

        ApiDtos.LoginResponse response = authService.login("sisi-admin", "secret");

        assertThat(response.ok()).isTrue();
        assertThat(response.user().id()).isEqualTo(300001L);
        assertThat(response.user().role()).isEqualTo("department");
    }

    @Test
    void returnsErrorForWrongPassword() {
        when(authAccountStore.findByUsername("anu")).thenReturn(Optional.of(
                new AuthAccountStore.AuthAccount(100001L, "anu", sha256("secret"), "student", "Ану", null, null)
        ));

        ApiDtos.LoginResponse response = authService.login("anu", "wrong");

        assertThat(response.ok()).isFalse();
        assertThat(response.message()).contains("буруу");
    }

    @Test
    void resetPasswordReturnsTemporaryPasswordForKnownIdentifier() {
        when(authAccountStore.findByUsername("22b1num0027")).thenReturn(Optional.of(
                new AuthAccountStore.AuthAccount(100001L, "22b1num0027", sha256("secret"), "student", "Ану", null, null)
        ));

        ApiDtos.PasswordResetResponse response = authService.resetPassword("22b1num0027");

        assertThat(response.ok()).isTrue();
        assertThat(response.username()).isEqualTo("22b1num0027");
        assertThat(response.message()).contains("Түр нууц үг");
        verify(authAccountStore).save(eq(100001L), eq("22b1num0027"), anyString(), eq("student"), eq("Ану"));
    }

    @Test
    void registersUserDirectlyIntoAuthStore() {
        when(authAccountStore.findByUsername("new-user")).thenReturn(Optional.empty());
        when(authAccountStore.nextUserId()).thenReturn(900001L);

        ApiDtos.RegistrationResponse response = authService.register("new-user", "secret1", "secret1");

        assertThat(response.ok()).isTrue();
        assertThat(response.username()).isEqualTo("new-user");
        verify(authAccountStore).save(eq(900001L), eq("new-user"), anyString(), eq("student"), eq("new-user"));
    }

    private String sha256(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
