package com.acme.sportplatform.analytics.application;

import com.acme.sportplatform.analytics.api.*;
import com.acme.sportplatform.common.exception.BusinessException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsExport {
    public byte[] event(EventAnalytics a) {
        try (var w=new XSSFWorkbook()) {
            var overview=sheet(w,"Обзор","Показатель","Значение");
            row(overview,"Событие",a.title());row(overview,"ID события",a.eventId());
            row(overview,"Часовой пояс",a.timezone());row(overview,"Данные на",a.generatedAt());
            row(overview,"Всего заявок",a.registrations().total());row(overview,"Участники",a.registrations().participants());
            var status=sheet(w,"Статусы заявок","Статус","Количество");
            a.registrations().byStatus().forEach(v -> row(status,v.key(),v.count()));
            var days=sheet(w,"Заявки по дням","Дата","Количество");
            a.registrations().byDay().forEach(v -> row(days,v.date(),v.count()));
            var funnel=sheet(w,"Воронка","Этап","Количество");var f=a.registrations().funnel();
            row(funnel,"Подано",f.submitted());row(funnel,"Подтверждено",f.confirmed());
            row(funnel,"Оплачено / без взноса",f.paid());row(funnel,"Допущено в посев",f.admitted());
            var disciplines=sheet(w,"Виды","ID","Вид","Заявки","Заплывы","Участники");
            a.byDiscipline().forEach(v -> row(disciplines,v.id(),v.name(),v.registrations(),v.heats(),v.participants()));
            breakdown(w,"Возрастные группы",a.byAgeGroup());breakdown(w,"Клубы",a.byClub());breakdown(w,"Пол",a.byGender());
            var sessions=sheet(w,"Сессии","ID","Сессия","Заплывы","Длительность, мин","Расчётное окончание","Задержка, сек");
            a.sessions().forEach(v -> row(sessions,v.id(),v.name(),v.heats(),v.estimatedDurationMin(),v.estimatedEndsAt(),v.delaySec()));
            var results=sheet(w,"Результаты","Показатель","Значение");var r=a.results();
            row(results,"Опубликовано заплывов",r.publishedHeats());row(results,"Медиана публикации, сек",r.medianPublicationSec());
            row(results,"p95 публикации, сек",r.p95PublicationSec());row(results,"Измерений задержки",r.latencySamples());
            row(results,"Источник задержки",r.latencySource());row(results,"Исправленные результаты",r.corrected());
            row(results,"Определение задержки","От фактического финиша до первого RANKING по виду; без него — последнее обновление результатов заплыва. Отрицательные и отсутствующие интервалы исключены.");
            row(results,"Пояснение источника","RANKING_LOG — все измерения по журналу; RESULTS_UPDATED_AT — есть измерения по обновлению результатов; UNAVAILABLE — нет данных.");
            var validator=sheet(w,"Валидатор","Прогон","Время","Ошибки","Предупреждения");var v=a.validator();
            row(validator,v.runId(),v.executedAt(),v.errors(),v.warnings());
            var imports=sheet(w,"Импорты","Доступно","Причина","Время","Строк","Создано","Обновлено","Ошибок","Длительность, мс");
            row(imports,a.importStats().available(),a.importStats().reason());
            a.importStats().recent().forEach(i -> row(imports,true,null,i.executedAt(),i.rows(),i.created(),i.updated(),i.errors(),i.durationMs()));
            return bytes(w);
        } catch(IOException ex) { throw failure(); }
    }
    public byte[] organization(OrganizationAnalytics a) {
        try(var w=new XSSFWorkbook()) {
            var overview=sheet(w,"Организация","Показатель","Значение");
            row(overview,"Организация",a.name());row(overview,"ID",a.organizationId());row(overview,"Данные на",a.generatedAt());
            row(overview,"События",a.events());row(overview,"Участия в событиях",a.participants());
            row(overview,"Уникальные участники",a.uniqueParticipants());row(overview,"Повторные участники",a.repeatParticipants());
            row(overview,"Средний размер события",a.averageEventSize());
            var statuses=sheet(w,"События по статусам","Статус","События");
            a.byStatus().forEach(v -> row(statuses,v.key(),v.count()));
            var revenue=sheet(w,"Выручка","Валюта","Оплачено");a.revenue().forEach(v -> row(revenue,v.currency(),v.amount()));
            return bytes(w);
        } catch(IOException ex) { throw failure(); }
    }
    private void breakdown(Workbook w,String title,List<EventAnalytics.Breakdown> values) {
        var s=sheet(w,title,"Код","Название","Заявки","Участники");
        values.forEach(v -> row(s,v.key(),v.name(),v.registrations(),v.participants()));
    }
    private Sheet sheet(Workbook w,String name,String... headers) {
        var s=w.createSheet(name);row(s,(Object[])headers);s.createFreezePane(0,1);
        var style=w.createCellStyle();var font=w.createFont();font.setBold(true);style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        for(var c:s.getRow(0))c.setCellStyle(style);
        return s;
    }
    private void row(Sheet s,Object... values) {
        var row=s.createRow(s.getPhysicalNumberOfRows()==0 ? 0 : s.getLastRowNum()+1);
        for(int i=0;i<values.length;i++) {
            var c=row.createCell(i);var value=values[i];
            if(value instanceof Number n)c.setCellValue(n.doubleValue());
            else if(value instanceof Boolean b)c.setCellValue(b);
            else if(value!=null)c.setCellValue(value.toString());
        }
    }
    private byte[] bytes(XSSFWorkbook w) throws IOException {
        for(var s:w) {
            for(int col=0;col<s.getRow(0).getLastCellNum();col++) {
                int width = col == 0 ? 36 : 18;
                if (col == 0 && (s.getSheetName().equals("Виды") || s.getSheetName().equals("Сессии"))) width = 40;
                s.setColumnWidth(col, 256 * width);
            }
            s.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,s.getLastRowNum(),0,s.getRow(0).getLastCellNum()-1));
        }
        try(var out=new ByteArrayOutputStream()) { w.write(out);return out.toByteArray(); }
    }
    private BusinessException failure() { return new BusinessException("analytics.export_failed","Не удалось сформировать файл аналитики"); }
}
