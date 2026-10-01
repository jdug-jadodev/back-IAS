DROP TABLE IF EXISTS credit_applications;
DROP TABLE IF EXISTS customers;

CREATE TABLE customers (
    customer_id TEXT PRIMARY KEY CHECK (btrim(customer_id) <> ''),
    status TEXT NOT NULL CHECK (status IN ('ELIGIBLE', 'BLOCKED')),
    approval_limit NUMERIC NOT NULL CHECK (
        approval_limit >= 0
        AND approval_limit NOT IN ('NaN'::NUMERIC, 'Infinity'::NUMERIC, '-Infinity'::NUMERIC)
    )
);

CREATE TABLE credit_applications (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_reference TEXT NOT NULL CHECK (btrim(application_reference) <> ''),
    requested_customer_id TEXT NOT NULL CHECK (btrim(requested_customer_id) <> ''),
    customer_id TEXT,
    amount NUMERIC NOT NULL CHECK (
        amount NOT IN ('NaN'::NUMERIC, 'Infinity'::NUMERIC, '-Infinity'::NUMERIC)
    ),
    term_months INTEGER NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('APPROVED', 'REJECTED')),
    reason_code TEXT,
    reason TEXT,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_credit_applications_reference UNIQUE (application_reference),
    CONSTRAINT fk_credit_applications_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE RESTRICT,
    CONSTRAINT ck_credit_applications_decision CHECK (
        (status = 'APPROVED' AND reason_code IS NULL AND reason IS NULL
            AND customer_id IS NOT NULL AND amount > 0 AND term_months BETWEEN 6 AND 60)
        OR (status = 'REJECTED' AND reason_code IS NOT NULL AND reason IS NOT NULL
            AND btrim(reason) <> '' AND reason_code IN (
                'INVALID_AMOUNT', 'INVALID_TERM', 'CUSTOMER_BLOCKED',
                'INSUFFICIENT_LIMIT', 'CUSTOMER_NOT_FOUND'
            ))
    ),
    CONSTRAINT ck_credit_applications_customer CHECK (
        (customer_id IS NULL AND status = 'REJECTED' AND reason_code = 'CUSTOMER_NOT_FOUND')
        OR (customer_id IS NOT NULL AND customer_id = requested_customer_id
            AND (reason_code IS NULL OR reason_code <> 'CUSTOMER_NOT_FOUND'))
    )
);

CREATE INDEX ix_credit_applications_customer_status ON credit_applications(customer_id, status);
CREATE INDEX ix_credit_applications_recent ON credit_applications(processed_at DESC, id DESC);

INSERT INTO customers (customer_id, status, approval_limit) VALUES
    ('CLI-1001', 'ELIGIBLE', 10000000),
    ('CLI-1002', 'BLOCKED', 8000000),
    ('CLI-2001', 'ELIGIBLE', 15000000);
