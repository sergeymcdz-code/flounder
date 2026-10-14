CREATE TABLE notification_outbox (
    id uuid PRIMARY KEY,
    event_id uuid REFERENCES events(id) ON DELETE CASCADE,
    recipient_email varchar(255) NOT NULL,
    template_code varchar(64) NOT NULL,
    payload jsonb NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','FAILED')),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
    last_error varchar(255),
    created_at timestamptz NOT NULL DEFAULT now(),
    sent_at timestamptz,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    delivery_key varchar(255) NOT NULL UNIQUE
);
CREATE INDEX notification_outbox_status_created_idx ON notification_outbox(status, created_at);
CREATE INDEX notification_outbox_event_created_idx ON notification_outbox(event_id, created_at);

CREATE TABLE organization_notification_settings (
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    template_code varchar(64) NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    PRIMARY KEY (organization_id, template_code)
);
