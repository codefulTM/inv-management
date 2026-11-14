package com.codefultm.inventory_management.testsupport;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton PostgreSQL 16 container shared by ALL tests in the JVM.
 *
 * <p>Why a singleton? Starting a database container takes 3-8 seconds. If every
 * test class started its own container, the suite would crawl. Here the container
 * is created exactly once (static field) and reused by every Spring context that
 * imports {@link TestcontainersConfig}.
 *
 * <p>Spring Boot starts the container automatically when it is registered as a
 * bean annotated with {@code @ServiceConnection} — see {@link TestcontainersConfig}.
 * If you ever need the container outside a Spring context (e.g. plain JUnit test),
 * call {@code PostgresContainer.getInstance().start()} yourself.
 *
 * <p>Image is pinned to {@code postgres:16-alpine} to match {@code docker-compose.yml}.
 */
public final class PostgresContainer {

    private static final DockerImageName IMAGE = DockerImageName.parse("postgres:16-alpine");

    private static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>(IMAGE)
            .withDatabaseName("inventory_test")
            .withUsername("test")
            .withPassword("test");

    private PostgresContainer() {
        // utility class
    }

    /**
     * @return the single container instance shared across the whole test JVM
     */
    public static PostgreSQLContainer<?> getInstance() {
        return INSTANCE;
    }
}
