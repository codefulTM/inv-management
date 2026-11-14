package com.codefultm.inventory_management.testsupport;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for every full-stack integration test.
 *
 * <p>{@code @SpringBootTest} boots the whole application context, so all
 * repositories, services and controllers are real. {@code @ActiveProfiles("test")}
 * loads {@code application-test.yml}. {@code @Import(TestcontainersConfig.class)}
 * wires the shared PostgreSQL container into the context and Flyway then applies
 * {@code db/migration/V1__init.sql}.
 *
 * <p>Usage:
 * <pre>{@code
 * class ProductServiceIT extends AbstractIntegrationTest { ... }
 * }</pre>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
public abstract class AbstractIntegrationTest {
    // subclasses inherit everything
}
