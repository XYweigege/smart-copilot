package com.yizhaoqi.smartpai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 敏感词过滤配置
 * 可通过 application.yml 的 smartpai.sensitive.* 进行覆盖
 */
@Component
@ConfigurationProperties(prefix = "smartpai.sensitive")
public class SensitiveWordConfig {

    /** 是否启用敏感词过滤，默认 true */
    private boolean enabled = true;

    /** 命中敏感词后返回的 HTTP 状态码，默认 400 */
    private int rejectStatusCode = 400;

    /** 命中敏感词后返回的提示信息 */
    private String rejectMessage = "请求内容包含敏感词，已被拦截";

    /** 是否记录命中审计日志，默认 true */
    private boolean auditLog = true;

    /** 自定义敏感词列表，会覆盖（而非追加）默认词库 */
    private List<String> words = new ArrayList<>();

    /** 是否启用默认内置词库（作为兜底），默认 true */
    private boolean useDefaultWords = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRejectStatusCode() {
        return rejectStatusCode;
    }

    public void setRejectStatusCode(int rejectStatusCode) {
        this.rejectStatusCode = rejectStatusCode;
    }

    public String getRejectMessage() {
        return rejectMessage;
    }

    public void setRejectMessage(String rejectMessage) {
        this.rejectMessage = rejectMessage;
    }

    public boolean isAuditLog() {
        return auditLog;
    }

    public void setAuditLog(boolean auditLog) {
        this.auditLog = auditLog;
    }

    public List<String> getWords() {
        return words;
    }

    public void setWords(List<String> words) {
        this.words = words;
    }

    public boolean isUseDefaultWords() {
        return useDefaultWords;
    }

    public void setUseDefaultWords(boolean useDefaultWords) {
        this.useDefaultWords = useDefaultWords;
    }
}
