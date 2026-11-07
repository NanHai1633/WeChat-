package com.hou.exam.auth;

import com.hou.exam.entity.Role;

/** 当前请求的登录用户，由 {@link AuthInterceptor} 写入，请求结束时清除。 */
public record CurrentUser(Long id, String username, Role role) {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    static void clear() {
        HOLDER.remove();
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static Long userId() {
        return HOLDER.get().id();
    }
}
