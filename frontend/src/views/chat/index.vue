<script setup lang="ts">
import { computed } from 'vue';
import { useChatStore } from '@/store/modules/chat';
import ChatList from './modules/chat-list.vue';
import InputBox from './modules/input-box.vue';

const chatStore = useChatStore();

// 新建会话
async function handleNewConversation() {
  try {
    await chatStore.newConversation();
  } catch (error) {
    console.error('新建会话失败:', error);
  }
}

// 切换到指定历史会话（继续聊天）
async function handleSwitchConversation(id: string) {
  try {
    await chatStore.switchToConversation(id);
  } catch (error) {
    console.error('切换会话失败:', error);
  }
}

// 删除指定历史会话
async function handleDeleteConversation(id: string) {
  try {
    await chatStore.removeConversation(id);
  } catch (error) {
    console.error('删除会话失败:', error);
  }
}

// 截断消息内容
function truncateContent(content: string, len = 30): string {
  if (!content) return '';
  return content.length > len ? content.substring(0, len) + '...' : content;
}

// 格式化时间显示
function formatTime(dateStr: string): string {
  if (!dateStr || dateStr === '未知时间') return '';
  const date = new Date(dateStr.replace(' ', 'T'));
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMins = Math.floor(diffMs / (1000 * 60));
  const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
  
  if (diffMins < 1) return '刚刚';
  if (diffMins < 60) return `${diffMins}分钟前`;
  if (diffHours < 24) return `${diffHours}小时前`;
  if (diffDays < 7) return `${diffDays}天前`;
  
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' });
}

// 当前会话标题（从历史会话列表中匹配 conversationId）
const currentTitle = computed(() => {
  const current = chatStore.conversationList.find(c => c.conversationId === chatStore.conversationId);
  return current?.title || (chatStore.list.length ? '当前会话' : '新会话');
});
</script>

<template>
  <div class="chat-container flex h-full relative">
    <!-- 历史记录侧边栏 -->
    <Transition name="slide">
      <div 
        v-if="chatStore.showHistorySidebar"
        class="history-sidebar w-80 bg-white border-r border-gray-200 flex flex-col shadow-lg absolute left-0 top-0 h-full z-10 rounded-l-xl"
      >
        <!-- 侧边栏头部 -->
        <div class="p-4 border-b border-gray-200 flex items-center justify-between">
          <h3 class="text-lg font-semibold text-gray-800">历史会话</h3>
          <button 
            @click="chatStore.toggleHistorySidebar()"
            class="p-1 hover:bg-gray-100 rounded-md transition-colors"
          >
            <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 text-gray-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
        
        <!-- 会话列表 -->
        <div class="flex-1 overflow-y-auto p-2">
          <!-- 空状态 -->
          <div v-if="chatStore.conversationList.length === 0" class="text-center py-8 text-gray-400">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-12 w-12 mx-auto mb-3 opacity-50" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
            </svg>
            <p class="text-sm">暂无历史会话</p>
          </div>
          
          <!-- 会话项列表 -->
          <div v-else class="space-y-2">
            <div
              v-for="conv in chatStore.conversationList"
              :key="conv.conversationId"
              class="group p-3 rounded-lg border border-gray-100 hover:bg-gray-50 transition-colors cursor-pointer"
              :class="conv.conversationId === chatStore.conversationId ? 'bg-blue-50 border-blue-200' : ''"
              @click="handleSwitchConversation(conv.conversationId)"
            >
              <!-- 标题与删除 -->
              <div class="flex items-center justify-between gap-2">
                <span class="text-sm font-medium text-gray-800 truncate flex-1">{{ conv.title || '未命名会话' }}</span>
                <button
                  class="opacity-0 group-hover:opacity-100 p-1 hover:bg-red-50 rounded transition-opacity"
                  @click.stop="handleDeleteConversation(conv.conversationId)"
                  title="删除会话"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                  </svg>
                </button>
              </div>
              
              <!-- 元信息 -->
              <div class="flex items-center gap-2 mt-1">
                <span class="text-xs text-gray-400">{{ formatTime(conv.updatedAt) }}</span>
                <span class="text-xs text-gray-400">·</span>
                <span class="text-xs text-gray-400">{{ conv.messageCount }} 条消息</span>
              </div>
            </div>
          </div>
        </div>
        
        <!-- 底部操作区 -->
        <div class="p-4 border-t border-gray-200">
          <button
            @click="handleNewConversation"
            class="w-full px-4 py-2 text-white rounded-lg transition-colors duration-200 text-sm font-medium"
            style="background-color: #2563eb"
          >
            新建会话
          </button>
        </div>
      </div>
    </Transition>
    
    <!-- 主聊天区域 -->
    <div class="flex-1 flex flex-col gap-4 transition-all duration-300" :class="{ 'ml-80': chatStore.showHistorySidebar }">
      <!-- 顶部按钮栏 -->
      <div class="flex justify-between items-center mb-2">
        <!-- 历史记录按钮 -->
        <button
          @click="chatStore.toggleHistorySidebar()"
          class="flex items-center gap-2 px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg transition-colors duration-200 text-sm font-medium"
        >
          <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          历史记录
        </button>

        <!-- 当前会话标题 -->
        <div class="flex items-center gap-2 text-gray-600 min-w-0 flex-1 justify-center px-4">
          <icon-material-symbols:chat-bubble-outline class="text-4 flex-shrink-0" />
          <span class="text-4 font-medium truncate">{{ currentTitle }}</span>
        </div>

        <!-- 新会话按钮 -->
        <button
          @click="handleNewConversation"
          class="flex items-center gap-2 px-4 py-2 text-white rounded-lg transition-colors duration-200 text-sm font-medium shadow-sm hover:shadow-md"
          style="background-color: #2563eb"
        >
          <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
          </svg>
          新会话
        </button>
      </div>
      
      <ChatList />
      <InputBox />
    </div>
  </div>
</template>

<style scoped>
/* 侧边栏滑入动画 */
.slide-enter-active,
.slide-leave-active {
  transition: transform 0.3s ease;
}

.slide-enter-from,
.slide-leave-to {
  transform: translateX(-100%);
}

/* 文本截断3行 */
.line-clamp-3 {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
