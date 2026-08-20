package com.wrj.platform.service;

import com.wrj.platform.entity.SysUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存 token 管理(演示级,重启失效):
 * token -> 用户信息 + 过期时间,滑动续期,定期清理。
 */
@Component
public class TokenManager {

    public record TokenInfo(Long userId, String username, String nickname, String roleCode, long expireAt) {
    }

    private final Map<String, TokenInfo> tokens = new ConcurrentHashMap<>();

    @Value("${auth.token-ttl-hours:12}")
    private long ttlHours;

    public String create(SysUser user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        String nickname = user.getNickname() == null || user.getNickname().isBlank()
                ? user.getUsername() : user.getNickname();
        String roleCode = user.getRole() == null ? "" : user.getRole().getCode();
        tokens.put(token, new TokenInfo(user.getId(), user.getUsername(), nickname, roleCode,
                System.currentTimeMillis() + ttlHours * 3600_000L));
        return token;
    }

    /** 校验并滑动续期,无效返回 null */
    public TokenInfo verify(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        TokenInfo info = tokens.get(token);
        if (info == null) {
            return null;
        }
        if (info.expireAt() < System.currentTimeMillis()) {
            tokens.remove(token);
            return null;
        }
        TokenInfo renewed = new TokenInfo(info.userId(), info.username(), info.nickname(),
                info.roleCode(), System.currentTimeMillis() + ttlHours * 3600_000L);
        tokens.put(token, renewed);
        return renewed;
    }

    public void invalidate(String token) {
        if (token != null) {
            tokens.remove(token);
        }
    }

    /** 每 10 分钟清理过期 token */
    @Scheduled(fixedDelay = 600_000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        tokens.entrySet().removeIf(e -> e.getValue().expireAt() < now);
    }
}
