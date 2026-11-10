/**
 * 记一笔 / 编辑账单（样本 BillEditActivity 等价页）。
 * 交互纪律：金额/日期不静默改值；分类、账户、标签、备注都可改；草稿切页不丢；
 * 编辑态可删除并有二次确认；保存失败保留输入。
 */
import { useEffect, useMemo, useState } from 'react';
import type { PageProps } from '../../app/App';
import { useNav } from '../../app/navigation';
import { useToast } from '../../app/useToast';
import { useRefresh } from '../../app/useRefresh';
import { Icon } from '../../components/Icon';
import {
  TopBar, GlassCard, SectionTitle, BottomSheet, ChipRow, ConfirmDialog, ListRow, EmptyState,
} from '../../components/ui';
import { api, local, type Dict } from '../common/api';
import { useAsync, useFormDraft } from '../common/hooks';
import { N, S, dayText, jsonArray, toCents, cateName, cateIcon, cateMap } from '../common/format';
import { newClientId, todayDay } from '../../db/client';
import { formatMoney } from '../../components/ui';
import './bills.css';

type Draft = {
  money: string; type: string; cateId: string; accountId: string; bookId: string;
  day: string; desc: string; tagIds: string; images: string; source: string;
};

const INCOME_HINT = '收入会记为正向金额，转账不参与收支统计';

