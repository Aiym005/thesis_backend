package com.tms.thesissystem.infrastructure.events;

import com.tms.thesissystem.application.event.WorkflowEvent;
import com.tms.thesissystem.application.port.WorkflowRepository;
import com.tms.thesissystem.domain.AuditEntry;
import com.tms.thesissystem.domain.Notification;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowEventProjectionServiceTest {

    private final WorkflowRepository repository = mock(WorkflowRepository.class);
    private final WorkflowEventProjectionService service = new WorkflowEventProjectionService(repository);

    @Test
    void storesNotificationForEachRecipient() {
        WorkflowEvent event = new WorkflowEvent("TOPIC", 1L, "TOPIC_PROPOSED", "Ану Бат-Эрдэнэ", "detail", "title", "message", List.of(10L, 20L), LocalDateTime.now());
        when(repository.nextNotificationId()).thenReturn(100L, 101L);

        service.storeNotifications(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository, org.mockito.Mockito.times(2)).saveNotification(captor.capture());
        assertThat(captor.getAllValues()).extracting(Notification::userId).containsExactly(10L, 20L);
    }

    @Test
    void storesAuditEntryFromEvent() {
        WorkflowEvent event = new WorkflowEvent("PLAN", 5L, "PLAN_SUBMITTED", "Teacher", "detail", "title", "message", List.of(10L), LocalDateTime.now());
        when(repository.nextAuditId()).thenReturn(200L);

        service.storeAuditEntry(event);

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(repository).saveAuditEntry(captor.capture());
        assertThat(captor.getValue().entityType()).isEqualTo("PLAN");
        assertThat(captor.getValue().action()).isEqualTo("PLAN_SUBMITTED");
    }
}
