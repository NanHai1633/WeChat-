package com.hou.exam.dto;

import com.hou.exam.entity.SubmissionStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 学生试卷列表的一行。status 为 null 表示还没开始做。 */
@Data
public class StudentPaperRow {
    private Long paperId;
    private String title;
    private Long submissionId;
    private SubmissionStatus status;
    private BigDecimal totalScore;
    private BigDecimal maxScore;
    private Integer questionCount;
    private Integer answeredCount;
    private LocalDateTime submittedAt;
}
