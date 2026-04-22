package com.xipeng.invoice.service;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class OcrService {

    private static final int MAX_IMAGE_DIMENSION = 3200;

    private final ITesseract tesseract;
    private final Object tesseractLock = new Object();

    public OcrService(@Value("${app.tesseract.datapath:/usr/share/tesseract-ocr/5/tessdata}") String dataPath) {
        Tesseract t = new Tesseract();
        t.setDatapath(dataPath);
        t.setLanguage("chi_sim+eng");
        t.setOcrEngineMode(1);
        t.setPageSegMode(1);
        this.tesseract = t;
    }

    public String recognizePng(InputStream pngStream) throws IOException {
        BufferedImage raw = ImageIO.read(pngStream);
        if (raw == null) {
            return "";
        }
        return ocrImage(prepareForOcr(raw));
    }

    public String recognizeImage(InputStream imageStream, String suffix) throws IOException {
        byte[] bytes = imageStream.readAllBytes();
        BufferedImage raw = ImageIO.read(new ByteArrayInputStream(bytes));
        if (raw == null) {
            Path tmp = Files.createTempFile("ocr-upload-", suffix.startsWith(".") ? suffix : "." + suffix);
            try {
                Files.write(tmp, bytes);
                raw = ImageIO.read(tmp.toFile());
            } finally {
                Files.deleteIfExists(tmp);
            }
        }
        if (raw == null) {
            return "";
        }
        return ocrImage(prepareForOcr(raw));
    }

    private String ocrImage(BufferedImage image) throws IOException {
        synchronized (tesseractLock) {
            try {
                return tesseract.doOCR(image);
            } catch (TesseractException e) {
                throw new IOException("OCR failed: " + e.getMessage(), e);
            } catch (Error e) {
                // Tesseract JNI: Invalid memory access on some JPEG/CMYK inputs
                throw new IOException("OCR native error (try resaving image as RGB PNG): " + e.getMessage(), e);
            }
        }
    }

    private static BufferedImage prepareForOcr(BufferedImage src) {
        BufferedImage rgb = toRgb(src);
        return scaleDownIfHuge(rgb);
    }

    private static BufferedImage toRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_3BYTE_BGR || src.getType() == BufferedImage.TYPE_INT_RGB) {
            return src;
        }
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static BufferedImage scaleDownIfHuge(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int max = Math.max(w, h);
        if (max <= MAX_IMAGE_DIMENSION) {
            return src;
        }
        double scale = MAX_IMAGE_DIMENSION / (double) max;
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        BufferedImage scaled = new BufferedImage(nw, nh, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, nw, nh, null);
        } finally {
            g.dispose();
        }
        return scaled;
    }
}
