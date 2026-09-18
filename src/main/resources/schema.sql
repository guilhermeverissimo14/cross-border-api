DROP TABLE IF EXISTS remittances CASCADE;
DROP TABLE IF EXISTS ledger_entries CASCADE;
DROP TABLE IF EXISTS wallets CASCADE;
DROP TABLE IF EXISTS exchange_rates CASCADE;
DROP TABLE IF EXISTS users CASCADE;

CREATE TABLE users (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name    VARCHAR(255)   NOT NULL,
    email        VARCHAR(255)   NOT NULL,
    password     VARCHAR(255)   NOT NULL,
    user_type    VARCHAR(2)     NOT NULL,
    document     VARCHAR(20)    NOT NULL,
    created_at   TIMESTAMP      NOT NULL,
    updated_at   TIMESTAMP      NOT NULL,
    CONSTRAINT uk_users_email    UNIQUE (email),
    CONSTRAINT uk_users_document UNIQUE (document)
);

CREATE TABLE wallets (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id  BIGINT         NOT NULL,
    currency VARCHAR(3)     NOT NULL,
    balance  DECIMAL(19, 4) NOT NULL DEFAULT 0,
    version  BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT fk_wallets_user          FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_wallets_user_currency UNIQUE (user_id, currency)
);

CREATE TABLE ledger_entries (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallet_id      BIGINT         NOT NULL,
    currency       VARCHAR(3)     NOT NULL,
    type           VARCHAR(10)    NOT NULL,
    amount         DECIMAL(19, 4) NOT NULL,
    reference_type VARCHAR(30)    NOT NULL,
    reference_id   BIGINT         NOT NULL,
    created_at     TIMESTAMP      NOT NULL,
    CONSTRAINT fk_ledger_entries_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id)
);

CREATE TABLE exchange_rates (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    quote_date DATE           NOT NULL,
    buy_rate   DECIMAL(19, 6) NOT NULL,
    fetched_at TIMESTAMP      NOT NULL,
    CONSTRAINT uk_exchange_rates_quote_date UNIQUE (quote_date)
);

CREATE TABLE remittances (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    sender_id      BIGINT         NOT NULL,
    receiver_id    BIGINT         NOT NULL,
    amount_brl     DECIMAL(19, 2) NOT NULL,
    amount_usd     DECIMAL(19, 2) NOT NULL,
    exchange_rate  DECIMAL(19, 6) NOT NULL,
    direction      VARCHAR(20)    NOT NULL,
    status         VARCHAR(20)    NOT NULL,
    details        TEXT,
    idempotency_key VARCHAR(64)   NOT NULL,
    created_at     TIMESTAMP      NOT NULL,
    CONSTRAINT fk_remittances_sender   FOREIGN KEY (sender_id)   REFERENCES users (id),
    CONSTRAINT fk_remittances_receiver FOREIGN KEY (receiver_id) REFERENCES users (id),
    CONSTRAINT uk_remittances_sender_idempotency_key UNIQUE (sender_id, idempotency_key)
);
