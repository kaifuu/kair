package com.wrj.platform.controller;

import com.wrj.platform.common.ApiResponse;
import com.wrj.platform.entity.SysLog;
import com.wrj.platform.entity.SysUser;
import com.wrj.platform.repository.SysLogRepository;
import com.wrj.platform.repository.SysUserRepository;
import com.wrj.platform.service.MenuService;
import com.wrj.platform.service.TokenManager;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 登录(SysUser + BCrypt + token)+ 图形验证码 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    /** 验证码缓存:cid -> code */
    private final Map<String, String> captchaStore = new ConcurrentHashMap<>();
    private static final long CAPTCHA_TTL_MS = 5 * 60 * 1000L;
    private final Map<String, Long> captchaTime = new ConcurrentHashMap<>();

    private final SysUserRepository userRepository;
    private final SysLogRepository logRepository;
    private final TokenManager tokenManager;
    private final MenuService menuService;

    public AuthController(SysUserRepository userRepository, SysLogRepository logRepository,
                          TokenManager tokenManager, MenuService menuService) {
        this.userRepository = userRepository;
        this.logRepository = logRepository;
        this.tokenManager = tokenManager;
        this.menuService = menuService;
    }

    /** 生成验证码(返回 SVG) */
    @GetMapping("/captcha")
    public ApiResponse<Map<String, String>> captcha() {
        String cid = UUID.randomUUID().toString().replace("-", "");
        String code = randomCode(4);
        captchaStore.put(cid, code);
        captchaTime.put(cid, System.currentTimeMillis());
        // 顺带清理过期
        captchaTime.forEach((k, ts) -> {
            if (System.currentTimeMillis() - ts > CAPTCHA_TTL_MS * 2) {
                captchaStore.remove(k);
                captchaTime.remove(k);
            }
        });
        return ApiResponse.ok(Map.of("cid", cid, "svg", buildSvg(code)));
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> body,
                                                  HttpServletRequest request) {
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");
        String cid = body.getOrDefault("cid", "");
        String captcha = body.getOrDefault("captcha", "");
        String ip = clientIp(request);

        // 校验验证码
        String expected = cid.isEmpty() ? null : captchaStore.get(cid);
        captchaStore.remove(cid);
        if (expected == null) {
            return ApiResponse.error(400, "验证码已过期,请刷新后重试");
        }
        if (!expected.equalsIgnoreCase(captcha)) {
            return ApiResponse.error(400, "验证码错误");
        }

        SysUser user = userRepository.findByUsername(username).orElse(null);
        if (user == null || !ENCODER.matches(password, user.getPassword())) {
            logRepository.save(new SysLog(SysLog.Type.LOGIN, username, "登录失败",
                    "用户名或密码错误", ip, false));
            return ApiResponse.error(401, "用户名或密码错误");
        }
        if (user.getStatus() != SysUser.Status.ENABLED) {
            logRepository.save(new SysLog(SysLog.Type.LOGIN, username, "登录失败",
                    "账号已停用", ip, false));
            return ApiResponse.error(403, "账号已停用,请联系管理员");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        String token = tokenManager.create(user);
        logRepository.save(new SysLog(SysLog.Type.LOGIN, username, "登录成功", null, ip, true));

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname() == null ? user.getUsername() : user.getNickname());
        data.put("roleCode", user.getRole() == null ? "" : user.getRole().getCode());
        data.put("menus", menuService.mine(user));
        return ApiResponse.ok(data);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String auth,
                                    HttpServletRequest request) {
        if (auth != null && auth.startsWith("Bearer ")) {
            tokenManager.invalidate(auth.substring(7).trim());
        }
        String username = String.valueOf(request.getAttribute("currentUser"));
        logRepository.save(new SysLog(SysLog.Type.LOGIN, "null".equals(username) ? null : username,
                "退出登录", null, clientIp(request), true));
        return ApiResponse.ok();
    }

    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> profile(HttpServletRequest request) {
        String username = (String) request.getAttribute("currentUser");
        SysUser user = userRepository.findByUsername(username == null ? "" : username).orElse(null);
        if (user == null) {
            return ApiResponse.error(401, "用户不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        data.put("phone", user.getPhone());
        data.put("roleCode", user.getRole() == null ? "" : user.getRole().getCode());
        data.put("roleName", user.getRole() == null ? "" : user.getRole().getName());
        data.put("menus", menuService.mine(user));
        return ApiResponse.ok(data);
    }

    private static String clientIp(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            return fwd.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String randomCode(int len) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return sb.toString();
    }

    /** 纯 Java 生成干扰线验证码 SVG */
    private static String buildSvg(String code) {
        int w = 120, h = 44;
        StringBuilder sb = new StringBuilder();
        sb.append("<svg xmlns='http://www.w3.org/2000/svg' width='").append(w).append("' height='").append(h)
                .append("' viewBox='0 0 ").append(w).append(" ").append(h).append("'>");
        sb.append("<rect width='100%' height='100%' fill='#eff4ff'/>");
        // 干扰线
        String[] lineColors = {"#bfd4f7", "#c9e2f5", "#dbe7fb"};
        for (int i = 0; i < 4; i++) {
            sb.append("<line x1='").append(rnd(0, w)).append("' y1='").append(rnd(0, h))
                    .append("' x2='").append(rnd(0, w)).append("' y2='").append(rnd(0, h))
                    .append("' stroke='").append(lineColors[i % lineColors.length]).append("' stroke-width='1'/>");
        }
        // 干扰点
        for (int i = 0; i < 14; i++) {
            sb.append("<circle cx='").append(rnd(0, w)).append("' cy='").append(rnd(0, h))
                    .append("' r='1' fill='#c3d5f2'/>");
        }
        // 字符:随机旋转/基线偏移/蓝色系
        String[] fills = {"#155eef", "#0e7ee0", "#2f6fe4", "#1d7ed8"};
        for (int i = 0; i < code.length(); i++) {
            double x = 14 + i * 26;
            double y = 30 + (Math.random() * 8 - 4);
            int rotate = (int) (Math.random() * 50 - 25);
            sb.append("<text x='").append(fmt(x)).append("' y='").append(fmt(y))
                    .append("' font-family='Arial, sans-serif' font-size='26' font-weight='700' fill='")
                    .append(fills[i % fills.length]).append("' transform='rotate(").append(rotate)
                    .append(" ").append(fmt(x)).append(" ").append(fmt(y)).append(")'>")
                    .append(code.charAt(i)).append("</text>");
        }
        sb.append("</svg>");
        return sb.toString();
    }

    private static int rnd(int min, int max) {
        return min + (int) (Math.random() * (max - min));
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
