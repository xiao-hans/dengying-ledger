/** 全局搜索：备注 / 分类 / 金额 / 编号，输入即筛，点结果进详情。 */
import { useMemo, useState } from 'react';
import type { PageProps } from '../../app/App';
import { useNav } from '../../app/navigation';
import { TopBar, SearchPill, ChipRow, GlassCard, EmptyState, ListRow, formatMoney } from '../../components/ui';
import { api, type Dict } from '../common/api';
import { useAsync } from '../common/hooks';
import { N, S, dayText, cateName, cateMap } from '../common/format';

export function GlobalSearch(_: PageProps) {
  const nav = useNav();
  const { data, loading } = useAsync(async () => {
    const [page, cates] = await Promise.all([api.billsPage(1), api.mainCates()]);
    return {
      rows: (page.bills as Dict[]) || (page.items as Dict[]) || [],
      cates: [...(cates.expense || []), ...(cates.income || [])],
    };
  });
  const [q, setQ] = useState('');
  const [type, setType] = useState<'all' | 1 | 2 | 3>('all');
  const cmap = useMemo(() => cateMap(data?.cates || []), [data?.cates]);
  const rows = (data?.rows || []).filter((r) => {
    if (type !== 'all' && N(r.type, 1) !== type) return false;
    if (!q.trim()) return true;
    const hay = `${S(r.desc)} ${cateName(cmap, r.member_cate_id)} ${S(r.client_id)} ${S(N(r.money) / 100)} ${S(r.day)}`.toLowerCase();
    return q.trim().toLowerCase().split(/\s+/).every((w) => hay.includes(w));
  });

  return (
    <div className="page">
      <TopBar title="搜索账单" onBack={nav.pop} />
      <SearchPill value={q} onChange={setQ} placeholder="备注、分类、金额、日期" />
      <ChipRow value={type} onChange={(v) => setType(v as typeof type)}
        items={[{ key: 1, label: '支出' }, { key: 2, label: '收入' }, { key: 3, label: '转账' }]} allLabel="全部" />
      <div className="page-scroll">
        <div className="page-pad">
          {loading && <GlassCard><div className="t-body-medium t-secondary">正在读取本地账本…</div></GlassCard>}
          {!loading && rows.length === 0 && (
            <div className="card"><EmptyState icon="search" title={q ? '没有匹配的账单' : '输入关键词开始搜索'}
              hint={q ? '支持空格分隔多个关键词' : '例如「餐饮 35」或「工资」'} /></div>
          )}
          {rows.length > 0 && (
            <div className="card">
              {rows.map((r) => (
                <ListRow key={S(r.client_id)} icon="receipt"
                  title={<span className="row-between"><span>{cateName(cmap, r.member_cate_id)}</span><span>{N(r.type) === 2 ? '+' : '-'}¥{formatMoney(N(r.money))}</span></span>}
                  subtitle={`${dayText(r.day)} · ${S(r.desc) || '无备注'}`} chevron={false}
                  onClick={() => nav.push('BillDetail', { clientId: S(r.client_id) })} />
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
