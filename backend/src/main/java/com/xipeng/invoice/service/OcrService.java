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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class OcrService {

    private static final int MAX_IMAGE_DIMENSION = 3200;

    private final ITesseract tesseract;
    private final Object tesseractLock = new Object();
    private final boolean cliFallbackEnabled;

    public OcrService(
            @Value("${app.tesseract.datapath:/usr/share/tesseract-ocr/5/tessdata}") String dataPath,
            @Value("${app.ocr.tesseract-cli-fallback:true}") boolean cliFallbackEnabled) {
        this.cliFallbackEnabled = cliFallbackEnabled;
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
            Path tmpIn = Files.createTempFile("ocr-upload-", suffix.startsWith(".") ? suffix : "." + suffix);
            try {
                Files.write(tmpIn, bytes);
                raw = ImageIO.read(tmpIn.toFile());
            } finally {
                Files.deleteIfExists(tmpIn);
            }
        }
        if (raw == null) {
            return "";
        }
        return ocrImage(prepareForOcr(raw));
    }

    /**
     * Always rasterize to PNG on disk first, then Tess4J; on JNI crash or Tess4J error,
     * fall back to {@code tesseract} CLI (separate process, avoids JVM native bugs on some JPEGs).
     */
    private String ocrImage(BufferedImage image) throws IOException {
        Path pngTmp = Files.createTempFile("ocr-rgb-", ".png");
        try {
            ImageIO.write(image, "png", pngTmp.toFile());
            synchronized (tesseractLock) {
                try {
                    BufferedImage forJvm = ImageIO.read(pngTmp.toFile());
                    if (forJvm == null) {
                        return tryCli(pngTmp);
                    }
                    return tesseract.doOCR(forJvm);
                } catch (TesseractException e) {
                    return tryCliOrThrow(pngTmp, new IOException("OCR failed: " + e.getMessage(), e));
                } catch (Error e) {
                    return tryCliOrThrow(
                            pngTmp,
                            new IOException("OCR native error: " + e.getMessage(), e));
                }
            }
        } finally {
            Files.deleteIfExists(pngTmp);
        }
    }

    private String tryCli(Path pngTmp) throws IOException {
        if (!cliFallbackEnabled) {
            return "";
        }
        return runTesseractCli(pngTmp);
    }

    private String tryCliOrThrow(Path pngTmp, IOException primary) throws IOException {
        if (!cliFallbackEnabled) {
            throw primary;
        }
        try {
            return runTesseractCli(pngTmp);
        } catch (IOException cli) {
            primary.addSuppressed(cli);
            throw primary;
        }
    }

    private String runTesseractCli(Path imagePath) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                "tesseract",
                imagePath.toAbsolutePath().toString(),
                "stdout",
                "-l",
                "chi_sim+eng",
                "--oem",
                "1",
                "--psm",
                "1");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try {
            byte[] out = p.getInputStream().readAllBytes();
            int code = p.waitFor();
            if (code != 0) {
                throw new IOException("tesseract CLI exit " + code + ": " + new String(out, StandardCharsets.UTF_8));
            }
            return new String(out, StandardCharsets.UTF_8);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("tesseract CLI interrupted", e);
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
