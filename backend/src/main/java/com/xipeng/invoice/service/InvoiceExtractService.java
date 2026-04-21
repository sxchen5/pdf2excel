package com.xipeng.invoice.service;

import com.xipeng.invoice.dto.ExtractedInvoiceDto;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

@Service
public class InvoiceExtractService {

    private final InvoicePdfService pdfService;
    private final OcrService ocrService;

    public InvoiceExtractService(InvoicePdfService pdfService, OcrService ocrService) {
        this.pdfService = pdfService;
        this.ocrService = ocrService;
    }

    public ExtractedInvoiceDto extract(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        byte[] bytes = file.getBytes();

        if (name.endsWith(".pdf") || contentType.contains("pdf")) {
            return pdfService.extract(bytes);
        }
        if (name.endsWith(".png") || contentType.contains("png")) {
            return InvoiceTextParser.parse(ocrService.recognizePng(new java.io.ByteArrayInputStream(bytes)));
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg") || contentType.contains("jpeg")) {
            return InvoiceTextParser.parse(ocrService.recognizeImage(new java.io.ByteArrayInputStream(bytes), ".jpg"));
        }
        if (name.endsWith(".webp")) {
            return InvoiceTextParser.parse(ocrService.recognizeImage(new java.io.ByteArrayInputStream(bytes), ".webp"));
        }
        throw new IllegalArgumentException("Unsupported file type. Use PDF, PNG, or JPEG.");
    }
}
