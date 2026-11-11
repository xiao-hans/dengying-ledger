/**
 * 应用外壳：Splash → 主界面（Tab 栈 + Dock + 路由注册表）。
 * 路由名对齐样本 Activity 名，见各 features/*\/routes.tsx 注册。
 */
import { useEffect, useMemo, useState, type ComponentType } from 'react';
import { NavProvider, useNav, DraftProvider, type Route } from './navigation';
import { RefreshCtx } from './useRefresh';
import { Dock } from '../components/Dock';
import { Icon } from '../components/Icon';
import { Toast } from '../components/ui';
import { useToast } from '../app/useToast';
import { routes as pageRoutes } from './routes';
import './App.css';

export interface PageProps { route: Route }

type PageComponent = ComponentType<PageProps>;

export function App() {
  const [version, setVersion] = useState(0);
  const refresh = useMemo(() => ({ version, bump: () => setVersion((v) => v + 1) }), [version]);
  return (
    <ThemeWrapper>
      <RefreshCtx.Provider value={refresh}>
        <NavProvider>
          <DraftProvider>
            <ToastProvider>
              <Shell />
            </ToastProvider>
          </DraftProvider>
        </NavProvider>
      </RefreshCtx.Provider>
    </ThemeWrapper>
  );
}

/* 主题在 main.tsx 的 ThemeProvider 内；这里只是显式包裹层级说明。 */
import { ThemeProvider } from '../theme/ThemeProvider';
function ThemeWrapper({ children }: { children: React.ReactNode }) {
  return <ThemeProvider>{children}</ThemeProvider>;
}

/* ---------------- Toast 全局 ---------------- */
import { ToastCtx } from './useToast';
function ToastProvider({ children }: { children: React.ReactNode }) {
  const [text, setText] = useState<string | null>(null);
  const show = (t: string) => setText(t);
  return (
    <ToastCtx.Provider value={{ show }}>
      {children}
      <Toast text={text} onDone={() => setText(null)} />
    </ToastCtx.Provider>
  );
}

/* ---------------- 外壳 ---------------- */
function Shell() {
  const nav = useNav();
  const toast = useToast();
  const [splash, setSplash] = useState(true);
  const [petVisible, setPetVisible] = useState(true);

  useEffect(() => {
    const t = setTimeout(() => setSplash(false), 700);
    return () => clearTimeout(t);
  }, []);

  useEffect(() => {
    // 验收期调试入口：让模拟器探针读出元素位置、触发点击、并确认输入是否真正入值。
    const w = window as unknown as { __dy?: Record<string, unknown> };
    w.__dy = {
      go: (name: string, params?: Record<string, string | number | boolean | undefined>) => nav.push(name, params),
      back: () => nav.pop(),
      tab: (name: string) => nav.switchTab(name as never),
      where: () => ({ name: nav.current.name, depth: nav.depth, tab: nav.tab }),
      boxes: () => [...document.querySelectorAll('input,button,.list-row,.topbar-title')].map((el) => {
        const r = el.getBoundingClientRect();
        return { tag: el.tagName, text: (el.textContent || '').trim().slice(0, 24), ph: (el as HTMLInputElement).placeholder || '', value: (el as HTMLInputElement).value || '', x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) };
      }),
      clickAt: (x: number, y: number) => {
        const el = document.elementFromPoint(x, y);
        (el as HTMLElement | null)?.click?.();
        return el ? `${el.tagName}.${el.className} "${(el.textContent || '').trim().slice(0, 20)}"` : 'none';
      },
    };
    const probe = (window as unknown as { Probe?: { log: (s: string) => void } }).Probe;
    if (probe) {
      const dock = document.querySelector('.dock');
      const r = dock ? dock.getBoundingClientRect() : null;
      probe.log(JSON.stringify({ inner: window.innerWidth, dock: r && { x: Math.round(r.x), w: Math.round(r.width) } }));
    }
    return () => { delete w.__dy; };
  });

  const Page: PageComponent | undefined = pageRoutes[nav.current.name];


  return (
    <div className="app-viewport" id="app-viewport">
      <div className="tab-body">
        {Page ? (
          <Page key={`${nav.tab}-${nav.depth}-${nav.current.name}`} route={nav.current} />
        ) : (
          <FallbackPage name={nav.current.name} onBack={nav.pop} />
        )}
      </div>

      <Dock tab={nav.tab} onSwitch={nav.switchTab} />

      {petVisible && nav.depth === 0 && (
        <button
          className="pet-fab glass glass-strong"
          aria-label="悬浮宠物（长按关闭）"
          title="点一下有惊喜，长按关闭"
          onClick={() => toast.show('汪！今天也要好好记账 🐾')}
          onContextMenu={(e) => { e.preventDefault(); setPetVisible(false); }}
        >
          <Icon name="pet" size={24} />
        </button>
      )}

      {splash && <Splash />}
    </div>
  );
}

function Splash() {
  return (
    <div className="splash fade-up">
      <div className="splash-logo"><Icon name="receipt" size={40} strokeWidth={1.6} /></div>
      <div className="t-title-large brand-name">喵啊记账</div>
      <div className="t-label-medium t-secondary splash-tip">本地优先 · 自动记账 · 数据在你手里</div>
    </div>
  );
}

function FallbackPage({ name, onBack }: { name: string; onBack: () => void }) {
  return (
    <div className="page">
      <div className="topbar">
        <button className="icon-btn" onClick={onBack} aria-label="返回"><Icon name="back" /></button>
        <div className="topbar-title"><div className="t-title-medium">页面建设中</div></div>
      </div>
      <div className="page-scroll">
        <div className="page-pad">
          <div className="card card-pad">
            <div className="t-body-large">「{name}」尚未注册</div>
            <div className="t-body-small t-secondary" style={{ marginTop: 8 }}>
              这是开发期占位页，正式版本不会保留。请返回上一页。
            </div>
            <button className="btn btn-primary" style={{ marginTop: 16 }} onClick={onBack}>返回</button>
          </div>
        </div>
      </div>
    </div>
  );
}
