DO $$
BEGIN
    IF EXISTS (SELECT lower(email) FROM users GROUP BY lower(email) HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'V051: найдены повторяющиеся email без учёта регистра. Устраните дубли вручную перед повтором миграции.';
    END IF;
END $$;

UPDATE users SET email = lower(email);
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));
ALTER TABLE users ADD COLUMN tokens_valid_after timestamptz;
