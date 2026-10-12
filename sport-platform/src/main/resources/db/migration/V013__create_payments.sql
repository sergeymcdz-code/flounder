create table payments (
    id uuid primary key,

    registration_id uuid not null unique,

    amount numeric(12, 2) not null,
    currency varchar(3) not null,

    status varchar(32) not null,
    provider varchar(32) not null,

    external_payment_id varchar(128) null,
    idempotency_key uuid not null unique,
    confirmation_url text null,

    provider_metadata jsonb not null default '{}'::jsonb,

    expires_at timestamptz null,
    paid_at timestamptz null,
    canceled_at timestamptz null,

    created_at timestamptz not null,
    updated_at timestamptz not null,

    constraint fk_payments_registration
        foreign key (registration_id)
        references registrations (id),

    constraint chk_payments_amount
        check (amount > 0),

    constraint chk_payments_currency
        check (
            char_length(currency) = 3
            and currency = upper(currency)
        ),

    constraint uq_payments_provider_external_payment_id
        unique (provider, external_payment_id)
);

create index idx_payments_registration_id
    on payments (registration_id);

create index idx_payments_status
    on payments (status);

create table payment_webhook_events (
    id uuid primary key,

    payment_id uuid null,

    provider varchar(32) not null,
    external_event_id varchar(255) null,
    deduplication_key varchar(128) not null unique,

    event_type varchar(128) not null,
    payload text not null,
    headers_json jsonb not null default '{}'::jsonb,

    processing_status varchar(32) not null,
    processing_error text null,

    received_at timestamptz not null,
    processed_at timestamptz null,

    constraint fk_payment_webhook_events_payment
        foreign key (payment_id)
        references payments (id)
);

create index idx_payment_webhook_events_payment_id
    on payment_webhook_events (payment_id);

create index idx_payment_webhook_events_provider_event
    on payment_webhook_events (provider, external_event_id);

create index idx_payment_webhook_events_processing_status
    on payment_webhook_events (processing_status);