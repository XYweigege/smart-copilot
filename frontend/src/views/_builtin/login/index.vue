<script setup lang="ts">
import { computed } from 'vue';
import type { Component } from 'vue';
import { mixColor } from '@sa/color';
import { loginModuleRecord } from '@/constants/app';
import { useAppStore } from '@/store/modules/app';
import { useThemeStore } from '@/store/modules/theme';
import { $t } from '@/locales';
import SvgIcon from '@/components/custom/svg-icon.vue';
import PwdLogin from './modules/pwd-login.vue';
import CodeLogin from './modules/code-login.vue';
import Register from './modules/register.vue';
import ResetPwd from './modules/reset-pwd.vue';
import BindWechat from './modules/bind-wechat.vue';

interface Props {
  /** The login module */
  module?: UnionKey.LoginModule;
}

const props = defineProps<Props>();

const appStore = useAppStore();
const themeStore = useThemeStore();

interface LoginModule {
  label: string;
  component: Component;
}

const moduleMap: Record<UnionKey.LoginModule, LoginModule> = {
  'pwd-login': { label: loginModuleRecord['pwd-login'], component: PwdLogin },
  'code-login': { label: loginModuleRecord['code-login'], component: CodeLogin },
  register: { label: loginModuleRecord.register, component: Register },
  'reset-pwd': { label: loginModuleRecord['reset-pwd'], component: ResetPwd },
  'bind-wechat': { label: loginModuleRecord['bind-wechat'], component: BindWechat }
};

const activeModule = computed(() => moduleMap[props.module || 'pwd-login']);

const bgColor = computed(() => {
  const ratio = themeStore.darkMode ? 0.9 : 0;

  return mixColor('#fff', '#000', ratio);
});
</script>

<template>
  <div class="relative size-full flex" :style="{ backgroundColor: bgColor }">
    <!-- 左侧品牌区（大屏显示） -->
    <div
      class="hidden md:flex flex-1 flex-col justify-between p-12 login-aside"
    >
      <div class="flex-y-center gap-3">
        <SvgIcon icon="material-symbols:smart-toy-outline" class="text-40px text-white" />
        <span class="text-22px font-600 text-white">{{ $t('system.title') }}</span>
      </div>

      <div class="max-w-420px">
        <h1 class="text-34px font-700 leading-tight text-white">
          企业智能知识库
        </h1>
        <p class="mt-4 text-16px leading-relaxed text-white/80">
          基于文档的智能问答与知识管理，让团队沉淀与检索更高效、更安全。
        </p>
        <ul class="mt-8 flex-col gap-3 text-15px text-white/85">
          <li class="flex-y-center gap-2">
            <icon-material-symbols:check-circle-rounded class="text-20px" />
            知识库检索增强，回答可溯源到原文
          </li>
          <li class="flex-y-center gap-2">
            <icon-material-symbols:check-circle-rounded class="text-20px" />
            多格式文档管理，企业级权限隔离
          </li>
          <li class="flex-y-center gap-2">
            <icon-material-symbols:check-circle-rounded class="text-20px" />
            流式对话，会话历史随时回溯
          </li>
        </ul>
      </div>

      <p class="text-13px text-white/60">© 2026 PaiSmart · 派聪明</p>
    </div>

    <!-- 右侧表单区 -->
    <div class="flex-1 flex-center lt-md:flex-full">
      <NCard :bordered="false" class="relative z-4 w-400px lt-sm:w-320px login-card">
        <div class="mb-24px flex-y-center justify-between">
          <div class="flex-y-center gap-3">
            <SvgIcon icon="material-symbols:smart-toy-outline" class="text-32px text-primary" />
            <h3 class="text-22px font-600 text-primary">{{ $t('system.title') }}</h3>
          </div>
          <div class="i-flex-col">
            <ThemeSchemaSwitch
              :theme-schema="themeStore.themeScheme"
              :show-tooltip="false"
              class="text-20px lt-sm:text-18px"
              @switch="themeStore.toggleThemeScheme"
            />
            <LangSwitch
              v-if="themeStore.header.multilingual.visible"
              :lang="appStore.locale"
              :lang-options="appStore.localeOptions"
              :show-tooltip="false"
              @change-lang="appStore.changeLocale"
            />
          </div>
        </div>
        <main>
          <h3 class="text-18px font-medium">{{ $t(activeModule.label) }}</h3>
          <div class="pt-24px">
            <Transition :name="themeStore.page.animateMode" mode="out-in" appear>
              <component :is="activeModule.component" />
            </Transition>
          </div>
        </main>
      </NCard>
    </div>
  </div>
</template>

<style scoped>
.login-aside {
  background:
    radial-gradient(120% 120% at 0% 0%, #2563eb 0%, #1e40af 55%, #1e3a8a 100%);
}

.login-card {
  border-radius: 8px;
  box-shadow: 0 8px 30px rgba(15, 23, 42, 0.12);
}

:deep(.n-card__content) {
  padding: 28px 32px;
}
</style>
