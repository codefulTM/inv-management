package com.codefultm.inventory_management.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Exposes the shared PostgreSQL container as a Spring bean.
 *
 * <p>{@code @ServiceConnection} tells Spring Boot: "this bean is a service container,
 * figure out how to connect to it yourself". Boot then creates the JDBC connection
 * details (url / username / password) from the running container, so no
 * {@code @DynamicPropertySource} and no {@code spring.datasource.*} entries in
 * {@code application-test.yml} are needed.
 *
 * <p>Usable from any test slice:
 * <ul>
 *   <li>{@code @SpringBootTest} — inherited via {@link AbstractIntegrationTest}</li>
 *   <li>{@code @DataJpaTest} / {@code @WebMvcTest} — add {@code @Import(TestcontainersConfig.class)}</li>
 * </ul>
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return PostgresContainer.getInstance();
    }
}
