package com.abhay.aidocument.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "transcript_segments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TranscriptSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long documentId;

    private Double startTime;

    private Double endTime;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String text;
}