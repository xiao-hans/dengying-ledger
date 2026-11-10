/** 账单详情：完整字段、编辑/删除入口、来源与幂等键说明。 */
import type { PageProps } from '../../app/App';
import { useNav } from '../../app/navigation';
import { useToast } from '../../app/useToast';
import { useRefresh } from '../../app/useRefresh';
import { Icon } from '../../components/Icon';
import { TopBar, GlassCard, SectionTitle, ListRow, EmptyState, Money, ConfirmDialog, formatMoney } from '../../components/ui';
import { api, local, type Dict } from '../common/api';
import { useAsync } from '../common/hooks';
import { N, S, dayText, timeText, jsonArray, cateName, cateIcon, cateMap, toCents } from '../common/format';
import { useState } from 'react';
import { newClientId } from '../../db/client';

const SOURCE: Record<number, string> = { 0: '手动记账', 1: '通知识别', 2: '无障碍识别', 3: '截屏 OCR', 4: '账单导入', 5: '周期账' };
const TYPE: Record<number, string> = { 1: '支出', 2: '收入', 3: '转账' };

export function BillDetail({ route }: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const { bump } = useRefresh();
  const clientId = S(route.params?.clientId);
  const { data, loading, error, reload } = useAsync(async () => {
    const [page, cates, refunds] = await Promise.all([api.billsPage(1), api.mainCates(), local.refunds()]);
    const rows: Dict[] = (page.bills as Dict[]) || (page.items as Dict[]) || [];
    return {
      row: rows.find((r) => S(r.client_id) === clientId),
      cates: [...(cates.expense || []), ...(cates.income || [])],
      refunds: refunds.filter((f) => S(f.client_id) === clientId),
    };
  }, [clientId]);
  const [confirm, setConfirm] = useState(false);
  const [refundId, setRefundId] = useState<number | null>(null);
  const [amount, setAmount] = useState('');
  const r = data?.row;
  const cmap = cateMap(data?.cates || []);
  const refunded = (data?.refunds || []).reduce((s, f) => s + N(f.money), 0);

  async function remove() {
    setConfirm(false);
    try {
      await api.billsDelete([{ clientId, client_id: clientId }]);
      bump();
      toast.show('账单已删除');
      nav.pop();
    } catch (e) { toast.show(`删除失败：${e instanceof Error ? e.message : String(e)}`); }
  }

  async function addRefund() {
    const cents = toCents(amount);
    if (cents === null || cents <= 0) return toast.show('请输入有效的退款金额');
    if (r && refunded + cents > N(r.money)) return toast.show('退款合计不能超过原账单金额');
    try {
      await api.refundCreate({ id: newClientId(), clientId, money: cents, day: r ? N(r.day) : 0, desc: '退款' });
      setAmount('');
      bump();
      reload();
      toast.show('已记录退款');
    } catch (e) { toast.show(`退款失败：${e instanceof Error ? e.message : String(e)}`); }
  }

  async function dropRefund() {
    const id = refundId;
    setRefundId(null);
    if (id === null) return;
    try {
      await api.refundDelete(String(id));
      bump();
      reload();
      toast.show('已撤销这笔退款');
    } catch (e) { toast.show(`撤销失败：${e instanceof Error ? e.message : String(e)}`); }
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
                    {refunded > 0 && <div className="t-label-medium t-secondary" style={{ marginTop: 4 }}>已退 ¥ {formatMoney(refunded)} · 实付 ¥ {formatMoney(Math.max(0, N(r.money) - refunded))}</div>}
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
              <SectionTitle>退款</SectionTitle>
              <div className="card">
                {(data?.refunds || []).length === 0 && <EmptyState icon="coin" title="还没有退款" hint="退款不改原账单，报表按实付金额统计" />}
                {(data?.refunds || []).map((f) => (
                  <ListRow key={String(f.id)} icon="coin" title={`退款 ¥ ${formatMoney(N(f.money))}`}
                    subtitle={`${dayText(f.day)} · ${S(f.desc) || '无备注'}`} right="撤销"
                    onClick={() => setRefundId(N(f.id))} />
                ))}
                <div className="list-row">
                  <span className="list-row-icon"><Icon name="plus" size={19} /></span>
                  <div className="list-row-main grow"><div className="t-body-large">新增退款（元）</div></div>
                  <input className="field desc-field" inputMode="decimal" placeholder="0.00" value={amount}
                    onChange={(e) => setAmount(e.target.value)} aria-label="退款金额" />
                </div>
              </div>
              <button className="btn btn-ghost" style={{ width: '100%', marginTop: 10 }} onClick={addRefund}>记录退款</button>
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
      <ConfirmDialog open={refundId !== null} title="撤销这笔退款？" message="撤销后报表按原金额重新统计。" onConfirm={dropRefund} onCancel={() => setRefundId(null)} />
    </div>
  );
}
