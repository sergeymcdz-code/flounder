package com.acme.sportplatform.registrations.application;

import com.acme.sportplatform.registrations.api.PaymentSummary;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.*;

@Service
public class PaymentSummaryExport {
    public byte[] export(PaymentSummary summary) {
        try (var workbook = new XSSFWorkbook();
                var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Оплата по клубам");
            String[] headers = {
                "Клуб", "Участники", "Заявки", "Начислено", "Оплачено", "Ожидается", "Валюта"
            };
            var header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            int index = 1;
            for (var club : summary.byClub()) {
                var row = sheet.createRow(index++);
                row.createCell(0).setCellValue(club.club());
                row.createCell(1).setCellValue(club.participants());
                row.createCell(2).setCellValue(club.registrations());
                row.createCell(3).setCellValue(club.due().doubleValue());
                row.createCell(4).setCellValue(club.paid().doubleValue());
                row.createCell(5).setCellValue(club.pending().doubleValue());
                row.createCell(6).setCellValue(summary.currency());
            }
            sheet.createFreezePane(0, 1);
            for (int i = 0; i < headers.length; i++) sheet.setColumnWidth(i, i == 0 ? 9000 : 4500);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not export payment summary", e);
        }
    }
}
