package com.abhay.aidocument.service;

import com.abhay.aidocument.entity.Document;
import com.abhay.aidocument.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final TranscriptionService transcriptionService;

    // Pinecone / Vector Search services
    private final TextChunkService textChunkService;
    private final EmbeddingService embeddingService;
    private final PineconeService pineconeService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public Document uploadFile(MultipartFile file, String userEmail) throws Exception {

        String originalName = file.getOriginalFilename();

        if (originalName == null || originalName.isBlank()) {
            throw new RuntimeException("File name is missing");
        }

        File directory = new File(System.getProperty("user.dir"), uploadDir);

        if (!directory.exists()) {
            directory.mkdirs();
        }

        String storedName = UUID.randomUUID() + "_" + originalName;
        File savedFile = new File(directory, storedName);
        file.transferTo(savedFile.getAbsoluteFile());

        String filePath = savedFile.getAbsolutePath();
        String lower = originalName.toLowerCase();

        Document document = Document.builder()
                .originalFileName(originalName)
                .storedFileName(storedName)
                .fileType(getFileType(originalName))
                .contentType(file.getContentType())
                .filePath(filePath)
                .userEmail(userEmail)
                .extractedText("")
                .uploadedAt(LocalDateTime.now())
                .build();

        document = documentRepository.save(document);

        String extractedText = "";

        if (lower.endsWith(".pdf")) {

            extractedText = extractPdfText(savedFile);

        } else if (
                lower.endsWith(".mp3") ||
                        lower.endsWith(".wav") ||
                        lower.endsWith(".m4a") ||
                        lower.endsWith(".mp4") ||
                        lower.endsWith(".mov") ||
                        lower.endsWith(".mkv")
        ) {

            extractedText = transcriptionService.transcribeAudioVideo(
                    filePath,
                    document.getId()
            );
        }

        document.setExtractedText(extractedText);

        document = documentRepository.save(document);

        indexDocumentInPinecone(document, userEmail);

        return document;
    }

    public List<Document> getDocumentsByUser(String userEmail) {
        return documentRepository.findByUserEmail(userEmail);
    }

    public Document getDocumentByIdAndUser(Long id, String userEmail) {
        return documentRepository.findByIdAndUserEmail(id, userEmail)
                .orElseThrow(() -> new RuntimeException("Document not found or access denied"));
    }

    private String extractPdfText(File file) throws Exception {
        try (PDDocument pdfDocument = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(pdfDocument);
        }
    }

    private void indexDocumentInPinecone(Document document, String userEmail) {

        try {
            if (document.getExtractedText() == null || document.getExtractedText().isBlank()) {
                return;
            }

            List<String> chunks = textChunkService.splitText(document.getExtractedText());

            for (int i = 0; i < chunks.size(); i++) {

                String chunk = chunks.get(i);

                List<Double> vector = embeddingService.embedText(chunk);

                pineconeService.upsertChunk(
                        document.getId(),
                        userEmail,
                        i,
                        chunk,
                        vector
                );
            }

            System.out.println("Pinecone indexing completed for document ID: " + document.getId());

        } catch (Exception e) {
            System.out.println("Pinecone indexing failed: " + e.getMessage());
        }
    }

    private String getFileType(String fileName) {
        String lower = fileName.toLowerCase();

        if (lower.endsWith(".pdf")) return "PDF";

        if (
                lower.endsWith(".mp3") ||
                        lower.endsWith(".wav") ||
                        lower.endsWith(".m4a")
        ) return "AUDIO";

        if (
                lower.endsWith(".mp4") ||
                        lower.endsWith(".mkv") ||
                        lower.endsWith(".mov")
        ) return "VIDEO";

        return "UNKNOWN";
    }
}