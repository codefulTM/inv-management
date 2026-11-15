-- ============================================================
-- V2.1 — Add missing audit columns to category and product
--
-- Why: the `Category` and `Product` entities extend
-- `BaseAuditableEntity`, so Hibernate (ddl-auto=validate) requires
-- the created_at / updated_at / created_by / updated_by columns.
-- `V1__init.sql` only created created_at/updated_at for `product`,
-- while `category` has no audit columns at all.
--
-- Do not modify V1 (already merged) — add a new migration per rule 4.3.
-- ============================================================

-- category: add all four audit columns
ALTER TABLE category
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(100);

-- product: already has created_at/updated_at, missing created_by/updated_by
ALTER TABLE product
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(100);
