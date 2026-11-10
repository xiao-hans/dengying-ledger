/**
 * 应用外壳：Splash → 主界面（Tab 栈 + Dock + 路由注册表）。
 * 路由名对齐样本 Activity 名，见各 features/*\/routes.tsx 注册。
 */
import { useEffect, useState, type ComponentType } from 'react';
import { NavProvider, useNav, DraftProvider, type Route } from './navigation';
import { Dock } from '../components/Dock';
import { Icon } from '../components/Icon';
import { Toast } from '../components/ui';
import { useToast } from '../app/useToast';
import { routes as pageRoutes } from './routes';
import './App.css';

export interface PageProps { route: Route }

type PageComponent = ComponentType<PageProps>;

export function App() {
  return (
    <ThemeWrapper>
      <NavProvider>
        <DraftProvider>
          <ToastProvider>
            <Shell />
          </ToastProvider>
        </DraftProvider>
      </NavProvider>
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

  const Page: PageComponent | undefined = pageRoutes[nav.current.name];

  const onQuickAdd = () => nav.push('BillEdit', { mode: 'create' });

  return (
    <div className="app-viewport" id="app-viewport">
      <div className="tab-body">
        {Page ? (
          <Page key={`${nav.tab}-${nav.depth}-${nav.current.name}`} route={nav.current} />
        ) : (
          <FallbackPage name={nav.current.name} onBack={nav.pop} />
        )}
      </div>

      <Dock tab={nav.tab} onSwitch={nav.switchTab} onQuickAdd={onQuickAdd} />

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
      <div className="t-title-large brand-name">灯影记账</div>
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
