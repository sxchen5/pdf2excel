package com.xipeng.invoice.service;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class OcrService {

    private final ITesseract tesseract;

    public OcrService(@Value("${app.tesseract.datapath:/usr/share/tesseract-ocr/5/tessdata}") String dataPath) {
        Tesseract t = new Tesseract();
        t.setDatapath(dataPath);
        t.setLanguage("chi_sim+eng");
        t.setOcrEngineMode(1);
        t.setPageSegMode(1);
        this.tesseract = t;
    }

    public String recognizePng(InputStream pngStream) throws IOException {
        BufferedImage image = ImageIO.read(pngStream);
        if (image == null) {
            return "";
        }
        try {
            return tesseract.doOCR(image);
        } catch (TesseractException e) {
            throw new IOException("OCR failed: " + e.getMessage(), e);
        }
    }

    public String recognizeImage(InputStream imageStream, String suffix) throws IOException {
        Path tmp = Files.createTempFile("ocr-upload-", suffix.startsWith(".") ? suffix : "." + suffix);
        try {
            Files.copy(imageStream, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return tesseract.doOCR(tmp.toFile());
        } catch (TesseractException e) {
            throw new IOException("OCR failed: " + e.getMessage(), e);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
