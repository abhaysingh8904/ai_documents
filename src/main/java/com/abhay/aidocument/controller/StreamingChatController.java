package com.abhay.aidocument.controller;

import com.abhay.aidocument.dto.ChatRequest;
import com.abhay.aidocument.entity.Document;
import com.abhay.aidocument.entity.TranscriptSegment;
import com.abhay.aidocument.repository.TranscriptSegmentRepository;
import com.abhay.aidocument.service.DocumentService;
import com.abhay.aidocument.service.EmbeddingService;
import com.abhay.aidocument.service.GeminiService;
import com.abhay.aidocument.service.JwtService;
import com.abhay.aidocument.service.PineconeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class StreamingChatController {

    private final DocumentService documentService;
    private final GeminiService geminiService;
    private final EmbeddingService embeddingService;
    private final PineconeService pineconeService;
    private final TranscriptSegmentRepository transcriptSegmentRepository;
    private final JwtService jwtService;

    @PostMapping(
            value = "/{documentId}/stream",
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    public StreamingResponseBody streamChat(
            @PathVariable Long documentId,
            @RequestBody ChatRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {

        return outputStream -> {

            try {
                if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
                    write(outputStream, "Unauthorized. Please login again.");
                    return;
                }

                String token = authorizationHeader.substring(7);
                String userEmail = jwtService.extractEmail(token);

                if (!jwtService.isTokenValid(token)) {
                    write(outputStream, "Invalid or expired token. Please login again.");
                    return;
                }

                Document document = documentService.getDocumentByIdAndUser(documentId, userEmail);

                List<Double> questionVector = embeddingService.embedText(request.getQuestion());

                List<String> relevantChunks = pineconeService.searchRelevantChunks(
                        documentId,
                        userEmail,
                        questionVector
                );

                String context;

                if (relevantChunks == null || relevantChunks.isEmpty()) {
                    context = document.getExtractedText();
                } else {
                    context = String.join("\n\n", relevantChunks);
                }

                String answer = geminiService.askQuestionWithContext(
                        context,
                        request.getQuestion()
                );

                Double timestamp = findBestTimestamp(
                        documentId,
                        request.getQuestion(),
                        answer
                );

                streamWords(outputStream, answer);

                write(outputStream, "\n\n[[TIMESTAMP:" + timestamp + "]]");

            } catch (Exception e) {
                e.printStackTrace();

                try {
                    write(outputStream, "AI streaming failed: " + e.getMessage());
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        };
    }

    private void streamWords(OutputStream outputStream, String answer) throws IOException {

        if (answer == null || answer.isBlank()) {
            write(outputStream, "No answer generated.");
            return;
        }

        String[] words = answer.split("\\s+");

        for (String word : words) {
            write(outputStream, word + " ");

            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void write(OutputStream outputStream, String text) throws IOException {
        outputStream.write(text.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
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

                String cleanWord = word
                        .replaceAll("[^a-zA-Z0-9]", "")
                        .toLowerCase();

                if (cleanWord.length() > 3 && segmentText.contains(cleanWord)) {
                    return segment.getStartTime();
                }
            }
        }

        return segments.get(0).getStartTime();
    }
}