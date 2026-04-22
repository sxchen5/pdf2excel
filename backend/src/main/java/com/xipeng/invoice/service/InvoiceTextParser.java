package com.xipeng.invoice.service;

import com.xipeng.invoice.dto.ExtractedInvoiceDto;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses common Chinese VAT / e-invoice text layouts (PDF text layer or OCR).
 */
public final class InvoiceTextParser {

    private static final Pattern DATE = Pattern.compile(
            "开\\s*票\\s*日\\s*期\\s*[:：]\\s*(\\d{4})\\s*年\\s*(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日");
    /** 全电票：发票号码：20位（无“码”字） */
    private static final Pattern INVOICE_NO = Pattern.compile(
            "发\\s*票\\s*号\\s*(?:码)?\\s*[:：]\\s*([0-9]{6,30})");
    /** 购/销方 “名称：” 单行 */
    private static final Pattern NAME_LABEL = Pattern.compile("名\\s*称\\s*[:：]\\s*([^\\r\\n]+)");
    /**
     * 数电票：{@code 销\\n售 名称：} 或 {@code 销 名称：}；专票常见「销售方信息」整块
     */
    private static final Pattern SELLER_NAME_SPLIT = Pattern.compile(
            "销(?:\\s+售)?\\s*名\\s*称\\s*[:：]\\s*([^\\r\\n]+)", Pattern.DOTALL);
    private static final Pattern BUYER_NAME_SPLIT = Pattern.compile(
            "购(?:\\s+买)?\\s*名\\s*称\\s*[:：]\\s*([^\\r\\n]+)", Pattern.DOTALL);
    /** 增值税专用发票：销售方信息 … 购买方信息 */
    private static final Pattern SELLER_INFO_SECTION = Pattern.compile(
            "(?:销售方信息|销售方)\\s*([\\s\\S]{0,1500}?)(?=购买方信息|购买方)", Pattern.DOTALL);
    private static final Pattern NAME_IN_BLOCK = Pattern.compile("名\\s*称\\s*[:：]\\s*([^\\r\\n]+)");
    /** Line item: *大类*明细 (common on VAT invoices) */
    private static final Pattern ITEM_STAR_LINE = Pattern.compile(
            "([*＊][^\\r\\n*]{1,40}[*＊][^\\r\\n]{1,120})");
    /** Require *category*detail so we do not capture cipher blocks on the same PDF line as the header */
    private static final Pattern ITEM_AFTER_HEADER = Pattern.compile(
            "(?:货物或应税劳务、服务名称|货物或应税劳务名称|货物或服务名称|项目名称)\\s*[:：]?\\s*\\n?\\s*([*＊][^*\\r\\n]{0,40}[*＊][^\\r\\n]+)");
    private static final Pattern ITEM_LINE = Pattern.compile("([*＊][^\\r\\n*]{1,120}[*＊][^\\r\\n]{0,80})");
    /** Amount + tax on same line as item row: 243.40 6% 14.60 */
    private static final Pattern LINE_AMOUNT_TAX = Pattern.compile(
            "([\\d,]+\\.\\d{2})\\s*\\d{1,2}\\s*%\\s*([\\d,]+\\.\\d{2})");
    /** 税 额 后的数值（常与“金 额”块配对） */
    private static final Pattern TAX_AFTER_SHUIE = Pattern.compile(
            "税\\s*\\n?\\s*额\\s*\\n\\s*([¥￥]?\\s*[\\d,]+\\.\\d{2})");
    private static final Pattern AMOUNT_TAX_BLOCK = Pattern.compile(
            "金\\s*\\n?\\s*额([\\s\\S]{0,1200}?)税\\s*\\n?\\s*额", Pattern.DOTALL);
    /** 不含¥的两位小数金额行（排除单价长小数） */
    private static final Pattern PLAIN_MONEY_TWO_DP = Pattern.compile(
            "(?:^|\\n)\\s*([\\d,]+\\.\\d{2})\\s*(?:\\n|$)");
    private static final Pattern MONEY_PAIR = Pattern.compile(
            "金\\s*额[^\\d¥￥]{0,80}([¥￥]?\\s*[\\d,]+\\.\\d{2})[^\\d¥￥]{0,80}税\\s*额[^\\d¥￥]{0,80}([¥￥]?\\s*[\\d,]+\\.\\d{2})");
    private static final Pattern TOTAL_LINE = Pattern.compile(
            "合\\s*计\\s*¥\\s*([\\d,]+\\.\\d{2})\\s*¥\\s*([\\d,]+\\.\\d{2})");
    /** PDF merges 金额+税率+税额 onto the same token as the line item */
    private static final Pattern MERGED_ITEM_TAIL = Pattern.compile(
            "^(.+?)(\\d+\\.\\d{2})(\\d{1,3}[%％])(\\d+\\.\\d{2})$");
    private static final Pattern MERGED_TAIL_ONLY = Pattern.compile("\\d+\\.\\d{2}\\d{1,3}[%％]\\d+\\.\\d{2}$");

    private InvoiceTextParser() {
    }

