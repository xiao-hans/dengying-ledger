/** KeepAliveActivity：Web 等价的保活设置——页面可见状态、定时自检、后台恢复提示。 */
import { useEffect, useState } from 'react';
import type { PageProps } from '../../../app/App';
import { useNav } from '../../../app/navigation';
import { useToast } from '../../../app/useToast';
import { TopBar, GlassCard, SectionTitle, ListRow, Switch, Badge } from '../../../components/ui';
import { useAsync } from '../../common/hooks';
import { api } from '../../common/api';
import { loadSettings, saveSetting } from '../engine';
import { N } from '../../common/format';
import '../auto.css';

export function KeepAlive(_: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const { data, reload } = useAsync(async () => {
    const [settings, events] = await Promise.all([loadSettings(), api.autoBillsCallback(0)]);
    return { settings, stat: events };
  });
  const [visible, setVisible] = useState(!document.hidden);
  const [lastCheck, setLastCheck] = useState(Date.now());
  useEffect(() => {
    const onVis = () => { setVisible(!document.hidden); setLastCheck(Date.now()); };
    document.addEventListener('visibilitychange', onVis);
    const timer = window.setInterval(() => setLastCheck(Date.now()), 30_000);  // 30s 自检，等价 KeepAliveService 心跳
    return () => { document.removeEventListener('visibilitychange', onVis); window.clearInterval(timer); };
  }, []);
  async function toggle(v: boolean) {
    await saveSetting('keepAlive', v);
    toast.show(v ? '已开启定时自检' : '已关闭定时自检');
    reload();
  }
  return (
    <div className="page">
      <TopBar title="保活设置" onBack={nav.pop} />
      <div className="page-scroll">
        <div className="page-pad">
          <GlassCard strong>
            <div className="row-between">
              <div><div className="t-body-large t-semi">后台持续运行</div><div className="t-label-medium t-secondary" style={{ marginTop: 4 }}>页面可见性 + 30 秒定时自检</div></div>
              <Switch checked={!!data?.settings.keepAlive} onChange={toggle} />
            </div>
          </GlassCard>
          <SectionTitle>运行状态</SectionTitle>
          <div className="card">
            <ListRow icon="eye" title="当前页面" subtitle={visible ? '可见 · 识别服务在线' : '已切到后台 · 恢复后继续'} right={<Badge tone={visible ? 'brand' : 'neutral'}>{visible ? '在线' : '后台'}</Badge>} chevron={false} />
            <ListRow icon="clock" title="最近自检" subtitle={`${new Date(lastCheck).toLocaleTimeString()} · 每 30 秒一次`} chevron={false} />
            <ListRow icon="chart" title="累计识别 / 入账" subtitle={`${N(data?.stat?.identified)} 笔 / ${N(data?.stat?.booked)} 笔`} chevron={false} />
          </div>
          <SectionTitle>建议</SectionTitle>
          <div className="card card-pad">
            <div className="t-body-medium t-secondary" style={{ lineHeight: 1.9 }}>
              · Web 浏览器可能冻结后台标签页；把本页保持在前台可获得最稳定的识别体验。<br />
              · 不需要关闭省电策略、不需要常驻通知，也不会申请 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS。<br />
              · 长时间离开后返回，待确认队列仍完整保留，不会丢事件。
            </div>
          </div>
          <div className="card" style={{ marginTop: 14 }}>
            <ListRow icon="check" title="查看待确认队列" subtitle="回来后先看这里" onClick={() => nav.push('BillIdentified')} />
          </div>
        </div>
      </div>
    </div>
  );
}
