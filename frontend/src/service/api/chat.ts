import { request } from '../request';
import { localStg } from '@/utils/storage';
import { getServiceBaseURL } from '@/utils/service';

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

// ============ SSE 流式聊天 ============

/** SSE 端点路径（与 request 共用同一代理规则） */
const isHttpProxy = import.meta.env.DEV && import.meta.env.VITE_HTTP_PROXY === 'Y';
const { baseURL } = getServiceBaseURL(import.meta.env, isHttpProxy);
const SSE_ENDPOINT = `${baseURL}/chat/stream`;

/** 聊天流回调 */
export interface ChatStreamCallbacks {
  /** 收到响应数据（已 JSON 解析） */
  onMessage: (data: any) => void;
  /** 收到 meta / connection 事件（含 sessionId） */
  onMeta?: (sessionId: string) => void;
  /** 流结束（正常完成或异常） */
  onComplete?: () => void;
  /** 发生错误 */
  onError?: (err: any) => void;
}

/** 当前活跃的 EventSource 引用，便于停止 */
let activeEventSource: EventSource | null = null;

/**
 * 通过 SSE 发送聊天消息并接收流式响应。
 * 返回的 EventSource 可调用 .close() 中断（配合 stopChat）。
 */
export function fetchChatStream(
  message: string,
  conversationId: string | undefined,
  callbacks: ChatStreamCallbacks
): EventSource {
  // 关闭已有连接
  if (activeEventSource) {
    activeEventSource.close();
    activeEventSource = null;
  }

  const token = localStg.get('token') || '';
  const params = new URLSearchParams({ message, token });
  if (conversationId) {
    params.set('conversationId', conversationId);
  }

  const url = `${SSE_ENDPOINT}?${params.toString()}`;
  const es = new EventSource(url);
  activeEventSource = es;

  // meta 事件：连接建立，返回 sessionId
  es.addEventListener('meta', (e: MessageEvent) => {
    try {
      const data = JSON.parse(e.data);
      if (data.sessionId && callbacks.onMeta) {
        callbacks.onMeta(data.sessionId);
      }
    } catch {
      /* ignore */
    }
  });

  // 默认 message 事件：服务端逐条推送 JSON
  es.onmessage = (e: MessageEvent) => {
    try {
      const data = JSON.parse(e.data);
      callbacks.onMessage(data);
      if (data.type === 'response_complete' || data.type === 'error') {
        es.close();
        activeEventSource = null;
        callbacks.onComplete?.();
      }
    } catch (err) {
      callbacks.onError?.(err);
    }
  };

  es.onerror = (err: Event) => {
    callbacks.onError?.(err);
    es.close();
    activeEventSource = null;
    callbacks.onComplete?.();
  };

  return es;
}

/** 停止当前聊天流（关闭 SSE 连接并通知后端中断） */
export function stopChat(sessionId?: string): Promise<void> {
  if (activeEventSource) {
    activeEventSource.close();
    activeEventSource = null;
  }
  return request
    .post('/chat/stop', { sessionId }, { alertErrorMessage: false })
    .then(() => undefined)
    .catch(() => undefined);
}
