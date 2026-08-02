package com.yizhaoqi.smartpai.config;

import com.yizhaoqi.smartpai.utils.LogUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 敏感词 HTTP 拦截过滤器
 *
 * 职责：
 * 1. 对进入系统的 HTTP 请求（GET 参数 + POST/PUT 等 body）做敏感词扫描。
 * 2. 命中敏感词时：记录审计日志（含用户、路径、命中词），返回 400 并拒绝请求，
 *    不将内容继续传递给业务层。
 * 3. 不拦截 OPTIONS、静态资源、文件上传（multipart）。
 *
 * 注册见 WebConfig。
 */
public class SensitiveWordFilterOnce extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(SensitiveWordFilterOnce.class);

    private final SensitiveWordFilter filter;
    private final SensitiveWordConfig config;

    public SensitiveWordFilterOnce(SensitiveWordFilter filter, SensitiveWordConfig config) {
        this.filter = filter;
        this.config = config;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!config.isEnabled()) {
            return true;
        }
        String method = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        String uri = request.getRequestURI();
        // 放行静态资源与 swagger/ actuator 等
        if (uri.startsWith("/swagger") || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/actuator") || uri.startsWith("/static")
                || uri.endsWith(".html") || uri.endsWith(".js") || uri.endsWith(".css")) {
            return true;
        }
        // 文件上传不扫描 body，避免误伤与内存问题
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().startsWith("multipart/")) {
            return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request);
        StringBuilder scan = new StringBuilder();

        // 1. 查询参数
        request.getParameterMap().forEach((k, vals) -> {
            scan.append(k).append("=");
            for (String v : vals) {
                scan.append(v).append(" ");
            }
        });

        // 2. body（非文件上传）
        String body = wrapped.getCachedBodyAsString();
        if (body != null && !body.isEmpty()) {
            scan.append(body);
        }

        if (!scan.toString().isEmpty()) {
            Set<String> hits = filter.findAll(scan.toString());
            if (!hits.isEmpty()) {
                handleReject(request, hits);
                response.setStatus(config.getRejectStatusCode() > 0 ? config.getRejectStatusCode() : HttpStatus.BAD_REQUEST.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                String json = "{\"error\":\"sensitive_word_blocked\",\"message\":\""
                        + escape(config.getRejectMessage()) + "\"}";
                response.getWriter().write(json);
                return;
            }
        }

        chain.doFilter(wrapped, response);
    }

    private void handleReject(HttpServletRequest request, Set<String> hits) {
        String username = "anonymous";
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UserDetails) {
                username = ((UserDetails) auth.getPrincipal()).getUsername();
            } else if (auth != null && auth.isAuthenticated()) {
                username = auth.getName();
            }
        } catch (Exception ignored) {
            // 忽略认证上下文异常
        }
        String msg = String.format("敏感词拦截: 用户=%s 路径=%s 方法=%s 命中词=%s IP=%s",
                username, request.getRequestURI(), request.getMethod(), hits, request.getRemoteAddr());
        if (config.isAuditLog()) {
            // 审计日志单独分类，便于后续检索
            LogUtils.logAudit("SENSITIVE_WORD_BLOCKED", msg);
        } else {
            logger.warn(msg);
        }
    }

    private String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\"", "\\\"").replace("\\", "\\\\").replace("\n", " ");
    }
}