    public static ExtractedInvoiceDto parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return empty(null);
        }
        String normalized = toAsciiDigits(normalize(Normalizer.normalize(raw, Normalizer.Form.NFKC)));
        String snippet = snippet(normalized, 2000);

        String invoiceDate = findDate(normalized);
        String invoiceNumber = findInvoiceNumber(normalized);
        String issuer = findIssuer(normalized);
        String item = sanitizeItemDisplay(stripTrailingMergedAmount(findItem(normalized)));
        String[] amounts = findAmountAndTax(normalized);

        return new ExtractedInvoiceDto(
                nz(invoiceDate),
                nz(invoiceNumber),
                nz(issuer),
                nz(item),
                nz(amounts[0]),
                nz(amounts[1]),
                snippet
        );
    }

    private static String normalize(String raw) {
        String s = raw.replace('\u00a0', ' ');
        s = s.replaceAll("[\t\f\r]+", "\n");
        s = s.replaceAll(" {2,}", " ");
        return s;
    }

    private static String findDate(String text) {
        Matcher m = DATE.matcher(text);
        if (m.find()) {
            return m.group(1) + "/" + Integer.parseInt(m.group(2)) + "/" + Integer.parseInt(m.group(3));
        }
        return "";
    }

    private static String findInvoiceNumber(String text) {
        Matcher m = INVOICE_NO.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }

    private static String findIssuer(String text) {
        Matcher info = SELLER_INFO_SECTION.matcher(text);
        if (info.find()) {
            String section = info.group(1);
            Matcher nm = NAME_IN_BLOCK.matcher(section);
            if (nm.find()) {
                String v = cleanPartyName(nm.group(1));
                if (looksLikeCompanyName(v)) {
                    return v;
                }
            }
        }
        Matcher seller = SELLER_NAME_SPLIT.matcher(text);
        if (seller.find()) {
            String v = cleanPartyName(seller.group(1));
            if (looksLikeCompanyName(v)) {
                return v;
            }
        }
        List<String> names = new ArrayList<>();
        Matcher m = NAME_LABEL.matcher(text);
        while (m.find()) {
            String v = cleanPartyName(m.group(1));
            if (v.length() >= 2 && !v.matches(".*有限公司.*有限公司.*")) {
                names.add(v);
            }
        }
        if (names.isEmpty()) {
            return "";
        }
        String buyer = "";
        Matcher buy = BUYER_NAME_SPLIT.matcher(text);
        if (buy.find()) {
            buyer = cleanPartyName(buy.group(1));
        }
        String buyerKey = normKey(buyer);
        // 若最后一项与购买方一致，则销方为倒数第二（数电票常见顺序）
        String last = names.get(names.size() - 1);
        if (!buyerKey.isEmpty() && normKey(last).equals(buyerKey) && names.size() >= 2) {
            return names.get(names.size() - 2);
        }
        // 旧版式：先买方后卖方
        return last;
    }

    private static String cleanPartyName(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("\\s+", "").trim();
    }

    private static String normKey(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        return Normalizer.normalize(cleanPartyName(s), Normalizer.Form.NFKC);
    }

    private static boolean looksLikeCompanyName(String s) {
        if (s == null || s.length() < 2) {
            return false;
        }
        if (s.matches("^[0-9A-Z]{10,20}$")) {
            return false;
        }
        return s.matches(".*[\\u4e00-\\u9fff].*") || s.contains("公司") || s.contains("有限");
    }

    private static String findItem(String text) {
        Matcher m1 = ITEM_AFTER_HEADER.matcher(text);
        if (m1.find()) {
            String candidate = cleanItem(m1.group(1));
            if (looksLikeInvoiceItem(candidate)) {
                return candidate;
            }
        }
        Matcher mStar = ITEM_STAR_LINE.matcher(text);
        String bestStar = "";
        while (mStar.find()) {
            String line = cleanItem(mStar.group(1));
            if (looksLikeInvoiceItem(line) && line.length() > bestStar.length()) {
                bestStar = line;
            }
        }
        if (!bestStar.isEmpty()) {
            return bestStar;
        }
        Matcher m2 = ITEM_LINE.matcher(text);
        String best = "";
        while (m2.find()) {
            String line = cleanItem(m2.group(1));
            if (looksLikeInvoiceItem(line) && line.length() > best.length()) {
                best = line;
            }
        }
        return best;
    }

    private static boolean looksLikeInvoiceItem(String s) {
        if (s == null || s.length() < 2) {
            return false;
        }
        if (!s.matches(".*[\\u4e00-\\u9fff].*")) {
            return false;
        }
        if (s.matches("^[0-9+\\-*/<>=]+$")) {
            return false;
        }
        return !s.matches(".*36728.*");
    }

    private static String cleanItem(String s) {
        String t = toAsciiDigits(s.replaceAll("\\s+", "").trim());
        Matcher m = MERGED_ITEM_TAIL.matcher(t);
        if (m.matches() && m.group(1).matches(".*[\\u4e00-\\u9fff].*")) {
            return m.group(1).trim();
        }
        String stripped = MERGED_TAIL_ONLY.matcher(t).replaceFirst("").trim();
        if (!stripped.equals(t) && stripped.matches(".*[\\u4e00-\\u9fff].*") && stripped.length() >= 2) {
            return stripped;
        }
        return t;
    }

    private static String toAsciiDigits(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '０' && c <= '９') {
                sb.append((char) ('0' + (c - '０')));
            } else if (c == '．') {
                sb.append('.');
            } else if (c == '，') {
                sb.append(',');
            } else if (c == '％') {
                sb.append('%');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String[] findAmountAndTax(String text) {
        Matcher mLine = LINE_AMOUNT_TAX.matcher(text);
        String amount = "";
        String tax = "";
        while (mLine.find()) {
            amount = stripMoney(mLine.group(1));
            tax = stripMoney(mLine.group(2));
        }
        if (!amount.isEmpty() || !tax.isEmpty()) {
            return new String[]{amount, tax};
        }
        Matcher block = AMOUNT_TAX_BLOCK.matcher(text);
        if (block.find()) {
            String amt = findPlainMoneyInBlock(block.group(1));
            Matcher s = TAX_AFTER_SHUIE.matcher(text);
            if (!amt.isEmpty() && s.find()) {
                return new String[]{amt, stripMoney(s.group(1))};
            }
        }
        Matcher m = MONEY_PAIR.matcher(text);
        while (m.find()) {
            amount = stripMoney(m.group(1));
            tax = stripMoney(m.group(2));
        }
        if (!amount.isEmpty() || !tax.isEmpty()) {
            return new String[]{amount, tax};
        }
        Matcher mt = TOTAL_LINE.matcher(text);
        if (mt.find()) {
            return new String[]{stripMoney(mt.group(1)), stripMoney(mt.group(2))};
        }
        return fallbackMoney(text);
    }

    private static String findPlainMoneyInBlock(String block) {
        if (block == null || block.isBlank()) {
            return "";
        }
        String best = "";
        Matcher m = PLAIN_MONEY_TWO_DP.matcher(block);
        while (m.find()) {
            String raw = m.group(1).replace(",", "");
            if (raw.matches("\\d+\\.\\d{2}") && raw.length() <= 14) {
                int dot = raw.indexOf('.');
                if (dot > 0 && raw.substring(0, dot).length() <= 10) {
                    best = stripMoney(m.group(1));
                }
            }
        }
        return best;
    }

    private static String[] fallbackMoney(String text) {
        Pattern pAmt = Pattern.compile("(?:^|\\n)\\s*金\\s*额\\s*[:：]?\\s*([¥￥]?\\s*[\\d,]+\\.\\d{2})");
        Pattern pTax = Pattern.compile("(?:^|\\n)\\s*税\\s*额\\s*[:：]?\\s*([¥￥]?\\s*[\\d,]+\\.\\d{2})");
        String a = "";
        String t = "";
        Matcher ma = pAmt.matcher(text);
        if (ma.find()) {
            a = stripMoney(ma.group(1));
        }
        Matcher mt = pTax.matcher(text);
        if (mt.find()) {
            t = stripMoney(mt.group(1));
        }
        return new String[]{a, t};
    }

    private static String stripMoney(String s) {
        if (s == null) {
            return "";
        }
        String v = s.replace("¥", "").replace("￥", "").replace(",", "").trim();
        try {
            return new BigDecimal(v).stripTrailingZeros().toPlainString();
        } catch (Exception e) {
            return v;
        }
    }

    private static String snippet(String text, int max) {
        String t = text.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** 去掉项目名称后误粘的数量、单价长小数、金额等 */
    private static String sanitizeItemDisplay(String item) {
        if (item == null || item.isEmpty()) {
            return "";
        }
        String t = toAsciiDigits(item.trim());
        t = MERGED_TAIL_ONLY.matcher(t).replaceFirst("").trim();
        for (int i = 0; i < 6; i++) {
            String next = t.replaceFirst("\\d+\\s+\\d+(\\.\\d+)?$", "").trim();
            if (next.equals(t)) {
                break;
            }
            t = next;
        }
        Matcher glued = Pattern.compile("^(.*[\\u4e00-\\u9fff*＊])(\\d[\\d.]*)$").matcher(t);
        if (glued.matches()) {
            String tail = glued.group(2);
            if (tail.matches("\\d+(\\.\\d{2,})?")) {
                t = glued.group(1).trim();
            }
        }
        return t;
    }

    /** Last-resort strip when PDF merges 金额+税率+税额 onto the item cell */
    private static String stripTrailingMergedAmount(String item) {
        if (item == null || item.isEmpty()) {
            return "";
        }
        String t = toAsciiDigits(item.trim());
        String stripped = MERGED_TAIL_ONLY.matcher(t).replaceFirst("").trim();
        if (!stripped.equals(t) && stripped.matches(".*[\\u4e00-\\u9fff].*")) {
            return stripped;
        }
        return item.trim();
    }

    private static ExtractedInvoiceDto empty(String snippet) {
        return new ExtractedInvoiceDto("", "", "", "", "", "", snippet == null ? "" : snippet);
    }
}
