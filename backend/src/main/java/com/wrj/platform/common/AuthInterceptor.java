package com.wrj.platform.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrj.platform.service.TokenManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 轻量登录态拦截器:
 * 校验 Authorization: Bearer <token>,通过后将用户名放入 request attribute(currentUser)。
 * 仅校验登录态,不做逐接口的角色鉴权(菜单级权限由前端动态菜单控制)。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final TokenManager tokenManager;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(TokenManager tokenManager, ObjectMapper objectMapper) {
        this.tokenManager = tokenManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String auth = request.getHeader("Authorization");
        String token = auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : null;
        TokenManager.TokenInfo info = token == null ? null : tokenManager.verify(token);
        if (info == null) {
            // video 标签等原生播放器无法带请求头;或长开页面请求头里的旧令牌已失效——
            // 允许 ?token= 查询参数兜底(视频代理链改写后的子请求均携带该参数)
            String paramToken = request.getParameter("token");
            if (paramToken != null && !paramToken.isBlank()) {
                token = paramToken;
                info = tokenManager.verify(token);
            }
        }
        if (info == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    objectMapper.writeValueAsString(ApiResponse.error(401, "未登录或登录已过期")));
            return false;
        }
        request.setAttribute("currentUser", info.username());
        request.setAttribute("currentNickname", info.nickname());
        return true;
    }
}
