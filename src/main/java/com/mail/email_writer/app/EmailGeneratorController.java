package com.mail.email_writer.app;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

@RestController
@RequestMapping("/api/email")
@AllArgsConstructor
@CrossOrigin(origins = "*")
public class EmailGeneratorController {

    private final EmailGeneratorService emailGeneratorService;
    private final ObjectMapper mapper = new ObjectMapper();

    @PostMapping("/generate")
    public ResponseEntity<String> generateEmail(@RequestBody EmailRequest emailRequest) {
        try {
            // Log incoming payload (what frontend sent)
            System.out.println("=== Frontend -> Backend payload ===");
            System.out.println(mapper.writeValueAsString(emailRequest));

            String response = emailGeneratorService.generateEmailReply(emailRequest);

            // If the service returned the friendly failure message, return 500 so frontend can show error
            if (response != null && response.startsWith("Failed to generate reply")) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Server error: " + e.getMessage());
        }
    }

    /**
     * Optional debug endpoint you can call directly from Postman on your deployed host.
     * It will build the minimal request with whatever you send and call Gemini,
     * printing both outgoing JSON and any error body.
     */

      @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "app", "email-backend");
    }
    
    @PostMapping("/debug/generateFromServer")
    public ResponseEntity<String> debugGenerateFromServer(@RequestBody Map<String, Object> payload) {
        try {
            // Map expected fields safely
            String emailContent = (payload.get("emailContent") == null) ? "" : payload.get("emailContent").toString();
            String tone = (payload.get("tone") == null) ? "" : payload.get("tone").toString();

            EmailRequest req = new EmailRequest();
            req.setEmailContent(emailContent);
            req.setTone(tone);

            String resp = emailGeneratorService.generateEmailReply(req);
            if (resp != null && resp.startsWith("Failed to generate reply")) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
            }
            return ResponseEntity.ok(resp);
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Debug endpoint error: " + ex.getMessage());
        }
    }
}
