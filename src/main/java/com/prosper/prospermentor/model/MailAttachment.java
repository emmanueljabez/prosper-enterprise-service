package com.prosper.prospermentor.model;

import java.io.ByteArrayOutputStream;

public record MailAttachment(String filename, ByteArrayOutputStream outputStream) {
}
