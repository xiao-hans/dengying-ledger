/** 全部账单：按日分组、类型筛选、搜索、分页加载；点击进入编辑。 */
import { useMemo, useState } from 'react';
import type { PageProps } from '../../app/App';
import { useNav } from '../../app/navigation';
import { Icon } from '../../components/Icon';
import { TopBar, SearchPill, ChipRow, GlassCard, ListRow, EmptyState, SectionTitle, formatMoney } from '../../components/ui';
import '../bills/bills.css';
import { api, type Dict } from '../common/api';
import { useAsync } from '../common/hooks';
import { N, S, dayText, dayMonth, cateName, cateIcon, cateMap } from '../common/format';

export function BillList(_: PageProps) {
  const nav = useNav();
  const { data, loading, error, reload } = useAsync(async () => {
    const [page, cates] = await Promise.all([api.billsPage(1), api.mainCates()]);
    return {
      rows: (page.bills as Dict[]) || (page.items as Dict[]) || [],
      total: N(page.total, 0), hasMore: N(page.hasMore, 0),
      cates: [...(cates.expense || []), ...(cates.income || [])],
    };
  });
  const [pageNum, setPageNum] = useState(1);
  const [extra, setExtra] = useState<Dict[]>([]);
  const [q, setQ] = useState('');
  const [type, setType] = useState<'all' | 1 | 2 | 3>('all');
  const [month, setMonth] = useState<'all' | number>('all');
  const cmap = useMemo(() => cateMap(data?.cates || []), [data?.cates]);

  const rows = [...(data?.rows || []), ...extra];
  const months = useMemo(() => [...new Set(rows.map((r) => dayMonth(r.day)).filter(Boolean))].sort((a, b) => b - a), [rows]);
  const filtered = rows.filter((r) => {
    if (type !== 'all' && N(r.type, 1) !== type) return false;
    if (month !== 'all' && dayMonth(r.day) !== month) return false;
    if (q.trim()) {
      const hay = `${S(r.desc)} ${cateName(cmap, r.member_cate_id)} ${S(r.client_id)} ${S(r.money)}`.toLowerCase();
      if (!hay.includes(q.trim().toLowerCase())) return false;
    }
    return true;
  });
  const groups = useMemo(() => {
    const m = new Map<string, Dict[]>();
    for (const r of filtered) {
      const k = S(r.day);
      if (!m.has(k)) m.set(k, []);
      m.get(k)!.push(r);
    }
    return [...m.entries()].sort((a, b) => b[0].localeCompare(a[0]));
  }, [filtered]);

  async function loadMore() {
    const next = pageNum + 1;
    const p = await api.billsPage(next);
    const items = (p.bills as Dict[]) || (p.items as Dict[]) || [];
    setExtra((old) => [...old, ...items]);
    setPageNum(next);
  }

  return (
    <div className="page">
      <TopBar title="全部账单" subtitle={`共 ${N(data?.total, rows.length)} 笔`} onBack={nav.pop}
        right={<button className="icon-btn" aria-label="刷新" onClick={reload}><Icon name="refresh" /></button>} />
      <SearchPill value={q} onChange={setQ} placeholder="搜索备注、分类、金额" />
      <ChipRow value={type} onChange={(v) => setType(v as typeof type)}
        items={[{ key: 1, label: '支出' }, { key: 2, label: '收入' }, { key: 3, label: '转账' }]} allLabel="全部" />
      <ChipRow value={month} onChange={(v) => setMonth(v as typeof month)}
        items={months.map((m) => ({ key: m, label: `${Math.trunc(m / 100) % 100}月` }))} allLabel="全部月份" />
      <div className="page-scroll">
        <div className="page-pad">
          {loading && <GlassCard><div className="t-body-medium t-secondary">正在读取本地账本…</div></GlassCard>}
          {error && <GlassCard><div className="t-body-medium t-danger">读取失败：{error}</div></GlassCard>}
          {!loading && !error && groups.length === 0 && (
            <div className="card"><EmptyState icon="receipt" title={q ? '没有匹配的账单' : '还没有账单'}
              hint={q ? '换个关键词，或清空搜索' : '记下第一笔，账单会按日期自动归档'}
              actionLabel={q ? '清空搜索' : '去记一笔'} onAction={() => q ? setQ('') : nav.push('BillEdit', { mode: 'create' })} /></div>
          )}
          {groups.map(([day, list]) => (
            <div key={day}>
              <SectionTitle action={<span className="t-label-medium t-secondary">{list.length} 笔</span>}>{dayText(day)}</SectionTitle>
              <div className="card">
                {list.map((r) => (
                  <ListRow key={S(r.client_id)}
                    icon={cateIcon(cmap, r.member_cate_id) as 'wallet'}
                    title={<span className="row-between"><span>{cateName(cmap, r.member_cate_id)}</span><span className={N(r.type) === 2 ? 't-semi' : ''}>{N(r.type) === 2 ? '+' : N(r.type) === 1 ? '-' : ''}¥{formatMoney(N(r.money))}</span></span>}
                    subtitle={[S(r.desc), S(r.client_id)].filter(Boolean).join(' · ') || '无备注'}
                    chevron={false}
                    onClick={() => nav.push('BillDetail', { clientId: S(r.client_id) })} />
                ))}
              </div>
            </div>
          ))}
          {N(data?.hasMore, 0) > 0 && (
            <button className="btn btn-ghost" style={{ marginTop: 12 }} onClick={loadMore}>加载更多</button>
          )}
        </div>
      </div>
    </div>
  );
}
