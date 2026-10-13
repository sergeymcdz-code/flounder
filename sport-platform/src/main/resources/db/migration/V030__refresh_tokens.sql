create table refresh_tokens (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    token_hash varchar(64) not null unique,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    user_agent varchar(512),
    created_at timestamptz not null default now()
);
create index idx_refresh_tokens_user_id on refresh_tokens(user_id);
