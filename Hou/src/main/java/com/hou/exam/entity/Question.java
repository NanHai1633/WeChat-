package com.hou.exam.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class Question {
    private Long id;
    private Long paperId;
    private QuestionType type;
    private Integer sortOrder;
    private String content;
    // 修改题型时选项和答案需要能被清空，所以更新时不跳过 null
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String optionA;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String optionB;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String optionC;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String optionD;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String answer;
    private BigDecimal score;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String images;
}
