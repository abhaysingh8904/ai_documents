package com.abhay.aidocument.controller;

import com.abhay.aidocument.dto.ChatResponse;
import com.abhay.aidocument.entity.Document;
import com.abhay.aidocument.service.CacheService;
import com.abhay.aidocument.service.DocumentService;
import com.abhay.aidocument.service.GeminiService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/summary")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class SummaryController {

    private final DocumentService documentService;
    private final GeminiService geminiService;
    private final CacheService cacheService;

    @PostMapping("/{documentId}")
    public ChatResponse summarizeDocument(
            @PathVariable Long documentId,
            Authentication authentication
    ) {

        String userEmail = authentication.getName();

        Document document = documentService.getDocumentByIdAndUser(documentId, userEmail);

        String cachedSummary = cacheService.getSummary(documentId, userEmail);

        if (cachedSummary != null && !cachedSummary.isBlank()) {
            return new ChatResponse(cachedSummary, null);
        }

        String summary = geminiService.summarize(document.getExtractedText());

        cacheService.saveSummary(documentId, userEmail, summary);

        return new ChatResponse(summary, null);
    }
}