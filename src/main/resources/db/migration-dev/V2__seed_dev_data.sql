-- ============================================================
-- V2__seed_dev_data.sql — Seed data cho profile dev
-- Migration này CHỈ chạy trong profile dev (migration-dev/)
-- KHÔNG commit dữ liệu nhạy cảm (password, token) vào migration
-- ============================================================

-- 1. Role definitions (3 roles cơ bản)
INSERT INTO role (name, description) VALUES
    ('ADMIN', 'Full access to all features'),
    ('STAFF', 'Can create and manage orders'),
    ('VIEWER', 'Read-only access');

-- 2. Warehouse
INSERT INTO warehouse (code, name, address) VALUES
    ('MAIN', 'Main Warehouse', '123 Industrial Street, District 1, Ho Chi Minh City');

-- 3. Categories
INSERT INTO category (name, description) VALUES
    ('Electronics', 'Electronic devices and components'),
    ('Furniture', 'Office and home furniture'),
    ('Stationery', 'Office supplies and stationery'),
    ('Clothing', 'Apparel and accessories');
