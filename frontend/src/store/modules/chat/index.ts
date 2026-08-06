import {
  fetchNewConversation,
  fetchConversations,
  fetchConversationList,
  switchConversation,
  deleteConversation,
  fetchChatStream,
  stopChat
} from '@/service/api/chat';

export const useChatStore = defineStore(SetupStoreId.Chat, () => {
  const conversationId = ref<string>('');
  const input = ref<Api.Chat.Input>({ message: '' });

  const list = ref<Api.Chat.Message[]>([]);

  // 历史会话列表（多个会话，用于"继续聊天"）
  const conversationList = ref<Api.Chat.ConversationSummary[]>([]);
  // 是否显示历史记录侧边栏
  const showHistorySidebar = ref(false);

  const store = useAuthStore();

  const sessionId = ref<string>(''); // 当前 SSE 会话标识
  const connected = ref(false); // SSE 是否已连接

  /** 通过 SSE 发送聊天消息 */
  function sendMessage(message: string) {
    sessionId.value = '';
    connected.value = true;
    fetchChatStream(message, conversationId.value || undefined, {
      onMeta: sid => {
        sessionId.value = sid;
      },
      onMessage: data => handleStreamMessage(data),
      onComplete: () => {
        connected.value = false;
      },
      onError: () => {
        connected.value = false;
      }
    });
  }

  /** 停止当前流式生成 */
  async function stopMessage() {
    await stopChat(sessionId.value);
    connected.value = false;
  }

  /** 处理 SSE 推送的消息 */
  function handleStreamMessage(data: any) {
    if (!data || !data.type) return;
    switch (data.type) {
      case 'connection':
        if (data.sessionId) sessionId.value = data.sessionId;
        break;
      case 'response_chunk':
        appendAssistantChunk(data.content);
        break;
      case 'response_complete':
        finalizeAssistantMessage();
        if (data.conversationId) conversationId.value = data.conversationId;
        break;
      case 'conversation_id':
        if (data.conversationId) conversationId.value = data.conversationId;
        break;
      case 'refuse':
        addMessage('assistant', data.message);
        break;
      case 'error':
        finalizeAssistantMessage();
        if (data.message) addMessage('assistant', `[错误] ${data.message}`);
        break;
      default:
        break;
    }
  }

  /** 当前正在生成中的助手消息索引（用于增量追加） */
  let streamingIndex = -1;

  function appendAssistantChunk(content: string) {
    if (streamingIndex === -1) {
      list.value.push({ role: 'assistant', content: '' });
      streamingIndex = list.value.length - 1;
    }
    list.value[streamingIndex].content += content;
    scrollToBottom.value?.();
  }

  function finalizeAssistantMessage() {
    streamingIndex = -1;
    scrollToBottom.value?.();
  }

  function addMessage(role: 'user' | 'assistant', content: string) {
    list.value.push({ role, content });
    scrollToBottom.value?.();
  }

  /**
   * 新建会话 - 清空当前消息列表并创建新会话
   * 用于减少 token 消耗，避免历史对话过长
   */
  async function newConversation() {
    try {
      // 调用后端 API 创建新会话（清除旧会话历史）
      const { data, error } = await fetchNewConversation();
      if (error || !data) {
        throw new Error('创建新会话失败');
      }

      // 清空前端消息列表
      list.value = [];

      // 重置 conversationId 为后端返回的新会话ID
      conversationId.value = data.conversationId;

      // 重新加载历史会话列表，确保侧边栏会话数据同步
      await loadConversationList();

      console.log('新会话已创建，消息列表已清空');
    } catch (error) {
      console.error('创建新会话失败:', error);
      throw error;
    }
  }

  /**
   * 加载历史会话列表（多会话，用于侧边栏"继续聊天"）
   * 接口: GET /api/v1/chat/conversations
   * request 封装会自动解包，data 已是 ConversationSummary[] 数组
   */
  async function loadConversationList() {
    try {
      const { data, error } = await fetchConversationList();
      if (error) {
        console.error('加载会话列表失败:', error);
        return;
      }
      conversationList.value = Array.isArray(data) ? data : [];
      console.log('加载会话列表成功，共', conversationList.value.length, '个会话');
    } catch (error) {
      console.error('加载会话列表失败:', error);
    }
  }

  /**
   * 切换到指定历史会话并加载其消息（继续聊天）
   * 后端 switch-conversation 会把 current_conversation 指向目标会话，
   * 后续 WebSocket 消息会自动落入该会话，无需重连 WS
   */
  async function switchToConversation(id: string) {
    try {
      const { data, error } = await switchConversation(id);
      if (error || !data) {
        throw new Error('切换会话失败');
      }
      conversationId.value = data.conversationId;
      list.value = Array.isArray(data.messages) ? data.messages : [];
      showHistorySidebar.value = false;
      console.log('切换到会话', id, '，加载', list.value.length, '条消息');
    } catch (error) {
      console.error('切换会话失败:', error);
      throw error;
    }
  }

  /**
   * 删除指定历史会话，并刷新列表
   */
  async function removeConversation(id: string) {
    try {
      const { error } = await deleteConversation(id);
      if (error) {
        throw new Error('删除会话失败');
      }
      await loadConversationList();
      // 若删除的是当前会话，清空对话区
      if (conversationId.value === id) {
        conversationId.value = '';
        list.value = [];
      }
      console.log('删除会话成功:', id);
    } catch (error) {
      console.error('删除会话失败:', error);
      throw error;
    }
  }

  /**
   * 切换历史记录侧边栏显示状态
   */
  function toggleHistorySidebar() {
    showHistorySidebar.value = !showHistorySidebar.value;

    // 打开时加载会话列表
    if (showHistorySidebar.value) {
      loadConversationList();
    }
  }

  /**
   * 清空当前消息列表（不调用后端API）
   */
  function clearMessages() {
    list.value = [];
  }

  const scrollToBottom = ref<null | (() => void)>(null);

  return {
    input,
    conversationId,
    list,
    conversationList,
    showHistorySidebar,
    connected,
    sessionId,
    sendMessage,
    stopMessage,
    scrollToBottom,
    newConversation,
    clearMessages,
    loadConversationList,
    switchToConversation,
    removeConversation,
    toggleHistorySidebar
  };
});
