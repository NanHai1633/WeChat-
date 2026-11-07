package com.hou.exam.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hou.exam.entity.Role;
import com.hou.exam.entity.SysUser;
import com.hou.exam.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 启动时创建配置中的管理员账号；系统不提供注册管理员的接口。已存在的账号不会被改动。 */
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final AppProperties properties;
    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        for (AppProperties.Admin admin : properties.admins()) {
            boolean exists = userMapper.exists(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getUsername, admin.username()));
            if (exists) {
                continue;
            }
            SysUser user = new SysUser();
            user.setUsername(admin.username());
            user.setPassword(passwordEncoder.encode(admin.password()));
            user.setRole(Role.ADMIN);
            user.setTokenVersion(0);
            user.setCreatedAt(LocalDateTime.now());
            userMapper.insert(user);
            log.info("已创建管理员账号 {}", admin.username());
        }
    }
}
