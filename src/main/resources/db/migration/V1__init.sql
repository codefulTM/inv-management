-- ============================================================
-- V1__init.sql — Initial schema for Inventory Management
-- This is the SINGLE source of truth for the database schema.
-- Hibernate operates in validate mode only.
-- ============================================================

-- 1. app_user — Application users (employees)
CREATE TABLE app_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    full_name VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'VIEWER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);

-- 2. role — Role definitions (if role table is separated)
CREATE TABLE role (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(200)
);

-- 3. refresh_token — JWT refresh tokens for session management
CREATE TABLE refresh_token (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMPTZ
);

-- 4. category — Product categories
CREATE TABLE category (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500)
);

-- 5. product — Products with pricing and stock thresholds
CREATE TABLE product (
    id BIGSERIAL PRIMARY KEY,
    sku VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    category_id BIGINT REFERENCES category(id),
    cost_price NUMERIC(19,2) NOT NULL CHECK (cost_price >= 0),
    sell_price NUMERIC(19,2) NOT NULL CHECK (sell_price >= 0),
    low_stock_threshold INT NOT NULL DEFAULT 10,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);

-- 6. warehouse — Warehouse/location definitions
CREATE TABLE warehouse (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    address TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 7. supplier — Suppliers/Vendors
CREATE TABLE supplier (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(20),
    address TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 8. stock_item — Current stock level per product per warehouse
CREATE TABLE stock_item (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES product(id),
    warehouse_id BIGINT NOT NULL REFERENCES warehouse(id),
    quantity INT NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    version INT NOT NULL DEFAULT 0,
    UNIQUE (product_id, warehouse_id)
);

-- 9. stock_movement — Append-only audit trail for stock changes
CREATE TABLE stock_movement (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES product(id),
    warehouse_id BIGINT NOT NULL REFERENCES warehouse(id),
    movement_type VARCHAR(20) NOT NULL,
    quantity_delta INT NOT NULL,
    reason VARCHAR(200) NOT NULL,
    reference VARCHAR(100),
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 10. purchase_order — Purchase orders (incoming goods)
CREATE TABLE purchase_order (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    supplier_id BIGINT REFERENCES supplier(id),
    total_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);

-- 10b. purchase_order_line — Lines within a purchase order
CREATE TABLE purchase_order_line (
    id BIGSERIAL PRIMARY KEY,
    purchase_order_id BIGINT NOT NULL REFERENCES purchase_order(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES product(id),
    warehouse_id BIGINT NOT NULL REFERENCES warehouse(id),
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_cost NUMERIC(19,2) NOT NULL CHECK (unit_cost >= 0)
);

-- 11. sales_order — Sales orders (outgoing goods)
CREATE TABLE sales_order (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    total_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);

-- 11b. sales_order_line — Lines within a sales order
CREATE TABLE sales_order_line (
    id BIGSERIAL PRIMARY KEY,
    sales_order_id BIGINT NOT NULL REFERENCES sales_order(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES product(id),
    warehouse_id BIGINT NOT NULL REFERENCES warehouse(id),
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(19,2) NOT NULL CHECK (unit_price >= 0)
);

-- 12. audit_log — General audit trail for all entity changes
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    actor VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT NOT NULL,
    old_value JSONB,
    new_value JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- Indexes for common query patterns
-- ============================================================

-- Stock movements by product, newest first
CREATE INDEX idx_stock_movement_product_created
    ON stock_movement (product_id, created_at DESC);

-- Product lookup by SKU (already has UNIQUE constraint, but explicit for clarity)
CREATE INDEX idx_product_sku ON product (sku);

-- Sales order lookup by idempotency key (already has UNIQUE constraint)
CREATE INDEX idx_sales_order_idempotency
    ON sales_order (idempotency_key);

-- Audit log queries by entity
CREATE INDEX idx_audit_log_entity
    ON audit_log (entity_type, entity_id);

-- Audit log queries by actor
CREATE INDEX idx_audit_log_actor
    ON audit_log (actor);

-- Audit log queries by time range
CREATE INDEX idx_audit_log_created
    ON audit_log (created_at DESC);
