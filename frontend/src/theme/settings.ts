/** Default theme settings */
export const themeSettings: App.Theme.ThemeSetting = {
  // 跟随系统主题（auto），明暗自动适配
  themeScheme: 'auto',
  grayscale: false,
  colourWeakness: false,
  recommendColor: true,
  // 沉稳的企业蓝，替代原本偏“科技/AI 炫光”的蓝紫色，更克制专业
  themeColor: '#2563eb',
  otherColor: { info: '#2563eb', success: '#16a34a', warning: '#d97706', error: '#dc2626' },
  isInfoFollowPrimary: true,
  resetCacheStrategy: 'close',
  layout: { mode: 'vertical', scrollMode: 'content', reverseHorizontalMix: false },
  // 关闭页面切换动画，去掉花哨的过渡效果，更加严肃简洁
  page: { animate: false, animateMode: 'fade-slide' },
  header: { height: 56, breadcrumb: { visible: false, showIcon: true }, multilingual: { visible: false } },
  tab: { visible: false, cache: true, height: 44, mode: 'chrome' },
  fixedHeaderAndTab: true,
  sider: {
    inverted: false,
    width: 200,
    collapsedWidth: 64,
    mixWidth: 90,
    mixCollapsedWidth: 64,
    mixChildMenuWidth: 200
  },
  footer: { visible: false, fixed: false, height: 48, right: true },
  watermark: { visible: false, text: '派聪明 PaiSmart' },
  tokens: {
    light: {
      colors: {
        // 容器与背景采用柔和中性灰白，弱化冷色调
        container: 'rgb(255, 255, 255)',
        layout: 'rgb(244, 246, 248)',
        // 侧边栏使用中性深灰，去掉原本偏蓝的深色（rgb(0, 20, 40)）
        inverted: 'rgb(33, 37, 41)',
        'base-text': 'rgb(28, 30, 33)'
      },
      boxShadow: {
        header: '0 1px 2px rgb(15, 23, 42, 0.06)',
        sider: '1px 0 0 rgb(15, 23, 42, 0.06)',
        tab: '0 1px 2px rgb(15, 23, 42, 0.06)'
      }
    },
    dark: {
      colors: {
        container: 'rgb(30, 32, 36)',
        layout: 'rgb(22, 24, 27)',
        inverted: 'rgb(20, 22, 25)',
        'base-text': 'rgb(226, 228, 230)'
      }
    }
  }
};

/**
 * Override theme settings
 *
 * If publish new version, use `overrideThemeSettings` to override certain theme settings
 */
export const overrideThemeSettings: Partial<App.Theme.ThemeSetting> = {};
