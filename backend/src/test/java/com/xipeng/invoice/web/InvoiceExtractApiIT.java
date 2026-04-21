package com.xipeng.invoice.web;

import com.xipeng.invoice.dto.ExtractedInvoiceDto;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InvoiceExtractApiIT {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void extractReturnsCleanItemForSamplePdf() {
        Path pdf = Path.of("/home/ubuntu/.cursor/projects/workspace/uploads/__.pdf");
        Assumptions.assumeTrue(Files.isRegularFile(pdf));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(pdf.toFile()));

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
        String url = "http://127.0.0.1:" + port + "/api/extract";
        ResponseEntity<ExtractedInvoiceDto> res = restTemplate.postForEntity(url, request, ExtractedInvoiceDto.class);

        assertTrue(res.getStatusCode().is2xxSuccessful());
        ExtractedInvoiceDto d = res.getBody();
        assert d != null;
        assertEquals("*餐饮服务*餐费", d.invoiceItem());
        assertEquals("243.4", d.invoiceAmount());
        assertEquals("14.6", d.taxAmount());
    }
}
