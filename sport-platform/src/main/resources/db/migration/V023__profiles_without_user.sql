-- V023: профили без учётной записи (импорт из Excel) (ТЗ §3)
alter table profiles alter column user_id drop not null;

-- нормализованный ключ импорта: lower(фамилия|имя|дата|пол)
alter table profiles add column external_key varchar(255);

create unique index profiles_external_key_uq
    on profiles (external_key)
    where external_key is not null;
