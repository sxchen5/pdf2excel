package com.xipeng.invoice.web;

import com.xipeng.invoice.dto.ExportRequestDto;
import com.xipeng.invoice.dto.ExtractedInvoiceDto;
import com.xipeng.invoice.service.InvoiceExcelExporter;
import com.xipeng.invoice.service.InvoiceExtractService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api")
public class InvoiceController {

    private final InvoiceExtractService extractService;
    private final InvoiceExcelExporter excelExporter;

    public InvoiceController(InvoiceExtractService extractService, InvoiceExcelExporter excelExporter) {
        this.extractService = extractService;
        this.excelExporter = excelExporter;
    }

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExtractedInvoiceDto extract(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        return extractService.extract(file);
    }

    @PostMapping(value = "/export", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> export(@RequestBody ExportRequestDto body) throws IOException {
        byte[] xlsx = excelExporter.export(body);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("发票移交表.xlsx", StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(xlsx.length);
        return ResponseEntity.ok().headers(headers).body(xlsx);
    }
}
