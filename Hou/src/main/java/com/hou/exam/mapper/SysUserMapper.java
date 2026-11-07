package com.hou.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hou.exam.entity.SysUser;
import org.apache.ibatis.annotations.Update;

public interface SysUserMapper extends BaseMapper<SysUser> {

    @Update("UPDATE sys_user SET deleted = id WHERE id = #{id} AND deleted = 0")
    int softDelete(Long id);
}
