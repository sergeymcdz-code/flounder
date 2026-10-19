update users set status = 'blocked', password_hash = '!disabled', updated_at = now()
where id = '11111111-1111-1111-1111-111111111111';

delete from refresh_tokens where user_id = '11111111-1111-1111-1111-111111111111';
