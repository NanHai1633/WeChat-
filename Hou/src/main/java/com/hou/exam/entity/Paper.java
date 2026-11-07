package com.hou.exam.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Paper {
    private Long id;
    private String title;
    private PaperStatus status;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;
}
