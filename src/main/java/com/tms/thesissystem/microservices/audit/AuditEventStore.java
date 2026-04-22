package com.tms.thesissystem.microservices.audit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AuditEventStore {
    private final String url;
    private final String username;
    private final String password;
    private final AtomicLong sequence = new AtomicLong(1);

    public AuditEventStore(@Value("${app.database.url}") String url,
                           @Value("${app.database.username}") String username,
                           @Value("${app.database.password}") String password) {
        this.url = url;
        this.username = username;
        this.password = password;
        ensureTable();
        syncSequence();
    }

    public synchronized void append(String entityType, Long entityId, String action, String actorName, String detail, LocalDateTime createdAt) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into workflow_audit(id, entity_type, entity_id, action, actor_name, detail, created_at)
                     values (?, ?, ?, ?, ?, ?, ?)
                     on conflict (id) do update
                     set entity_type = excluded.entity_type,
                         entity_id = excluded.entity_id,
                         action = excluded.action,
                         actor_name = excluded.actor_name,
                         detail = excluded.detail,
                         created_at = excluded.created_at
                     """)) {
            statement.setLong(1, sequence.incrementAndGet());
            statement.setString(2, entityType);
            statement.setLong(3, entityId);
            statement.setString(4, action);
            statement.setString(5, actorName);
            statement.setString(6, detail);
            statement.setTimestamp(7, Timestamp.valueOf(createdAt));
            statement.executeUpdate();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to store audit event", exception);
        }
    }

    public synchronized List<AuditView> findAll() {
        List<AuditView> auditEntries = new ArrayList<>();
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     select id, entity_type, entity_id, action, actor_name, detail, created_at
                     from workflow_audit
                     order by created_at desc, id desc
                     """)) {
            while (resultSet.next()) {
                auditEntries.add(new AuditView(
                        resultSet.getLong("id"),
                        resultSet.getString("entity_type"),
                        resultSet.getLong("entity_id"),
                        resultSet.getString("action"),
                        resultSet.getString("actor_name"),
                        resultSet.getString("detail"),
                        resultSet.getTimestamp("created_at").toLocalDateTime()
                ));
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load audit events", exception);
        }
        return auditEntries;
    }

    private void ensureTable() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table if not exists workflow_audit (
                        id bigint primary key,
                        entity_type text not null,
                        entity_id bigint not null,
                        action text not null,
                        actor_name text not null,
                        detail text not null,
                        created_at timestamp not null
                    )
                    """);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize audit table", exception);
        }
    }

    private void syncSequence() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select coalesce(max(id), 1) from workflow_audit")) {
            if (resultSet.next()) {
                sequence.set(resultSet.getLong(1));
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sync audit sequence", exception);
        }
    }

    private Connection getConnection() throws Exception {
        return DriverManager.getConnection(url, username, password);
    }

    public record AuditView(Long id, String entityType, Long entityId, String action, String actorName, String detail, LocalDateTime createdAt) {
    }
}
