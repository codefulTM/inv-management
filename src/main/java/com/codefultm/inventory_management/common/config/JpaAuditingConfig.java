package com.codefultm.inventory_management.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA Auditing.
 *
 * <p>Required so that {@code BaseAuditableEntity} automatically populates
 * {@code createdAt} ({@code @CreatedDate}) and {@code updatedAt}
 * ({@code @LastModifiedDate}). Without it, the {@code created_at NOT NULL}
 * column would be inserted as null.
 *
 * <p>Kept as a separate class (not attached to {@code @SpringBootApplication})
 * so slice tests such as {@code @DataJpaTest} can {@code @Import} it.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
