package com.acme.sportplatform.notifications.domain;

import com.acme.sportplatform.common.exception.BusinessException;
import java.util.Arrays;
import java.util.List;

public enum NotificationTemplate {
    registration_submitted("Заявка принята"),
    registration_confirmed("Заявка подтверждена"),
    payment_reminder("Напоминание об оплате участия"),
    registration_rejected("Заявка отклонена"),
    start_list_published("Стартовый протокол опубликован"),
    results_published("Результаты опубликованы"),
    organization_invitation("Приглашение в организацию SportGate"),
    schedule_changed("Изменилось расписание"),
    password_reset("Восстановление пароля SportGate");

    private final String subject;
    NotificationTemplate(String subject) { this.subject = subject; }
    public String subject() { return subject; }
    public static List<NotificationTemplate> organizerTemplates() {
        return Arrays.stream(values()).filter(t -> t != password_reset && t != organization_invitation).toList();
    }
    public static NotificationTemplate organizerTemplate(String code) {
        return organizerTemplates().stream().filter(t -> t.name().equals(code)).findFirst()
                .orElseThrow(() -> new BusinessException("notifications.invalid_template", "Неизвестный шаблон уведомления"));
    }
}
