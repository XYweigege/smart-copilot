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
        class="history-sidebar ui-card ui-surface w-80 flex flex-col absolute left-4 top-4 bottom-4 z-20 rounded-10px overflow-hidden"
      >
        <!-- 侧边栏头部 -->
        <div class="px-4 py-3.5 border-b border-[var(--app-border)] flex items-center justify-between bg-[rgb(var(--primary-color)_/_0.04)]">
          <div class="flex items-center gap-2">
            <icon-material-symbols:history class="text-5 text-[rgb(var(--primary-color))]" />
            <h3 class="text-base font-semibold text-gray-800 dark:text-gray-100">历史会话</h3>
          </div>
          <button 
            @click="chatStore.toggleHistorySidebar()"
            class="p-1.5 hover:bg-gray-100 rounded-lg transition-colors"
          >
            <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 text-gray-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
        
        <!-- 会话列表 -->
        <div class="flex-1 overflow-y-auto p-3 space-y-2">
          <!-- 空状态 -->
          <div v-if="chatStore.conversationList.length === 0" class="text-center py-12 text-gray-400">
            <icon-material-symbols:chat-bubble-outline class="h-12 w-12 mx-auto mb-3 opacity-40" />
            <p class="text-sm">暂无历史会话</p>
            <p class="text-xs mt-1 text-gray-300">开始一段新对话吧</p>
          </div>
          
          <!-- 会话项列表 -->
          <div v-else class="space-y-2">
            <div
              v-for="conv in chatStore.conversationList"
              :key="conv.conversationId"
              class="group p-3 rounded-8px border border-[var(--app-border)] hover:bg-[rgb(var(--primary-color)_/_0.04)] transition-colors duration-150 cursor-pointer relative overflow-hidden"
              :class="conv.conversationId === chatStore.conversationId ? 'conv-item--active' : ''"
              @click="handleSwitchConversation(conv.conversationId)"
            >
              <!-- 选中态左侧色条 -->
              <span
                v-if="conv.conversationId === chatStore.conversationId"
                class="absolute left-0 top-0 bottom-0 w-1 bg-[rgb(var(--primary-color))]"
              />
              <!-- 标题与删除 -->
              <div class="flex items-center justify-between gap-2">
                <span class="text-sm font-medium text-gray-800 truncate flex-1 pl-1">{{ conv.title || '未命名会话' }}</span>
                <button
                  class="opacity-0 group-hover:opacity-100 p-1 hover:bg-red-50 rounded-lg transition-all"
                  @click.stop="handleDeleteConversation(conv.conversationId)"
                  title="删除会话"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                  </svg>
                </button>
              </div>
              
              <!-- 元信息 -->
              <div class="flex items-center gap-2 mt-1.5 pl-1">
                <span class="text-xs text-gray-400">{{ formatTime(conv.updatedAt) }}</span>
                <span class="text-xs text-gray-300">·</span>
                <span class="text-xs text-gray-400">{{ conv.messageCount }} 条消息</span>
              </div>
            </div>
          </div>
        </div>
        
        <!-- 底部操作区 -->
        <div class="p-3 border-t border-[var(--app-border)]">
          <button
            @click="handleNewConversation"
            class="ui-btn-primary w-full px-4 py-2 text-white rounded-8px text-sm font-medium flex items-center justify-center gap-2"
          >
            <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
              <path stroke-linecap="round" stroke-linejoin="round" d="M12 4v16m8-8H4" />
            </svg>
            新建会话
          </button>
        </div>
      </div>
    </Transition>
    
    <!-- 主聊天区域 -->
    <div class="flex-1 flex flex-col gap-4 transition-all duration-300 min-w-0" :class="{ 'ml-88': chatStore.showHistorySidebar }">
      <!-- 顶部按钮栏 -->
      <div class="ui-card flex justify-between items-center px-4 py-2.5 sticky top-0 z-10">
        <!-- 历史记录按钮 -->
        <button
          @click="chatStore.toggleHistorySidebar()"
          class="flex items-center gap-2 px-3 py-1.5 text-gray-600 bg-[rgb(var(--primary-color)_/_0.05)] hover:bg-[rgb(var(--primary-color)_/_0.1)] rounded-6px transition-colors duration-150 text-sm font-medium"
        >
          <icon-material-symbols:history class="h-4 w-4" />
          历史记录
        </button>

        <!-- 当前会话标题 -->
        <div class="flex items-center gap-2 text-gray-600 dark:text-gray-300 min-w-0 flex-1 justify-center px-4">
          <icon-material-symbols:chat-bubble-outline class="text-5 flex-shrink-0 text-[rgb(var(--primary-color))]" />
          <span class="text-base font-semibold truncate">{{ currentTitle }}</span>
        </div>

        <!-- 新会话按钮 -->
        <button
          @click="handleNewConversation"
          class="ui-btn-primary flex items-center gap-2 px-3.5 py-1.5 text-white rounded-6px text-sm font-medium"
        >
          <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 4v16m8-8H4" />
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
  transition: transform 0.32s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.32s ease;
}

.slide-enter-from,
.slide-leave-to {
  transform: translateX(-12px);
  opacity: 0;
}

/* 文本截断3行 */
.line-clamp-3 {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 当前会话选中态：主色浅底 + 左侧高亮，弱化写死蓝的炫光感 */
.conv-item--active {
  background-color: rgb(var(--primary-color) / 0.08);
  border-color: rgb(var(--primary-color) / 0.3);
}
</style>
