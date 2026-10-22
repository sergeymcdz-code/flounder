alter table organizations
  add column public_slug varchar(128),
  add column description text,
  add column website varchar(255),
  add column public_email varchar(255),
  add column public_phone varchar(32),
  add column is_public boolean not null default true,
  add column logo_file_key varchar(255);
create unique index uq_organizations_public_slug on organizations(public_slug) where public_slug is not null;
