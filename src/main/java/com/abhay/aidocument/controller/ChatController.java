package com.abhay.aidocument.controller;

import com.abhay.aidocument.dto.ChatRequest;
import com.abhay.aidocument.dto.ChatResponse;
import com.abhay.aidocument.entity.Document;
import com.abhay.aidocument.entity.TranscriptSegment;
import com.abhay.aidocument.repository.TranscriptSegmentRepository;
import com.abhay.aidocument.service.DocumentService;
import com.abhay.aidocument.service.GeminiService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class ChatController {

    private final DocumentService documentService;
    private final GeminiService geminiService;
    private final TranscriptSegmentRepository transcriptSegmentRepository;

    @PostMapping("/{documentId}")
    public ChatResponse chatWithDocument(
            @PathVariable Long documentId,
            @RequestBody ChatRequest request,
            Authentication authentication
    ) {

        String userEmail = authentication.getName();

        Document document = documentService.getDocumentByIdAndUser(documentId, userEmail);

        String answer = geminiService.askQuestion(
                document.getExtractedText(),
                request.getQuestion()
        );

        Double timestamp = findBestTimestamp(documentId, request.getQuestion(), answer);

        return new ChatResponse(answer, timestamp);
    }

    private Double findBestTimestamp(Long documentId, String question, String answer) {

        List<TranscriptSegment> segments =
                transcriptSegmentRepository.findByDocumentId(documentId);

        if (segments == null || segments.isEmpty()) {
            return null;
        }

        String combinedText = (question + " " + answer).toLowerCase();

        for (TranscriptSegment segment : segments) {

            if (segment.getText() == null) {
                continue;
            }

            String segmentText = segment.getText().toLowerCase();

            String[] words = combinedText.split("\\s+");

            for (String word : words) {
                String cleanWord = word.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();

                if (cleanWord.length() > 3 && segmentText.contains(cleanWord)) {
                    return segment.getStartTime();
                }
            }
        }

        return segments.get(0).getStartTime();
    }
}