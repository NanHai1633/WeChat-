package com.hou.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hou.exam.auth.JwtService;
import com.hou.exam.common.BizException;
import com.hou.exam.common.PageResult;
import com.hou.exam.dto.AuthDtos;
import com.hou.exam.entity.Role;
import com.hou.exam.entity.SysUser;
import com.hou.exam.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public record StudentVO(Long id, String username, LocalDateTime createdAt) {
    }

    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, request.username()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw BizException.unauthorized("账号或密码错误");
        }
        if (request.role() != null && request.role() != user.getRole()) {
            throw BizException.forbidden(request.role() == Role.ADMIN ? "该账号不是管理员账号" : "该账号不是学生账号");
        }
        return new AuthDtos.LoginResponse(jwtService.issue(user), user.getId(), user.getUsername(), user.getRole());
    }

    public void changeAdminPassword(Long adminId, AuthDtos.ChangePasswordRequest request) {
        SysUser user = userMapper.selectById(adminId);
        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw BizException.badRequest("原密码错误");
        }
        updatePassword(user, request.newPassword());
    }

    public StudentVO createStudent(AuthDtos.CreateStudentRequest request) {
        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.STUDENT);
        user.setTokenVersion(0);
        user.setCreatedAt(LocalDateTime.now());
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw BizException.conflict("账号已存在");
        }
        return toVO(user);
    }

    public PageResult<StudentVO> listStudents(String keyword, int page, int size) {
        LambdaQueryWrapper<SysUser> query = Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getRole, Role.STUDENT)
                .like(StringUtils.hasText(keyword), SysUser::getUsername, keyword)
                .orderByDesc(SysUser::getId);
        Page<SysUser> result = userMapper.selectPage(PageResult.pageOf(page, size), query);
        return PageResult.of(result, this::toVO);
    }

    public void deleteStudent(Long id) {
        requireStudent(id);
        userMapper.softDelete(id);
    }

    public void resetStudentPassword(Long id, AuthDtos.ResetPasswordRequest request) {
        updatePassword(requireStudent(id), request.password());
    }

    public SysUser requireStudent(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null || user.getRole() != Role.STUDENT) {
            throw BizException.notFound("学生账号不存在");
        }
        return user;
    }

    private void updatePassword(SysUser user, String rawPassword) {
        // 版本号加一，让旧 token 失效
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setPassword(passwordEncoder.encode(rawPassword));
        update.setTokenVersion(user.getTokenVersion() + 1);
        userMapper.updateById(update);
    }

    private StudentVO toVO(SysUser user) {
        return new StudentVO(user.getId(), user.getUsername(), user.getCreatedAt());
    }
}
