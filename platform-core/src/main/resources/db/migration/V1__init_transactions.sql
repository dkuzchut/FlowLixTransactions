CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_id UUID NOT NULL,
    merchant_id VARCHAR(255) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    status VARCHAR(32) NOT NULL,
    -- Event timestamp (producer-side). `created_at` is DB insert time.
    event_created_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    failure_reason TEXT,
    CONSTRAINT uq_transactions_external_id UNIQUE (external_id),
    CONSTRAINT chk_transactions_amount_non_negative CHECK (amount_minor >= 0),
    CONSTRAINT chk_transactions_status CHECK (status IN ('IN_PROGRESS', 'SUCCEEDED', 'FAILED'))
);

-- Finalizer claim path: filter by status and process oldest first for predictability.
CREATE INDEX idx_transactions_in_progress_created_at
    ON transactions (created_at)
    WHERE status = 'IN_PROGRESS';
