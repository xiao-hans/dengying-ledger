/** MatchingRulesList / Search / Settings 三页合一：列表、搜索、新建与编辑规则。 */
import { useMemo, useState } from 'react';
import type { PageProps } from '../../../app/App';
import { useNav } from '../../../app/navigation';
import { useToast } from '../../../app/useToast';
import { Icon } from '../../../components/Icon';
import { TopBar, GlassCard, SectionTitle, ListRow, EmptyState, BottomSheet, ConfirmDialog, SearchPill, Badge } from '../../../components/ui';
import { useAsync } from '../../common/hooks';
import { api, type Dict } from '../../common/api';
import { N, S } from '../../common/format';
import '../auto.css';

export function RuleList({ route }: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const { data, reload } = useAsync(async () => {
    const [rules, cates] = await Promise.all([api.ruleList(), api.mainCates()]);
    return { rules, cates: [...(cates.expense || []), ...(cates.income || [])] };
  });
  const [q, setQ] = useState(S(route.params?.q));
  const [sheet, setSheet] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [cate, setCate] = useState(9);
  const [type, setType] = useState(1);
  const [editing, setEditing] = useState<Dict | null>(null);
  const [confirm, setConfirm] = useState<Dict | null>(null);
  const cmap = useMemo(() => new Map((data?.cates || []).map((c) => [N(c.cate_id ?? c.id), S(c.title)])), [data?.cates]);
  const shown = (data?.rules || []).filter((r) => !q.trim() || `${S(r.key_word)} ${cmap.get(N(r.member_cate_id))}`.toLowerCase().includes(q.trim().toLowerCase()));

  function openNew() { setEditing(null); setKeyword(''); setCate(9); setType(1); setSheet(true); }
  function openEdit(r: Dict) {
    setEditing(r); setKeyword(S(r.key_word)); setCate(N(r.member_cate_id, 9)); setType(N(r.type, 1)); setSheet(true);
  }
  async function save() {
    const kw = keyword.trim();
    if (!kw) return toast.show('请输入商户关键词');
    if ((data?.rules || []).some((r) => S(r.key_word).toLowerCase() === kw.toLowerCase() && S(r.id) !== S(editing?.id)))
      return toast.show('已有相同关键词的规则');
    try {
      await api.saveRule({ keyWord: kw, type, memberCateId: cate, billsBookId: 1, fundAccountId: 1, memberTagIds: editing ? S(editing.tag_ids) : '', ...(editing ? { id: N(editing.id) } : {}) }, !!editing);
      toast.show(editing ? '规则已更新' : '规则已创建'); setSheet(false); reload();
    } catch (e) { toast.show(`保存失败：${e instanceof Error ? e.message : String(e)}`); }
  }
  async function remove() {
    const r = confirm; setConfirm(null);
    if (!r) return;
    try { await api.deleteRule(N(r.id)); toast.show('规则已删除'); reload(); }
    catch (e) { toast.show(`删除失败：${e instanceof Error ? e.message : String(e)}`); }
  }

  return (
    <div className="page">
      <TopBar title="匹配规则" subtitle="商户关键词 → 分类 / 账户 / 标签" onBack={nav.pop}
        right={<button className="icon-btn" aria-label="新建规则" onClick={openNew}><Icon name="plus" /></button>} />
      <div className="page-scroll">
        <div className="page-pad">
          <div style={{ padding: '4px 0 12px' }}><SearchPill value={q} onChange={setQ} placeholder="搜索规则关键词 / 分类" /></div>
          <GlassCard>
            <div className="t-body-medium t-semi">规则如何生效</div>
            <div className="t-label-medium t-secondary" style={{ marginTop: 8, lineHeight: 1.8 }}>
              识别到商户文本后，按最长关键词匹配规则，自动带入分类与账户；未命中时默认进入「未分类」并由你在确认页选择。规则不会替你直接入账。
            </div>
          </GlassCard>
          <SectionTitle action={<button onClick={openNew}>新建 ›</button>}>全部规则（{shown.length}）</SectionTitle>
          {shown.length === 0 ? (
            <div className="card"><EmptyState icon="note" title={q ? '没有匹配的规则' : '还没有规则'} hint="例如「星巴克」→ 餐饮，" actionLabel={q ? '清空搜索' : '新建规则'} onAction={() => q ? setQ('') : openNew()} /></div>
          ) : shown.map((r) => (
            <div className="card" key={S(r.id)}>
              <ListRow icon="note" title={<span className="row" style={{ gap: 8 }}>{S(r.key_word)} <Badge tone="brand">{N(r.type) === 2 ? '收入' : '支出'}</Badge></span>}
                subtitle={`→ ${cmap.get(N(r.member_cate_id)) || '未分类'} · 账户 #${N(r.assets_id, 1)}${S(r.tag_ids) ? ` · 标签 ${S(r.tag_ids)}` : ''}`}
                onClick={() => openEdit(r)} />
              <div className="row" style={{ padding: '0 16px 12px', gap: 8 }}>
                <button className="btn btn-ghost" onClick={() => openEdit(r)}>编辑</button>
                <button className="btn btn-ghost t-danger" onClick={() => setConfirm(r)}>删除</button>
              </div>
            </div>
          ))}
        </div>
      </div>

      <BottomSheet open={sheet} title={editing ? '编辑匹配规则' : '新建匹配规则'} onClose={() => setSheet(false)}>
        <div className="t-label-medium t-secondary">商户关键词（最长匹配优先）</div>
        <input className="field" style={{ marginTop: 8 }} placeholder="例如 星巴克 / 地铁 / 工资" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
        <div className="t-label-medium t-secondary" style={{ margin: '14px 0 8px' }}>账单类型</div>
        <div className="row" style={{ gap: 8 }}>
          {([{ k: 1, t: '支出' }, { k: 2, t: '收入' }] as const).map((x) => (
            <button key={x.k} className={`chip ${type === x.k ? 'selected' : ''}`} onClick={() => setType(x.k)}>{x.t}</button>
          ))}
        </div>
        <div className="t-label-medium t-secondary" style={{ margin: '14px 0 8px' }}>归入分类</div>
        <div className="cate-picker">
          {(data?.cates || []).filter((c) => N(c.type, 1) === type).map((c) => (
            <button key={N(c.cate_id ?? c.id)} className={`pick-tile ${cate === N(c.cate_id ?? c.id) ? 'selected' : ''}`}
              onClick={() => setCate(N(c.cate_id ?? c.id))}>{S(c.title)}</button>
          ))}
        </div>
        <div className="row" style={{ gap: 10, marginTop: 18 }}>
          <button className="btn btn-ghost" onClick={() => setSheet(false)}>取消</button>
          <button className="btn btn-primary grow" onClick={save}>保存规则</button>
        </div>
      </BottomSheet>
      <ConfirmDialog open={!!confirm} title={`删除规则「${S(confirm?.key_word)}」？`} message="已入账的账单不受影响，之后的识别不再套用这条规则。"
        onConfirm={remove} onCancel={() => setConfirm(null)} />
    </div>
  );
}
