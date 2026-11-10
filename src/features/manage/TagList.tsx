/** 标签管理：样本 MemberTag 只有 Create/Update/Delete/UpdateSort，列表从本地表等价读取。 */
import { useEffect, useState } from 'react';
import type { PageProps } from '../../app/App';
import { useNav } from '../../app/navigation';
import { useRefresh } from '../../app/useRefresh';
import { useToast } from '../../app/useToast';
import { Icon } from '../../components/Icon';
import { TopBar, GlassCard, SectionTitle, ListRow, EmptyState, BottomSheet, ConfirmDialog } from '../../components/ui';
import { useAsync } from '../common/hooks';
import { api, local, type Dict } from '../common/api';
import { N, S } from '../common/format';
import './manage.css';

export function TagList(_: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const { data, reload } = useAsync(() => local.tags(), [refresh.version]);
  const [rows, setRows] = useState<Dict[]>([]);
  const [sheet, setSheet] = useState(false);
  const [name, setName] = useState('');
  const [editing, setEditing] = useState<Dict | null>(null);
  const [confirm, setConfirm] = useState<Dict | null>(null);
  useEffect(() => { if (data) setRows([...data].sort((a, b) => N(a.sort) - N(b.sort))); }, [data]);
  function openNew() { setEditing(null); setName(''); setSheet(true); }
  function openEdit(t: Dict) { setEditing(t); setName(S(t.name)); setSheet(true); }
  async function save() {
    const n = name.trim(); if (!n) return toast.show('请输入标签名称');
    if (rows.some((r) => S(r.name) === n && S(r.tag_id) !== S(editing?.tag_id))) return toast.show('已有同名标签');
    try {
      if (editing) await api.tagUpdate(N(editing.tag_id), n); else await api.tagCreate(1, n);
      toast.show(editing ? '标签已更新' : '标签已创建'); setSheet(false); reload(); refresh.bump();
    } catch (e) { toast.show(`保存失败：${e instanceof Error ? e.message : String(e)}`); }
  }
  async function move(i: number, delta: number) {
    const j = i + delta; if (j < 0 || j >= rows.length) return;
    const next = [...rows]; [next[i], next[j]] = [next[j], next[i]];
    const items = next.map((r, k) => ({ tagId: N(r.tag_id), sort: k + 1 }));
    setRows(next.map((r, k) => ({ ...r, sort: k + 1 })));
    try { await api.tagSort(items); toast.show('排序已保存'); }
    catch (e) { toast.show(`排序失败：${e instanceof Error ? e.message : String(e)}`); }
  }
  async function del() {
    const t = confirm; setConfirm(null); if (!t) return;
    try { await api.tagDelete(N(t.tag_id)); toast.show('标签已删除'); reload(); refresh.bump(); }
    catch (e) { toast.show(`删除失败：${e instanceof Error ? e.message : String(e)}`); }
  }
  return (
    <div className="page">
      <TopBar title="标签管理" subtitle="给账单打标签，按使用习惯排序" onBack={nav.pop}
        right={<button className="icon-btn" aria-label="新建标签" onClick={openNew}><Icon name="plus" /></button>} />
      <div className="page-scroll">
        <div className="page-pad">
          <GlassCard><div className="t-body-medium t-semi">共 {rows.length} 个标签</div><div className="t-label-medium t-secondary manage-note">标签只作用于账本内的账单；删除标签不会删除账单本身。</div></GlassCard>
          <SectionTitle>全部标签</SectionTitle>
          {rows.length === 0 ? <div className="card"><EmptyState icon="tag" title="还没有标签" hint="例如「报销」「旅行」「必要支出」" actionLabel="新建标签" onAction={openNew} /></div> : (
            <div className="card">{rows.map((t, i) => (
              <ListRow key={S(t.tag_id)} icon="tag" title={S(t.name)} subtitle={`排序 ${N(t.sort, i + 1)} · 账本 #${N(t.book_id, 1)}`}
                right={<span className="row" style={{ gap: 4 }}>
                  <button className="icon-btn" aria-label="上移" disabled={i === 0} onClick={() => move(i, -1)}><Icon name="arrow" size={16} /></button>
                  <button className="icon-btn" aria-label="下移" disabled={i === rows.length - 1} onClick={() => move(i, 1)}><Icon name="chevron" size={16} /></button>
                </span>} chevron={false} onClick={() => openEdit(t)} />
            ))}</div>
          )}
          {rows.length > 0 && <SectionTitle>编辑与删除</SectionTitle>}
          {rows.length > 0 && <div className="card">{rows.map((t) => (
            <ListRow key={`x${S(t.tag_id)}`} icon="trash" danger title={`删除「${S(t.name)}」`} subtitle="已有账单保留，只是不再带此标签" onClick={() => setConfirm(t)} chevron={false} />
          ))}</div>}
        </div>
      </div>
      <BottomSheet open={sheet} title={editing ? '编辑标签' : '新建标签'} onClose={() => setSheet(false)}>
        <div className="t-label-medium t-secondary">标签名称</div>
        <input className="field" style={{ marginTop: 8 }} value={name} onChange={(e) => setName(e.target.value)} placeholder="例如 报销" />
        <div className="row" style={{ gap: 10, marginTop: 18 }}><button className="btn btn-ghost" onClick={() => setSheet(false)}>取消</button><button className="btn btn-primary grow" onClick={save}>保存</button></div>
      </BottomSheet>
      <ConfirmDialog open={!!confirm} title={`删除标签「${S(confirm?.name)}」？`} message="已有账单不会被删除。" onConfirm={del} onCancel={() => setConfirm(null)} />
    </div>
  );
}
