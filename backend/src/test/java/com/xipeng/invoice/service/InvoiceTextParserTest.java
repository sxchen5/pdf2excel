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

    @Test
    void parsesFullDigitalInvoiceWithSplitSellerLabel() {
        String text = """
                电子发票（普通发票）
                发票号码：26322000002467050361
                开票日期：2026年03月30日
                项目名称
                *餐饮服务*餐饮服务
                销 名称：南京茴一餐饮管理有限公司
                售
                方
                购 名称：亚信安全科技股份有限公司
                买
                方
                金
                额
                258.32
                税
                额
                ¥2.58
                """;
        ExtractedInvoiceDto d = InvoiceTextParser.parse(text);
        assertTrue(d.issuer().contains("南京茴一餐饮"));
        assertEquals("*餐饮服务*餐饮服务", d.invoiceItem());
        assertEquals("258.32", d.invoiceAmount());
        assertEquals("2.58", d.taxAmount());
    }

    @Test
    void parsesSellerWhenXiaoAndShangAreOnSeparateLines() {
        String text = """
                销
                售 名称：上海莫泰金陵东路酒店有限公司
                方
                购
                买 名称：亚信科技（成都）有限公司
                方
                发票号码： 23312000000078711639
                开票日期： 2023年09月15日
                """;
        ExtractedInvoiceDto d = InvoiceTextParser.parse(text);
        assertTrue(d.issuer().contains("莫泰"));
    }

    @Test
    void parsesSellerFromShouMingChengWhenPdfBoxInterleavesMai() {
        // PDFBox 抽取顺序：销、买名称、售名称 交错
        String text = """
                销
                买 名称：亚信科技（成都）有限公司
                售 名称：上海莫泰金陵东路酒店有限公司
                方
                发票号码：23312000000167282178
                开票日期：2023年12月19日
                """;
        ExtractedInvoiceDto d = InvoiceTextParser.parse(text);
        assertTrue(d.issuer().contains("莫泰"));
    }

    @Test
    void parsesSellerFromVatSpecialSellerInfoBlock() {
        String text = """
                电子发票（增值税专用发票）
                销售方信息
                名称：中国移动通信集团江苏有限公司南京分公司
                统一社会信用代码/纳税人识别号：91320100MA1YH0XXXX
                购买方信息
                名称：锡彭市政建设（江苏）有限公司
                发票号码：26327000000431239653
                开票日期：2026年03月02日
                """;
        ExtractedInvoiceDto d = InvoiceTextParser.parse(text);
        assertTrue(d.issuer().contains("中国移动"));
    }

    @Test
    void parsesHotelInvoiceItemWithoutQuantityGlue() {
        String text = """
                电子发票（普通发票）
                项目名称
                *住宿服务*住宿费
                销
                售 名称：上海莫泰金陵东路酒店有限公司
                方
                购
                买 名称：亚信科技（成都）有限公司
                方
                数 量
                单 价
                金 额
                3 368.867924528302
                1106.60
                税率/征收率
                6%
                税 额
                ¥66.40
                发票号码： 23312000000073410584
                开票日期： 2023年09月08日
                """;
        ExtractedInvoiceDto d = InvoiceTextParser.parse(text);
        assertTrue(d.issuer().contains("莫泰"));
        assertEquals("*住宿服务*住宿费", d.invoiceItem());
        assertEquals("1106.6", d.invoiceAmount());
        assertEquals("66.4", d.taxAmount());
    }
}
