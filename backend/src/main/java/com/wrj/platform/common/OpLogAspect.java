package com.wrj.platform.common;

import com.wrj.platform.entity.SysLog;
import com.wrj.platform.repository.SysLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 操作日志切面:成功/失败均记录,操作人取 AuthInterceptor 放入的 currentUser */
@Aspect
@Component
public class OpLogAspect {

    private final SysLogRepository logRepository;

    public OpLogAspect(SysLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    @Around("@annotation(opLog)")
    public Object around(ProceedingJoinPoint pjp, OpLog opLog) throws Throwable {
        Throwable error = null;
        try {
            return pjp.proceed();
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            try {
                record(opLog, error);
            } catch (Exception ignored) {
            }
        }
    }

    private void record(OpLog opLog, Throwable error) {
        String username = "anonymous";
        String ip = "";
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest req = attrs.getRequest();
            Object user = req.getAttribute("currentUser");
            if (user != null) {
                username = user.toString();
            }
            ip = clientIp(req);
        }
        SysLog log = new SysLog();
        log.setType(SysLog.Type.OPERATE);
        log.setUsername(username);
        log.setAction(opLog.module() + "·" + opLog.action());
        log.setDetail(error == null ? "成功"
                : "失败: " + error.getClass().getSimpleName() + " " + error.getMessage());
        log.setIp(ip);
        log.setSuccess(error == null);
        logRepository.save(log);
    }

    private String clientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
