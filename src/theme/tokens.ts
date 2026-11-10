/**
 * 灯影设计 token（外部 UI 基准）—— 本工程唯一来源。
 * 直接移植自 D:\workspace\dengY\DengYing\ui\src\commonMain\kotlin\com\dengying\player\ui\theme\
 *   Color.kt (LightPalette / DarkPalette)
 *   Glass.kt (LightGlass / DarkGlass)
 *   Theme.kt (ThemeMode / AppTypography / lightColors / darkColors)
 * 任何页面禁止硬编码颜色，一律用这里导出的 CSS 变量（见 tokens.css）。
 */

export interface PaletteScheme {
  brand: string;
  brandSecondary: string;
  appBg: string;
  cardBg: string;
  searchBg: string;
  textPrimary: string;
  textSecondary: string;
  divider: string;
  danger: string;
  badgeScrim: string;
}

export const LightPalette: PaletteScheme = {
  brand: '#0C0C09',          // 哑光黑 · 品牌主色
  brandSecondary: '#312C85', // 品牌副色
  appBg: '#F4F4F1',          // 页面底色 · 暖灰
  cardBg: '#FFFFFF',         // 卡片底色
  searchBg: '#EAEAE5',       // 搜索框 / 胶囊底色
  textPrimary: '#0C0C09',
  textSecondary: '#6E6E68',
  divider: '#E4E4DE',
  danger: '#DC2626',
  badgeScrim: 'rgba(0,0,0,0.70)',
};

export const DarkPalette: PaletteScheme = {
  brand: '#EDEDE8',          // 深色下品牌反转为暖白
  brandSecondary: '#8F8AD6',
  appBg: '#111114',          // 近黑带暖
  cardBg: '#1C1C20',         // 卡片比底色亮一档
  searchBg: '#2A2A2F',
  textPrimary: '#F2F2EE',
  textSecondary: '#B6B6AD',  // 玻璃卡上实测 4.4:1，压住副文本
  divider: '#2E2E33',
  danger: '#E15555',
  badgeScrim: 'rgba(0,0,0,0.70)',
};

export interface GlassScheme {
  fill: string;
  fillStrong: string;
  darkFill: string;
  liquidTint: string;
  border: string;
  shadow: string;
  chipFill: string;
  chipSelectedFill: string;
  dockHighlight: string;
  sheenAlpha: number;
}

/** v5 磨砂：白面够实 + 模糊够浅。选中态不用墨色实底，用更亮更实的玻璃面。 */
export const LightGlass: GlassScheme = {
  fill: 'rgba(255,255,255,0.60)',
  fillStrong: 'rgba(255,255,255,0.78)',
  darkFill: 'rgba(0,0,0,0.45)',
  liquidTint: 'rgba(255,255,255,0.40)',
  border: 'rgba(12,12,9,0.14)',
  shadow: 'rgba(12,12,9,0.10)',
  chipFill: 'rgba(255,255,255,0.36)',
  chipSelectedFill: 'rgba(255,255,255,0.80)',
  dockHighlight: 'rgba(255,255,255,0.85)',
  sheenAlpha: 0.30,
};

export const DarkGlass: GlassScheme = {
  fill: 'rgba(255,255,255,0.11)',
  fillStrong: 'rgba(255,255,255,0.78)',
  darkFill: 'rgba(0,0,0,0.45)',
  liquidTint: 'rgba(255,255,255,0.11)',
  border: 'rgba(255,255,255,0.24)',
  shadow: 'rgba(0,0,0,0.40)',
  chipFill: 'rgba(255,255,255,0.18)',
  chipSelectedFill: 'rgba(255,255,255,0.32)',
  dockHighlight: 'rgba(255,255,255,0.24)',
  sheenAlpha: 0.06,
};

/** 字号体系对齐 Theme.kt AppTypography，行高 1.35 倍（中文在 1.2 倍下显挤）。 */
export const TypeScale = {
  titleLarge: { size: 20, line: 27, weight: 700 },  // 品牌字（沿用灯影 titleLarge 字阶）
  titleMedium: { size: 18, line: 24, weight: 700 }, // 页面标题
  titleSmall: { size: 17, line: 23, weight: 700 },  // 详情标题
  bodyLarge: { size: 15, line: 20, weight: 400 },   // 列表条目名
  bodyMedium: { size: 14, line: 19, weight: 400 },  // 常规正文
  bodySmall: { size: 13, line: 18, weight: 400 },   // 次要说明
  labelMedium: { size: 12, line: 16, weight: 400 }, // 元信息
  labelSmall: { size: 11, line: 15, weight: 400 },  // 极小角标
} as const;

export type ThemeMode = 'system' | 'light' | 'dark';
export const THEME_KEY = 'ui_theme_mode';

export function paletteFor(mode: ThemeMode, systemDark: boolean): PaletteScheme {
  const dark = mode === 'dark' || (mode === 'system' && systemDark);
  return dark ? DarkPalette : LightPalette;
}

export function glassFor(mode: ThemeMode, systemDark: boolean): GlassScheme {
  const dark = mode === 'dark' || (mode === 'system' && systemDark);
  return dark ? DarkGlass : LightGlass;
}

/** 把一份方案写成 CSS 变量挂到根节点（对齐 CompositionLocalProvider 注入）。 */
export function applyThemeVars(el: HTMLElement, mode: ThemeMode, systemDark: boolean): void {
  const p = paletteFor(mode, systemDark);
  const g = glassFor(mode, systemDark);
  const set = (k: string, v: string | number) => el.style.setProperty(k, String(v));
  set('--brand', p.brand);
  set('--brand-secondary', p.brandSecondary);
  set('--app-bg', p.appBg);
  set('--card-bg', p.cardBg);
  set('--search-bg', p.searchBg);
  set('--text-primary', p.textPrimary);
  set('--text-secondary', p.textSecondary);
  set('--divider', p.divider);
  set('--danger', p.danger);
  set('--badge-scrim', p.badgeScrim);
  set('--glass-fill', g.fill);
  set('--glass-fill-strong', g.fillStrong);
  set('--glass-dark-fill', g.darkFill);
  set('--glass-liquid-tint', g.liquidTint);
  set('--glass-border', g.border);
  set('--glass-shadow', g.shadow);
  set('--chip-fill', g.chipFill);
  set('--chip-selected-fill', g.chipSelectedFill);
  set('--dock-highlight', g.dockHighlight);
  set('--sheen-alpha', g.sheenAlpha);
  el.dataset.theme = (mode === 'dark' || (mode === 'system' && systemDark)) ? 'dark' : 'light';
}
