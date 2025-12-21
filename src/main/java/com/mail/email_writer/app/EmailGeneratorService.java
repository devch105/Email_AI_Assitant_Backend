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
@Service
public class EmailGeneratorService {

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${GEMINI_KEY}")
    private String geminiApiKey;

    public EmailGeneratorService(WebClient.Builder builder) {
        this.webClient = builder
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();
    }

    public String generateEmailReply(EmailRequest emailRequest) {

        String prompt = buildPrompt(emailRequest);

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )
        );

        try {
            String requestJson = mapper.writeValueAsString(requestBody);
            System.out.println("=== Outgoing Gemini Request JSON ===");
            System.out.println(requestJson);

            String response = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1beta/models/gemini-2.0-flash:generateContent")
                            .queryParam("key", geminiApiKey)
                            .build())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .bodyValue(requestJson)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return extractResponseContent(response);

        } catch (WebClientResponseException.TooManyRequests e) {
            System.err.println("=== GEMINI RATE LIMIT (429) ===");
            System.err.println(e.getResponseBodyAsString());
            throw new RuntimeException("AI quota exceeded. Please try again after some time.");

        } catch (WebClientResponseException e) {
            System.err.println("=== GEMINI ERROR " + e.getRawStatusCode() + " ===");
            System.err.println(e.getResponseBodyAsString());
            throw new RuntimeException("AI service error.");

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Internal server error.");
        }
    }

    private String buildPrompt(EmailRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("Write an email reply.\n");

        if (req.getTone() != null && !req.getTone().isBlank()) {
            sb.append("Tone: ").append(req.getTone()).append("\n");
        }

        sb.append("Email:\n");
        sb.append(req.getEmailContent() == null ? "" : req.getEmailContent());

        return sb.toString();
    }

    private String extractResponseContent(String response) throws Exception {
        JsonNode root = mapper.readTree(response);
        JsonNode parts = root
                .path("candidates")
                .path(0)
                .path("content")
                .path("parts");

        if (parts.isArray() && parts.size() > 0) {
            return parts.get(0).path("text").asText();
        }

        return "No response generated.";
    }
}
