package com.yizhaoqi.smartpai.service;

/**
 * 聊天输出通道抽象。
 * 将"向客户端推送消息"与具体传输协议（WebSocket / SSE）解耦。
 * WebSocket 方案已移除，当前仅 SSE 实现（{@link SseChatOutput}）。
 */
public interface ChatOutput {

    /** 底层会话标识（WebSocket 时为 sessionId，SSE 时为业务 sessionId） */
    String getId();

    /** 客户端 IP，仅用于审计日志 */
    String getClientIp();

    /** 发送一个 JSON 可序列化的对象（如 Map）给客户端 */
    void send(Object payload);

    /** 发送一个文本增量（chunk），前端按 content 字段累加 */
    void sendChunk(String chunk);

    /** 标记本次响应流结束（正常或异常），由实现方决定是否真正关闭通道 */
    void complete();

    /** 当前是否已被用户停止 */
    boolean isStopped();
}
