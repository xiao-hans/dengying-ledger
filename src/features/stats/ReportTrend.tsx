/** 收支趋势：按月/按年聚合，纯 CSS 柱状图（不引第三方图表）。 */
import { useMemo, useState } from 'react';
import type { PageProps } from '../../app/App';
import { useNav } from '../../app/navigation';
import { TopBar, ChipRow, GlassCard, EmptyState, SectionTitle, formatMoney } from '../../components/ui';
import { api, local, type Dict } from '../common/api';
import { useAsync } from '../common/hooks';
import { N } from '../common/format';
import { periodKey, periodLabel, refundByBill, netMoney } from './stats';
import './stats.css';

type Group = { key: number; expense: number; income: number };

export function ReportTrend(_: PageProps) {
  const nav = useNav();
  const { data, loading } = useAsync(async () => {
    const [page, refunds] = await Promise.all([api.billsPage(1), local.refunds()]);
    return {
      rows: (page.bills as Dict[]) || (page.items as Dict[]) || [],
      refunds: refundByBill(refunds),
    };
  });
  const [period, setPeriod] = useState<'month' | 'year'>('month');
  const [kind, setKind] = useState<'both' | 'expense' | 'income'>('both');
  const groups = useMemo<Group[]>(() => {
    const m = new Map<number, Group>();
    const refunds = data?.refunds ?? new Map();
    for (const r of data?.rows || []) {
      const k = periodKey(r.day, period);
      if (!k) continue;
      if (!m.has(k)) m.set(k, { key: k, expense: 0, income: 0 });
      const g = m.get(k)!;
      if (N(r.type, 1) === 1) g.expense += netMoney(r, refunds);
      if (N(r.type, 1) === 2) g.income += netMoney(r, refunds);
    }
    return [...m.values()].sort((a, b) => a.key - b.key).slice(-12);
  }, [data, period]);
  const max = Math.max(1, ...groups.map((g) => Math.max(g.expense, g.income)));

  return (
    <div className="page">
      <TopBar title="收支趋势" subtitle="最近 12 个期间" onBack={nav.pop} />
      <ChipRow value={period} onChange={(v) => setPeriod(v as typeof period)} items={[{ key: 'month', label: '按月' }, { key: 'year', label: '按年' }]} />
      <ChipRow value={kind} onChange={(v) => setKind(v as typeof kind)} items={[{ key: 'expense', label: '支出' }, { key: 'income', label: '收入' }]} allLabel="收支对比" />
      <div className="page-scroll">
        <div className="page-pad">
          <GlassCard>
            <div className="t-label-medium t-secondary">期间合计</div>
            <div className="row" style={{ gap: 22, marginTop: 8 }}>
              <div><div className="t-label-medium t-secondary">支出</div><div className="t-title-small">¥ {formatMoney(groups.reduce((s, g) => s + g.expense, 0))}</div></div>
              <div><div className="t-label-medium t-secondary">收入</div><div className="t-title-small">¥ {formatMoney(groups.reduce((s, g) => s + g.income, 0))}</div></div>
            </div>
          </GlassCard>
          <SectionTitle>趋势</SectionTitle>
          {loading && <div className="card card-pad"><div className="t-body-medium t-secondary">统计中…</div></div>}
          {!loading && groups.length === 0 && (
            <div className="card"><EmptyState icon="chart" title="还没有可统计的账单" hint="记一笔后这里会生成趋势" actionLabel="去记一笔" onAction={() => nav.push('BillEdit', { mode: 'create' })} /></div>
          )}
          {groups.length > 0 && (
            <div className="card card-pad trend-chart">
              <div className="trend-bars">
                {groups.map((g) => (
                  <div className="trend-col" key={g.key} title={`${periodLabel(g.key, period)} 支出 ¥${formatMoney(g.expense)} / 收入 ¥${formatMoney(g.income)}`}>
                    <div className="trend-pair">
                      {kind !== 'income' && <div className="trend-bar expense" style={{ height: `${Math.max(2, g.expense / max * 100)}%` }} />}
                      {kind !== 'expense' && <div className="trend-bar income" style={{ height: `${Math.max(2, g.income / max * 100)}%` }} />}
                    </div>
                    <div className="t-label-small t-secondary trend-label">{period === 'month' ? `${g.key % 100}月` : `${g.key}`}</div>
                  </div>
                ))}
              </div>
            </div>
          )}
          <div className="card">
            {groups.slice().reverse().map((g) => (
              <div className="list-row" key={g.key}>
                <div className="list-row-main"><div className="t-body-large">{periodLabel(g.key, period)}</div>
                  <div className="t-label-medium t-secondary">结余 ¥ {formatMoney(g.income - g.expense)}</div></div>
                <div className="list-row-right t-body-medium">-¥{formatMoney(g.expense)} / +¥{formatMoney(g.income)}</div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
