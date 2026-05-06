package com.abhay.aidocument.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PineconeService {

    @Value("${pinecone.api.key}")
    private String pineconeApiKey;

    @Value("${pinecone.index.host}")
    private String pineconeIndexHost;

    @Value("${pinecone.namespace}")
    private String namespace;

    private final RestClient restClient = RestClient.create();

    public void upsertChunk(
            Long documentId,
            String userEmail,
            int chunkIndex,
            String chunkText,
            List<Double> vector
    ) {

        String vectorId = userEmail + "-" + documentId + "-" + chunkIndex;

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("documentId", documentId);
        metadata.put("userEmail", userEmail);
        metadata.put("chunkIndex", chunkIndex);
        metadata.put("text", chunkText);

        Map<String, Object> vectorObject = new HashMap<>();
        vectorObject.put("id", vectorId);
        vectorObject.put("values", vector);
        vectorObject.put("metadata", metadata);

        Map<String, Object> requestBody = Map.of(
                "vectors", List.of(vectorObject),
                "namespace", namespace
        );

        restClient.post()
                .uri(pineconeIndexHost + "/vectors/upsert")
                .header("Api-Key", pineconeApiKey)
                .header("Content-Type", "application/json")
                .body(requestBody)
                .retrieve()
                .body(Map.class);
    }

    public List<String> searchRelevantChunks(
            Long documentId,
            String userEmail,
            List<Double> queryVector
    ) {

        Map<String, Object> filter = Map.of(
                "$and", List.of(
                        Map.of("documentId", Map.of("$eq", documentId)),
                        Map.of("userEmail", Map.of("$eq", userEmail))
                )
        );

        Map<String, Object> requestBody = Map.of(
                "vector", queryVector,
                "topK", 5,
                "includeMetadata", true,
                "namespace", namespace,
                "filter", filter
        );

        Map response = restClient.post()
                .uri(pineconeIndexHost + "/query")
                .header("Api-Key", pineconeApiKey)
                .header("Content-Type", "application/json")
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        List<Map<String, Object>> matches =
                (List<Map<String, Object>>) response.get("matches");

        List<String> chunks = new ArrayList<>();

        if (matches == null) {
            return chunks;
        }

        for (Map<String, Object> match : matches) {

            Map<String, Object> metadata =
                    (Map<String, Object>) match.get("metadata");

            if (metadata != null && metadata.get("text") != null) {
                chunks.add(metadata.get("text").toString());
            }
        }

        return chunks;
    }
}