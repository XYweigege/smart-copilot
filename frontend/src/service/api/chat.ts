import { request } from '../request';

/**
 * 新建会话
 * 清除当前会话历史，创建新会话ID，减少 token 消耗
 */
export function fetchNewConversation() {
  return request<{ conversationId: string }>({
    url: '/chat/new-conversation',
    method: 'post'
  });
}

/**
 * 获取当前会话的历史消息（使用项目已有的 ConversationController 接口）
 * 接口路径: /api/v1/users/conversation
 * 后端返回结构: { code, message, data: Api.Chat.Message[] }
 */
export function fetchConversations() {
  return request<Api.Chat.ConversationsResponse>({
    url: '/users/conversation',
    method: 'get'
  });
}

/**
 * 获取 WebSocket 停止指令 Token
 */
export function fetchWebSocketToken() {
  return request<{ cmdToken: string }>({
    url: '/chat/websocket-token',
    method: 'get'
  });
}
