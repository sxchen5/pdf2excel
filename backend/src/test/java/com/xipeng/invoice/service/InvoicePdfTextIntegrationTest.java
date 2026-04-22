package com.xipeng.invoice.service;

import com.xipeng.invoice.dto.ExtractedInvoiceDto;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoicePdfTextIntegrationTest {

    @Test
    void parsesUserSamplePdfIfPresent() throws Exception {
        Path pdf = Path.of("/home/ubuntu/.cursor/projects/workspace/uploads/__.pdf");
        Assumptions.assumeTrue(Files.isRegularFile(pdf));

        String text;
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            text = stripper.getText(doc);
        }
        ExtractedInvoiceDto d = InvoiceTextParser.parse(text);
        assertEquals("2022/6/25", d.invoiceDate());
        assertEquals("76554432", d.invoiceNumber());
        assertTrue(d.issuer().contains("新白鹿"));
        assertEquals("*餐饮服务*餐费", d.invoiceItem());
        assertEquals("243.4", d.invoiceAmount());
        assertEquals("14.6", d.taxAmount());
    }

    @Test
    void parsesShanghaiDecemberHotelPdfIfPresent() throws Exception {
        Path pdf = Path.of("/home/ubuntu/.cursor/projects/workspace/uploads/____12_.pdf");
        Assumptions.assumeTrue(Files.isRegularFile(pdf));

        String text;
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            text = stripper.getText(doc);
        }
        ExtractedInvoiceDto d = InvoiceTextParser.parse(text);
        assertTrue(d.issuer().contains("莫泰"), "issuer=" + d.issuer());
    }
}
