package com.abhay.aidocument.repository;

import com.abhay.aidocument.entity.TranscriptSegment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TranscriptSegmentRepository extends JpaRepository<TranscriptSegment, Long> {

    List<TranscriptSegment> findByDocumentId(Long documentId);
}