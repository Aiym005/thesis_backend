package com.tms.thesissystem.application.service;

import com.tms.thesissystem.api.ApiDtos;
import com.tms.thesissystem.domain.model.User;
import com.tms.thesissystem.domain.model.UserRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final WorkflowQueryService queryService = mock(WorkflowQueryService.class);
    private final AuthService authService = new AuthService(queryService, "secret");

    @Test
    void returnsValidationMessageWhenCredentialsBlank() {
        ApiDtos.LoginResponse response = authService.login(" ", "");

        assertThat(response.ok()).isFalse();
        assertThat(response.message()).contains("Нэвтрэх нэр");
        assertThat(response.user()).isNull();
    }

    @Test
    void authenticatesUsingEmailAliasAndNormalizesUsername() {
        User user = new User(100001L, UserRole.STUDENT, "22b1num0027", "Ану", "Бат-Эрдэнэ", "anu.bat-erdene@tms.mn", "Software Engineering", "SE");
        when(queryService.getDashboard()).thenReturn(snapshotWithUsers(user));

        ApiDtos.LoginResponse response = authService.login("  22b1num0027  ", "secret");

        assertThat(response.ok()).isTrue();
        assertThat(response.user().id()).isEqualTo(100001L);
        assertThat(response.user().username()).isEqualTo("22b1num0027");
        assertThat(response.user().role()).isEqualTo("student");
    }

    @Test
    void authenticatesDepartmentUserViaDepartmentAlias() {
        User department = new User(300001L, UserRole.DEPARTMENT, "sisi-admin", "Department", "Admin", "dept@example.com", "Software Engineering", "B.SE");
        when(queryService.getDashboard()).thenReturn(snapshotWithUsers(department));

        ApiDtos.LoginResponse response = authService.login("se-dept", "secret");

        assertThat(response.ok()).isTrue();
        assertThat(response.user().id()).isEqualTo(300001L);
        assertThat(response.user().role()).isEqualTo("department");
    }

    @Test
    void returnsErrorForWrongPassword() {
        ApiDtos.LoginResponse response = authService.login("anu", "wrong");

        assertThat(response.ok()).isFalse();
        assertThat(response.message()).contains("буруу");
    }

    @Test
    void resetPasswordReturnsTemporaryPasswordForKnownIdentifier() {
        User user = new User(100001L, UserRole.STUDENT, "22b1num0027", "Ану", "Бат-Эрдэнэ", "anu.bat-erdene@tms.mn", "Software Engineering", "SE");
        when(queryService.getDashboard()).thenReturn(snapshotWithUsers(user));

        ApiDtos.PasswordResetResponse response = authService.resetPassword("22b1num0027");

        assertThat(response.ok()).isTrue();
        assertThat(response.username()).isEqualTo("22b1num0027");
        assertThat(response.message()).contains("secret");
    }

    private WorkflowQueryService.DashboardSnapshot snapshotWithUsers(User... users) {
        return new WorkflowQueryService.DashboardSnapshot(
                List.of(users),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new WorkflowQueryService.Summary(0, 0, 0)
        );
    }
}
