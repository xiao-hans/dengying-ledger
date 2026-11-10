/** 账单详情：完整字段、编辑/删除入口、来源与幂等键说明。 */
import type { PageProps } from '../../app/App';
import { useNav } from '../../app/navigation';
import { useToast } from '../../app/useToast';
import { useRefresh } from '../../app/useRefresh';
import { Icon } from '../../components/Icon';
import { TopBar, GlassCard, SectionTitle, ListRow, EmptyState, Money, ConfirmDialog } from '../../components/ui';
import { api, type Dict } from '../common/api';
import { useAsync } from '../common/hooks';
import { N, S, dayText, timeText, jsonArray, cateName, cateIcon, cateMap } from '../common/format';
import { useState } from 'react';

const SOURCE: Record<number, string> = { 0: '手动记账', 1: '通知识别', 2: '无障碍识别', 3: '截屏 OCR', 4: '账单导入', 5: '周期账' };
const TYPE: Record<number, string> = { 1: '支出', 2: '收入', 3: '转账' };

export function BillDetail({ route }: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const { bump } = useRefresh();
  const clientId = S(route.params?.clientId);
  const { data, loading, error, reload } = useAsync(async () => {
    const [page, cates] = await Promise.all([api.billsPage(1), api.mainCates()]);
    const rows: Dict[] = (page.bills as Dict[]) || (page.items as Dict[]) || [];
    return { row: rows.find((r) => S(r.client_id) === clientId), cates: [...(cates.expense || []), ...(cates.income || [])] };
  }, [clientId]);
  const [confirm, setConfirm] = useState(false);
  const r = data?.row;
  const cmap = cateMap(data?.cates || []);

  async function remove() {
    setConfirm(false);
    try {
      await api.billsDelete([{ clientId, client_id: clientId }]);
      bump();
      toast.show('账单已删除');
      nav.pop();
    } catch (e) { toast.show(`删除失败：${e instanceof Error ? e.message : String(e)}`); }
  }

  return (
    <div className="page">
      <TopBar title="账单详情" onBack={nav.pop}
        right={<button className="icon-btn" aria-label="刷新" onClick={reload}><Icon name="refresh" /></button>} />
      <div className="page-scroll">
        <div className="page-pad">
          {loading && <GlassCard><div className="t-body-medium t-secondary">正在读取…</div></GlassCard>}
          {error && <GlassCard><div className="t-body-medium t-danger">读取失败：{error}</div></GlassCard>}
          {!loading && !error && !r && (
            <div className="card"><EmptyState icon="file" title="找不到这笔账单" hint="它可能已被删除" actionLabel="返回" onAction={nav.pop} /></div>
          )}
          {r && (
            <>
              <GlassCard strong>
                <div className="row-between">
                  <div>
                    <div className="t-label-medium t-secondary">{TYPE[N(r.type, 1)] || '账单'} · {cateName(cmap, r.member_cate_id)}</div>
                    <div className="t-title-large" style={{ marginTop: 6 }}><Money cents={N(r.money)} type={N(r.type, 1) as 1 | 2 | 3} /></div>
                  </div>
                  <span className="list-row-icon"><Icon name={cateIcon(cmap, r.member_cate_id) as 'wallet'} size={26} /></span>
                </div>
                <div className="t-label-medium t-secondary" style={{ marginTop: 10 }}>{dayText(r.day)} {timeText(r.create_date)}</div>
              </GlassCard>

              <SectionTitle>明细</SectionTitle>
              <div className="card">
                <ListRow icon="note" title="备注" right={S(r.desc) || '无'} chevron={false} />
                <ListRow icon="book" title="账本" right={`#${N(r.book_id, 1)}`} chevron={false} />
                <ListRow icon="wallet" title="账户" right={`#${N(r.from_assets_id, 1)}`} chevron={false} />
                <ListRow icon="tag" title="标签" right={jsonArray(r.tag_ids).length ? jsonArray(r.tag_ids).join('、') : '无'} chevron={false} />
                <ListRow icon="auto" title="来源" right={SOURCE[N(r.source, 0)] || '未知'} chevron={false} />
                <ListRow icon="image" title="票据" right={jsonArray(r.images).length ? `${jsonArray(r.images).length} 张` : '无'} chevron={false} />
                <ListRow icon="info" title="账单编号" right={clientId.slice(0, 14) + (clientId.length > 14 ? '…' : '')} chevron={false} />
              </div>
              <div className="t-label-medium t-secondary" style={{ padding: '12px 4px' }}>
                账单编号是端侧幂等键，离线记账后同步不会重复入账。
              </div>
            </>
          )}
        </div>
      </div>
      {r && (
        <div className="page-fixed-bottom">
          <button className="btn btn-ghost" onClick={() => setConfirm(true)}>删除</button>
          <button className="btn btn-primary grow" onClick={() => nav.push('BillEdit', { clientId })}>编辑这笔</button>
        </div>
      )}
      <ConfirmDialog open={confirm} title="删除这笔账单？" message="删除后不可恢复。" onConfirm={remove} onCancel={() => setConfirm(false)} />
    </div>
  );
}
