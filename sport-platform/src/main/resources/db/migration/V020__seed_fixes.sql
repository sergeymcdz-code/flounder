-- V020: исправления seed (T01 п.4)
-- 1) роль judge (используется в RoleCodes.JUDGE и @PreAuthorize, но не была засеяна в V002)
-- 2) seed-админу из V002 — рабочий bcrypt-хеш пароля Demo2026! и email admin@flounder.demo

insert into roles (code, name)
values ('judge', 'Judge')
on conflict (code) do nothing;

-- bcrypt(cost=10) для "Demo2026!"; формат $2a$ совместим с BCryptPasswordEncoder(10).
update users
set email         = 'admin@flounder.demo',
    password_hash = '$2a$10$ywQlLXDOvyEGIe9oLGjWKee1pbyIkzOmAWQB0D8lYKc.E.dGJp2jC',
    status        = 'active',
    updated_at    = now()
where id = '11111111-1111-1111-1111-111111111111'
  and not exists (select 1 from users u where u.email = 'admin@flounder.demo' and u.id <> '11111111-1111-1111-1111-111111111111');
