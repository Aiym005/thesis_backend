package com.tms.thesissystem.config;

import com.tms.thesissystem.persistence.entity.AuthAccountEntity;
import com.tms.thesissystem.persistence.repository.AuthAccountJpaRepository;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigurationPackage(basePackageClasses = AuthAccountEntity.class)
@EnableJpaRepositories(basePackageClasses = AuthAccountJpaRepository.class)
public class PersistenceConfiguration {
}
