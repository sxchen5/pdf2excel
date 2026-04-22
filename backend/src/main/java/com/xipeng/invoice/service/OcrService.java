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
import java.util.ArrayList;
import java.util.List;

@Service
public class OcrService {

    private static final int MAX_IMAGE_DIMENSION = 3200;

    private final ITesseract tesseract;
    private final Object tesseractLock = new Object();
    private final boolean cliFallbackEnabled;
    private final String tessDataPath;

    public OcrService(
            @Value("${app.tesseract.datapath:/usr/share/tesseract-ocr/5/tessdata}") String dataPath,
            @Value("${app.ocr.tesseract-cli-fallback:true}") boolean cliFallbackEnabled) {
        this.cliFallbackEnabled = cliFallbackEnabled;
        String p = dataPath == null ? "" : dataPath.trim();
        if (p.isEmpty()) {
            p = "/usr/share/tesseract-ocr/5/tessdata";
        }
        this.tessDataPath = p;
        Tesseract t = new Tesseract();
        t.setDatapath(p);
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
     * Rasterize to PNG on disk, Tess4J first; on JNI crash / API error fall back to {@code tesseract}
     * CLI with explicit {@code --tessdata-dir} (Windows/Linux portable installs).
     */
    private String ocrImage(BufferedImage image) throws IOException {
        Path pngTmp = Files.createTempFile("ocr-rgb-", ".png");
        try {
            ImageIO.write(image, "png", pngTmp.toFile());
            synchronized (tesseractLock) {
                try {
                    BufferedImage forJvm = ImageIO.read(pngTmp.toFile());
                    if (forJvm == null) {
                        return runTesseractCliOrDisabled(pngTmp, null);
                    }
                    return tesseract.doOCR(forJvm);
                } catch (TesseractException e) {
                    return runTesseractCliOrDisabled(pngTmp, new IOException("Tess4J OCR failed: " + e.getMessage(), e));
                } catch (Error e) {
                    return runTesseractCliOrDisabled(
                            pngTmp,
                            new IOException("Tess4J native error (will try tesseract CLI): " + e.getMessage(), e));
                }
            }
        } finally {
            Files.deleteIfExists(pngTmp);
        }
    }

    private String runTesseractCliOrDisabled(Path imagePath, IOException tess4jFailure) throws IOException {
        if (!cliFallbackEnabled) {
            if (tess4jFailure != null) {
                throw tess4jFailure;
            }
            return "";
        }
        try {
            return runTesseractCli(imagePath);
        } catch (IOException cli) {
            if (tess4jFailure != null) {
                tess4jFailure.addSuppressed(cli);
                throw tess4jFailure;
            }
            throw cli;
        }
    }

    private String runTesseractCli(Path imagePath) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add("tesseract");
        cmd.add(imagePath.toAbsolutePath().toString());
        cmd.add("stdout");
        cmd.add("-l");
        cmd.add("chi_sim+eng");
        if (tessDataPath != null && !tessDataPath.isBlank()) {
            cmd.add("--tessdata-dir");
            cmd.add(tessDataPath);
        }
        cmd.add("--oem");
        cmd.add("1");
        cmd.add("--psm");
        cmd.add("1");
        ProcessBuilder pb = new ProcessBuilder(cmd);
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
