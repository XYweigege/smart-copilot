import { useWebSocket } from '@vueuse/core';
import {
  fetchNewConversation,
  fetchConversations,
  fetchConversationList,
  switchConversation,
  deleteConversation
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

  const sessionId = ref<string>(''); // WebSocket session ID

  const {
    status: wsStatus,
    data: wsData,
    send: wsSend,
    open: wsOpen,
    close: wsClose
  } = useWebSocket(`/proxy-ws/chat/${store.token}`, {
    autoReconnect: true
  });

  // 监听WebSocket消息，捕获sessionId
  watch(wsData, (val) => {
    if (!val) return;
    try {
      const data = JSON.parse(val);
      if (data.type === 'connection' && data.sessionId) {
        sessionId.value = data.sessionId;
        console.log('WebSocket会话ID已更新:', sessionId.value);
      }
    } catch (e) {
      // Ignore JSON parse errors for non-JSON messages
    }
  });

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
    wsStatus,
    wsData,
    wsSend,
    wsOpen,
    wsClose,
    sessionId,
    scrollToBottom,
    newConversation,
    clearMessages,
    loadConversationList,
    switchToConversation,
    removeConversation,
    toggleHistorySidebar
  };
});
