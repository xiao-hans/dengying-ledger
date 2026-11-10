/** 周期账与提醒。金额整数分；删除二次确认。 */
import { useEffect, useState } from 'react';
import type { PageProps } from '../../app/App';
import { useNav } from '../../app/navigation';
import { useRefresh } from '../../app/useRefresh';
import { useToast } from '../../app/useToast';
import { Icon } from '../../components/Icon';
import {
  TopBar, GlassCard, SectionTitle, ListRow, EmptyState, ConfirmDialog, formatMoney,
} from '../../components/ui';
import { useAsync } from '../common/hooks';
import { api, type Dict } from '../common/api';
import { N, S, toCents, centsToYuan } from '../common/format';
import './manage.css';

const CYCLE = [
  { id: 1, name: '每月' },
  { id: 2, name: '每周' },
  { id: 3, name: '每年' },
];
const BILL_TYPE = [
  { id: 1, name: '支出' },
  { id: 2, name: '收入' },
];

function cycleName(n: number): string {
  return CYCLE.find((c) => c.id === n)?.name || '每月';
}
function itemsOf(data: { items?: Dict[] } | null): Dict[] {
  return data?.items || [];
}

export function FixedBillList(_: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const { data, loading, error, reload } = useAsync(() => api.fixedBills(), [refresh.version]);
  const rows = itemsOf(data);
  const [pending, setPending] = useState<Dict | null>(null);

  async function remove() {
    const row = pending;
    setPending(null);
    if (!row) return;
    try {
      await api.deleteFixedBill(S(row.id));
      toast.show('已删除');
      reload(); refresh.bump();
    } catch (e) { toast.show(`删除失败：${e instanceof Error ? e.message : String(e)}`); }
  }

  return (
    <div className="page">
      <TopBar title="周期账" subtitle="到日提醒，确认后才入账" onBack={nav.pop}
        right={<button className="icon-btn" aria-label="新建周期账" onClick={() => nav.push('FixedBillEdit', { mode: 'create' })}><Icon name="plus" /></button>} />
      <div className="page-scroll">
        <div className="page-pad">
          {loading && <div className="t-body-medium t-secondary">加载中…</div>}
          {error && <div className="card"><EmptyState icon="warning" title="加载失败" hint={error} actionLabel="重试" onAction={reload} /></div>}
          {!loading && !error && rows.length === 0 && (
            <div className="card">
              <EmptyState icon="cycle" title="还没有周期账" hint="房租、会员费这类固定支出可以设成周期" actionLabel="新建周期账" onAction={() => nav.push('FixedBillEdit', { mode: 'create' })} />
            </div>
          )}
          {rows.map((row) => (
            <GlassCard key={S(row.id)} onClick={() => nav.push('FixedBillEdit', { id: S(row.id) })}>
              <div className="row-between">
                <div className="row" style={{ gap: 10 }}>
                  <span className="manage-icon"><Icon name="cycle" size={18} /></span>
                  <div>
                    <div className="t-body-large t-semi">{S(row.desc) || '周期账'}</div>
                    <div className="t-label-medium t-secondary">{cycleName(N(row.fixed_type, 1))} · 每期第 {N(row.fixed_day, 1)} 天 · {N(row.bill_type) === 2 ? '收入' : '支出'}</div>
                  </div>
                </div>
                <div className="t-body-medium t-semi">¥ {formatMoney(N(row.money))}</div>
              </div>
              <div className="row" style={{ gap: 8, marginTop: 10 }}>
                <button className="btn btn-ghost" style={{ padding: '4px 10px' }} onClick={(e) => { e.stopPropagation(); nav.push('ReminderList', { fixedId: S(row.id) }); }}>提醒</button>
                <button className="btn btn-ghost" style={{ padding: '4px 10px' }} onClick={(e) => { e.stopPropagation(); setPending(row); }}>删除</button>
              </div>
            </GlassCard>
          ))}
          <SectionTitle>提醒</SectionTitle>
          <div className="card">
            <ListRow icon="bell" title="全部提醒" subtitle="到日不会静默入账" onClick={() => nav.push('ReminderList')} />
          </div>
        </div>
      </div>
      <ConfirmDialog open={Boolean(pending)} title="删除这条周期账？" message="删除后不再提醒。已入账的账单不会被删除。" danger confirmLabel="删除" onConfirm={remove} onCancel={() => setPending(null)} />
    </div>
  );
}

