/** 消费分期：列表 / 创建 / 详情 / 结清 / 删除。金额整数分。 */
import { useMemo, useState } from 'react';
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
import { all } from '../../db/client';
import { N, S, toCents } from '../common/format';
import { pct } from './common';
import './manage.css';

function planName(row: Dict): string {
  return S(row.name) || S(row.desc) || '分期';
}

export function InstallmentList(_: PageProps) {
  const nav = useNav();
  const { data, loading, error, reload } = useAsync(() => api.installmentAll(), [useRefresh().version]);
  const rows = data || [];
  return (
    <div className="page">
      <TopBar title="消费分期" subtitle="按期生成，结清后不再占用" onBack={nav.pop}
        right={<button className="icon-btn" aria-label="新建分期" onClick={() => nav.push('InstallmentEdit')}><Icon name="plus" /></button>} />
      <div className="page-scroll">
        <div className="page-pad">
          {loading && <div className="t-body-medium t-secondary">加载中…</div>}
          {error && <div className="card"><EmptyState icon="warning" title="加载失败" hint={error} actionLabel="重试" onAction={reload} /></div>}
          {!loading && !error && rows.length === 0 && (
            <div className="card">
              <EmptyState icon="receipt" title="还没有分期" hint="大额消费可以拆成若干期" actionLabel="新建分期" onAction={() => nav.push('InstallmentEdit')} />
            </div>
          )}
          {rows.map((row) => {
            const total = N(row.total_count, 1);
            const left = N(row.remaining_count);
            const done = Math.max(0, total - left);
            const closed = N(row.status) === 2;
            return (
              <GlassCard key={S(row.id)} onClick={() => nav.push('InstallmentDetail', { planId: S(row.id) })}>
                <div className="row-between">
                  <div className="row" style={{ gap: 10 }}>
                    <span className="manage-icon"><Icon name="receipt" size={18} /></span>
                    <div>
                      <div className="t-body-large t-semi">{planName(row)}</div>
                      <div className="t-label-medium t-secondary">{closed ? '已结清' : `已还 ${done}/${total} 期`} · 利息 ¥ {formatMoney(N(row.interest))}</div>
                    </div>
                  </div>
                  <div className="t-body-medium t-semi">¥ {formatMoney(N(row.total_money))}</div>
                </div>
                <div className="manage-progress" style={{ marginTop: 10 }}>
                  <div className="manage-bar" style={{ width: `${pct(done, total)}%` }} />
                </div>
              </GlassCard>
            );
          })}
        </div>
      </div>
    </div>
  );
}

export function InstallmentEdit(_: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const [name, setName] = useState('');
  const [money, setMoney] = useState('');
  const [count, setCount] = useState('12');
  const [interest, setInterest] = useState('');
  const [interestType, setInterestType] = useState(1);

  async function save() {
    const n = name.trim();
    if (!n) return toast.show('请填写名称');
    const cents = toCents(money);
    if (cents === null || cents <= 0) return toast.show('总金额要大于 0');
    const totalCount = Math.min(60, Math.max(2, Math.round(Number(count) || 12)));
    const interestCents = toCents(interest) ?? 0;
    try {
      await api.installmentCreate({
        name: n, totalMoney: cents, totalCount,
        interest: interestCents, interestType, remainderProcessType: 1,
      });
      toast.show('分期已创建');
      refresh.bump(); nav.pop();
    } catch (e) { toast.show(`创建失败：${e instanceof Error ? e.message : String(e)}`); }
  }

  return (
    <div className="page">
      <TopBar title="新建分期" onBack={nav.pop} />
      <div className="page-scroll">
        <div className="page-pad">
          <SectionTitle>名称</SectionTitle>
          <input className="field" value={name} onChange={(e) => setName(e.target.value)} placeholder="例如 电脑" />
          <SectionTitle>总金额（元）</SectionTitle>
          <input className="field" inputMode="decimal" value={money} onChange={(e) => setMoney(e.target.value)} placeholder="0.00" />
          <SectionTitle>期数（2–60）</SectionTitle>
          <input className="field" inputMode="numeric" value={count} onChange={(e) => setCount(e.target.value)} />
          <SectionTitle>利息（元，可空）</SectionTitle>
          <input className="field" inputMode="decimal" value={interest} onChange={(e) => setInterest(e.target.value)} placeholder="0" />
          <SectionTitle>利息方式</SectionTitle>
          <div className="row" style={{ gap: 8 }}>
            {[{ id: 1, name: '均摊到每期' }, { id: 2, name: '记在首期' }].map((t) => (
              <button key={t.id} className={`chip ${interestType === t.id ? 'selected' : ''}`} onClick={() => setInterestType(t.id)}>{t.name}</button>
            ))}
          </div>
          <p className="t-label-medium t-secondary manage-note">创建后按期生成明细。每期金额 = 总金额 ÷ 期数，余数按「余数处理」记在最后一期，不用小数累加。</p>
          <button className="btn btn-primary" style={{ marginTop: 8, width: '100%' }} onClick={save}>创建</button>
        </div>
      </div>
    </div>
  );
}

