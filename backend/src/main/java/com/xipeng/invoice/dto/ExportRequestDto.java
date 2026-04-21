package com.xipeng.invoice.dto;

import java.util.List;

public record ExportRequestDto(
        String sheetTitle,
        String serialNo,
        String sheetName,
        List<InvoiceRowDto> rows
) {
}
