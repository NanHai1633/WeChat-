package com.hou.exam.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Submission {
    private Long id;
    private Long paperId;
    private Long studentId;
    private SubmissionStatus status;
    private BigDecimal autoScore;
    private BigDecimal totalScore;
    private LocalDateTime submittedAt;
    private LocalDateTime gradedAt;
    private Long gradedBy;
    private LocalDateTime createdAt;
}
