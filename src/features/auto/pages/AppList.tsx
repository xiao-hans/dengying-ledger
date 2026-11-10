/** AppListActivity：监听目标 App 白名单（样本 queries 声明的 5 个 + 常见支付扩展）。 */
import { useEffect, useState } from 'react';
import type { PageProps } from '../../../app/App';
import { useNav } from '../../../app/navigation';
import { useToast } from '../../../app/useToast';
import { Icon, type IconName } from '../../../components/Icon';
import { TopBar, GlassCard, SectionTitle, EmptyState } from '../../../components/ui';
import { loadSettings, saveSetting, WATCH_APPS } from '../engine';
import { useAsync } from '../../common/hooks';
import '../auto.css';

export function AppList(_: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const { data, reload } = useAsync(() => loadSettings());
  const [apps, setApps] = useState<string[]>([]);
  useEffect(() => { if (data) setApps(data.apps); }, [data]);
  async function toggle(pkg: string, on: boolean) {
    const next = on ? [...apps, pkg] : apps.filter((p) => p !== pkg);
    setApps(next);
    await saveSetting('apps', next);
    toast.show(on ? '已加入监听' : '已移出监听');
    reload();
  }
  return (
    <div className="page">
      <TopBar title="选择监听的 App" subtitle={`已选 ${apps.length} / ${WATCH_APPS.length}`} onBack={nav.pop}
        right={<button className="btn btn-ghost" style={{ padding: '6px 10px' }} onClick={async () => { const next = apps.length === WATCH_APPS.length ? [] : WATCH_APPS.map((a) => a.pkg); setApps(next); await saveSetting('apps', next); reload(); }}>{apps.length === WATCH_APPS.length ? '清空' : '全选'}</button>} />
      <div className="page-scroll">
        <div className="page-pad">
          <GlassCard><div className="t-body-medium">只有白名单内 App 的通知 / 页面 / 截图文本会被识别。白名单为空时三路采集不产生事件，但你仍可手动粘贴文本做一次性识别。</div></GlassCard>
          <SectionTitle>支付与常用 App</SectionTitle>
          {apps.length === 0 && <div className="card"><EmptyState icon="grid" title="白名单为空" hint="点「全选」快速开启，或逐个选择" /></div>}
          <div className="card">
            {WATCH_APPS.map((a) => {
              const on = apps.includes(a.pkg);
              return (
                <button key={a.pkg} className="list-row app-row" onClick={() => toggle(a.pkg, !on)}>
                  <span className="list-row-icon"><Icon name={a.icon as IconName} size={18} /></span>
                  <div className="list-row-main"><div className="t-body-medium">{a.name}</div><div className="t-label-small t-secondary">{a.pkg}</div></div>
                  <span className={`app-check ${on ? 'on' : ''}`}>{on && <Icon name="check" size={14} />}</span>
                </button>
              );
            })}
          </div>
          <div className="t-label-medium t-secondary" style={{ padding: '14px 4px' }}>Web 版不读取设备已安装应用列表；此处是等价白名单设置，不申请 QUERY_ALL_PACKAGES 权限。</div>
        </div>
      </div>
    </div>
  );
}
