package com.xipeng.invoice.service;

import com.xipeng.invoice.dto.ExtractedInvoiceDto;

import java.math.BigDecimal;
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
    private static final Pattern INVOICE_NO = Pattern.compile("发\\s*票\\s*号\\s*码\\s*[:：]\\s*([0-9]{6,30})");
    private static final Pattern NAME_LABEL = Pattern.compile("名\\s*称\\s*[:：]\\s*([^\\r\\n]+)");
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
        String normalized = toAsciiDigits(normalize(raw));
        String snippet = snippet(normalized, 2000);

        String invoiceDate = findDate(normalized);
        String invoiceNumber = findInvoiceNumber(normalized);
        String issuer = findIssuer(normalized);
        String item = stripTrailingMergedAmount(findItem(normalized));
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
        List<String> names = new ArrayList<>();
        Matcher m = NAME_LABEL.matcher(text);
        while (m.find()) {
            String v = m.group(1).trim();
            if (v.length() >= 2 && !v.matches(".*有限公司.*有限公司.*")) {
                names.add(v);
            }
        }
        if (names.isEmpty()) {
            return "";
        }
        // Typical layout: buyer first, seller last in PDF text order
        return names.get(names.size() - 1);
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
