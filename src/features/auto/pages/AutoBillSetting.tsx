/** AutoBillSettingActivity：总开关、三路采集、监听白名单、确认策略、回传统计。 */
import { useEffect, useState } from 'react';
import type { PageProps } from '../../../app/App';
import { useNav } from '../../../app/navigation';
import { useToast } from '../../../app/useToast';
import { TopBar, GlassCard, SectionTitle, ListRow, Switch, Badge } from '../../../components/ui';
import { api } from '../../common/api';
import { useAsync } from '../../common/hooks';
import { N, S } from '../../common/format';
import { loadSettings, saveSetting, WATCH_APPS, type AutoSettings } from '../engine';
import '../auto.css';

export function AutoBillSetting(_: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const { data, reload } = useAsync(async () => {
    const [settings, events, stat] = await Promise.all([loadSettings(), api.ruleList(), api.autoBillsCallback(0)]);
    return { settings, ruleCount: events.length, stat };
  });
  const [s, setS] = useState<AutoSettings | null>(null);
  useEffect(() => { if (data?.settings) setS(data.settings); }, [data?.settings]);

  async function toggle<K extends keyof AutoSettings>(key: K, value: AutoSettings[K]) {
    if (!s) return;
    const next: AutoSettings = { ...s, [key]: value };
    // 关闭总开关时同步关闭三路，避免「总开关关了但后台仍在采集」的反人类状态
    if (key === 'master' && !value) { next.notification = false; next.accessibility = false; next.ocr = false; }
    // 开启任一采集自动带开总开关
    if (value && (key === 'notification' || key === 'accessibility' || key === 'ocr')) next.master = true;
    setS(next);
    await Promise.all((['master', 'notification', 'accessibility', 'ocr'] as const)
      .filter((k) => next[k] !== s[k]).map((k) => saveSetting(k, next[k])));
    toast.show('设置已保存');
    reload();
  }

  return (
    <div className="page">
      <TopBar title="自动记账设置" onBack={nav.pop} />
      <div className="page-scroll">
        <div className="page-pad">
          <GlassCard strong>
            <div className="row-between">
              <div><div className="t-body-large t-semi">自动记账总开关</div><div className="t-label-medium t-secondary" style={{ marginTop: 4 }}>{s?.master ? '已开启 · 识别结果进入待确认队列' : '已关闭 · 不产生任何识别事件'}</div></div>
              <Switch checked={!!s?.master} onChange={(v) => toggle('master', v)} />
            </div>
          </GlassCard>

          <SectionTitle>三路采集</SectionTitle>
          <div className="card">
            <ListRow icon="bell" title="通知识别" subtitle="支付通知文本 → 金额与商户" right={<Switch checked={!!s?.notification} onChange={(v) => toggle('notification', v)} />} chevron={false} />
            <ListRow icon="shield" title="无障碍识别" subtitle="支付成功页文本树" right={<Switch checked={!!s?.accessibility} onChange={(v) => toggle('accessibility', v)} />} chevron={false} />
            <ListRow icon="camera" title="截屏 OCR" subtitle="截图文字 → 交易信息" right={<Switch checked={!!s?.ocr} onChange={(v) => toggle('ocr', v)} />} chevron={false} />
          </div>

          <SectionTitle>监听与规则</SectionTitle>
          <div className="card">
            <ListRow icon="grid" title="监听 App 白名单" subtitle={s ? `已选 ${s.apps.length} / ${WATCH_APPS.length} 个` : '读取中…'} right={<Badge tone="brand">{s ? String(s.apps.length) : '—'}</Badge>} onClick={() => nav.push('AppList')} />
            <ListRow icon="note" title="匹配规则" subtitle="关键词 → 分类 / 账户 / 标签" right={`${data?.ruleCount ?? 0} 条`} onClick={() => nav.push('RuleList')} />
            <ListRow icon="refresh" title="保活设置" subtitle={s?.keepAlive ? '已开启定时自检' : '未开启'} onClick={() => nav.push('KeepAlive')} />
          </div>

          <SectionTitle>识别结果</SectionTitle>
          <div className="card">
            <ListRow icon="check" title="待确认队列" subtitle="不会静默入账" onClick={() => nav.push('BillIdentified')} />
            <ListRow icon="file" title="识别数据" subtitle="原始事件与解析明细" onClick={() => nav.push('AutoBillData')} />
            <ListRow icon="chart" title="识别统计回传" subtitle={`Bills/AutoBillsCallBack · 累计识别 ${N(data?.stat?.identified)} 笔 / 入账 ${N(data?.stat?.booked)} 笔`} chevron={false} />
          </div>
          <div className="t-label-medium t-secondary" style={{ padding: '14px 4px', lineHeight: 1.8 }}>
            关闭总开关会同时关闭三路采集；开启任意一路会自动带开总开关。所有设置保存在本机，不上传。{S(data?.stat?.callbackTime) && ` 最近回传：${new Date(N(data?.stat?.callbackTime) * 1000).toLocaleString()}`}
          </div>
        </div>
      </div>
    </div>
  );
}
