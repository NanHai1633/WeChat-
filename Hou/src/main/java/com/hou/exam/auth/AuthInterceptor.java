package com.hou.exam.auth;

import com.hou.exam.common.BizException;
import com.hou.exam.entity.Role;
import com.hou.exam.entity.SysUser;
import com.hou.exam.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/** 校验 token，并按路径前缀隔离管理员和学生接口。 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final SysUserMapper userMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            throw BizException.unauthorized("请先登录");
        }
        JwtService.TokenInfo token = jwtService.parse(header.substring(BEARER.length()))
                .orElseThrow(() -> BizException.unauthorized("登录已过期，请重新登录"));

        // 每次都查库，账号被删除或密码被重置后，旧 token 立即失效
        SysUser user = userMapper.selectById(token.userId());
        if (user == null || !user.getTokenVersion().equals(token.version())) {
            throw BizException.unauthorized("登录已失效，请重新登录");
        }

        // 用路由实际匹配到的路径判断；原始 URI 可以带 ;x 这样的内容绕过前缀比较
        String path = String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
        if (path.startsWith("/api/admin/") && user.getRole() != Role.ADMIN) {
            throw BizException.forbidden("需要管理员权限");
        }
        if (path.startsWith("/api/student/") && user.getRole() != Role.STUDENT) {
            throw BizException.forbidden("仅学生账号可以访问");
        }
        CurrentUser.set(new CurrentUser(user.getId(), user.getUsername(), user.getRole()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CurrentUser.clear();
    }
}
