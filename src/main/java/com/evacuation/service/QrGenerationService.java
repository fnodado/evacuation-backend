package com.evacuation.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

@Service
public class QrGenerationService {

    @Value("${app.hmac.secret}")
    private String hmacSecret;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public String generateZoneQr(String zoneId, String floorId, String buildingId) throws Exception {
        String sig = computeHmac(zoneId + ":" + (floorId != null ? floorId : "") + ":" + buildingId);

        String qrContent = frontendUrl + "/checkin"
            + "?zone_id=" + URLEncoder.encode(zoneId, StandardCharsets.UTF_8)
            + "&floor_id=" + URLEncoder.encode(floorId != null ? floorId : "", StandardCharsets.UTF_8)
            + "&building_id=" + URLEncoder.encode(buildingId, StandardCharsets.UTF_8)
            + "&sig=" + URLEncoder.encode(sig, StandardCharsets.UTF_8);

        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(qrContent, BarcodeFormat.QR_CODE, 400, 400);

        Path qrDir = Paths.get(uploadDir, "qr").toAbsolutePath();
        Files.createDirectories(qrDir);

        String fileName = "zone-" + zoneId + ".png";
        Path filePath = qrDir.resolve(fileName);
        MatrixToImageWriter.writeToPath(matrix, "PNG", filePath);

        return uploadDir + "/qr/" + fileName;
    }

    public boolean validateSignature(String zoneId, String floorId, String buildingId, String sig) {
        try {
            String expected = computeHmac(zoneId + ":" + (floorId != null ? floorId : "") + ":" + buildingId);
            return expected.equals(sig);
        } catch (Exception e) {
            return false;
        }
    }

    private String computeHmac(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(hmacSecret.getBytes(), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes()));
    }
}
