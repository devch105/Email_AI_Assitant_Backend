package com.mail.email_writer.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

@Service
public class EmailGeneratorService {

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();

    // Read env var / property. Provide empty default to avoid startup failure if missing.
    @Value("${GEMINI_KEY}")
    private String geminiApiKey;

    // prefer builder so you can set base url if desired
    public EmailGeneratorService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://generativelanguage.googleapis.com").build();
    }

    /**
     * Build and call Gemini. This method now logs outgoing JSON and returns either the text
     * or detailed error info when a 4xx/5xx comes back.
     */
    public String generateEmailReply(EmailRequest emailRequest) {
        String prompt = buildPrompt(emailRequest);

        // Build the exact expected JSON structure
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )
        );

        try {
            // Convert to JSON string (so logs show exactly what is sent)
            String requestJson = mapper.writeValueAsString(requestBody);
            System.out.println("=== Outgoing Gemini Request JSON ===\n" + requestJson);

            // Call Gemini (use query param key or switch to Bearer header if you prefer)
            String resp = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1beta/models/gemini-2.0-flash:generateContent")
                            .queryParam("key", geminiApiKey)
                            .build())
                    .header("Content-Type", "application/json")
                    .bodyValue(requestJson)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();


            System.out.println(" Gemini raw response : " + resp);
            return extractResponseContent(resp);
        } catch (WebClientResponseException e) {
            // Print the server response body - this is critical to debug 400s
            System.err.println("=== Gemini ERROR status: " + e.getRawStatusCode() + " ===");
            System.err.println("=== Gemini ERROR body: ===\n" + e.getResponseBodyAsString());
            // Return a human-friendly message to frontend but keep full body in logs
            return "Failed to generate reply (remote error). See server logs for details.";
        } catch (Exception ex) {
            ex.printStackTrace();
            return "Server error while generating reply: " + ex.getMessage();
        }
    }

    private String extractResponseContent(String response) {
        if (response == null || response.isBlank()) {
            return "Empty response from Gemini";
        }
        try {
            JsonNode rootNode = mapper.readTree(response);
            JsonNode cand = rootNode.path("candidates");
            if (cand.isArray() && cand.size() > 0) {
                JsonNode first = cand.get(0);
                JsonNode textNode = first.path("content").path("parts");
                if (textNode.isArray() && textNode.size() > 0) {
                    return textNode.get(0).path("text").asText("");
                }
            }
            // fallback: return the whole body to help debugging
            return response;
        } catch (Exception e) {
            e.printStackTrace();
            return "Error processing response: " + e.getMessage();
        }
    }

    private String buildPrompt(EmailRequest emailRequest) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate a professional email reply for the following email content. Please don't generate a subject line. ");
        if (emailRequest.getTone() != null && !emailRequest.getTone().isEmpty()) {
            prompt.append("Use a ").append(emailRequest.getTone()).append(" tone. ");
        }
        prompt.append("\nOriginal email: \n").append(emailRequest.getEmailContent() == null ? "" : emailRequest.getEmailContent());
        return prompt.toString();
    }
}
