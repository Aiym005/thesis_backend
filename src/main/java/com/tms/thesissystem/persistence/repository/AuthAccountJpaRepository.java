package com.tms.thesissystem.persistence.repository;

import com.tms.thesissystem.persistence.entity.AuthAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthAccountJpaRepository extends JpaRepository<AuthAccountEntity, Long> {
    Optional<AuthAccountEntity> findByUsernameIgnoreCase(String username);
}
