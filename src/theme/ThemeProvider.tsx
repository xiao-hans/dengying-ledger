import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { applyThemeVars, THEME_KEY, type ThemeMode } from './tokens';

interface ThemeCtx {
  mode: ThemeMode;
  setMode: (m: ThemeMode) => void;
  isDark: boolean;
}

const Ctx = createContext<ThemeCtx>({ mode: 'system', setMode: () => {}, isDark: false });

function systemPrefersDark(): boolean {
  return typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: dark)').matches === true;
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [mode, setModeState] = useState<ThemeMode>(() => {
    const saved = localStorage.getItem(THEME_KEY);
    return saved === 'light' || saved === 'dark' || saved === 'system' ? saved : 'system';
  });
  const [systemDark, setSystemDark] = useState(systemPrefersDark);

  useEffect(() => {
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const onChange = () => setSystemDark(mq.matches);
    mq.addEventListener?.('change', onChange);
    return () => mq.removeEventListener?.('change', onChange);
  }, []);

  const isDark = mode === 'dark' || (mode === 'system' && systemDark);

  useEffect(() => {
    applyThemeVars(document.documentElement, mode, systemDark);
    const meta = document.querySelector('meta[name="theme-color"]');
    meta?.setAttribute('content', isDark ? '#111114' : '#F4F4F1');
  }, [mode, systemDark, isDark]);

  const setMode = (m: ThemeMode) => {
    localStorage.setItem(THEME_KEY, m);
    setModeState(m);
  };

  const value = useMemo(() => ({ mode, setMode, isDark }), [mode, isDark]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export const useTheme = () => useContext(Ctx);
