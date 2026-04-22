package com.tms.thesissystem.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class AuthAccountStore {
    private final String url;
    private final String username;
    private final String password;

    public AuthAccountStore(@Value("${app.database.url}") String url,
                            @Value("${app.database.username}") String username,
                            @Value("${app.database.password}") String password) {
        this.url = url;
        this.username = username;
        this.password = password;
        ensureTable();
    }

    public Optional<AuthAccount> findByUserId(Long userId) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select user_id, username, password_hash, role, display_name, created_at, updated_at
                     from auth_account
                     where user_id = ?
                     """)) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(map(resultSet));
                }
            }
            return Optional.empty();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load auth account", exception);
        }
    }

    public Optional<AuthAccount> findByUsername(String usernameValue) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select user_id, username, password_hash, role, display_name, created_at, updated_at
                     from auth_account
                     where lower(username) = lower(?)
                     """)) {
            statement.setString(1, usernameValue);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(map(resultSet));
                }
            }
            return Optional.empty();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load auth account by username", exception);
        }
    }

    public Long nextUserId() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select nextval('auth_account_user_id_seq')")) {
            resultSet.next();
            return resultSet.getLong(1);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate auth account id", exception);
        }
    }

    public AuthAccount save(Long userId, String usernameValue, String passwordHash, String role, String displayName) {
        LocalDateTime now = LocalDateTime.now();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into auth_account(user_id, username, password_hash, role, display_name, created_at, updated_at)
                     values (?, ?, ?, ?, ?, ?, ?)
                     on conflict (user_id) do update
                     set username = excluded.username,
                         password_hash = excluded.password_hash,
                         role = excluded.role,
                         display_name = excluded.display_name,
                         updated_at = excluded.updated_at
                     """)) {
            statement.setLong(1, userId);
            statement.setString(2, usernameValue);
            statement.setString(3, passwordHash);
            statement.setString(4, role);
            statement.setString(5, displayName);
            statement.setTimestamp(6, Timestamp.valueOf(now));
            statement.setTimestamp(7, Timestamp.valueOf(now));
            statement.executeUpdate();
            return new AuthAccount(userId, usernameValue, passwordHash, role, displayName, now, now);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to save auth account", exception);
        }
    }

    private AuthAccount map(ResultSet resultSet) throws Exception {
        return new AuthAccount(
                resultSet.getLong("user_id"),
                resultSet.getString("username"),
                resultSet.getString("password_hash"),
                resultSet.getString("role"),
                resultSet.getString("display_name"),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                resultSet.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private void ensureTable() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("create sequence if not exists auth_account_user_id_seq start with 1 increment by 1");
            statement.execute("""
                    create table if not exists auth_account (
                        user_id bigint primary key,
                        username text not null unique,
                        password_hash text not null,
                        role text not null default 'student',
                        display_name text,
                        created_at timestamp not null,
                        updated_at timestamp not null
                    )
                    """);
            statement.execute("alter table auth_account add column if not exists role text not null default 'student'");
            statement.execute("alter table auth_account add column if not exists display_name text");
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize auth account table", exception);
        }
    }

    private Connection getConnection() throws Exception {
        return DriverManager.getConnection(url, username, password);
    }

    public record AuthAccount(
            Long userId,
            String username,
            String passwordHash,
            String role,
            String displayName,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
