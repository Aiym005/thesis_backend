package com.tms.thesissystem.application.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseStatusServiceTest {

    @Test
    void returnsDisconnectedStatusWhenConnectionFails() {
        DatabaseStatusService service = new DatabaseStatusService(
                "localhost",
                5432,
                "thesisdb",
                "user",
                "jdbc:postgresql://localhost:1/does_not_exist"
        );

        DatabaseStatusService.DatabaseStatus status = service.check();

        assertThat(status.connected()).isFalse();
        assertThat(status.host()).isEqualTo("localhost");
        assertThat(status.database()).isEqualTo("thesisdb");
        assertThat(status.message()).isNotBlank();
    }
}
