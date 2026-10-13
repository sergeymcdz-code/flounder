create table participant_documents (
    id uuid primary key,
    profile_id uuid not null references profiles(id),
    event_id uuid references events(id),
    type varchar(32) not null check (type in ('MEDICAL_CERT','INSURANCE','PARENT_CONSENT','PDN_CONSENT')),
    file_key varchar(255) not null unique,
    original_name varchar(255) not null,
    content_type varchar(255) not null check (content_type in ('application/pdf','image/jpeg','image/png')),
    size_bytes bigint not null check (size_bytes > 0 and size_bytes <= 10485760),
    issued_at date,
    valid_until date,
    status varchar(255) not null check (status in ('NOT_UPLOADED','PENDING','ACCEPTED','REJECTED')),
    reject_reason text,
    uploaded_by uuid not null references users(id),
    uploaded_at timestamptz not null,
    reviewed_by uuid references users(id),
    reviewed_at timestamptz,
    check (issued_at is null or valid_until is null or issued_at <= valid_until)
);
create index ix_participant_documents_profile on participant_documents(profile_id);
create index ix_participant_documents_event on participant_documents(event_id);
create index ix_participant_documents_status on participant_documents(status);
create table file_access_log (
    id uuid primary key,
    file_key varchar(255),
    document_id uuid,
    user_id uuid references users(id) on delete set null,
    action varchar(255) not null check (action in ('UPLOAD','SIGN_DOWNLOAD','DOWNLOAD','DELETE','DENIED')),
    at timestamptz not null
);
create index ix_file_access_log_file_at on file_access_log(file_key, at);
create index ix_file_access_log_user_at on file_access_log(user_id, at);
