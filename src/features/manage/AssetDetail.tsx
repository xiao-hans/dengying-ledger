/** 账户详情：余额、额度、状态、记一笔、删除。 */
import { useState } from 'react';
import type { PageProps } from '../../app/App';
import { useNav } from '../../app/navigation';
import { useRefresh } from '../../app/useRefresh';
import { useToast } from '../../app/useToast';
import { TopBar, GlassCard, SectionTitle, ListRow, ConfirmDialog, formatMoney } from '../../components/ui';
import { useAsync } from '../common/hooks';
import { api, local } from '../common/api';
import { N, S } from '../common/format';
import './manage.css';

export function AssetDetail({ route }: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const id = N(route.params?.assetId);
  const { data, reload } = useAsync(async () => ({ assets: await local.assets(), iou: await api.iou() }), [refresh.version]);
  const row = (data?.assets || []).find((a) => N(a.id) === id);
  const [confirm, setConfirm] = useState(false);
  async function toggle() {
    try { await api.assetStatus(id, N(row?.status, 1) === 0 ? 1 : 0); toast.show('账户状态已更新'); reload(); refresh.bump(); }
    catch (e) { toast.show(`更新失败：${e instanceof Error ? e.message : String(e)}`); }
  }
  async function del() {
    setConfirm(false);
    try { await api.deleteAsset(id); toast.show('账户已删除'); refresh.bump(); nav.pop(); }
    catch (e) { toast.show(`删除失败：${e instanceof Error ? e.message : String(e)}`); }
  }
  if (!row) return <div className="page"><TopBar title="账户详情" onBack={nav.pop} /><div className="page-scroll"><div className="page-pad"><GlassCard>账户不存在，可能已被删除。</GlassCard></div></div></div>;
  return (
    <div className="page">
      <TopBar title={S(row.name, '账户详情')} onBack={nav.pop} />
      <div className="page-scroll">
        <div className="page-pad">
          <GlassCard strong>
            <div className="t-label-medium t-secondary">当前余额</div>
            <div className="t-title-large t-bold" style={{ marginTop: 4 }}>¥{formatMoney(N(row.balance_cents))}</div>
            <div className="t-label-medium t-secondary" style={{ marginTop: 8 }}>{S(row.type_title, S(row.type_name, '账户'))} · {N(row.is_total_assets, 1) ? '计入总资产' : '不计入总资产'}</div>
          </GlassCard>
          <SectionTitle>账户信息</SectionTitle>
          <div className="card">
            <ListRow icon="wallet" title="信用额度" subtitle="信用卡可用额度" right={`¥${formatMoney(N(row.credit_line))}`} chevron={false} />
            <ListRow icon="calendar" title="还款日 / 账单日" subtitle={`每月 ${N(row.repayment_date) || '未设置'} 号 / ${N(row.statement_date) || '未设置'} 号`} chevron={false} />
            <ListRow icon="bell" title="还款提醒" subtitle={N(row.is_remind) ? '已开启' : '未开启'} chevron={false} />
            <ListRow icon="note" title="备注" subtitle={S(row.desc, '无')} chevron={false} />
            <ListRow icon="receipt" title="借据名单" subtitle={S(data?.iou) || '暂无借据'} chevron={false} />
          </div>
          <SectionTitle>操作</SectionTitle>
          <div className="card">
            <ListRow icon="plus" title="用这个账户记一笔" subtitle="打开记一笔并预填账户" onClick={() => nav.push('BillEdit', { mode: 'create', accountId: id })} />
            <ListRow icon="edit" title="编辑账户" onClick={() => nav.push('AssetEdit', { assetId: id })} />
            <ListRow icon={N(row.status, 1) === 0 ? 'eye' : 'close'} title={N(row.status, 1) === 0 ? '恢复账户' : '隐藏账户'} subtitle="隐藏后不出现在账户选择器里" onClick={toggle} />
            {id !== 1 && <ListRow icon="trash" danger title="删除账户" subtitle="默认现金账户不可删除" onClick={() => setConfirm(true)} />}
          </div>
        </div>
      </div>
      <ConfirmDialog open={confirm} title={`删除账户「${S(row.name)}」？`} message="已有账单不会删除，但账户不再出现在列表里。" onConfirm={del} onCancel={() => setConfirm(false)} />
    </div>
  );
}
