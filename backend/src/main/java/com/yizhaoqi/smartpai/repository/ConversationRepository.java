package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /**
     * 根据用户 ID 和时间范围查询对话记录。
     *
     * @param userId 用户 ID
     * @param startDate 起始日期（可选）
     * @param endDate 结束日期（可选）
     * @return 符合条件的对话记录列表
     */
    List<Conversation> findByUserIdAndTimestampBetween(Long userId, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * 根据用户 ID 查询所有对话记录。
     *
     * @param userId 用户 ID
     * @return 符合条件的对话记录列表
     */
    List<Conversation> findByUserId(Long userId);
    
    /**
     * 根据时间范围查询所有对话记录。
     *
     * @param startDate 起始日期
     * @param endDate 结束日期
     * @return 符合条件的对话记录列表
     */
    List<Conversation> findByTimestampBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * 根据会话 ID 查询该会话下的所有对话记录（按时间升序）。
     * 用于 Redis 过期后从 MySQL 兜底还原某个历史会话的完整消息。
     *
     * @param conversationId 会话 ID
     * @return 该会话的所有对话记录（升序）
     */
    List<Conversation> findByConversationIdOrderByTimestampAsc(String conversationId);
}