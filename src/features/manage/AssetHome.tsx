/** 资产与账户：总资产 / 负债 / 账户列表 / 借据 / 隐藏账户。 */
import type { PageProps } from '../../app/App';
import { useNav } from '../../app/navigation';
import { useRefresh } from '../../app/useRefresh';
import { useToast } from '../../app/useToast';
import { Icon } from '../../components/Icon';
import { TopBar, GlassCard, SectionTitle, ListRow, EmptyState, formatMoney } from '../../components/ui';
import { useAsync } from '../common/hooks';
import { api, local, type Dict } from '../common/api';
import { N, S } from '../common/format';
import { safeIcon } from './common';
import './manage.css';

export function AssetHome(_: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const { data, reload } = useAsync(async () => {
    const [assets, types, iou] = await Promise.all([local.assets(), api.assetTypes(), api.iou()]);
    return { assets, types, iou: S(iou) };
  }, [refresh.version]);
  const rows = (data?.assets || []).filter((a) => N(a.status, 1) !== 0);
  const total = rows.filter((a) => N(a.is_total_assets, 1) === 1 && N(a.type) !== 3).reduce((s, a) => s + N(a.balance_cents), 0);
  const debt = rows.filter((a) => N(a.type) === 3 || N(a.balance_cents) < 0).reduce((s, a) => s + Math.abs(Math.min(0, N(a.balance_cents))), 0);
  async function toggleHidden(a: Dict) {
    try { await api.assetStatus(N(a.id), N(a.status, 1) === 0 ? 1 : 0); toast.show('账户状态已更新'); reload(); refresh.bump(); }
    catch (e) { toast.show(`更新失败：${e instanceof Error ? e.message : String(e)}`); }
  }
  return (
    <div className="page">
      <TopBar title="资产与账户" subtitle="余额 / 信用卡 / 借据" onBack={nav.pop}
        right={<button className="icon-btn" aria-label="新建账户" onClick={() => nav.push('AssetEdit')}><Icon name="plus" /></button>} />
      <div className="page-scroll">
        <div className="page-pad">
          <GlassCard strong>
            <div className="t-label-medium t-secondary">计入总资产</div>
            <div className="t-title-large t-bold" style={{ marginTop: 4 }}>¥{formatMoney(total)}</div>
            <div className="row-between" style={{ marginTop: 12 }}><span className="t-label-medium t-secondary">负债 / 待还</span><span className="t-body-medium t-danger">¥{formatMoney(debt)}</span></div>
            <div className="row-between"><span className="t-label-medium t-secondary">账户数量</span><span className="t-body-medium">{rows.length} 个</span></div>
          </GlassCard>
          <SectionTitle>资金账户</SectionTitle>
          {rows.length === 0 ? <div className="card"><EmptyState icon="wallet" title="还没有账户" hint="添加一个现金 / 银行卡 / 支付宝账户" actionLabel="新建账户" onAction={() => nav.push('AssetEdit')} /></div> : (
            <div className="card">{rows.map((a: Dict) => (
              <ListRow key={S(a.id)} icon={safeIcon(a.icon, 'wallet')} title={S(a.name, '账户')}
                subtitle={`${S(a.type_title, S(a.type_name, '账户'))} · ${N(a.is_total_assets, 1) ? '计入总资产' : '不计入'}${N(a.credit_line) ? ` · 额度 ¥${formatMoney(N(a.credit_line))}` : ''}`}
                right={`¥${formatMoney(N(a.balance_cents))}`} onClick={() => nav.push('AssetDetail', { assetId: N(a.id) })} />
            ))}</div>
          )}
          <SectionTitle>借据与转账</SectionTitle>
          <div className="card">
            <ListRow icon="receipt" title="借据名单" subtitle={data?.iou ? data.iou : '暂无借据'} chevron={false} />
            <ListRow icon="transfer" title="记一笔转账" subtitle="从账户 A 转到账户 B" onClick={() => nav.push('BillEdit', { mode: 'create', type: 3 })} />
          </div>
          <SectionTitle>隐藏 / 恢复账户</SectionTitle>
          <div className="card">{(data?.assets || []).length === 0 ? <ListRow icon="info" title="暂无账户" chevron={false} /> : (data?.assets || []).map((a: Dict) => (
            <ListRow key={`h${S(a.id)}`} icon={N(a.status, 1) === 0 ? 'eye' : 'close'} title={`${N(a.status, 1) === 0 ? '恢复' : '隐藏'}「${S(a.name)}」`}
              subtitle={N(a.status, 1) === 0 ? '当前已隐藏' : '隐藏后不出现在记一笔的账户选择器里'} onClick={() => toggleHidden(a)} chevron={false} />
          ))}</div>
          <div className="t-label-medium t-secondary manage-note">余额以整数分存储，避免浮点误差；信用卡负债单独列出，不与现金资产混算。</div>
        </div>
      </div>
    </div>
  );
}
