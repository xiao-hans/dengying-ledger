/** AutoBillDataActivity：按采集方式粘贴原始文本 → 解析 → 规则 → 去重 → 待确认队列。 */
import { useState } from 'react';
import type { PageProps } from '../../../app/App';
import { useNav } from '../../../app/navigation';
import { useToast } from '../../../app/useToast';
import { TopBar, GlassCard, SectionTitle, ListRow, ChipRow, EmptyState, formatMoney } from '../../../components/ui';
import { useAsync } from '../../common/hooks';
import { api, type Dict } from '../../common/api';
import { N, S } from '../../common/format';
import { capture, pendingEvents, WATCH_APPS, type CaptureSource, type Candidate, SOURCE_LABEL } from '../engine';
import '../auto.css';

const SAMPLES: Record<CaptureSource, string> = {
  notification: '支付宝通知：付款成功 ¥38.50 商户：星巴克咖啡',
  accessibility: '微信支付\n支付成功\n向 瑞幸咖啡 付款 ¥19.90\n完成',
  ocr: '收款成功 ¥128.00 商户：楼下超市\n付款成功 ¥6.50 商户：地铁出行',
};
const HINT: Record<CaptureSource, string> = {
  notification: '把支付/到账通知文字粘贴进来，一行一笔；会自动提取金额、商户与收支方向。',
  accessibility: '把支付成功页的文本树内容粘贴进来；支持「向 X 付款 ¥N」等常见结构。',
  ocr: '把 OcrLite 三模型（DBNet + Angle + CRNN）输出的文字粘贴进来；多行会按行拆成多笔。',
};

export function AutoBillData({ route }: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const initial = (['notification', 'accessibility', 'ocr'] as CaptureSource[]).includes(route.params?.source as CaptureSource)
    ? route.params!.source as CaptureSource : 'notification';
  const [source, setSource] = useState<CaptureSource>(initial);
  const [app, setApp] = useState<string>(WATCH_APPS[0].pkg);
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const { data, reload } = useAsync(async () => {
    const [events, rules] = await Promise.all([pendingEvents(), api.ruleList()]);
    return { events, rules };
  });

  async function run() {
    if (!text.trim()) return toast.show('请先粘贴要识别的文本');
    setBusy(true);
    try {
      const settings = await (await import('../engine')).loadSettings();
      if (!settings.master) return toast.show('自动记账总开关未开启，请先在设置里开启');
      const key = source === 'notification' ? 'notification' : source === 'accessibility' ? 'accessibility' : 'ocr';
      if (!settings[key]) return toast.show(`${SOURCE_LABEL[source]}未开启，请先在设置里开启`);
      if (!settings.apps.includes(app)) return toast.show('该 App 不在监听白名单里');
      const r = await capture(text, source, Date.now(), WATCH_APPS.find((a) => a.pkg === app)?.name || SOURCE_LABEL[source]);
      toast.show(`识别完成：新增 ${r.created} 笔${r.duplicate ? ` · 折叠重复 ${r.duplicate} 笔` : ''}${r.unsupported ? ` · 未识别 ${r.unsupported} 行` : ''}`);
      setText(''); reload();
    } catch (e) { toast.show(`识别失败：${e instanceof Error ? e.message : String(e)}`); }
    finally { setBusy(false); }
  }

  return (
    <div className="page">
      <TopBar title="识别数据" subtitle="原始事件 → 待确认队列" onBack={nav.pop}
        right={<button className="btn btn-ghost" style={{ padding: '6px 10px' }} onClick={() => nav.push('BillIdentified')}>待确认</button>} />
      <div className="page-scroll">
        <div className="page-pad">
          <ChipRow value={source} onChange={(v) => setSource(v as CaptureSource)}
            items={[{ key: 'notification', label: '通知' }, { key: 'accessibility', label: '无障碍' }, { key: 'ocr', label: '截屏 OCR' }]} />
          <GlassCard>
            <div className="t-label-medium t-secondary">{HINT[source]}</div>
            <select className="field" style={{ marginTop: 10 }} value={app} onChange={(e) => setApp(e.target.value)} aria-label="来源 App">
              {WATCH_APPS.map((a) => <option key={a.pkg} value={a.pkg}>{a.name}</option>)}
            </select>
            <textarea className="field raw-input" value={text} onChange={(e) => setText(e.target.value)} placeholder="在此粘贴通知 / 页面 / OCR 文本…" rows={5} />
            <div className="row" style={{ marginTop: 10, gap: 8 }}>
              <button className="btn btn-ghost" onClick={() => setText(SAMPLES[source])}>填入示例</button>
              <button className="btn btn-primary grow" disabled={busy} onClick={run}>{busy ? '识别中…' : '识别并入队'}</button>
            </div>
          </GlassCard>
          <SectionTitle action={<button onClick={() => nav.push('RuleList')}>规则设置 ›</button>}>最近识别事件</SectionTitle>
          {(data?.events || []).length === 0 ? (
            <div className="card"><EmptyState icon="scan" title="还没有识别事件" hint="粘贴一段支付文本，识别结果会出现在「待确认队列」" /></div>
          ) : (data?.events || []).slice(0, 12).map((ev: Candidate) => (
            <div className="card" key={S(ev.id)}>
              <ListRow icon={ev.status === 0 ? 'clock' : ev.status === 1 ? 'check' : 'close'}
                title={`${S(ev.merchant)} · ¥${formatMoney(N(ev.money))}`}
                subtitle={`${SOURCE_LABEL[ev.source as CaptureSource] || S(ev.source)} · ${S(ev.sourceApp)} · 规则 ${N(ev.matchedRule) || '未命中'}`}
                right={ev.status === 0 ? '待确认' : ev.status === 1 ? '已入账' : '已忽略'} chevron={false} />
            </div>
          ))}
          <div className="t-label-medium t-secondary" style={{ padding: '14px 4px', lineHeight: 1.8 }}>
            去重键 = 来源 App + 金额 + 分钟级时间 + 商户；同一分钟内同一笔支付只入队一次。识别结果不会自动写入账单。
          </div>
        </div>
      </div>
    </div>
  );
}
