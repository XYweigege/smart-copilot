<script setup lang="ts">
import { NScrollbar } from 'naive-ui';
import { VueMarkdownItProvider } from 'vue-markdown-shiki';
import ChatMessage from './chat-message.vue';
import { fetchConversationsByDate } from '@/service/api/chat';

defineOptions({
  name: 'ChatList'
});

const chatStore = useChatStore();
const { list, sessionId } = storeToRefs(chatStore);

// 提供消息索引给子组件，用于单条删除
provide('messageIndex', ref(-1));

const loading = ref(false);
const scrollbarRef = ref<InstanceType<typeof NScrollbar>>();

watch(() => [...list.value], scrollToBottom);

function scrollToBottom() {
  setTimeout(() => {
    scrollbarRef.value?.scrollBy({
      top: 999999999999999,
      behavior: 'auto'
    });
  }, 100);
}

const range = ref<[number, number]>([dayjs().subtract(7, 'day').valueOf(), dayjs().add(1, 'day').valueOf()]);

const params = computed(() => {
  return {
    start_date: dayjs(range.value[0]).format('YYYY-MM-DD'),
    end_date: dayjs(range.value[1]).format('YYYY-MM-DD')
  };
});

watchEffect(() => {
  getList();
});

async function getList() {
  loading.value = true;
  const { error, data } = await fetchConversationsByDate(params.value.start_date, params.value.end_date);
  if (!error && data) {
    list.value = data;
  }
  loading.value = false;
}

// 删除单条消息
function handleDelete(index?: number) {
  if (typeof index === 'number' && index >= 0) {
    list.value.splice(index, 1);
  }
}

// 重新生成：移除当前助手消息，用上一条用户消息重新提问
function handleRegenerate() {
  const last = list.value[list.value.length - 1];
  if (last?.role === 'assistant') {
    list.value.pop();
  }
  const userMsg = [...list.value].reverse().find(m => m.role === 'user');
  if (userMsg) {
    chatStore.sendMessage(userMsg.content);
    list.value.push({ content: '', role: 'assistant', status: 'pending' });
  }
}

onMounted(() => {
  chatStore.scrollToBottom = scrollToBottom;
});
</script>

<template>
  <Suspense>
    <NScrollbar ref="scrollbarRef" class="h-0 flex-auto">
      <Teleport defer to="#header-extra">
        <div class="px-10">
          <NForm :model="params" label-placement="left" :show-feedback="false" inline>
            <NFormItem label="时间">
              <NDatePicker v-model:value="range" type="daterange" />
            </NFormItem>
          </NForm>
        </div>
      </Teleport>
      <NSpin :show="loading">
        <VueMarkdownItProvider>
          <ChatMessage
            v-for="(item, index) in list"
            :key="index"
            :msg="item"
            :session-id="sessionId"
            :index="index"
            @delete="handleDelete"
            @regenerate="handleRegenerate"
          />
        </VueMarkdownItProvider>
      </NSpin>
    </NScrollbar>
  </Suspense>
</template>

<style scoped lang="scss"></style>
