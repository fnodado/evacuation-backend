package com.evacuation.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromEmail;

    @Async
    public void sendEvacuationAlert(String toEmail, String name, String zoneName,
                                     String buildingName, String disasterType, String exitRoute) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true);
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("[URGENT] " + disasterType + " Evacuation Alert — " + buildingName);
            helper.setText(
                "<div style='font-family:Arial'>" +
                "<h2 style='color:#ef4444'>🚨 EVACUATION ALERT</h2>" +
                "<p>Dear <b>" + name + "</b>,</p>" +
                "<p>A <b>" + disasterType + "</b> has been detected.</p>" +
                "<p>You are currently registered in <b>" + zoneName + "</b>, <b>" + buildingName + "</b>.</p>" +
                "<p>Please evacuate immediately via: <b>" + (exitRoute != null ? exitRoute : "nearest exit") + "</b></p>" +
                "<h3>Instructions:</h3><ul>" +
                "<li>Stay calm and move quickly</li>" +
                "<li>Do NOT use elevators</li>" +
                "<li>Follow floor marshals</li>" +
                "<li>Go to your designated assembly point</li></ul>" +
                "<p><i>This is an automated alert from the Evacuation Management System.</i></p>" +
                "</div>", true);
            mailSender.send(msg);
        } catch (Exception e) {
            System.err.println("Email error to " + toEmail + ": " + e.getMessage());
        }
    }

    @Async
    public void sendAllClearEmail(String toEmail, String name, String buildingName) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true);
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("[ALL CLEAR] Evacuation Ended — " + buildingName);
            helper.setText(
                "<div style='font-family:Arial'>" +
                "<h2 style='color:#22c55e'>✅ ALL CLEAR</h2>" +
                "<p>Dear <b>" + name + "</b>,</p>" +
                "<p>The evacuation of <b>" + buildingName + "</b> has ended. It is now safe to return.</p>" +
                "<p><i>Evacuation Management System</i></p>" +
                "</div>", true);
            mailSender.send(msg);
        } catch (Exception e) {
            System.err.println("Email error to " + toEmail + ": " + e.getMessage());
        }
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true);
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Password Reset Request — Evacuation System");
            helper.setText(
                "<div style='font-family:Arial'>" +
                "<h2>Password Reset</h2>" +
                "<p>Click the link below to reset your password. It expires in 1 hour.</p>" +
                "<p><a href='" + resetLink + "' style='color:#3b82f6'>Reset Password</a></p>" +
                "<p>If you did not request this, ignore this email.</p>" +
                "</div>", true);
            mailSender.send(msg);
        } catch (Exception e) {
            System.err.println("Email error to " + toEmail + ": " + e.getMessage());
        }
    }

    // Backward-compatible wrapper kept for NotifyController
    public void sendEarthquakeAlert(String toEmail, String name, String zone, String building) {
        sendEvacuationAlert(toEmail, name, zone, building, "EARTHQUAKE", null);
    }
}
