package com.abhay.aidocument.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class EmbeddingService {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.embedding.model}")
    private String embeddingModel;

    @Value("${gemini.embedding.output-dimensions}")
    private Integer outputDimensions;

    private final RestClient restClient = RestClient.create();

    public List<Double> embedText(String text) {

        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + embeddingModel
                + ":embedContent?key="
                + geminiApiKey;

        Map<String, Object> requestBody = Map.of(
                "model", "models/" + embeddingModel,
                "content", Map.of(
                        "parts", List.of(
                                Map.of("text", text)
                        )
                ),
                "outputDimensionality", outputDimensions
        );

        Map response = restClient.post()
                .uri(url)
                .header("Content-Type", "application/json")
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        Map embedding = (Map) response.get("embedding");

        return (List<Double>) embedding.get("values");
    }
}