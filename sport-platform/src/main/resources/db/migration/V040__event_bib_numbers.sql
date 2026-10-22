create table event_bib_numbers (
  event_id uuid not null references events(id) on delete cascade,
  profile_id uuid not null references profiles(id),
  bib_number integer not null check (bib_number > 0),
  assigned_at timestamptz not null default now(),
  primary key (event_id, profile_id),
  unique (event_id, bib_number)
);
