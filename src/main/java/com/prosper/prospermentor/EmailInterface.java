package com.prosper.prospermentor;

import com.prosper.prospermentor.model.MailAttachment;

import java.util.List;

public interface EmailInterface {
    void sendEmail(String recipient, String subject, String message, List<MailAttachment> attachments);
}
