package com.hou.exam.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SubmissionAnswer {
    private Long id;
    private Long submissionId;
    private Long questionId;
    private String answer;
    private Boolean correct;
    private BigDecimal score;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String teacherComment;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String images;
    private LocalDateTime updatedAt;
}
