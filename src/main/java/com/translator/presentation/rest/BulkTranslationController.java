package com.translator.presentation.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.translator.infrastructure.aws.SqsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/translations/bulk")
@Tag(name = "Bulk Translation", description = "Asynchronous bulk translation via SQS")
@SecurityRequirement(name = "bearerAuth")
public class BulkTranslationController {

    private final SqsService sqsService;
    private final ObjectMapper objectMapper;

    public BulkTranslationController(SqsService sqsService, ObjectMapper objectMapper) {
        this.sqsService = sqsService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @Operation(summary = "Submit bulk translation job",
               description = "Enqueues each text onto SQS for async processing. Returns a jobId for tracking.")
    public ResponseEntity<Map<String, String>> submitBulkRequests(
            @RequestBody List<String> requests,
            @AuthenticationPrincipal UUID userId) {
        String jobId = UUID.randomUUID().toString();
        for (String text : requests) {
            try {
                // Use Jackson to safely serialize — prevents JSON injection from user text (H5 fix)
                String payload = objectMapper.writeValueAsString(Map.of(
                        "jobId", jobId,
                        "userId", userId.toString(),
                        "text", text
                ));
                sqsService.sendMessage(payload);
            } catch (Exception e) {
                // Log and skip malformed entry rather than aborting the whole job
            }
        }
        return ResponseEntity.accepted().body(Map.of("jobId", jobId));
    }
}

