package com.acme.sportplatform.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.common.MailSender;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.identity.domain.PasswordPolicy;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.PasswordResetTokenEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.*;
import com.acme.sportplatform.identity.infrastructure.security.OpaqueTokens;

@Service
public class PasswordService {
    private final UserRepository users;
    private final PasswordResetTokenRepository resets;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final MailSender mail;
    private final Clock clock;
    private final String publicUrl;

    public PasswordService(UserRepository users, PasswordResetTokenRepository resets, RefreshTokenRepository refreshTokens,
            PasswordEncoder encoder, MailSender mail, @Qualifier("identityClock") Clock clock,
            @Value("${app.public-url:http://localhost:5173}") String publicUrl) {
        this.users = users; this.resets = resets; this.refreshTokens = refreshTokens;
        this.encoder = encoder; this.mail = mail; this.clock = clock;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    @Transactional
    public void change(UUID userId, String currentPassword, String newPassword) {
        var user = users.findByIdForUpdate(userId).orElseThrow(() ->
                new BusinessException("identity.user_not_found", "Пользователь не найден"));
        if (!encoder.matches(currentPassword, user.getPasswordHash()))
            throw new BusinessException("identity.password_mismatch", "Текущий пароль указан неверно");
        PasswordPolicy.validate(newPassword);
        user.setPasswordHash(encoder.encode(newPassword));
        refreshTokens.invalidateAll(userId, clock.instant());
        resets.invalidateAll(userId, clock.instant());
    }

    @Transactional
    public void changeByAdministrator(UUID userId, String newPassword) {
        // Preserve M01's password contract while invalidating M02's new credentials.
        if (newPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new BusinessException("identity.invalid_password", "Пароль не должен превышать 72 байта в UTF-8");
        var user = users.findByIdForUpdate(userId).orElseThrow(() ->
                new BusinessException("identity.user_not_found", "Пользователь не найден"));
        user.setPasswordHash(encoder.encode(newPassword));
        refreshTokens.invalidateAll(userId, clock.instant());
        resets.invalidateAll(userId, clock.instant());
    }

    @Transactional
    public void requestReset(String email) {
        var found = users.findByEmailIgnoreCase(email.trim());
        if (found.isEmpty()) return;
        var user = users.findByIdForUpdate(found.get().getId()).orElseThrow(this::invalidReset);
        if (!"active".equalsIgnoreCase(user.getStatus())) return;
        resets.invalidateAll(user.getId(), clock.instant());
        String raw = OpaqueTokens.generate();
        var token = new PasswordResetTokenEntity();
        token.setUserId(user.getId()); token.setTokenHash(OpaqueTokens.hash(raw));
        token.setCreatedAt(clock.instant()); token.setExpiresAt(clock.instant().plus(Duration.ofHours(1)));
        resets.save(token);
        mail.send(user.getEmail(), "Восстановление пароля SportGate",
                "Для восстановления пароля перейдите по ссылке: " + publicUrl + "/reset-password?token=" + raw
                        + "\nСсылка действует 1 час. Если вы не запрашивали восстановление, проигнорируйте письмо.");
    }

    @Transactional
    public void confirmReset(String rawToken, String newPassword) {
        String hash = OpaqueTokens.hash(rawToken);
        var snapshot = resets.findUserIdByTokenHash(hash).orElseThrow(this::invalidReset);
        var user = users.findByIdForUpdate(snapshot).orElseThrow(this::invalidReset);
        var token = resets.findForUpdate(hash).orElseThrow(this::invalidReset);
        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(clock.instant())
                || !"active".equalsIgnoreCase(user.getStatus())) throw invalidReset();
        PasswordPolicy.validate(newPassword);
        token.setUsedAt(clock.instant());
        user.setPasswordHash(encoder.encode(newPassword));
        resets.invalidateAll(user.getId(), clock.instant());
        refreshTokens.invalidateAll(user.getId(), clock.instant());
    }

    private BusinessException invalidReset() {
        return new BusinessException("identity.reset_token_invalid", "Ссылка восстановления недействительна или истекла");
    }
}
