package com.xipeng.invoice.service;

import com.xipeng.invoice.dto.ExtractedInvoiceDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceTextParserTest {

    @Test
    void parsesSampleVatElectronicInvoiceText() {
        String text = """
                浙江增值税电子普通发票
                货物或应税劳务、服务名称
                *餐饮服务*餐费
                名称: 亚信安全科技股份有限公司
                名称: 杭州新白鹿拾叁店餐饮有限公司
                发票号码: 76554432
                开票日期: 2022 年 06 月25日
                金额 ¥243.40 税额 ¥14.60
                """;

        ExtractedInvoiceDto d = InvoiceTextParser.parse(text);

        assertEquals("2022/6/25", d.invoiceDate());
        assertEquals("76554432", d.invoiceNumber());
        assertTrue(d.issuer().contains("新白鹿"));
        assertTrue(d.invoiceItem().contains("餐费") || d.invoiceItem().contains("餐饮"));
        assertEquals("243.4", d.invoiceAmount());
        assertEquals("14.6", d.taxAmount());
    }

    @Test
    void parsesAmountFromCompactLineLayout() {
        String text = """
                货物或应税劳务、服务名称
                *餐饮服务*餐费 243.40 6% 14.60
                名称: 买方公司
                名称: 卖方餐饮有限公司
                发票号码: 76554432
                开票日期: 2022 年 06 月25日
                """;
        ExtractedInvoiceDto d = InvoiceTextParser.parse(text);
        assertEquals("243.4", d.invoiceAmount());
        assertEquals("14.6", d.taxAmount());
        assertTrue(d.invoiceItem().contains("餐费"));
    }

    @Test
    void stripsMergedAmountFromItemLine() {
        String text = """
                *餐饮服务*餐费243.406%14.60
                发票号码: 76554432
                开票日期: 2022 年 06 月25日
                """;
        ExtractedInvoiceDto d = InvoiceTextParser.parse(text);
        assertEquals("*餐饮服务*餐费", d.invoiceItem());
    }
}
