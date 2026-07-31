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
 * 注意：request 封装会自动解包，返回的 data 已经是消息数组 Message[]
 */
export function fetchConversations() {
  return request<Api.Chat.Message[]>({
    url: '/users/conversation',
    method: 'get'
  });
}

/**
 * 按时间范围获取当前用户的历史会话消息（主聊天页 ChatList 使用）
 * 接口路径: /api/v1/users/conversation?start_date=&end_date=
 * request 封装已自动解包，返回的 data 为消息数组 Message[]
 */
export function fetchConversationsByDate(startDate?: string, endDate?: string) {
  return request<Api.Chat.Message[]>({
    url: '/users/conversation',
    method: 'get',
    params: { start_date: startDate, end_date: endDate }
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

/**
 * 获取历史会话列表（多个会话，非消息流）
 * 接口路径: /api/v1/chat/conversations
 * 返回: Api.Chat.ConversationSummary[]
 */
export function fetchConversationList() {
  return request<Api.Chat.ConversationSummary[]>({
    url: '/chat/conversations',
    method: 'get'
  });
}

/**
 * 切换到指定历史会话并取回其消息（继续聊天）
 * 接口路径: /api/v1/chat/switch-conversation
 * 返回: { conversationId, messages: Api.Chat.Message[] }
 */
export function switchConversation(conversationId: string) {
  return request<{ conversationId: string; messages: Api.Chat.Message[] }>({
    url: '/chat/switch-conversation',
    method: 'post',
    data: { conversationId }
  });
}

/**
 * 删除指定历史会话
 * 接口路径: /api/v1/chat/delete-conversation
 */
export function deleteConversation(conversationId: string) {
  return request<null>({
    url: '/chat/delete-conversation',
    method: 'post',
    data: { conversationId }
  });
}
