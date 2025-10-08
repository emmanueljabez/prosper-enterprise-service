package com.prosper.prospermentor.service;

import com.prosper.prospermentor.EmailInterface;
import com.prosper.prospermentor.model.MailAttachment;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.net.URLConnection;
import java.util.Date;
import java.util.List;
import java.util.Properties;

@Service
public class EmailService implements EmailInterface {

    @Value("${prosper.mail.email_address}")
    String emailAddress;

    @Value("${prosper.mail.username}")
    String emailUsername;

    @Value("${prosper.mail.password}")
    String emailPassword;

    @Value("${prosper.mail.host}")
    String emailHost;

    @Value("${prosper.mail.port}")
    String emailPort;

    private final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Async
    public void sendEmail(String recipient, String subject, String message, List<MailAttachment> attachments) {

        Properties props = new Properties();
        props.put("mail.smtp.host", emailHost);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.port", emailPort);
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.ssl.enable", "false");
        props.put("mail.smtp.ssl.trust", "*");

        Session session = Session.getInstance(props, new Authenticator(){
            @Override
            protected PasswordAuthentication getPasswordAuthentication(){
                return new PasswordAuthentication(emailUsername, emailPassword);
            }
        });

        try {
            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(emailAddress));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
            msg.setSubject(subject);
            msg.setSentDate(new Date());

            MimeMessageHelper helper = new MimeMessageHelper(msg, true);

            // Set the email parameters
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(message, true);

            for (MailAttachment attachment: attachments) {
                byte[] byteArray = attachment.outputStream().toByteArray();
                ByteArrayResource byteArrayResource = new ByteArrayResource(byteArray);

                String contentType = URLConnection.guessContentTypeFromName(attachment.filename());
                helper.addAttachment(attachment.filename(), byteArrayResource, contentType);
            }

            Transport.send(msg);

            logger.error("Message sent");

        } catch (Exception e) {
            logger.error("Mail error: " + e.getMessage());
            e.printStackTrace();
        }

    }
}
