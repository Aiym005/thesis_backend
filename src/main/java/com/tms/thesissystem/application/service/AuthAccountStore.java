package com.tms.thesissystem.application.service;

import com.tms.thesissystem.persistence.entity.AuthAccountEntity;
import com.tms.thesissystem.persistence.repository.AuthAccountJpaRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class AuthAccountStore {
    private final AuthAccountJpaRepository repository;
    private final Map<Long, AuthAccount> inMemoryAccounts = new LinkedHashMap<>();

    public AuthAccountStore(ObjectProvider<AuthAccountJpaRepository> repositoryProvider) {
        this.repository = repositoryProvider.getIfAvailable();
    }

    public Optional<AuthAccount> findByUsername(String usernameValue) {
        if (repository == null) {
            return inMemoryAccounts.values().stream()
                    .filter(account -> account.username().equalsIgnoreCase(usernameValue))
                    .findFirst();
        }
        return repository.findByUsernameIgnoreCase(usernameValue).map(this::map);
    }

    public List<AuthAccount> findAllByRole(String roleValue) {
        if (repository == null) {
            return inMemoryAccounts.values().stream()
                    .filter(account -> account.role().equalsIgnoreCase(roleValue))
                    .sorted(Comparator.comparing(AuthAccount::userId))
                    .toList();
        }
        return repository.findAll().stream()
                .map(this::map)
                .filter(account -> account.role().equalsIgnoreCase(roleValue))
                .sorted(Comparator.comparing(AuthAccount::userId))
                .toList();
    }

    @Transactional
    public AuthAccount save(Long userId, String usernameValue, String passwordHash, String role, String displayName) {
        LocalDateTime now = LocalDateTime.now();
        if (repository == null) {
            AuthAccount existing = inMemoryAccounts.get(userId);
            AuthAccount account = new AuthAccount(
                    userId,
                    usernameValue,
                    passwordHash,
                    role,
                    displayName,
                    existing == null ? now : existing.createdAt(),
                    now
            );
            inMemoryAccounts.put(userId, account);
            return account;
        }
        AuthAccountEntity entity = repository.findById(userId).orElseGet(AuthAccountEntity::new);
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUserId(userId);
        entity.setUsername(usernameValue);
        entity.setPasswordHash(passwordHash);
        entity.setRole(role);
        entity.setDisplayName(displayName);
        entity.setUpdatedAt(now);
        return map(repository.save(entity));
    }

    private AuthAccount map(AuthAccountEntity entity) {
        return new AuthAccount(
                entity.getUserId(),
                entity.getUsername(),
                entity.getPasswordHash(),
                entity.getRole(),
                entity.getDisplayName(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
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
