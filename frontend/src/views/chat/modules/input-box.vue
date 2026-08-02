<script setup lang="ts">
const chatStore = useChatStore();
const { input, list, wsStatus, wsData } = storeToRefs(chatStore);

const latestMessage = computed(() => {
  return list.value[list.value.length - 1] ?? {};
});

const isSending = computed(() => {
  return (
    latestMessage.value?.role === 'assistant' && ['loading', 'pending'].includes(latestMessage.value?.status || '')
  );
});

const sendable = computed(
  () => (!input.value.message && !isSending) || ['CLOSED', 'CONNECTING'].includes(wsStatus.value)
);

watch(wsData, val => {
  const data = JSON.parse(val);

  // 后端回传当前会话ID，保持前后端会话状态同步
  if (data.type === 'conversation' && data.conversationId) {
    chatStore.conversationId = data.conversationId;
    return;
  }

  const assistant = list.value[list.value.length - 1];

  if (data.type === 'completion' && data.status === 'finished' && assistant.status !== 'error')
    assistant.status = 'finished';
  if (data.error) assistant.status = 'error';
  else if (data.chunk) {
    assistant.status = 'loading';
    assistant.content += data.chunk;
  }
});

const handleSend = async () => {
  //  判断是否正在发送, 如果发送中，则停止ai继续响应
  if (isSending.value) {
    const { error, data } = await request<Api.Chat.Token>({ url: 'chat/websocket-token', baseURL: 'proxy-api' });
    if (error) return;

    chatStore.wsSend(JSON.stringify({ type: 'stop', _internal_cmd_token: data.cmdToken }));

    list.value[list.value.length - 1].status = 'finished';
    if (!latestMessage.value.content) list.value.pop();
    return;
  }

  list.value.push({
    content: input.value.message,
    role: 'user'
  });
  chatStore.wsSend(input.value.message);
  list.value.push({
    content: '',
    role: 'assistant',
    status: 'pending'
  });
  input.value.message = '';
  nextTick(() => inputRef.value?.focus());
};

const inputRef = ref();
// 清空输入框
const handleClear = () => {
  input.value.message = '';
  inputRef.value?.focus();
};
// 手动插入换行符（确保所有浏览器兼容）
const insertNewline = () => {
  const textarea = inputRef.value;
  const start = textarea.selectionStart;
  const end = textarea.selectionEnd;

  // 在光标位置插入换行符
  input.value.message = `${input.value.message.substring(0, start)}\n${input.value.message.substring(end)}`;

  // 更新光标位置（在插入的换行符之后）
  nextTick(() => {
    textarea.selectionStart = start + 1;
    textarea.selectionEnd = start + 1;
    textarea.focus(); // 确保保持焦点
  });
};

// ctrl + enter 换行
// enter 发送
const handShortcut = (e: KeyboardEvent) => {
  if (e.key === 'Enter') {
    e.preventDefault();

    if (!e.shiftKey && !e.ctrlKey) {
      handleSend();
    } else insertNewline();
  }
};
</script>

<template>
  <div class="ui-card ui-surface relative w-full p-3">
    <textarea
      ref="inputRef"
      v-model.trim="input.message"
      placeholder="给 派聪明 发送消息，Enter 发送 / Shift+Enter 换行"
      class="min-h-12 w-full cursor-text resize-none border-none bg-transparent text-4 leading-relaxed color-#333 caret-[rgb(var(--primary-color))] outline-none dark:color-#f1f1f1"
      @keydown="handShortcut"
    />
    <div class="flex items-center justify-between pt-2">
      <div class="flex items-center gap-3">
        <!-- 连接状态徽标 -->
        <NTag
          round
          size="small"
          :bordered="false"
          :type="wsStatus === 'OPEN' ? 'success' : wsStatus === 'CONNECTING' ? 'warning' : 'error'"
        >
          <template #icon>
            <icon-eos-icons:loading v-if="wsStatus === 'CONNECTING'" />
            <icon-fluent:plug-connected-checkmark-20-filled v-else-if="wsStatus === 'OPEN'" />
            <icon-tabler:plug-connected-x v-else />
          </template>
          {{ wsStatus === 'OPEN' ? '已连接' : wsStatus === 'CONNECTING' ? '连接中' : '已断开' }}
        </NTag>
        <NButton
          v-if="input.message"
          quaternary
          size="small"
          class="rounded-8"
          @click="handleClear"
        >
          <template #icon>
            <icon-material-symbols:close-small-outline />
          </template>
          清空
        </NButton>
      </div>
      <NButton
        :disabled="sendable"
        class="ui-btn-primary rounded-8px px-4!"
        strong
        type="primary"
        @click="handleSend"
      >
        <template #icon>
          <icon-material-symbols:stop-rounded v-if="isSending" />
          <icon-guidance:send v-else />
        </template>
        {{ isSending ? '停止' : '发送' }}
      </NButton>
    </div>
  </div>
</template>

<style scoped></style>
