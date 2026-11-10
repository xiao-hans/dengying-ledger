/** 预算执行：总预算 + 分类预算，按实际账单计算已用，可新增/编辑/删除。 */
import { useMemo, useState } from 'react';
import type { PageProps } from '../../app/App';
import { useNav } from '../../app/navigation';
import { useToast } from '../../app/useToast';
import { Icon } from '../../components/Icon';
import { TopBar, GlassCard, SectionTitle, ListRow, EmptyState, BottomSheet, ConfirmDialog, ChipRow, formatMoney } from '../../components/ui';
import { api, type Dict } from '../common/api';
import { useAsync } from '../common/hooks';
import { N, S, cateName, cateMap, dayMonth } from '../common/format';
import { toCents } from '../common/format';
import './stats.css';

function currentMonth(): number {
  const d = new Date(); return d.getFullYear() * 100 + d.getMonth() + 1;
}

export function BudgetList(_: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const { data, loading, reload } = useAsync(async () => {
    const [list, page, cates] = await Promise.all([api.budgetList(), api.billsPage(1), api.mainCates()]);
    return { budgets: list, rows: (page.bills as Dict[]) || (page.items as Dict[]) || [], cates: cates.expense || [] };
  });
  const [sheet, setSheet] = useState(false);
  const [money, setMoney] = useState('');
  const [cate, setCate] = useState<number | 'all'>('all');
  const [remind, setRemind] = useState(false);
  const [confirm, setConfirm] = useState<Dict | null>(null);
  const cmap = useMemo(() => cateMap(data?.cates || []), [data?.cates]);
  const month = currentMonth();
  const spent = (data?.rows || []).filter((r) => N(r.type, 1) === 1 && dayMonth(r.day) === month).reduce((s, r) => s + N(r.money), 0);
  const total = (data?.budgets || []).find((b) => N(b.day) === month);
  const pct = total && N(total.money) > 0 ? Math.min(1, spent / N(total.money)) : 0;

  async function save() {
    const cents = toCents(money);
    if (cents === null || cents <= 0) return toast.show('请输入有效预算金额');
    try {
      if (cate === 'all') {
        await api.saveBudget({ billsBookId: 1, day: month, money: cents, isRemind: remind ? 1 : 0, remindRate: 80 }, !!total);
      } else {
        await api.saveCateBudget({ billsBookId: 1, day: month, cateId: cate, money: cents }, false);
      }
      toast.show('预算已保存'); setSheet(false); setMoney(''); setCate('all'); reload();
    } catch (e) { toast.show(`保存失败：${e instanceof Error ? e.message : String(e)}`); }
  }
  async function remove() {
    const b = confirm; setConfirm(null);
    if (!b) return;
    try {
      await api.deleteBudget({ billsBookId: 1, day: N(b.day, month) });
      toast.show('预算已删除'); reload();
    } catch (e) { toast.show(`删除失败：${e instanceof Error ? e.message : String(e)}`); }
  }

  return (
    <div className="page">
      <TopBar title="预算执行" subtitle={`${Math.trunc(month / 100)}年${month % 100}月`} onBack={nav.pop}
        right={<button className="icon-btn" aria-label="新增预算" onClick={() => setSheet(true)}><Icon name="plus" /></button>} />
      <div className="page-scroll">
        <div className="page-pad">
          <GlassCard strong>
            <div className="row-between">
              <div><div className="t-label-medium t-secondary">本月总预算</div><div className="t-title-large" style={{ marginTop: 4 }}>¥ {formatMoney(N(total?.money))}</div></div>
              <div style={{ textAlign: 'right' }}><div className="t-label-medium t-secondary">已支出</div><div className="t-body-large t-semi" style={{ marginTop: 8 }}>¥ {formatMoney(spent)}</div></div>
            </div>
            <div className="bar-track"><div className="bar-fill" style={{ width: `${pct * 100}%`, background: pct >= 1 ? 'var(--danger)' : 'var(--brand-secondary)' }} /></div>
            <div className="t-label-medium t-secondary" style={{ marginTop: 8 }}>
              {total ? `剩余 ¥${formatMoney(Math.max(0, N(total.money) - spent))} · 已用 ${(pct * 100).toFixed(1)}%` : '还没有设置本月预算'}
            </div>
          </GlassCard>

          <SectionTitle action={<button onClick={() => setSheet(true)}>设置预算 ›</button>}>历史预算</SectionTitle>
          {loading && <div className="card card-pad"><div className="t-body-medium t-secondary">读取中…</div></div>}
          {!loading && (data?.budgets || []).length === 0 && (
            <div className="card"><EmptyState icon="wallet" title="还没有预算" hint="设置一个月度预算，随时掌握剩余额度" actionLabel="设置总预算" onAction={() => setSheet(true)} /></div>
          )}
          {(data?.budgets || []).map((b) => (
            <div className="card" key={`${S(b.book_id)}-${S(b.day)}`}>
              <ListRow icon="calendar" title={`${S(b.day).slice(0, 4)}年${Number(S(b.day).slice(4, 6))}月`}
                subtitle={`预算 ¥${formatMoney(N(b.money))}${N(b.is_remind) ? ' · 已开提醒' : ''}`}
                right={`${N(b.day) === month ? `已用 ¥${formatMoney(spent)}` : ''}`}
                onClick={() => { setConfirm(b); }} />
            </div>
          ))}
          <SectionTitle>分类预算</SectionTitle>
          <div className="card card-pad">
            <div className="t-body-medium t-secondary">分类预算与总预算共用账本；设置时选择具体分类即可。内置分类：{(data?.cates || []).map((c) => S(c.title)).join('、') || '—'}</div>
          </div>
        </div>
      </div>

      <BottomSheet open={sheet} title="设置预算" onClose={() => setSheet(false)}>
        <div className="t-label-medium t-secondary">金额（元）</div>
        <input className="field" style={{ marginTop: 8 }} inputMode="decimal" placeholder="例如 3000" value={money} onChange={(e) => setMoney(e.target.value)} />
        <div className="t-label-medium t-secondary" style={{ margin: '14px 0 6px' }}>应用到</div>
        <ChipRow value={cate} onChange={(v) => setCate(v as typeof cate)}
          items={(data?.cates || []).map((c) => ({ key: N(c.cate_id ?? c.id), label: cateName(cmap, c.cate_id ?? c.id) }))} allLabel="总预算" />
        <div className="row" style={{ marginTop: 14 }}>
          <button className="btn btn-ghost" onClick={() => setSheet(false)}>取消</button>
          <button className="btn btn-primary grow" onClick={save}>保存预算</button>
        </div>
      </BottomSheet>
      <ConfirmDialog open={!!confirm} title="删除这个月的预算？" message="删除后统计不再显示该月预算，账单不受影响。"
        onConfirm={remove} onCancel={() => setConfirm(null)} />
    </div>
  );
}
