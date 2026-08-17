package com.wrj.platform.controller;

import com.wrj.platform.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 登录(演示:admin/admin123)+ 图形验证码 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** 验证码缓存:cid -> code */
    private final Map<String, String> captchaStore = new ConcurrentHashMap<>();
    private static final long CAPTCHA_TTL_MS = 5 * 60 * 1000L;
    private static final long CAPTCHA_TS = 10 * 60 * 1000L;
    private final Map<String, Long> captchaTime = new ConcurrentHashMap<>();

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
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");
        String cid = body.getOrDefault("cid", "");
        String captcha = body.getOrDefault("captcha", "");

        // 校验验证码
        String expected = cid.isEmpty() ? null : captchaStore.get(cid);
        captchaStore.remove(cid);
        if (expected == null) {
            return ApiResponse.error(400, "验证码已过期,请刷新后重试");
        }
        if (!expected.equalsIgnoreCase(captcha)) {
            return ApiResponse.error(400, "验证码错误");
        }

        if ("admin".equals(username) && "admin123".equals(password)) {
            return ApiResponse.ok(Map.of(
                    "token", "demo-token-" + System.currentTimeMillis(),
                    "username", username,
                    "nickname", "系统管理员"
            ));
        }
        return ApiResponse.error(401, "用户名或密码错误");
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