export function FixedBillEdit({ route }: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const id = S(route.params?.id);
  const editing = Boolean(id);
  const { data } = useAsync(async () => (editing ? api.fixedBills() : { items: [] as Dict[] }), [id]);
  const row = itemsOf(data).find((r) => S(r.id) === id) || null;
  const [desc, setDesc] = useState('');
  const [money, setMoney] = useState('');
  const [billType, setBillType] = useState(1);
  const [fixedType, setFixedType] = useState(1);
  const [fixedDay, setFixedDay] = useState('1');
  const [notInBudget, setNotInBudget] = useState(false);
  useEffect(() => {
    if (!row) return;
    setDesc(S(row.desc));
    setMoney(centsToYuan(N(row.money)));
    setBillType(N(row.bill_type, 1));
    setFixedType(N(row.fixed_type, 1));
    setFixedDay(String(N(row.fixed_day, 1)));
    setNotInBudget(N(row.not_in_budget) === 1);
  }, [row]);

  async function save() {
    const cents = toCents(money);
    if (cents === null || cents <= 0) return toast.show('请填写金额');
    const day = Math.min(31, Math.max(1, Math.round(Number(fixedDay) || 1)));
    const body: Dict = {
      desc: desc.trim() || '周期账', money: cents, billType, fixedType, fixedDay: day,
      notInBudget: notInBudget ? 1 : 0, status: 1,
    };
    if (editing) body.id = id;
    try {
      await api.saveFixedBill(body, editing);
      toast.show(editing ? '已更新' : '已创建');
      refresh.bump(); nav.pop();
    } catch (e) { toast.show(`保存失败：${e instanceof Error ? e.message : String(e)}`); }
  }

  return (
    <div className="page">
      <TopBar title={editing ? '编辑周期账' : '新建周期账'} onBack={nav.pop} />
      <div className="page-scroll">
        <div className="page-pad">
          <SectionTitle>说明</SectionTitle>
          <input className="field" value={desc} onChange={(e) => setDesc(e.target.value)} placeholder="例如 房租" />
          <SectionTitle>金额（元）</SectionTitle>
          <input className="field" inputMode="decimal" value={money} onChange={(e) => setMoney(e.target.value)} placeholder="0.00" />
          <SectionTitle>类型</SectionTitle>
          <div className="row" style={{ gap: 8 }}>
            {BILL_TYPE.map((t) => (
              <button key={t.id} className={`chip ${billType === t.id ? 'selected' : ''}`} onClick={() => setBillType(t.id)}>{t.name}</button>
            ))}
          </div>
          <SectionTitle>周期</SectionTitle>
          <div className="row" style={{ gap: 8 }}>
            {CYCLE.map((c) => (
              <button key={c.id} className={`chip ${fixedType === c.id ? 'selected' : ''}`} onClick={() => setFixedType(c.id)}>{c.name}</button>
            ))}
          </div>
          <SectionTitle>每期第几天（1–31）</SectionTitle>
          <input className="field" inputMode="numeric" value={fixedDay} onChange={(e) => setFixedDay(e.target.value)} />
          <label className="row" style={{ gap: 8, marginTop: 14 }}>
            <input type="checkbox" checked={notInBudget} onChange={(e) => setNotInBudget(e.target.checked)} />
            <span className="t-body-medium">不计入预算</span>
          </label>
          <button className="btn btn-primary" style={{ marginTop: 18, width: '100%' }} onClick={save}>保存</button>
        </div>
      </div>
    </div>
  );
}

export function ReminderList({ route }: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const fixedId = S(route.params?.fixedId);
  const { data, loading, error, reload } = useAsync(() => api.reminders(), [refresh.version]);
  const rows = (data || []).filter((r) => !fixedId || S(r.fixed_bill_id) === fixedId || S(r.fixedId) === fixedId);
  const [title, setTitle] = useState('');
  const [day, setDay] = useState('1');
  const [hour, setHour] = useState('9');
  const now = new Date();
  const due = rows.filter((r) => N(r.status, 1) === 1 && N(r.day) > 0 && N(r.day) <= now.getDate() && N(r.hour, 0) <= now.getHours());

  async function add() {
    const t = title.trim();
    if (!t) return toast.show('请填写提醒内容');
    try {
      await api.saveReminder({
        title: t, day: Math.min(31, Math.max(1, Math.round(Number(day) || 1))),
        hour: Math.min(23, Math.max(0, Math.round(Number(hour) || 9))),
        fixedBillId: fixedId || undefined, status: 1,
      });
      setTitle(''); toast.show('提醒已保存');
      reload(); refresh.bump();
    } catch (e) { toast.show(`保存失败：${e instanceof Error ? e.message : String(e)}`); }
  }

  return (
    <div className="page">
      <TopBar title="提醒" subtitle="到点只提醒，不会自动入账" onBack={nav.pop} />
      <div className="page-scroll">
        <div className="page-pad">
          <SectionTitle>新建提醒</SectionTitle>
          <input className="field" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="例如 交房租" />
          <div className="row" style={{ gap: 8, marginTop: 10 }}>
            <input className="field" style={{ flex: 1 }} inputMode="numeric" value={day} onChange={(e) => setDay(e.target.value)} aria-label="日" />
            <span className="t-body-medium t-secondary">日</span>
            <input className="field" style={{ flex: 1 }} inputMode="numeric" value={hour} onChange={(e) => setHour(e.target.value)} aria-label="时" />
            <span className="t-body-medium t-secondary">时</span>
          </div>
          <button className="btn btn-primary" style={{ marginTop: 12, width: '100%' }} onClick={add}>保存提醒</button>
          {due.length > 0 && (
            <div className="card" style={{ marginTop: 12 }}>
              <SectionTitle>本月已到点</SectionTitle>
              {due.map((r) => (
                <ListRow key={`due-${S(r.id)}`} icon="bell" title={S(r.title) || '提醒'} subtitle="只提醒，点这里去记一笔" chevron
                  onClick={() => nav.push('BillEdit', { desc: S(r.title) })} />
              ))}
            </div>
          )}
          <SectionTitle>已有提醒</SectionTitle>
          {loading && <div className="t-body-medium t-secondary">加载中…</div>}
          {error && <div className="card"><EmptyState icon="warning" title="加载失败" hint={error} actionLabel="重试" onAction={reload} /></div>}
          {!loading && !error && rows.length === 0 && (
            <div className="card"><EmptyState icon="bell" title="还没有提醒" hint="加一条，到日会在这里看见" /></div>
          )}
          {rows.length > 0 && (
            <div className="card">{rows.map((r) => (
              <ListRow key={S(r.id)} icon="bell" title={S(r.title) || '提醒'} subtitle={`每月 ${N(r.day, 1)} 日 ${N(r.hour, 9)} 时`} chevron={false} />
            ))}</div>
          )}
          <p className="t-label-medium t-secondary manage-note">Web 端没有系统闹钟。打开这一页时，已到「日 + 时」的提醒会列在「本月已到点」，点进去才记一笔，不会自动入账。</p>
        </div>
      </div>
    </div>
  );
}
