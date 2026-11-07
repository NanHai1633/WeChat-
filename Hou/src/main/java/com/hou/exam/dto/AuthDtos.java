package com.hou.exam.dto;

import com.hou.exam.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public final class AuthDtos {

    private static final String PASSWORD_RULE = "^[\\x21-\\x7E]{6,64}$";
    private static final String PASSWORD_MESSAGE = "密码需为 6-64 位字母、数字或符号";

    private AuthDtos() {
    }

    /** role 是登录页选择的身份，和账号实际角色不一致时拒绝登录。 */
    public record LoginRequest(
            @NotBlank(message = "请输入账号") String username,
            @NotBlank(message = "请输入密码") String password,
            Role role) {
    }

    public record LoginResponse(String token, Long userId, String username, Role role) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "请输入原密码") String oldPassword,
            @NotBlank(message = "请输入新密码")
            @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String newPassword) {
    }

    public record CreateStudentRequest(
            @NotBlank(message = "请输入账号")
            @Pattern(regexp = "^\\S{2,32}$", message = "账号需为 2-32 位且不能包含空格") String username,
            @NotBlank(message = "请输入密码")
            @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String password) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "请输入新密码")
            @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String password) {
    }
}
