package com.acme.sportplatform.events;

import com.acme.sportplatform.common.exception.BusinessException;
import java.util.*;
import java.util.regex.Pattern;

public record DiplomaSettings(boolean enabled, String title, String bodyTemplate, String footer, boolean showLogo, String backgroundFileKey) {
    public static final List<String> PLACEHOLDERS = List.of("lastName","firstName","birthYear","club","place","placeText","discipline","ageGroup","result","eventTitle","eventDates","venue","city");
    public static DiplomaSettings defaults() {
        return new DiplomaSettings(false,"ГРАМОТА","награждается {lastName} {firstName} ({birthYear}, {club}), занявший(ая) {place} место на дистанции {discipline} с результатом {result}","Главный судья ______ Главный секретарь ______",false,null);
    }
    public DiplomaSettings normalized() {
        var d=defaults();
        return new DiplomaSettings(enabled,title==null?d.title:title,bodyTemplate==null?d.bodyTemplate:bodyTemplate,footer==null?d.footer:footer,showLogo,backgroundFileKey);
    }
    public void validate() {
        var d=normalized();
        for (var text : List.of(d.title,d.bodyTemplate,d.footer)) {
            if (text.length()>4000) throw new BusinessException("documents.invalid_template","Текст шаблона не должен превышать 4000 символов");
            var matcher=Pattern.compile("\\{([^{}]*)}").matcher(text);
            while(matcher.find()) if (!PLACEHOLDERS.contains(matcher.group(1)))
                throw new BusinessException("documents.invalid_template","Допустимые плейсхолдеры: " + String.join(", ", PLACEHOLDERS));
        }
    }
    public static String placeText(int place) { return place + "-е"; }
    public String substitute(String template, Map<String,String> values) {
        validate();
        var matcher=Pattern.compile("\\{([^{}]*)}").matcher(template);
        var out=new StringBuffer();
        while(matcher.find()) matcher.appendReplacement(out,java.util.regex.Matcher.quoteReplacement(values.getOrDefault(matcher.group(1),"—")));
        matcher.appendTail(out); return out.toString();
    }
}
