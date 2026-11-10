/** 分类报表：支出/收入切换 + 期间切换 + 分类占比条 + 下钻明细。 */
import { useMemo, useState } from 'react';
import type { PageProps } from '../../app/App';
import { useNav } from '../../app/navigation';
import { TopBar, ChipRow, GlassCard, EmptyState, SectionTitle, formatMoney, ListRow } from '../../components/ui';
import { api, type Dict } from '../common/api';
import { useAsync } from '../common/hooks';
import { N, S, cateMap, cateName } from '../common/format';
import { summarize, periodKey, periodLabel } from './stats';
import './stats.css';

export function ReportCategory(_: PageProps) {
  const nav = useNav();
  const { data, loading } = useAsync(async () => {
    const [page, cates] = await Promise.all([api.billsPage(1), api.mainCates()]);
    return {
      rows: (page.bills as Dict[]) || (page.items as Dict[]) || [],
      cates: [...(cates.expense || []), ...(cates.income || [])],
    };
  });
  const [type, setType] = useState<1 | 2>(1);
  const [period, setPeriod] = useState<'month' | 'year' | 'all'>('month');
  const [selected, setSelected] = useState<number | null>(null);
  const cmap = useMemo(() => cateMap(data?.cates || []), [data?.cates]);
  const rows = data?.rows || [];
  const now = new Date();
  const key = now.getFullYear() * 100 + now.getMonth() + 1;
  const scoped = rows.filter((r) => period === 'all' ? true : period === 'year' ? periodKey(r.day, 'year') === now.getFullYear() : periodKey(r.day, 'month') === key);
  const sum = summarize(scoped, type, (id) => cateName(cmap, id));
  const detail = selected === null ? [] : scoped.filter((r) => N(r.type, 1) === type && N(r.member_cate_id, 9) === selected).sort((a, b) => S(b.day).localeCompare(S(a.day)));

  return (
    <div className="page">
      <TopBar title="分类报表" subtitle={period === 'all' ? '全部时间' : periodLabel(period === 'year' ? now.getFullYear() : key, period === 'year' ? 'year' : 'month')} onBack={nav.pop} />
      <ChipRow value={type} onChange={(v) => { setType(v as 1 | 2); setSelected(null); }} items={[{ key: 1, label: '支出' }, { key: 2, label: '收入' }]} />
      <ChipRow value={period} onChange={(v) => setPeriod(v as typeof period)} items={[{ key: 'month', label: '本月' }, { key: 'year', label: '本年' }, { key: 'all', label: '全部' }]} />
      <div className="page-scroll">
        <div className="page-pad">
          <GlassCard>
            <div className="t-label-medium t-secondary">{type === 1 ? '总支出' : '总收入'}</div>
            <div className="t-title-large" style={{ marginTop: 4 }}>¥ {formatMoney(type === 1 ? sum.expense : sum.income)}</div>
            <div className="t-label-medium t-secondary" style={{ marginTop: 8 }}>结余 ¥ {formatMoney(sum.net)} · {sum.count} 笔账单</div>
          </GlassCard>
          <SectionTitle>分类占比</SectionTitle>
          {loading && <div className="card card-pad"><div className="t-body-medium t-secondary">统计中…</div></div>}
          {!loading && sum.byCate.length === 0 && (
            <div className="card"><EmptyState icon="pie" title="这个期间还没有账单" hint="记一笔后就能看到分类占比" actionLabel="去记一笔" onAction={() => nav.push('BillEdit', { mode: 'create' })} /></div>
          )}
          {sum.byCate.map((c) => (
            <GlassCard key={c.id} onClick={() => setSelected(selected === c.id ? null : c.id)}>
              <div className="row-between">
                <span className="t-body-large t-semi">{c.name}</span>
                <span className="t-body-medium">¥ {formatMoney(c.cents)} <span className="t-secondary t-label-medium">{(c.pct * 100).toFixed(1)}%</span></span>
              </div>
              <div className="bar-track"><div className="bar-fill" style={{ width: `${Math.max(2, c.pct * 100)}%` }} /></div>
              <div className="t-label-medium t-secondary" style={{ marginTop: 6 }}>{c.count} 笔 · 点击{detail.length && selected === c.id ? '收起' : '查看'}明细</div>
            </GlassCard>
          ))}
          {selected !== null && (
            <>
              <SectionTitle>{cateName(cmap, selected)} · 明细</SectionTitle>
              <div className="card">
                {detail.length === 0 && <EmptyState icon="file" title="没有明细" />}
                {detail.map((r) => (
                  <ListRow key={S(r.client_id)} icon="receipt" title={S(r.desc) || '无备注'}
                    subtitle={S(r.day)} right={`¥${formatMoney(N(r.money))}`} chevron={false}
                    onClick={() => nav.push('BillDetail', { clientId: S(r.client_id) })} />
                ))}
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
