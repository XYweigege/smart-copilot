package com.yizhaoqi.smartpai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 基于 SseEmitter 的 {@link ChatOutput} 实现。
 * 每个聊天流对应一个 SseEmitter，通过它向前端推送事件。
 */
public class SseChatOutput implements ChatOutput {

    private static final Logger logger = LoggerFactory.getLogger(SseChatOutput.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final SseEmitter emitter;
    private final String sessionId;
    private final String clientIp;
    private volatile boolean stopped = false;
    private volatile boolean closed = false;

    public SseChatOutput(SseEmitter emitter, String sessionId, String clientIp) {
        this.emitter = emitter;
        this.sessionId = sessionId;
        this.clientIp = clientIp;
    }

    @Override
    public String getId() {
        return sessionId;
    }

    @Override
    public String getClientIp() {
        return clientIp;
    }

    @Override
    public void send(Object payload) {
        if (closed) {
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .data(mapper.writeValueAsString(payload))
                    .id(sessionId));
        } catch (IllegalStateException e) {
            // 通道已关闭（客户端断开），标记后静默返回
            closed = true;
            logger.debug("SSE 通道已关闭，忽略发送: {}", e.getMessage());
        } catch (Exception e) {
            closed = true;
            logger.error("SSE 发送数据失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void sendChunk(String chunk) {
        send(Map.of("type", "response_chunk", "content", chunk != null ? chunk : "", "chunkType", "delta"));
    }

    @Override
    public void complete() {
        if (closed) {
            return;
        }
        try {
            emitter.complete();
            closed = true;
        } catch (Exception e) {
            logger.debug("SSE 完成通知失败（通道可能已关闭）: {}", e.getMessage());
        }
    }

    @Override
    public boolean isStopped() {
        return stopped;
    }

    public void markStopped() {
        this.stopped = true;
    }

    public SseEmitter getEmitter() {
        return emitter;
    }
}
