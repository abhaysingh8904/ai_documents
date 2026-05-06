package com.abhay.aidocument.service;

import com.abhay.aidocument.entity.TranscriptSegment;
import com.abhay.aidocument.repository.TranscriptSegmentRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;

@Service
@RequiredArgsConstructor
public class TranscriptionService {

    private final TranscriptSegmentRepository transcriptSegmentRepository;

    public String transcribeAudioVideo(String filePath, Long documentId) {

        try {

            File file = new File(filePath);

            String outputDir = file.getParent();

            ProcessBuilder processBuilder = new ProcessBuilder(
                    "python",
                    "-m",
                    "whisper",
                    file.getAbsolutePath(),
                    "--model",
                    "base",
                    "--output_format",
                    "json",
                    "--output_dir",
                    outputDir,
                    "--word_timestamps",
                    "True"
            );

            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            String line;

            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            process.waitFor();

            String fileName = file.getName();

            int dotIndex = fileName.lastIndexOf(".");

            String jsonFileName = fileName.substring(0, dotIndex) + ".json";

            File jsonFile = new File(outputDir, jsonFileName);

            String jsonContent = Files.readString(jsonFile.toPath());

            ObjectMapper objectMapper = new ObjectMapper();

            JsonNode root = objectMapper.readTree(jsonContent);

            JsonNode segments = root.get("segments");

            StringBuilder fullTranscript = new StringBuilder();

            if (segments != null && segments.isArray()) {

                for (JsonNode segment : segments) {

                    double start = segment.get("start").asDouble();
                    double end = segment.get("end").asDouble();
                    String text = segment.get("text").asText();

                    fullTranscript.append(text).append(" ");

                    TranscriptSegment transcriptSegment = TranscriptSegment.builder()
                            .documentId(documentId)
                            .startTime(start)
                            .endTime(end)
                            .text(text)
                            .build();

                    transcriptSegmentRepository.save(transcriptSegment);
                }
            }

            return fullTranscript.toString();

        } catch (Exception e) {
            throw new RuntimeException("Failed to transcribe audio/video", e);
        }
    }
}