export function InstallmentDetail({ route }: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const planId = S(route.params?.planId);
  const { data, loading, error, reload } = useAsync(async () => {
    const [got, items] = await Promise.all([
      api.installmentGet(planId),
      all<Dict>('installment_item'),
    ]);
    return { plan: (got.plan as Dict) || null, items: items.filter((it) => S(it.plan_id) === planId) };
  }, [planId, refresh.version]);
  const plan = data?.plan || null;
  const items = useMemo(() => (data?.items || []).slice().sort((a, b) => N(a.num) - N(b.num)), [data]);
  const [payoff, setPayoff] = useState(false);
  const [del, setDel] = useState(false);
  const [keepBills, setKeepBills] = useState(true);

  async function doPayoff() {
    setPayoff(false);
    try {
      await api.installmentPayOff({ planId });
      toast.show('已结清');
      reload(); refresh.bump();
    } catch (e) { toast.show(`结清失败：${e instanceof Error ? e.message : String(e)}`); }
  }
  async function doDelete() {
    setDel(false);
    try {
      await api.installmentDelete(planId, keepBills ? 1 : 0);
      toast.show('已删除');
      refresh.bump(); nav.pop();
    } catch (e) { toast.show(`删除失败：${e instanceof Error ? e.message : String(e)}`); }
  }

  const total = plan ? N(plan.total_count, 1) : 0;
  const left = plan ? N(plan.remaining_count) : 0;
  const closed = plan ? N(plan.status) === 2 : false;

  return (
    <div className="page">
      <TopBar title={plan ? planName(plan) : '分期详情'} onBack={nav.pop} />
      <div className="page-scroll">
        <div className="page-pad">
          {loading && <div className="t-body-medium t-secondary">加载中…</div>}
          {error && <div className="card"><EmptyState icon="warning" title="加载失败" hint={error} actionLabel="重试" onAction={reload} /></div>}
          {!loading && !error && !plan && (
            <div className="card"><EmptyState icon="receipt" title="找不到这期分期" hint="可能已被删除" actionLabel="返回列表" onAction={nav.pop} /></div>
          )}
          {plan && (
            <GlassCard>
              <div className="t-label-medium t-secondary">{closed ? '已结清' : '进行中'}</div>
              <div className="t-title-large" style={{ marginTop: 4 }}>¥ {formatMoney(N(plan.total_money))}</div>
              <div className="t-body-medium t-secondary">共 {total} 期 · 剩余 {left} 期 · 利息 ¥ {formatMoney(N(plan.interest))}</div>
              <div className="manage-progress" style={{ marginTop: 10 }}>
                <div className="manage-bar" style={{ width: `${pct(total - left, total)}%` }} />
              </div>
            </GlassCard>
          )}
          <SectionTitle>期次</SectionTitle>
          {items.length === 0 ? (
            <div className="card"><EmptyState icon="receipt" title="没有期次明细" hint="本地记录为空，可返回后重试" /></div>
          ) : (
            <div className="card">{items.map((it) => (
              <ListRow key={S(it.id)} icon="receipt" chevron={false}
                title={`第 ${N(it.num)} 期 · ¥ ${formatMoney(N(it.money))}`}
                subtitle={N(it.status) === 1 ? '已还' : '待还'} />
            ))}</div>
          )}
          {plan && !closed && (
            <>
              <SectionTitle>操作</SectionTitle>
              <div className="card">
                <ListRow icon="check" title="提前结清" subtitle="剩余期次记为已还，不再占用" onClick={() => setPayoff(true)} chevron={false} />
                <ListRow icon="trash" title="删除分期" subtitle={keepBills ? '删除时保留已生成的账单' : '删除时一并删掉关联账单'} danger onClick={() => setDel(true)} chevron={false} />
                <ListRow icon="edit" title={keepBills ? '保留已生成的账单' : '不保留已生成的账单'} subtitle="点击切换，再点上面的删除" onClick={() => setKeepBills((v) => !v)} chevron={false} />
              </div>
            </>
          )}
        </div>
      </div>
      <ConfirmDialog open={payoff} title="结清这笔分期？" message="剩余期次会记为已还。已入账的账单保留。" confirmLabel="结清" onConfirm={doPayoff} onCancel={() => setPayoff(false)} />
      <ConfirmDialog open={del} title="删除这笔分期？" danger confirmLabel="删除" onConfirm={doDelete} onCancel={() => setDel(false)}
        message={keepBills ? '将保留已生成的账单。' : '将同时删除关联账单。'} />
    </div>
  );
}
