DROP TABLE IF EXISTS credit_applications;
DROP TABLE IF EXISTS customers;
DROP FUNCTION IF EXISTS next_application_reference();
DROP SEQUENCE IF EXISTS credit_applications_reference_seq;

CREATE SEQUENCE credit_applications_reference_seq AS BIGINT START WITH 1;
CREATE FUNCTION next_application_reference() RETURNS TEXT
LANGUAGE SQL VOLATILE AS '
    SELECT ''REF-'' || CASE WHEN length(value) < 3 THEN lpad(value, 3, ''0'') ELSE value END
    FROM (SELECT nextval(''credit_applications_reference_seq'')::TEXT AS value) sequence_value
';

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
    application_reference TEXT NOT NULL DEFAULT next_application_reference() CHECK (btrim(application_reference) <> ''),
    idempotency_key TEXT NOT NULL CHECK (idempotency_key ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'),
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
    CONSTRAINT uq_credit_applications_idempotency_key UNIQUE (idempotency_key),
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
        customer_id IS NULL OR customer_id = requested_customer_id
    ),
    CONSTRAINT ck_credit_applications_customer_not_found CHECK (
        reason_code IS DISTINCT FROM 'CUSTOMER_NOT_FOUND' OR customer_id IS NULL
    )
);

CREATE INDEX ix_credit_applications_customer_status ON credit_applications(customer_id, status);
CREATE INDEX ix_credit_applications_recent ON credit_applications(processed_at DESC, id DESC);

INSERT INTO customers (customer_id, status, approval_limit) VALUES
    ('CLI-1001', 'ELIGIBLE', 10000000),
    ('CLI-1002', 'BLOCKED', 8000000),
    ('CLI-2001', 'ELIGIBLE', 15000000);
