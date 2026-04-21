package com.xipeng.invoice.service;

import com.xipeng.invoice.dto.ExtractedInvoiceDto;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
public class InvoicePdfService {

    private final OcrService ocrService;

    public InvoicePdfService(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    public ExtractedInvoiceDto extract(byte[] pdfBytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);
            ExtractedInvoiceDto fromText = InvoiceTextParser.parse(text);
            if (isUsable(fromText)) {
                return fromText;
            }
            return ocrFirstPage(doc);
        }
    }

    private boolean isUsable(ExtractedInvoiceDto d) {
        return !d.invoiceDate().isBlank()
                && !d.invoiceNumber().isBlank()
                && (!d.issuer().isBlank() || !d.invoiceItem().isBlank());
    }

    private ExtractedInvoiceDto ocrFirstPage(PDDocument doc) throws IOException {
        PDFRenderer renderer = new PDFRenderer(doc);
        BufferedImage image = renderer.renderImageWithDPI(0, 300, ImageType.RGB);
        byte[] png;
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            png = baos.toByteArray();
        }
        String ocrText = ocrService.recognizePng(new ByteArrayInputStream(png));
        return InvoiceTextParser.parse(ocrText);
    }

    public ExtractedInvoiceDto extractFromStream(InputStream in) throws IOException {
        return extract(in.readAllBytes());
    }
}
