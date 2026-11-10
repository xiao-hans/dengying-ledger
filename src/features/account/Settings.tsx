import { useState } from 'react';
import type { PageProps } from '../../app/App';
import { useNav } from '../../app/navigation';
import { useRefresh } from '../../app/useRefresh';
import { useToast } from '../../app/useToast';
import { useTheme } from '../../theme/ThemeProvider';
import type { ThemeMode } from '../../theme/tokens';
import { ChipRow, ConfirmDialog, EmptyState, GlassCard, ListRow, SectionTitle, TopBar } from '../../components/ui';
import { clear } from '../../db/client';
import { STORE_NAMES } from '../../db/schema';
import { api } from '../common/api';
import { useAsync } from '../common/hooks';
import { N, S } from '../common/format';
import './account.css';

function errText(e: unknown): string {
  return e instanceof Error ? e.message : '操作失败';
}

const THEME_ITEMS: { key: ThemeMode; label: string }[] = [
  { key: 'system', label: '跟随系统' },
  { key: 'light', label: '浅色' },
  { key: 'dark', label: '深色' },
];

export function Settings(_props: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const { mode, setMode } = useTheme();
  const { data, error, reload } = useAsync(async () => {
    const [setting, config, remind] = await Promise.all([
      api.memberSetting(),
      api.serverConfig({}),
      api.updateRemind(),
    ]);
    return {
      setting: (setting ?? {}) as Record<string, string>,
      config: (config ?? {}) as Record<string, string>,
      remind: (remind ?? {}) as Record<string, unknown>,
    };
  }, [refresh.version]);
  const [askClear, setAskClear] = useState(false);

  const toggle = (key: string, current: string) => {
    const next = current === '1' ? '0' : '1';
    void api.updateSetting(key, next).then(() => refresh.bump()).catch((e) => toast.show(errText(e)));
  };

  const budgetOn = data?.setting.budget_alert === '1';
  const soundOn = data?.setting.sound !== '0';

  return (
    <div className="page">
      <TopBar title="设置" onBack={() => nav.pop()} />
      {error && <EmptyState title="设置加载失败" actionLabel="重试" onAction={reload} />}
      <GlassCard>
        <SectionTitle>外观</SectionTitle>
        <ChipRow items={THEME_ITEMS} value={mode} onChange={(v) => { if (v !== 'all') setMode(v); }} />
        <p className="account-note">跟随系统只在这个选项下生效。手动选了浅色或深色会记住。</p>
      </GlassCard>
      <ListRow title="预算提醒" subtitle={budgetOn ? '开' : '关'} onClick={() => toggle('budget_alert', budgetOn ? '1' : '0')} />
      <ListRow title="按键音" subtitle={soundOn ? '开' : '关'} onClick={() => toggle('sound', soundOn ? '1' : '0')} />
      <ListRow title="保活说明" subtitle="不会申请电池权限" onClick={() => nav.push('KeepAlive')} />
      <ListRow
        title="检查更新"
        subtitle={N(data?.remind.show) === 1 ? `有新版本 ${S(data?.remind.version)}` : `当前 ${S(data?.config.latest_version) || '1.8.1'}`}
        onClick={() => toast.show(N(data?.remind.force) === 1 ? '此版本要求更新（本地无安装包）' : '已是本地配置中的版本')}
      />
      <ListRow title="刷新登录态" subtitle="只换本地令牌" onClick={() => {
        void api.refreshToken().then(() => toast.show('已刷新')).catch((e) => toast.show(errText(e)));
      }} />
      <ListRow title="清空本地数据" subtitle="不可恢复" onClick={() => setAskClear(true)} />
      <ConfirmDialog
        open={askClear}
        title="清空本地数据"
        message="会删除本机全部账单、账本、资产和设置，并刷新页面。此操作不能撤销。"
        confirmLabel="清空"
        danger
        onCancel={() => setAskClear(false)}
        onConfirm={() => {
          setAskClear(false);
          void Promise.all(STORE_NAMES.map((name) => clear(name))).then(() => {
            toast.show('已清空');
            window.location.reload();
          }).catch((e) => toast.show(errText(e)));
        }}
      />
    </div>
  );
}

export function HelpHome(_props: PageProps) {
  const nav = useNav();
  const { data } = useAsync(() => api.personIcons(), []);
  const icons = (Array.isArray(data) ? data : []) as { id?: number; title?: string; icon?: string }[];
  return (
    <div className="page">
      <TopBar title="帮助" onBack={() => nav.pop()} />
      <GlassCard>
        <SectionTitle>怎么用</SectionTitle>
        <p className="account-note">记一笔在首页。自动记账的识别结果进待确认，通过后才入账。金额以分存储，页面按元显示。</p>
        <p className="account-note">删除、退出账本、清空数据都会再问一次。返回上一页不会丢掉还没提交的草稿。</p>
      </GlassCard>
      {icons.map((item) => (
        <ListRow key={N(item.id)} title={S(item.title) || '入口'} subtitle="本页说明，不跳外部链接" />
      ))}
      <ListRow title="用户协议" onClick={() => nav.push('Legal', { kind: 'terms' })} />
      <ListRow title="隐私说明" onClick={() => nav.push('Legal', { kind: 'privacy' })} />
    </div>
  );
}

export function Legal({ route }: PageProps) {
  const nav = useNav();
  const kind = S(route.params?.kind);
  const privacy = kind === 'privacy';
  return (
    <div className="page">
      <TopBar title={privacy ? '隐私说明' : '用户协议'} onBack={() => nav.pop()} />
      <GlassCard>
        <p className="account-note">
          {privacy
            ? '账单、账本和资产默认只存在这台浏览器的 IndexedDB 里。登录、会员和验证码都是本地模拟，不会把手机号发到真实短信网关，也不会接入原 App 的服务器。'
            : '这是喵啊记账的本地复刻，用来对齐样本 App 的功能面。协议文本为自行撰写，不使用原 App 文案。继续使用表示你知道数据留在本机。'}
        </p>
      </GlassCard>
    </div>
  );
}

export function About(_props: PageProps) {
  const nav = useNav();
  const { data } = useAsync(() => api.serverConfig({}), []);
  const cfg = (data ?? {}) as Record<string, string>;
  return (
    <div className="page">
      <TopBar title="关于" onBack={() => nav.pop()} />
      <GlassCard>
        <div className="account-hero">
          <div className="name">喵啊记账</div>
          <div className="sub">本地复刻 · 配置版本 {S(cfg.latest_version) || '1.8.1'}</div>
        </div>
        <p className="account-note">功能对齐自动记账样本，界面复用外部 UI 基准灯影的配色和玻璃底栏。广告、推送、埋点均为空实现。</p>
      </GlassCard>
      <ListRow title="最低可用版本" subtitle={S(cfg.min_version) || '1.0.0'} />
      <ListRow title="广告开关" subtitle={cfg.ad_enabled === '1' ? '开' : '关（默认）'} />
    </div>
  );
}
