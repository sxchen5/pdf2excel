package com.xipeng.invoice.service;

import com.xipeng.invoice.dto.ExportRequestDto;
import com.xipeng.invoice.dto.InvoiceRowDto;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class InvoiceExcelExporter {

    private static final String[] HEADERS = {
            "序号", "票据类型", "移交日期", "开票日期", "票据号码", "开票单位", "开票项目",
            "开票金额", "税额", "移交人", "接收人", "监督人", "备注"
    };

    public byte[] export(ExportRequestDto req) throws IOException {
        String title = (req.sheetTitle() == null || req.sheetTitle().isBlank())
                ? "锡彭市政建设（江苏）有限公司发票移交表"
                : req.sheetTitle();
        String serial = req.serialNo() == null ? "" : req.serialNo();
        String tab = (req.sheetName() == null || req.sheetName().isBlank()) ? "已收发票" : req.sheetName();

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(tab);

            CellStyle titleStyle = titleStyle(wb);
            CellStyle headerStyle = headerStyle(wb);
            CellStyle textStyle = textStyle(wb);
            CellStyle moneyStyle = moneyStyle(wb);

            Row r0 = sheet.createRow(0);
            Cell cTitle = r0.createCell(0);
            cTitle.setCellValue(title);
            cTitle.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));
            Cell cNoLabel = r0.createCell(7);
            cNoLabel.setCellValue("编号");
            cNoLabel.setCellStyle(titleStyle);
            Cell cNoVal = r0.createCell(8);
            cNoVal.setCellValue(serial);
            cNoVal.setCellStyle(titleStyle);

            Row r1 = sheet.createRow(1);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell hc = r1.createCell(i);
                hc.setCellValue(HEADERS[i]);
                hc.setCellStyle(headerStyle);
            }
            sheet.setAutoFilter(new CellRangeAddress(1, 1, 0, HEADERS.length - 1));

            List<InvoiceRowDto> rows = req.rows() == null ? List.of() : req.rows();
            int excelRow = 2;
            for (InvoiceRowDto row : rows) {
                Row dataRow = sheet.createRow(excelRow++);
                writeCell(dataRow, 0, stringOf(row.seqNo()), textStyle);
                writeCell(dataRow, 1, row.invoiceType(), textStyle);
                writeCell(dataRow, 2, row.transferDate(), textStyle);
                writeCell(dataRow, 3, row.invoiceDate(), textStyle);
                writeCell(dataRow, 4, row.invoiceNumber(), textStyle);
                writeCell(dataRow, 5, row.issuer(), textStyle);
                writeCell(dataRow, 6, row.invoiceItem(), textStyle);
                writeMoney(dataRow, 7, row.invoiceAmount(), moneyStyle, textStyle);
                writeMoney(dataRow, 8, row.taxAmount(), moneyStyle, textStyle);
                writeCell(dataRow, 9, row.transferor(), textStyle);
                writeCell(dataRow, 10, row.receiver(), textStyle);
                writeCell(dataRow, 11, row.supervisor(), textStyle);
                writeCell(dataRow, 12, row.remarks(), textStyle);
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
                int w = sheet.getColumnWidth(i);
                sheet.setColumnWidth(i, Math.min(w + 512, 50 * 256));
            }

            wb.write(out);
            return out.toByteArray();
        }
    }

    private static void writeCell(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value == null ? "" : value);
        c.setCellStyle(style);
    }

    private static void writeMoney(Row row, int col, String value, CellStyle moneyStyle, CellStyle textStyle) {
        Cell c = row.createCell(col);
        if (value == null || value.isBlank()) {
            c.setCellValue("");
            c.setCellStyle(textStyle);
            return;
        }
        String v = value.replace(",", "").trim();
        try {
            double d = Double.parseDouble(v);
            c.setCellValue(d);
            c.setCellStyle(moneyStyle);
        } catch (NumberFormatException e) {
            c.setCellValue(value);
            c.setCellStyle(textStyle);
        }
    }

    private static String stringOf(Integer n) {
        return n == null ? "" : String.valueOf(n);
    }

    private static CellStyle titleStyle(Workbook wb) {
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 14);
        CellStyle s = wb.createCellStyle();
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        return s;
    }

    private static CellStyle headerStyle(Workbook wb) {
        Font f = wb.createFont();
        f.setBold(true);
        CellStyle s = wb.createCellStyle();
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    private static CellStyle textStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    private static CellStyle moneyStyle(Workbook wb) {
        DataFormat df = wb.createDataFormat();
        CellStyle s = textStyle(wb);
        s.setDataFormat(df.getFormat("#,##0.00"));
        s.setAlignment(HorizontalAlignment.RIGHT);
        return s;
    }
}
