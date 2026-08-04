package com.prosper.prospermentor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class WatiService{

    @Value("${wati.token}")
    private String watiToken;

    private final String WATI_BASE_URL = "https://live-mt-server.wati.io/1041967/api/v1/";


    public void sendMessage(String templateName, Map<String, String> templateParams, String phoneNumber) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        try {
            // Build parameters list
            List<Map<String, String>> parameters = new ArrayList<>();
            if (templateParams != null) {
                for (Map.Entry<String, String> entry : templateParams.entrySet()) {
                    parameters.add(Map.of(
                        "name", entry.getKey(),
                        "value", entry.getValue()
                    ));
                }
            }

            // Create request body
            Map<String, Object> requestBody = Map.of(
                "template_name", templateName,
                "broadcast_name", templateName,
                "parameters", parameters,
                "channel_number", phoneNumber
            );

            // Convert to JSON
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonBody = objectMapper.writeValueAsString(requestBody);

            // Build the URL
            HttpUrl url = HttpUrl.parse(WATI_BASE_URL).newBuilder()
                    .addPathSegment("sendTemplateMessage")
                    .build();

            // Log request details
            log.info("Wati Request URL: {}", url);
            log.info("Wati Request Headers: Authorization=Bearer [REDACTED], Content-Type=application/json");
            log.info("Wati Request Body: {}", jsonBody);

            // Create the request
            RequestBody body = RequestBody.create(
                jsonBody,
                MediaType.parse("application/json")
            );

            Request watiRequest = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + watiToken)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .post(body)
                    .build();

            // Execute the request with retry mechanism
            Response watiResponse = executeWithRetry(client, watiRequest, 3);

            // Log response details
            log.info("Wati Response Status: {}", watiResponse.code());
            log.info("Wati Response Headers: {}", watiResponse.headers());

            if (!watiResponse.isSuccessful()) {
                String errorBody = watiResponse.body() != null ? watiResponse.body().string() : "No response body";
                log.error("Wati Response Body (Error): {}", errorBody);
                log.error("Failed to send WhatsApp message. Status: {}, Body: {}", watiResponse.code(), errorBody);
                throw new IOException("Failed to send WhatsApp message: " + errorBody);
            }

            String responseBody = watiResponse.body() != null ? watiResponse.body().string() : "No response body";
            log.info("Wati Response Body (Success): {}", responseBody);
            log.info("Message sent successfully via WhatsApp to {}", phoneNumber);

        } catch (IOException e) {
            log.error("Error sending message via Wati to {}", phoneNumber, e);
            throw new RuntimeException("Failed to send message: " + e.getMessage());
        }
    }

    private Response executeWithRetry(OkHttpClient client, Request request, int maxRetries) throws IOException {
        int retryCount = 0;
        IOException lastException = null;

        while (retryCount < maxRetries) {
            try {
                log.info("Executing Wati request (attempt {}/{})", retryCount + 1, maxRetries);
                Response response = client.newCall(request).execute();

                log.info("Wati request returned status: {}", response.code());

                if (response.isSuccessful()) {
                    return response;
                }

                // Log unsuccessful response
                String errorBody = response.body() != null ? response.body().string() : "No response body";
                log.warn("Wati request unsuccessful (attempt {}/{}). Status: {}, Body: {}",
                        retryCount + 1, maxRetries, response.code(), errorBody);

                retryCount++;
                if (retryCount < maxRetries) {
                    long sleepTime = (long) (Math.pow(2, retryCount) * 1000);
                    log.info("Retrying after {} ms...", sleepTime);
                    Thread.sleep(sleepTime);
                }
            } catch (IOException e) {
                lastException = e;
                log.error("IOException during Wati request (attempt {}/{}): {}",
                        retryCount + 1, maxRetries, e.getMessage(), e);
                retryCount++;

                if (retryCount == maxRetries) {
                    throw e;
                }

                try {
                    long sleepTime = (long) (Math.pow(2, retryCount) * 1000);
                    log.info("Retrying after {} ms due to IOException...", sleepTime);
                    Thread.sleep(sleepTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Retry interrupted", e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Retry interrupted", e);
            }
        }

        String errorMessage = "Failed after " + maxRetries + " retries";
        if (lastException != null) {
            errorMessage += ". Last error: " + lastException.getMessage();
        }
        log.error("Wati request failed: {}", errorMessage);
        throw new IOException(errorMessage);
    }
}
