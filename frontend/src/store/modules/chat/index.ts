import { useWebSocket } from '@vueuse/core';
import { fetchNewConversation, fetchConversations } from '@/service/api/chat';

export const useChatStore = defineStore(SetupStoreId.Chat, () => {
  const conversationId = ref<string>('');
  const input = ref<Api.Chat.Input>({ message: '' });

  const list = ref<Api.Chat.Message[]>([]);
  
  // 历史会话消息（从后端获取）
  const historyMessages = ref<Api.Chat.Message[]>([]);
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
      await fetchNewConversation();

      // 清空前端消息列表
      list.value = [];

      // 重置 conversationId
      conversationId.value = '';

      // 重新加载历史会话列表，确保侧边栏会话数据同步
      await loadConversations();

      console.log('新会话已创建，消息列表已清空');
    } catch (error) {
      console.error('创建新会话失败:', error);
      throw error;
    }
  }

  /**
   * 加载当前会话的历史消息（使用项目已有的 ConversationController 接口）
   * request 封装会自动解包，fetchConversations 返回的 data 已是消息数组 Message[]
   */
  async function loadConversations() {
    try {
      const { data, error } = await fetchConversations();
      if (error) {
        console.error('加载历史消息失败:', error);
        return;
      }
      // data 已是 Message[] 数组（request 已解包 response.data.data）
      historyMessages.value = Array.isArray(data) ? data : [];
      console.log('加载历史消息成功，共', historyMessages.value.length, '条消息');
    } catch (error) {
      console.error('加载历史消息失败:', error);
    }
  }

  /**
   * 查看历史记录 - 将历史消息加载到当前聊天列表
   */
  function viewHistory() {
    if (historyMessages.value.length > 0) {
      list.value = [...historyMessages.value];
      showHistorySidebar.value = false;
      console.log('查看历史记录，加载', historyMessages.value.length, '条消息');
    }
  }

  /**
   * 切换历史记录侧边栏显示状态
   */
  function toggleHistorySidebar() {
    showHistorySidebar.value = !showHistorySidebar.value;
    
    // 打开时加载历史消息
    if (showHistorySidebar.value) {
      loadConversations();
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
    historyMessages,
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
    loadConversations,
    viewHistory,
    toggleHistorySidebar
  };
});
