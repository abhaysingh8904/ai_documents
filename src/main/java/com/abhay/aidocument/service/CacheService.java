package com.abhay.aidocument.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class CacheService {

    private final StringRedisTemplate redisTemplate;

    public String getSummary(Long documentId, String userEmail) {
        String key = buildSummaryKey(documentId, userEmail);
        return redisTemplate.opsForValue().get(key);
    }

    public void saveSummary(Long documentId, String userEmail, String summary) {
        String key = buildSummaryKey(documentId, userEmail);

        redisTemplate.opsForValue().set(
                key,
                summary,
                Duration.ofHours(24)
        );
    }

    private String buildSummaryKey(Long documentId, String userEmail) {
        return "summary:" + userEmail + ":" + documentId;
    }
}