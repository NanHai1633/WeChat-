package com.hou.exam.dto;

import com.hou.exam.entity.QuestionType;
import com.hou.exam.entity.SubmissionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class GradingDtos {

    private GradingDtos() {
    }

    public record GradeItem(
            @NotNull(message = "缺少题目") Long questionId,
            @NotNull(message = "请输入分数")
            @DecimalMin(value = "0", message = "分数不能小于 0")
            @Digits(integer = 4, fraction = 2, message = "分数最多两位小数") BigDecimal score,
            @Size(max = 1000, message = "评语不能超过 1000 字") String comment) {
    }

    public record GradesRequest(@NotEmpty(message = "请至少提交一题分数") List<@Valid GradeItem> grades) {
    }

    public record AnswerDetailVO(Long questionId, QuestionType type, Integer sortOrder, String content,
                                 String optionA, String optionB, String optionC, String optionD,
                                 String correctAnswer, List<List<String>> correctBlanks,
                                 String studentAnswer, List<String> studentBlanks,
                                 Boolean correct, BigDecimal score, BigDecimal maxScore, String comment,
                                 List<String> images, List<String> studentImages) {
    }

    public record SubmissionDetailVO(Long submissionId, Long paperId, String title, Long studentId, String username,
                                     SubmissionStatus status, BigDecimal autoScore, BigDecimal totalScore,
                                     BigDecimal maxScore, LocalDateTime submittedAt, LocalDateTime gradedAt,
                                     List<AnswerDetailVO> answers) {
    }
}
