/** 账本列表：多账本 / 邀请码 / 成员 / 月起始日。 */
import { useState } from 'react';
import type { PageProps } from '../../app/App';
import { useNav } from '../../app/navigation';
import { useRefresh } from '../../app/useRefresh';
import { useToast } from '../../app/useToast';
import { Icon } from '../../components/Icon';
import { TopBar, GlassCard, SectionTitle, ListRow, EmptyState, BottomSheet, ConfirmDialog } from '../../components/ui';
import { useAsync } from '../common/hooks';
import { api, type Dict } from '../common/api';
import { N, S } from '../common/format';
import './manage.css';

export function BookList(_: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const { data, reload } = useAsync(() => api.bookList(), [refresh.version]);
  const [join, setJoin] = useState(false);
  const [code, setCode] = useState('');
  const [confirm, setConfirm] = useState<Dict | null>(null);
  async function joinBook() {
    const c = code.trim(); if (!c) return toast.show('请输入邀请码');
    try { const info = await api.bookInfoByCode(c); const id = N(info.id); if (!id) return toast.show('邀请码无效'); await api.bookJoin(id); toast.show('已加入共享账本'); setJoin(false); reload(); refresh.bump(); }
    catch (e) { toast.show(`加入失败：${e instanceof Error ? e.message : String(e)}`); }
  }
  async function del() {
    const b = confirm; setConfirm(null); if (!b) return;
    try { await api.bookDelete(N(b.id)); toast.show('账本已删除'); reload(); refresh.bump(); }
    catch (e) { toast.show(`删除失败：${e instanceof Error ? e.message : String(e)}`); }
  }
  const books = data || [];
  return (
    <div className="page">
      <TopBar title="账本管理" subtitle="多账本 / 共享账本 / 月起始日" onBack={nav.pop}
        right={<button className="icon-btn" aria-label="新建账本" onClick={() => nav.push('BookEdit')}><Icon name="plus" /></button>} />
      <div className="page-scroll">
        <div className="page-pad">
          <GlassCard strong><div className="t-body-large t-semi">共 {books.length} 本账</div><div className="t-label-medium t-secondary" style={{ marginTop: 6, lineHeight: 1.8 }}>默认账本「日常账本」不可删除；月起始日影响每月统计区间。</div></GlassCard>
          <SectionTitle action={<button onClick={() => setJoin(true)}>加入账本 ›</button>}>我的账本</SectionTitle>
          {books.length === 0 ? <div className="card"><EmptyState icon="book" title="还没有账本" hint="新建一本账本开始记账" actionLabel="新建账本" onAction={() => nav.push('BookEdit')} /></div> : (
            <div className="card">{books.map((b: Dict) => (
              <ListRow key={S(b.id)} icon="book" title={<span className="row" style={{ gap: 8 }}>{S(b.name)} {N(b.id) === 1 ? <span className="badge">默认</span> : null}</span>}
                subtitle={`${N(b.member_count, 1)} 位成员 · 每月 ${N(b.month_start_date, 1)} 号起始 · ${S(b.role, 'owner') === 'owner' ? '我创建' : '我加入'}`}
                onClick={() => nav.push('BookDetail', { bookId: N(b.id) })} />
            ))}</div>
          )}
          <SectionTitle>更多</SectionTitle>
          <div className="card">
            <ListRow icon="users" title="账本成员" subtitle="查看成员与移除权限" onClick={() => nav.push('BookMemberList', { bookId: 1 })} />
            <ListRow icon="trash" title="删除自定义账本" subtitle="默认账本保留，避免误删全部账单" onClick={() => {
              const b = books.find((x: Dict) => N(x.id) !== 1); if (!b) return toast.show('没有可删除的自定义账本'); setConfirm(b);
            }} chevron={false} />
          </div>
        </div>
      </div>
      <BottomSheet open={join} title="加入共享账本" onClose={() => setJoin(false)}>
        <div className="t-label-medium t-secondary">输入对方分享的邀请码</div>
        <input className="field" style={{ marginTop: 8 }} value={code} onChange={(e) => setCode(e.target.value)} placeholder="例如 DY1ABC23" />
        <div className="t-label-medium t-secondary manage-note">本地版不做真实远程协作，加入后仅在设备上生成一本共享账本副本。</div>
        <button className="btn btn-primary" style={{ width: '100%', marginTop: 12 }} onClick={joinBook}>加入账本</button>
      </BottomSheet>
      <ConfirmDialog open={!!confirm} title={`删除账本「${S(confirm?.name)}」？`} message="账本内账单不会自动删除，但将不再出现在账本列表里。" onConfirm={del} onCancel={() => setConfirm(null)} />
    </div>
  );
}
