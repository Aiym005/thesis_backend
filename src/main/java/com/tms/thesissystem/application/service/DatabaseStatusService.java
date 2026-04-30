package com.tms.thesissystem.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@Service
public class DatabaseStatusService {
    private final String host;
    private final int port;
    private final String name;
    private final String username;
    private final String url;
    private final DataSource dataSource;

    @Autowired
    public DatabaseStatusService(@Value("${app.database.host}") String host,
                                 @Value("${app.database.port}") int port,
                                 @Value("${app.database.name}") String name,
                                 @Value("${app.database.username}") String username,
                                 @Value("${app.database.url}") String url,
                                 ObjectProvider<DataSource> dataSourceProvider) {
        this.host = host;
        this.port = port;
        this.name = name;
        this.username = username;
        this.url = url;
        this.dataSource = dataSourceProvider.getIfAvailable();
    }

    DatabaseStatusService(String host,
                          int port,
                          String name,
                          String username,
                          String url) {
        this.host = host;
        this.port = port;
        this.name = name;
        this.username = username;
        this.url = url;
        this.dataSource = null;
    }

    public DatabaseStatus check() {
        if (dataSource == null) {
            return new DatabaseStatus(
                    false,
                    host,
                    port,
                    name,
                    username,
                    url,
                    null,
                    null,
                    "Datasource is not configured"
            );
        }
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select current_database(), current_user")) {
            if (resultSet.next()) {
                return new DatabaseStatus(
                        true,
                        host,
                        port,
                        name,
                        username,
                        url,
                        resultSet.getString(1),
                        resultSet.getString(2),
                        "Connection successful"
                );
            }
            return new DatabaseStatus(true, host, port, name, username, url, name, username, "Connection opened");
        } catch (Exception exception) {
            return new DatabaseStatus(
                    false,
                    host,
                    port,
                    name,
                    username,
                    url,
                    null,
                    null,
                    exception.getMessage()
            );
        }
    }

    public record DatabaseStatus(
            boolean connected,
            String host,
            int port,
            String database,
            String username,
            String url,
            String resolvedDatabase,
            String resolvedUser,
            String message
    ) {
    }
}
