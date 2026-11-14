package com.codefultm.inventory_management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.codefultm.inventory_management.testsupport.AbstractIntegrationTest;

/**
 * Smoke test for the Phase 2 test harness.
 *
 * <p>Proves three things at once:
 * <ol>
 *   <li>the Testcontainers PostgreSQL instance starts and Spring connects to it,</li>
 *   <li>Flyway ran {@code V1__init.sql} (tables exist),</li>
 *   <li>the DB is real PostgreSQL with the CHECK constraint from the schema.</li>
 * </ol>
 */
class InventoryManagementApplicationTests extends AbstractIntegrationTest {

    private final JdbcTemplate jdbcTemplate;

    InventoryManagementApplicationTests(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void contextLoads() {
        // context started => container + datasource + Flyway all worked
    }

    @Test
    void flywayMigrationsAreApplied() {
        Integer tables = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public'",
                Integer.class);

        // V1__init.sql creates 14 tables (12 entity tables + 2 order-line tables)
        assertThat(tables).isGreaterThanOrEqualTo(14);
    }

    @Test
    void databaseIsPostgresNotH2() {
        String version = jdbcTemplate.queryForObject("SELECT version()", String.class);

        assertThat(version).containsIgnoringCase("PostgreSQL");
    }

    @Test
    void stockItemRejectsNegativeQuantity() {
        Long productId = jdbcTemplate.queryForObject("""
                INSERT INTO product (sku, name, cost_price, sell_price)
                VALUES ('SMOKE-1', 'Smoke Product', 1.00, 2.00)
                RETURNING id
                """, Long.class);

        Long warehouseId = jdbcTemplate.queryForObject("""
                INSERT INTO warehouse (code, name)
                VALUES ('SMOKE-WH', 'Smoke Warehouse')
                RETURNING id
                """, Long.class);

        // CHECK (quantity >= 0) must be enforced by the real database
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO stock_item (product_id, warehouse_id, quantity) VALUES (?, ?, -1)",
                productId, warehouseId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

