package com.hou.exam.dto;

import com.hou.exam.entity.PaperStatus;
import com.hou.exam.entity.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class PaperDtos {

    private PaperDtos() {
    }

    public record PaperRequest(
            @NotBlank(message = "请输入试卷名称")
            @Size(max = 200, message = "试卷名称不能超过 200 字") String title) {
    }

    public record PaperStatusRequest(@NotNull(message = "请指定状态") PaperStatus status) {
    }

    /**
     * 题目和答案一起上传。
     * 选择题填 optionA-D，answer 为 A/B/C/D；判断题 answer 为 TRUE/FALSE；
     * 填空题用 blanks，每个空是一组可接受的答案；大题 answer 是可选的参考答案。
     * images 是题干图片的文件名，修改题目时不传表示不改动。
     */
    public record QuestionRequest(
            @NotNull(message = "请指定题型") QuestionType type,
            @NotBlank(message = "请输入题干") String content,
            @Size(max = 1000, message = "选项不能超过 1000 字") String optionA,
            @Size(max = 1000, message = "选项不能超过 1000 字") String optionB,
            @Size(max = 1000, message = "选项不能超过 1000 字") String optionC,
            @Size(max = 1000, message = "选项不能超过 1000 字") String optionD,
            String answer,
            List<List<String>> blanks,
            @NotNull(message = "请输入分值")
            @DecimalMin(value = "0.01", message = "分值必须大于 0")
            @DecimalMax(value = "1000", message = "分值不能超过 1000")
            @Digits(integer = 4, fraction = 2, message = "分值最多两位小数") BigDecimal score,
            Integer sortOrder,
            List<String> images) {
    }

    public record QuestionBatchRequest(
            @NotEmpty(message = "请至少上传一道题") List<@Valid QuestionRequest> questions) {
    }

    public record PaperVO(Long id, String title, PaperStatus status, long questionCount,
                          BigDecimal maxScore, LocalDateTime createdAt) {
    }

    public record QuestionVO(Long id, Long paperId, QuestionType type, Integer sortOrder, String content,
                             String optionA, String optionB, String optionC, String optionD,
                             String answer, List<List<String>> blanks, BigDecimal score, List<String> images) {
    }

    public record PaperDetailVO(Long id, String title, PaperStatus status, BigDecimal maxScore,
                                LocalDateTime createdAt, List<QuestionVO> questions) {
    }
}