export function BillEdit({ route }: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const { bump } = useRefresh();
  const clientId = S(route.params?.clientId);
  const isEdit = !!clientId;
  const quick = !isEdit && S(route.params?.mode) === 'quick';
  const fallback: Draft = {
    money: '', type: '1', cateId: '1', accountId: '1', bookId: '1',
    day: String(todayDay()), desc: S(route.params?.desc), tagIds: '', images: '', source: '0',
  };
  const { value: d, patch, reset } = useFormDraft<Draft>(`bill-edit:${clientId || 'new'}`, fallback);
  const { data, loading } = useAsync(async () => {
    const [cates, assets, tags, bills] = await Promise.all([
      api.mainCates(), local.assets(), local.tags(), api.billsPage(1),
    ]);
    const rows: Dict[] = (bills.bills as Dict[]) || (bills.items as Dict[]) || [];
    return {
      cates: [...(cates.expense || []), ...(cates.income || [])],
      assets, tags,
      original: rows.find((r) => S(r.client_id ?? r.clientId) === clientId),
    };
  });
  const [sheet, setSheet] = useState<'cate' | 'account' | 'tag' | null>(null);
  const [confirm, setConfirm] = useState(false);
  const [saving, setSaving] = useState(false);

  // 编辑态：首次载入时把服务端行灌入草稿（之后用户编辑优先，不再覆盖）
  useEffect(() => {
    const o = data?.original;
    if (!isEdit || !o || d.money) return;
    patch({
      money: String(N(o.money) / 100), type: String(N(o.type, 1)),
      cateId: String(N(o.member_cate_id, 1)), accountId: String(N(o.from_assets_id, 1)),
      bookId: String(N(o.book_id, 1)), day: String(N(o.day, todayDay())),
      desc: S(o.desc), tagIds: jsonArray(o.tag_ids).join(','),
      images: jsonArray(o.images).join(','), source: String(N(o.source, 0)),
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data?.original, isEdit]);

  const cmap = useMemo(() => cateMap(data?.cates || []), [data?.cates]);
  const cates = (data?.cates || []).filter((c) => N(c.type, 1) === N(d.type, 1));
  const selectedTags = d.tagIds.split(',').filter(Boolean);

  const showError = (msg: string) => toast.show(msg);
  async function submit() {
    const cents = toCents(d.money);
    if (cents === null || cents === 0) return showError('请输入有效金额（最多两位小数）');
    if (!d.cateId) return showError('请选择分类');
    const id = clientId || newClientId();
    const item: Dict = {
      clientId: id, client_id: id, billType: N(d.type, 1), type: N(d.type, 1),
      money: Math.abs(cents), memberCateId: N(d.cateId, 1), cateId: N(d.cateId, 1),
      fromFundAccountId: N(d.accountId, 1), toFundAccountId: N(d.accountId, 1),
      billBookId: N(d.bookId, 1), bookId: N(d.bookId, 1),
      day: N(d.day, todayDay()), desc: d.desc,
      tagIds: selectedTags.map((t) => N(t)).filter(Boolean),
      images: d.images.split(',').map((s) => s.trim()).filter(Boolean),
      source: N(d.source, 0), currency: 'CNY', status: 1,
      createDate: Date.now(),
    };
    setSaving(true);
    try {
      if (isEdit) await api.billsUpdate([item]); else await api.billsCreate([item]);
      bump();
      reset();
      // 回写一条行为埋点（样本 Behavior/Add，不阻塞 UI）
      void api.behavior({ event: isEdit ? 'bill_update' : 'bill_create', clientId: id }).catch(() => {});
      toast.show(isEdit ? '已更新账单' : '已记下这笔');
      nav.pop();
    } catch (e) {
      showError(`保存失败：${e instanceof Error ? e.message : String(e)}`);
    } finally { setSaving(false); }
  }

  async function remove() {
    setConfirm(false);
    try {
      await api.billsDelete([{ clientId, client_id: clientId }]);
      bump();
      reset();
      toast.show('账单已删除');
      nav.pop();
    } catch (e) { showError(`删除失败：${e instanceof Error ? e.message : String(e)}`); }
  }

  return (
    <div className="page">
      <TopBar
        title={isEdit ? '编辑账单' : quick ? '快捷记账' : '记一笔'}
        subtitle={quick ? '只填金额就能记下，分类和备注可后补' : undefined}
        onBack={nav.pop}
        right={isEdit ? <button className="icon-btn" aria-label="删除" onClick={() => setConfirm(true)}><Icon name="trash" /></button> : undefined}
      />
      <div className="page-scroll">
        <div className="page-pad">
          <ChipRow
            value={d.type}
            onChange={(v) => patch({ type: String(v), cateId: v === '2' ? '101' : '1' })}
            items={[{ key: '1', label: '支出' }, { key: '2', label: '收入' }, { key: '3', label: '转账' }]}
          />
          <GlassCard strong>
            <div className="t-label-medium t-secondary">金额（元）</div>
            <div className="amount-input-wrap">
              <span className="t-title-large">¥</span>
              <input
                className="amount-input" inputMode="decimal" placeholder="0.00" value={d.money}
                onChange={(e) => patch({ money: e.target.value })} aria-label="金额"
              />
            </div>
            <div className="t-label-medium t-secondary">{INCOME_HINT}</div>
          </GlassCard>

          {quick && (
            <button className="btn btn-primary" style={{ width: '100%', marginTop: 12 }} disabled={saving || loading} onClick={submit}>
              {saving ? '保存中…' : '一键记下'}
            </button>
          )}

          <SectionTitle>{quick ? '可选补充' : '账单信息'}</SectionTitle>
          <div className="card">
            <ListRow icon="grid" title="分类" right={cateName(cmap, d.cateId)} onClick={() => setSheet('cate')} />
            <ListRow icon="wallet" title="账户" right={S((data?.assets || []).find((a) => String(a.id) === d.accountId)?.name ?? '现金')} onClick={() => setSheet('account')} />
            <ListRow icon="tag" title="标签" right={selectedTags.length ? `${selectedTags.length} 个` : '无'} onClick={() => setSheet('tag')} />
            <div className="list-row">
              <span className="list-row-icon"><Icon name="calendar" size={19} /></span>
              <div className="list-row-main"><div className="t-body-large">日期</div><div className="t-label-medium t-secondary">{dayText(d.day)}</div></div>
              <input className="field date-field" type="date" value={dayToInput(d.day)} onChange={(e) => patch({ day: e.target.value.replace(/-/g, '') })} aria-label="日期" />
            </div>
            <div className="list-row">
              <span className="list-row-icon"><Icon name="note" size={19} /></span>
              <div className="list-row-main grow"><div className="t-body-large">备注</div></div>
              <input className="field desc-field" placeholder="写点什么（选填）" value={d.desc} onChange={(e) => patch({ desc: e.target.value })} aria-label="备注" />
            </div>
            <div className="list-row">
              <span className="list-row-icon"><Icon name="image" size={19} /></span>
              <div className="list-row-main grow"><div className="t-body-large">票据图片</div></div>
              <input className="field desc-field" placeholder="图片地址，逗号分隔（选填）" value={d.images} onChange={(e) => patch({ images: e.target.value })} aria-label="票据图片" />
            </div>
          </div>

          <div className="t-label-medium t-secondary" style={{ padding: '12px 4px' }}>
            {isEdit ? `账单编号 ${clientId}` : '保存后仅存于此设备；草稿在切页时自动保留'}
          </div>
        </div>
      </div>
      <div className="page-fixed-bottom">
        <button className="btn btn-ghost" onClick={nav.pop}>取消</button>
        <button className="btn btn-primary grow" disabled={saving || loading} onClick={submit}>
          {saving ? '保存中…' : isEdit ? '保存修改' : '保存账单'}
        </button>
      </div>

      <BottomSheet open={sheet === 'cate'} title="选择分类" onClose={() => setSheet(null)}>
        {cates.length === 0 && <EmptyState title="暂无分类" hint="可在「我的 → 分类与标签」里新增" />}
        <div className="grid-3">
          {cates.map((c) => (
            <button key={String(c.cate_id ?? c.id)} className={`pick-tile glass ${String(c.cate_id ?? c.id) === d.cateId ? 'selected' : ''}`}
              onClick={() => { patch({ cateId: String(c.cate_id ?? c.id) }); setSheet(null); }}>
              <Icon name={safeIcon(cateIcon(cmap, c.cate_id ?? c.id))} size={22} />
              <span className="t-body-small t-semi">{S(c.title, '未命名')}</span>
            </button>
          ))}
        </div>
      </BottomSheet>
      <BottomSheet open={sheet === 'account'} title="选择账户" onClose={() => setSheet(null)}>
        <div className="card">
          {(data?.assets || []).map((a) => (
            <ListRow key={String(a.id)} icon="wallet" title={S(a.name, '未命名账户')}
              subtitle={`余额 ¥${formatMoney(N(a.balance_cents))}`} chevron={false}
              right={String(a.id) === d.accountId ? <Icon name="check" size={18} /> : undefined}
              onClick={() => { patch({ accountId: String(a.id) }); setSheet(null); }} />
          ))}
        </div>
      </BottomSheet>
      <BottomSheet open={sheet === 'tag'} title="选择标签" onClose={() => setSheet(null)}>
        <div className="card">
          {(data?.tags || []).length === 0 && <EmptyState title="还没有标签" hint="我的 → 分类与标签 可以创建" />}
          {(data?.tags || []).map((t) => {
            const id = String(t.tag_id);
            const on = selectedTags.includes(id);
            return (
              <ListRow key={id} icon="tag" title={S(t.name, '未命名标签')} chevron={false}
                right={on ? <Icon name="check" size={18} /> : undefined}
                onClick={() => patch({ tagIds: (on ? selectedTags.filter((x) => x !== id) : [...selectedTags, id]).join(',') })} />
            );
          })}
        </div>
        <button className="btn btn-primary" style={{ marginTop: 14 }} onClick={() => setSheet(null)}>完成</button>
      </BottomSheet>

      <ConfirmDialog open={confirm} title="删除这笔账单？" message="删除后不可恢复，账本统计会同步更新。"
        onConfirm={remove} onCancel={() => setConfirm(false)} />
    </div>
  );
}

function dayToInput(day: string): string {
  const s = day.replace(/-/g, '');
  return s.length === 8 ? `${s.slice(0, 4)}-${s.slice(4, 6)}-${s.slice(6, 8)}` : '';
}
function safeIcon(name: string) {
  const known = ['wallet', 'transfer', 'gift', 'folder', 'star', 'heart', 'book', 'users', 'more', 'coin', 'chart', 'grid'];
  return (known.includes(name) ? name : 'grid') as 'grid';
}
