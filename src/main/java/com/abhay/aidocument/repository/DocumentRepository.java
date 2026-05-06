package com.abhay.aidocument.repository;

import com.abhay.aidocument.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByUserEmail(String userEmail);

    Optional<Document> findByIdAndUserEmail(Long id, String userEmail);
}