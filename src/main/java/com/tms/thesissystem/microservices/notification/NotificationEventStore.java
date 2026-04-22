package com.tms.thesissystem.microservices.notification;

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
public class NotificationEventStore {
    private final String url;
    private final String username;
    private final String password;
    private final AtomicLong sequence = new AtomicLong(1);

    public NotificationEventStore(@Value("${app.database.url}") String url,
                                  @Value("${app.database.username}") String username,
                                  @Value("${app.database.password}") String password) {
        this.url = url;
        this.username = username;
        this.password = password;
        ensureTable();
        syncSequence();
    }

    public synchronized void append(Long userId, String title, String message, LocalDateTime createdAt) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into workflow_notification(id, user_id, title, message, created_at)
                     values (?, ?, ?, ?, ?)
                     on conflict (id) do update
                     set user_id = excluded.user_id,
                         title = excluded.title,
                         message = excluded.message,
                         created_at = excluded.created_at
                     """)) {
            statement.setLong(1, sequence.incrementAndGet());
            statement.setLong(2, userId);
            statement.setString(3, title);
            statement.setString(4, message);
            statement.setTimestamp(5, Timestamp.valueOf(createdAt));
            statement.executeUpdate();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to store notification event", exception);
        }
    }

    public synchronized List<NotificationView> findAll() {
        List<NotificationView> notifications = new ArrayList<>();
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     select id, user_id, title, message, created_at
                     from workflow_notification
                     order by created_at desc, id desc
                     """)) {
            while (resultSet.next()) {
                notifications.add(new NotificationView(
                        resultSet.getLong("id"),
                        resultSet.getLong("user_id"),
                        resultSet.getString("title"),
                        resultSet.getString("message"),
                        resultSet.getTimestamp("created_at").toLocalDateTime()
                ));
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load notifications", exception);
        }
        return notifications;
    }

    private void ensureTable() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table if not exists workflow_notification (
                        id bigint primary key,
                        user_id bigint not null,
                        title text not null,
                        message text not null,
                        created_at timestamp not null
                    )
                    """);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize notification table", exception);
        }
    }

    private void syncSequence() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select coalesce(max(id), 1) from workflow_notification")) {
            if (resultSet.next()) {
                sequence.set(resultSet.getLong(1));
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sync notification sequence", exception);
        }
    }

    private Connection getConnection() throws Exception {
        return DriverManager.getConnection(url, username, password);
    }

    public record NotificationView(Long id, Long userId, String title, String message, LocalDateTime createdAt) {
    }
}
