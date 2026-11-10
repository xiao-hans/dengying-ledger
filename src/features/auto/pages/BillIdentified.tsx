/** BillIdentifiedActivity：识别结果确认队列。逐条修改 / 批量通过 / 忽略 / 删除，绝不静默入账。 */
import { useMemo, useState } from 'react';
import type { PageProps } from '../../../app/App';
import { useNav } from '../../../app/navigation';
import { useToast } from '../../../app/useToast';
import { useRefresh } from '../../../app/useRefresh';
import { TopBar, GlassCard, ChipRow, ListRow, EmptyState, BottomSheet, ConfirmDialog, Badge, formatMoney } from '../../../components/ui';
import { useAsync } from '../../common/hooks';
import { api } from '../../common/api';
import { N, S } from '../../common/format';
import { confirmEvent, ignoreEvent, deleteEvent, pendingEvents, SOURCE_LABEL, type Candidate, type CaptureSource } from '../engine';
import '../auto.css';

export function BillIdentified(_: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const { bump } = useRefresh();
  const { data, reload } = useAsync(async () => {
    const [events, cates] = await Promise.all([pendingEvents(), api.mainCates()]);
    return { events, cates: [...(cates.expense || []), ...(cates.income || [])] };
  });
  const [filter, setFilter] = useState<0 | 1 | 2>(0);
  const [selected, setSelected] = useState<string[]>([]);
  const [editing, setEditing] = useState<Candidate | null>(null);
  const [money, setMoney] = useState('');
  const [desc, setDesc] = useState('');
  const [cate, setCate] = useState(9);
  const [confirm, setConfirm] = useState<{ kind: 'ignore' | 'delete' | 'batch'; ev?: Candidate } | null>(null);
  const [busy, setBusy] = useState(false);
  const events = (data?.events || []).filter((e) => e.status === filter);
  const pending = (data?.events || []).filter((e) => e.status === 0);
  const cmap = useMemo(() => new Map((data?.cates || []).map((c) => [N(c.cate_id ?? c.id), S(c.title)])), [data?.cates]);

  function openEdit(ev: Candidate) {
    setEditing(ev); setMoney((N(ev.money) / 100).toFixed(2)); setDesc(ev.desc || ev.merchant); setCate(ev.cateId);
  }
  async function submitEdit() {
    if (!editing) return;
    const cents = Math.round(Number(money) * 100);
    if (!Number.isFinite(cents) || Math.abs(cents) < 1) return toast.show('请输入有效金额');
    setBusy(true);
    try {
      await confirmEvent(editing, { money: Math.abs(cents), desc, cateId: cate });
      toast.show('已确认并入账'); setEditing(null); bump(); reload();
    } catch (e) { toast.show(`入账失败：${e instanceof Error ? e.message : String(e)}`); }
    finally { setBusy(false); }
  }
  async function batchConfirm() {
    const rows = pending.filter((e) => selected.includes(e.id));
    if (!rows.length) return toast.show('请先选择要通过的识别结果');
    setBusy(true);
    try {
      for (const ev of rows) await confirmEvent(ev);
      toast.show(`已入账 ${rows.length} 笔`); setSelected([]); setConfirm(null); bump(); reload();
    } catch (e) { toast.show(`批量入账失败：${e instanceof Error ? e.message : String(e)}`); }
    finally { setBusy(false); }
  }
  async function act(kind: 'ignore' | 'delete', ev: Candidate) {
    if (kind === 'ignore') await ignoreEvent(ev); else await deleteEvent(ev);
    setConfirm(null); toast.show(kind === 'ignore' ? '已忽略' : '已删除'); reload();
  }

  return (
    <div className="page">
      <TopBar title="识别结果确认" subtitle={`${pending.length} 笔待确认`} onBack={nav.pop}
        right={<button className="btn btn-ghost" style={{ padding: '6px 10px' }} onClick={() => nav.push('RuleList')}>规则</button>} />
      <div className="page-scroll">
        <div className="page-pad">
          <ChipRow<'all' | 1 | 2> value={filter === 0 ? 'all' : filter}
            onChange={(v) => { setFilter(v === 'all' ? 0 : v as 1 | 2); setSelected([]); }}
            items={[{ key: 1, label: '已入账' }, { key: 2, label: '已忽略' }]} allLabel="待确认" />
          {filter === 0 && pending.length > 0 && (
            <GlassCard>
              <div className="row-between">
                <div className="t-body-medium">已选 {selected.length} / {pending.length} 笔</div>
                <div className="row" style={{ gap: 8 }}>
                  <button className="btn btn-ghost" style={{ padding: '6px 12px' }} onClick={() => setSelected(selected.length === pending.length ? [] : pending.map((e) => e.id))}>{selected.length === pending.length ? '取消全选' : '全选'}</button>
                  <button className="btn btn-primary" style={{ padding: '6px 12px' }} disabled={busy} onClick={() => setConfirm({ kind: 'batch' })}>批量通过</button>
                </div>
              </div>
            </GlassCard>
          )}
          {events.length === 0 ? (
            <div className="card"><EmptyState icon={filter === 0 ? 'check' : 'file'} title={filter === 0 ? '没有待确认的识别结果' : '这里还没有记录'}
              hint={filter === 0 ? '去「识别数据」粘贴一段支付文本' : '确认或忽略后会出现在这里'}
              actionLabel={filter === 0 ? '去识别' : undefined} onAction={() => nav.push('AutoBillData')} /></div>
          ) : events.map((ev) => {
            const on = selected.includes(ev.id);
            return (
              <div className="card identified-card" key={ev.id}>
                <div className="row-between" style={{ padding: '12px 14px 4px' }}>
                  <div className="row" style={{ gap: 8 }}>
                    {filter === 0 && <button className={`app-check ${on ? 'on' : ''}`} aria-label="选择" onClick={() => setSelected(on ? selected.filter((x) => x !== ev.id) : [...selected, ev.id])}>{on && '✓'}</button>}
                    <Badge tone={ev.status === 1 ? 'brand' : ev.status === 2 ? 'neutral' : 'danger'}>{ev.status === 1 ? '已入账' : ev.status === 2 ? '已忽略' : '待确认'}</Badge>
                    <span className="t-label-medium t-secondary">{SOURCE_LABEL[ev.source as CaptureSource] || S(ev.source)} · {S(ev.sourceApp)}</span>
                  </div>
                  <span className="t-label-small t-secondary">¥{formatMoney(N(ev.money))}</span>
                </div>
                <ListRow icon={N(ev.type) === 2 ? 'coin' : 'receipt'}
                  title={ev.desc || ev.merchant}
                  subtitle={`${cmap.get(N(ev.cateId)) || '未分类'} · ${N(ev.matchedRule) ? `命中规则 #${N(ev.matchedRule)}` : '未命中规则'} · 去重 ${S(ev.hash).slice(0, 8)}`}
                  chevron={false} />
                <div className="raw-preview t-label-small t-secondary">{ev.rawText}</div>
                {filter === 0 && <div className="row" style={{ gap: 8, padding: '0 14px 12px' }}>
                  <button className="btn btn-ghost grow" onClick={() => openEdit(ev)}>修改并确认</button>
                  <button className="btn btn-ghost" onClick={() => setConfirm({ kind: 'ignore', ev })}>忽略</button>
                  <button className="btn btn-primary" onClick={async () => { setBusy(true); try { await confirmEvent(ev); toast.show('已入账'); bump(); reload(); } catch (e) { toast.show(String(e)); } finally { setBusy(false); } }} disabled={busy}>直接入账</button>
                </div>}
                {filter === 2 && <div className="row" style={{ padding: '0 14px 12px' }}><button className="btn btn-ghost" onClick={() => setConfirm({ kind: 'delete', ev })}>删除事件</button></div>}
              </div>
            );
          })}
          <div className="t-label-medium t-secondary" style={{ padding: '14px 4px', lineHeight: 1.8 }}>
            所有识别结果都需你确认才会写进账本；忽略只保留原始事件不产生账单，已忽略事件不会被再次去重拦截。
          </div>
        </div>
      </div>

      <BottomSheet open={!!editing} title="修改并确认" onClose={() => setEditing(null)}>
        <div className="t-label-medium t-secondary">金额（元）</div>
        <input className="field" style={{ marginTop: 8 }} inputMode="decimal" value={money} onChange={(e) => setMoney(e.target.value)} />
        <div className="t-label-medium t-secondary" style={{ margin: '14px 0 8px' }}>备注 / 商户</div>
        <input className="field" value={desc} onChange={(e) => setDesc(e.target.value)} />
        <div className="t-label-medium t-secondary" style={{ margin: '14px 0 8px' }}>分类</div>
        <ChipRow<number> value={cate} onChange={(v) => setCate(v === 'all' ? 9 : v as number)}
          items={(data?.cates || []).map((c) => ({ key: N(c.cate_id ?? c.id), label: S(c.title) }))} />
        <div className="row" style={{ gap: 10, marginTop: 18 }}>
          <button className="btn btn-ghost" onClick={() => setEditing(null)}>取消</button>
          <button className="btn btn-primary grow" disabled={busy} onClick={submitEdit}>确认入账</button>
        </div>
      </BottomSheet>
      <ConfirmDialog open={confirm?.kind === 'batch'} title={`批量通过 ${selected.length} 笔识别结果？`}
        message="将按当前分类、账户与金额写入账本；写入后可在账单里继续编辑。"
        confirmLabel="确认入账" danger={false} onConfirm={batchConfirm} onCancel={() => setConfirm(null)} />
      <ConfirmDialog open={confirm?.kind === 'ignore'} title="忽略这条识别结果？" message="不会产生账单，原始文本仍保留在识别数据里。"
        confirmLabel="忽略" danger={false} onConfirm={() => confirm?.ev && act('ignore', confirm.ev)} onCancel={() => setConfirm(null)} />
      <ConfirmDialog open={confirm?.kind === 'delete'} title="删除这条事件？" message="删除后同一笔支付若再次出现会重新入队。"
        onConfirm={() => confirm?.ev && act('delete', confirm.ev)} onCancel={() => setConfirm(null)} />
    </div>
  );
}
