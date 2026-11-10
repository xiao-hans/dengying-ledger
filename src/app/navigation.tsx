/**
 * 导航：模拟原生 Activity 栈 + 底部 Dock 常驻。
 * - 每个 Tab 有自己的栈；切换 Tab 不销毁其它栈（保留筛选/滚动位置的等价语义）。
 * - 二级页 push 到当前栈，pop 返回；栈空时回到 Dock 首页。
 * - 表单草稿由 DraftProvider 单独持久化，返回/切 Tab 不丢（交互纪律 §6.2）。
 */
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';

export type TabKey = 'bills' | 'stats' | 'auto' | 'mine';

export interface Route {
  name: string;
  params?: Record<string, string | number | boolean | undefined>;
}

interface NavValue {
  tab: TabKey;
  stack: Route[];
  current: Route;
  depth: number;
  push: (name: string, params?: Route['params']) => void;
  pop: () => void;
  popToRoot: () => void;
  switchTab: (t: TabKey) => void;
  replace: (name: string, params?: Route['params']) => void;
}

const TAB_ROOT: Record<TabKey, string> = {
  bills: 'BillsHome', stats: 'StatsHome', auto: 'AutoHome', mine: 'MineHome',
};

const NavCtx = createContext<NavValue | null>(null);

export function NavProvider({ children }: { children: ReactNode }) {
  const [tab, setTab] = useState<TabKey>('bills');
  const [stacks, setStacks] = useState<Record<TabKey, Route[]>>(() => ({
    bills: [{ name: TAB_ROOT.bills }],
    stats: [{ name: TAB_ROOT.stats }],
    auto: [{ name: TAB_ROOT.auto }],
    mine: [{ name: TAB_ROOT.mine }],
  }));

  const stack = stacks[tab];
  const current = stack[stack.length - 1];

  const push = useCallback((name: string, params?: Route['params']) => {
    setStacks((s) => ({ ...s, [tab]: [...s[tab], { name, params }] }));
  }, [tab]);

  const pop = useCallback(() => {
    setStacks((s) => (s[tab].length > 1 ? { ...s, [tab]: s[tab].slice(0, -1) } : s));
  }, [tab]);

  const popToRoot = useCallback(() => {
    setStacks((s) => ({ ...s, [tab]: [{ name: TAB_ROOT[tab] }] }));
  }, [tab]);

  const switchTab = useCallback((t: TabKey) => {
    setTab(t);
    setStacks((s) => ({ ...s, [t]: s[t].length ? s[t] : [{ name: TAB_ROOT[t] }] }));
  }, []);

  const replace = useCallback((name: string, params?: Route['params']) => {
    setStacks((s) => ({ ...s, [tab]: [...s[tab].slice(0, -1), { name, params }] }));
  }, [tab]);

  const value = useMemo<NavValue>(() => ({
    tab, stack, current, depth: stack.length - 1, push, pop, popToRoot, switchTab, replace,
  }), [tab, stack, current, push, pop, popToRoot, switchTab, replace]);

  return <NavCtx.Provider value={value}>{children}</NavCtx.Provider>;
}

export function useNav(): NavValue {
  const v = useContext(NavCtx);
  if (!v) throw new Error('useNav 必须在 NavProvider 内使用');
  return v;
}

/* ---------------- 表单草稿（切页不丢） ---------------- */
type DraftMap = Record<string, Record<string, unknown>>;
const DRAFT_KEY = 'dengying_drafts_v1';
const DraftCtx = createContext<{
  get: <T extends Record<string, unknown>>(key: string, fallback: T) => T;
  set: (key: string, patch: Record<string, unknown>) => void;
  clear: (key: string) => void;
} | null>(null);

function loadDrafts(): DraftMap {
  try { return JSON.parse(localStorage.getItem(DRAFT_KEY) || '{}') as DraftMap; } catch { return {}; }
}

export function DraftProvider({ children }: { children: ReactNode }) {
  const [drafts, setDrafts] = useState<DraftMap>(loadDrafts);
  const persist = (next: DraftMap) => {
    setDrafts(next);
    try { localStorage.setItem(DRAFT_KEY, JSON.stringify(next)); } catch { /* 隐私模式下静默降级为内存态 */ }
  };
  const value = useMemo(() => ({
    get: <T extends Record<string, unknown>>(key: string, fallback: T): T =>
      ({ ...fallback, ...(drafts[key] || {}) }) as T,
    set: (key: string, patch: Record<string, unknown>) =>
      persist({ ...drafts, [key]: { ...(drafts[key] || {}), ...patch } }),
    clear: (key: string) => {
      const next = { ...drafts }; delete next[key]; persist(next);
    },
  }), [drafts]);
  return <DraftCtx.Provider value={value}>{children}</DraftCtx.Provider>;
}

export function useDraft() {
  const v = useContext(DraftCtx);
  if (!v) throw new Error('useDraft 必须在 DraftProvider 内使用');
  return v;
}
