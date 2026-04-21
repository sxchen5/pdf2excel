package com.xipeng.invoice.dto;

public record ExtractedInvoiceDto(
        String invoiceDate,
        String invoiceNumber,
        String issuer,
        String invoiceItem,
        String invoiceAmount,
        String taxAmount,
        String rawSnippet
) {
}
