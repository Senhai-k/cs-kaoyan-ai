package com.kaoyan.assistant.ai;

import com.kaoyan.assistant.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/internal/agent")
public class InternalAgentEvidenceController {

    private final InternalAgentEvidenceService service;
    private final String internalToken;

    public InternalAgentEvidenceController(
            InternalAgentEvidenceService service,
            @Value("${app.ai.agent.internal-token:}") String internalToken
    ) {
        this.service = service;
        this.internalToken = internalToken;
    }

    @PostMapping("/evidence")
    public ResponseEntity<ApiResponse<InternalAgentEvidenceResult>> publish(
            @RequestHeader(value = "X-Agent-Service-Token", required = false) String token,
            @Valid @RequestBody InternalAgentEvidenceRequest request
    ) {
        if (internalToken == null || internalToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.failure(503, "agent internal token is not configured"));
        }
        if (!secureEquals(internalToken, token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure(401, "invalid agent service token"));
        }
        return ResponseEntity.ok(ApiResponse.success(service.publish(request)));
    }

    private boolean secureEquals(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
