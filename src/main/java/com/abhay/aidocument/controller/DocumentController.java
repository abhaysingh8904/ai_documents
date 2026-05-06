package com.abhay.aidocument.controller;

import com.abhay.aidocument.entity.Document;
import com.abhay.aidocument.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/documents")
@CrossOrigin(origins = "http://localhost:5173")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/upload")
    public Document uploadDocument(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) throws Exception {

        String userEmail = authentication.getName();

        return documentService.uploadFile(file, userEmail);
    }

    @GetMapping
    public List<Document> getAllDocuments(Authentication authentication) {

        String userEmail = authentication.getName();

        return documentService.getDocumentsByUser(userEmail);
    }

    @GetMapping("/{id}")
    public Document getDocumentById(
            @PathVariable Long id,
            Authentication authentication
    ) {

        String userEmail = authentication.getName();

        return documentService.getDocumentByIdAndUser(id, userEmail);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> getFile(
            @PathVariable Long id,
            Authentication authentication
    ) throws Exception {

        String userEmail = authentication.getName();

        Document document = documentService.getDocumentByIdAndUser(id, userEmail);

        Path path = Paths.get(document.getFilePath());
        Resource resource = new UrlResource(path.toUri());

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + document.getOriginalFileName() + "\""
                )
                .header(
                        HttpHeaders.CONTENT_TYPE,
                        document.getContentType()
                )
                .body(resource);
    }
}