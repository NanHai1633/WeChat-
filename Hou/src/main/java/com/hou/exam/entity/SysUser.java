package com.hou.exam.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    private Long id;
    private String username;
    private String password;
    private Role role;
    private Integer tokenVersion;
    /** 软删除通过 {@code SysUserMapper#softDelete} 写入自身 id，这里只用于查询时过滤 */
    @TableLogic(value = "0", delval = "1")
    private Long deleted;
    private LocalDateTime createdAt;
}
