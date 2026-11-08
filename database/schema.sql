-- TindaTrack - Day 1, Task 3
-- Source: RTM Part D, (D.3) Schema.pdf, with money rules from NFR-05.
-- Requires MySQL 8.0.16 or newer so CHECK constraints are enforced.
--
-- The RTM defines table/column names, primary keys, and customer relationships.
-- SQL types, lengths, defaults, and the additional validation constraints below
-- are implementation choices documented in README.md.
--
-- This script creates missing objects only. It does not migrate existing tables,
-- drop objects, change existing rows, or insert sample data.

CREATE DATABASE IF NOT EXISTS tindatrack
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE tindatrack;

-- Authentication: store password hashes, never plaintext passwords.
CREATE TABLE IF NOT EXISTS users (
    user_id INT NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(100) NOT NULL,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id),
    CONSTRAINT uq_users_username UNIQUE (username)
) ENGINE = InnoDB;

-- Inventory: expiration_date may be NULL for products without an expiry date.
CREATE TABLE IF NOT EXISTS products (
    product_id INT NOT NULL AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    stock_qty INT NOT NULL DEFAULT 0,
    expiration_date DATE NULL,

    PRIMARY KEY (product_id),
    CONSTRAINT chk_products_price CHECK (price >= 0),
    CONSTRAINT chk_products_stock_qty CHECK (stock_qty >= 0)
) ENGINE = InnoDB;

-- Credit: create customers before the tables that reference them.
CREATE TABLE IF NOT EXISTS customers (
    customer_id INT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    balance DECIMAL(10,2) NOT NULL DEFAULT 0.00,

    PRIMARY KEY (customer_id),
    CONSTRAINT chk_customers_balance CHECK (balance >= 0)
) ENGINE = InnoDB;

-- Keep the RTM's single items field; do not add an unrequested line-item table.
CREATE TABLE IF NOT EXISTS credit_transactions (
    transaction_id INT NOT NULL AUTO_INCREMENT,
    customer_id INT NOT NULL,
    items TEXT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    transaction_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (transaction_id),
    CONSTRAINT chk_credit_transactions_amount CHECK (amount > 0),
    CONSTRAINT fk_credit_transactions_customer
        FOREIGN KEY (customer_id) REFERENCES customers (customer_id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS payments (
    payment_id INT NOT NULL AUTO_INCREMENT,
    customer_id INT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    payment_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (payment_id),
    CONSTRAINT chk_payments_amount CHECK (amount > 0),
    CONSTRAINT fk_payments_customer
        FOREIGN KEY (customer_id) REFERENCES customers (customer_id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE = InnoDB;
