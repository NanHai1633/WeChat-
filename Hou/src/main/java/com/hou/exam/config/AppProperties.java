package com.hou.exam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, List<Admin> admins, String uploadDir) {

    public record Jwt(String secret, int expireHours) {
    }

    public record Admin(String username, String password) {
    }
}
