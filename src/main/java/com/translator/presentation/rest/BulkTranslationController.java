package com.translator.presentation.rest;

import com.translator.infrastructure.aws.SqsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/translations/bulk")
public class BulkTranslationController {

    private final SqsService sqsService;

    public BulkTranslationController(SqsService sqsService) {
        this.sqsService = sqsService;
    }

    @PostMapping
    public ResponseEntity<String> submitBulkRequests(@RequestBody List<String> requests, @AuthenticationPrincipal UUID userId) {
        String jobId = UUID.randomUUID().toString();
        
        // Push each request to SQS. In a real app we would use JSON with DTO structure.
        for (String req : requests) {
             String payload = "{\"jobId\":\"" + jobId + "\", \"userId\":\"" + userId + "\", \"text\":\"" + req + "\"}";
             sqsService.sendMessage(payload);
        }
        
        return ResponseEntity.accepted().body("{\"jobId\":\"" + jobId + "\"}");
    }
}
