package com.prosper.prospermentor.service;

import com.prosper.prospermentor.util.PhoneNumberUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends WhatsApp template messages through nautix-service third-party API.
 */
@Service
@Slf4j
public class NautixWhatsAppService {

    private static final String TEMPLATE_SEND_PATH = "/api/thirdparty/v1/messages/template/send";

    private final RestTemplate restTemplate;

    @Value("${nautix.whatsapp.enabled:false}")
    private boolean enabled;

    @Value("${nautix.whatsapp.base-url:http://localhost:7091}")
    private String baseUrl;

    @Value("${nautix.whatsapp.api-key:}")
    private String apiKey;

    @Value("${nautix.whatsapp.language:en_US}")
    private String language;

    @Value("${nautix.whatsapp.responder-display-name:ProsperMentor}")
    private String responderDisplayName;

    public NautixWhatsAppService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void sendTemplateMessage(String templateName, String recipientPhoneE164, List<String> bodyParameters) {
        sendTemplateMessage(templateName, recipientPhoneE164, bodyParameters, null, null);
    }

    public void sendTemplateMessage(String templateName,
                                    String recipientPhoneE164,
                                    List<String> bodyParameters,
                                    String flowToken,
                                    Map<String, Object> flowActionData) {
        if (!enabled) {
            log.debug("Nautix WhatsApp integration disabled; skipping template {}", templateName);
            return;
        }
        if (!StringUtils.hasText(apiKey)) {
            log.warn("Nautix WhatsApp API key is not configured; skipping template {}", templateName);
            return;
        }
        if (!StringUtils.hasText(recipientPhoneE164)) {
            throw new IllegalArgumentException("Recipient phone number is required");
        }

        String normalizedRecipient = PhoneNumberUtil.normalizeToE164(recipientPhoneE164);
        if (!StringUtils.hasText(normalizedRecipient)) {
            throw new IllegalArgumentException("Recipient phone number must be a valid E.164 number");
        }

        String url = buildEndpointUrl();
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("recipient", normalizedRecipient);
        requestBody.put("templateName", templateName);
        requestBody.put("language", language);
        requestBody.put("bodyParameters", bodyParameters);
        requestBody.put("responderDisplayName", responderDisplayName);
        if (StringUtils.hasText(flowToken)) {
            requestBody.put("flowToken", flowToken);
        }
        if (flowActionData != null && !flowActionData.isEmpty()) {
            requestBody.put("flowActionData", flowActionData);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("API-KEY", apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("Unexpected status from Nautix: " + response.getStatusCode());
            }
            log.info("Nautix WhatsApp template {} accepted for recipient {}", templateName, normalizedRecipient);
        } catch (HttpStatusCodeException e) {
            String errorBody = e.getResponseBodyAsString();
            throw new IllegalStateException("Nautix template send failed: " + e.getStatusCode() +
                    (StringUtils.hasText(errorBody) ? " - " + errorBody : ""), e);
        } catch (Exception e) {
            throw new IllegalStateException("Nautix template send failed: " + e.getMessage(), e);
        }
    }

    private String buildEndpointUrl() {
        String trimmedBaseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        return trimmedBaseUrl + TEMPLATE_SEND_PATH;
    }
}
