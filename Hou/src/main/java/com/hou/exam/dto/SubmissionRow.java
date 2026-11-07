package com.hou.exam.dto;

import com.hou.exam.entity.SubmissionStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 管理员查看做题情况的一行。status 为 null 表示还没开始做。 */
@Data
public class SubmissionRow {
    private Long submissionId;
    private Long paperId;
    private Long studentId;
    private String username;
    private String title;
    private SubmissionStatus status;
    private BigDecimal autoScore;
    private BigDecimal totalScore;
    private LocalDateTime submittedAt;
    private LocalDateTime gradedAt;
}
