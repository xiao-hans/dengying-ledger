/** 分类与标签管理：分类列表 / 显示隐藏 / 新建 / 编辑 / 删除 + 标签入口。 */
import { useState } from 'react';
import type { PageProps } from '../../app/App';
import { useNav } from '../../app/navigation';
import { useRefresh } from '../../app/useRefresh';
import { useToast } from '../../app/useToast';
import { Icon } from '../../components/Icon';
import { TopBar, GlassCard, SectionTitle, ListRow, ChipRow, EmptyState, ConfirmDialog } from '../../components/ui';
import { useAsync } from '../common/hooks';
import { api, type Dict } from '../common/api';
import { N, S } from '../common/format';
import { safeIcon } from './common';
import './manage.css';

export function CategoryManage(_: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const [type, setType] = useState<1 | 2>(1);
  const [confirm, setConfirm] = useState<Dict | null>(null);
  const { data, reload } = useAsync(async () => {
    const [cates, icons, tags] = await Promise.all([api.mainCates(), api.cateIcons(), api.bookList()]);
    return { cates, icons, books: tags };
  }, [refresh.version]);

  const rows = (type === 1 ? data?.cates.expense : data?.cates.income) || [];
  async function hide(c: Dict) {
    try { await api.updateCateExtend([{ memberCateId: N(c.id), bookId: 1, hidden: N(c.hidden) ? 0 : 1, sort: N(c.sort) }]); toast.show('已更新显示状态'); reload(); refresh.bump(); }
    catch (e) { toast.show(`更新失败：${e instanceof Error ? e.message : String(e)}`); }
  }
  async function del() {
    const c = confirm; setConfirm(null); if (!c) return;
    try { await api.deleteCate(N(c.id)); toast.show('分类已删除'); reload(); refresh.bump(); }
    catch (e) { toast.show(`删除失败：${e instanceof Error ? e.message : String(e)}`); }
  }

  return (
    <div className="page">
      <TopBar title="分类与标签" subtitle="内置分类不可删除，可隐藏" onBack={nav.pop}
        right={<button className="icon-btn" aria-label="新建分类" onClick={() => nav.push('CateEdit', { type })}><Icon name="plus" /></button>} />
      <div className="page-scroll">
        <div className="page-pad">
          <GlassCard strong>
            <div className="t-body-large t-semi">分类字典</div>
            <div className="t-label-medium t-secondary" style={{ marginTop: 6, lineHeight: 1.8 }}>
              支出 {data?.cates.expense.length ?? 0} 项 · 收入 {data?.cates.income.length ?? 0} 项 · 自定义分类可编辑或删除，内置分类只可隐藏。
            </div>
          </GlassCard>
          <div style={{ margin: '12px 0' }}><ChipRow value={type} onChange={(v) => setType(v as 1 | 2)} items={[{ key: 1, label: '支出分类' }, { key: 2, label: '收入分类' }]} /></div>
          <div className="card"><ListRow icon="tag" title="标签管理" subtitle="给账单打标签 · 排序 / 重命名 / 删除" onClick={() => nav.push('TagList')} /></div>
          <SectionTitle action={<button onClick={() => nav.push('CateEdit', { type })}>新建分类 ›</button>}>{type === 1 ? '支出' : '收入'}分类（{rows.length}）</SectionTitle>
          {rows.length === 0 ? <div className="card"><EmptyState icon="grid" title="还没有分类" hint="新建一个分类开始记账" actionLabel="新建分类" onAction={() => nav.push('CateEdit', { type })} /></div> : (
            <div className="card">{rows.map((c: Dict) => (
              <ListRow key={S(c.id)} icon={safeIcon(c.icon ?? c.base_icon)} title={<span className="row" style={{ gap: 8 }}>{S(c.title)} {N(c.is_custom) ? <span className="badge">自定义</span> : null}</span>}
                subtitle={`编号 #${N(c.id)} · ${N(c.hidden) ? '已隐藏' : '显示中'} · 排序 ${N(c.sort)}`}
                onClick={() => nav.push('CateEdit', { type, cateId: N(c.id) })} />
            ))}</div>
          )}
          <SectionTitle>快捷操作</SectionTitle>
          <div className="card">{rows.slice(0, 8).map((c: Dict) => (
            <ListRow key={`a${S(c.id)}`} icon={N(c.hidden) ? 'eye' : 'close'} title={`${N(c.hidden) ? '显示' : '隐藏'}「${S(c.title)}」`}
              subtitle="隐藏后不出现在记一笔的分类选择器里，已有账单不受影响"
              onClick={() => hide(c)} chevron={false} />
          ))}</div>
          {rows.some((c: Dict) => !!N(c.is_custom)) && (
            <div className="card" style={{ marginTop: 12 }}>{rows.filter((c: Dict) => !!N(c.is_custom)).map((c: Dict) => (
              <ListRow key={`d${S(c.id)}`} icon="trash" danger title={`删除「${S(c.title)}」`} subtitle="删除后已有账单仍保留原分类名称"
                onClick={() => setConfirm(c)} chevron={false} />
            ))}</div>
          )}
        </div>
      </div>
      <ConfirmDialog open={!!confirm} title={`删除分类「${S(confirm?.title)}」？`} message="内置分类不能删除；自定义分类删除后，已记账单不会改动。"
        onConfirm={del} onCancel={() => setConfirm(null)} />
    </div>
  );
}
