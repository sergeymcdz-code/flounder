package com.acme.sportplatform.events;

import com.acme.sportplatform.common.exception.BusinessException;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record DiplomaSettings(
        Boolean enabled,
        String title,
        String bodyTemplate,
        String footer,
        Boolean showLogo,
        String backgroundFileKey) {
    public static final List<String> PLACEHOLDERS =
            List.of(
                    "lastName",
                    "firstName",
                    "birthYear",
                    "club",
                    "place",
                    "placeText",
                    "discipline",
                    "ageGroup",
                    "result",
                    "eventTitle",
                    "eventDates",
                    "venue",
                    "city");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]*)}");

    public static DiplomaSettings defaults() {
        return new DiplomaSettings(
                false,
                "ГРАМОТА",
                "награждается {lastName} {firstName} ({birthYear}, {club}), занявший(ая) {place}"
                    + " место на дистанции {discipline} с результатом {result}",
                "Главный судья ______ Главный секретарь ______",
                false,
                null);
    }

    public DiplomaSettings normalized() {
        return mergeWith(defaults());
    }

    /** Null fields preserve the current settings; an empty background key explicitly clears it. */
    public DiplomaSettings mergeWith(DiplomaSettings current) {
        return new DiplomaSettings(
                enabled == null ? current.enabled : enabled,
                title == null ? current.title : title,
                bodyTemplate == null ? current.bodyTemplate : bodyTemplate,
                footer == null ? current.footer : footer,
                showLogo == null ? current.showLogo : showLogo,
                backgroundFileKey == null
                        ? current.backgroundFileKey
                        : backgroundFileKey.isEmpty() ? null : backgroundFileKey);
    }

    public void validate() {
        var settings = normalized();
        for (var text : List.of(settings.title, settings.bodyTemplate, settings.footer)) {
            if (text.length() > 4000) {
                throw new BusinessException(
                        "documents.invalid_template",
                        "Текст шаблона не должен превышать 4000 символов");
            }
            var matcher = PLACEHOLDER.matcher(text);
            while (matcher.find()) {
                if (!PLACEHOLDERS.contains(matcher.group(1))) {
                    throw new BusinessException(
                            "documents.invalid_template",
                            "Допустимые плейсхолдеры: " + String.join(", ", PLACEHOLDERS));
                }
            }
        }
    }

    public static String placeText(int place) {
        return place + "-е";
    }

    public String substitute(String template, Map<String, String> values) {
        validate();
        var matcher = PLACEHOLDER.matcher(template);
        var output = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(
                    output, Matcher.quoteReplacement(values.getOrDefault(matcher.group(1), "—")));
        }
        matcher.appendTail(output);
        return output.toString();
    }
}
