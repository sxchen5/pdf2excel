package com.xipeng.invoice.dto;

public record InvoiceRowDto(
        Integer seqNo,
        String invoiceType,
        String transferDate,
        String invoiceDate,
        String invoiceNumber,
        String issuer,
        String invoiceItem,
        String invoiceAmount,
        String taxAmount,
        String transferor,
        String receiver,
        String supervisor,
        String remarks
) {
}
