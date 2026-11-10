/** 账本成员：本地 member_book 表等价展示，owner 不可移除。 */
import type { PageProps } from '../../app/App';
import { useNav } from '../../app/navigation';
import { useRefresh } from '../../app/useRefresh';
import { useToast } from '../../app/useToast';
import { TopBar, GlassCard, SectionTitle, ListRow, EmptyState, ConfirmDialog } from '../../components/ui';
import { useAsync } from '../common/hooks';
import { api } from '../common/api';
import { N, S } from '../common/format';
import { all } from '../../db/client';
import { useState } from 'react';
import './manage.css';

interface MemberRow { member_id: number; book_id: number; role: string }

export function BookMemberList({ route }: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const id = N(route.params?.bookId, 1);
  const { data, reload } = useAsync(async () => {
    const [books, links, members] = await Promise.all([
      api.bookList(), all<MemberRow>('member_book'), all<{ id: number; name: string }>('member'),
    ]);
    return { books, links: links.filter((r) => N(r.book_id) === id), members };
  }, [refresh.version, id]);
  const [confirm, setConfirm] = useState<MemberRow | null>(null);
  const book = (data?.books || []).find((b) => N(b.id) === id);
  async function remove() {
    const m = confirm; setConfirm(null); if (!m) return;
    try { await api.bookRemoveMember(id, N(m.member_id)); toast.show('成员已移除'); reload(); refresh.bump(); }
    catch (e) { toast.show(`移除失败：${e instanceof Error ? e.message : String(e)}`); }
  }
  const rows = data?.links || [];
  return (
    <div className="page">
      <TopBar title="账本成员" subtitle={S(book?.name, '账本')} onBack={nav.pop} />
      <div className="page-scroll">
        <div className="page-pad">
          <GlassCard><div className="t-body-medium t-semi">本地协作说明</div><div className="t-label-medium t-secondary manage-note">样本通过云端成员表协作；本地版把成员关系写入设备数据库，完整保留创建者 / 成员角色、加入与移除交互，不连接远程服务。</div></GlassCard>
          <SectionTitle>成员（{rows.length}）</SectionTitle>
          {rows.length === 0 ? <div className="card"><EmptyState icon="users" title="暂无成员记录" hint="加入共享账本后会出现在这里" /></div> : (
            <div className="card">{rows.map((m) => {
              const person = (data?.members || []).find((x) => N(x.id) === N(m.member_id));
              const owner = S(m.role) === 'owner' || N(m.member_id) === 0;
              return <ListRow key={`${m.member_id}-${m.book_id}`} icon={owner ? 'star' : 'users'} title={S(person?.name, `成员 #${N(m.member_id)}`)}
                subtitle={owner ? '创建者 · 不可移除' : `成员 · 编号 #${N(m.member_id)}`} right={owner ? '创建者' : '成员'}
                onClick={() => { if (!owner) setConfirm(m); else toast.show('创建者不能被移除'); }} chevron={!owner} />;
            })}</div>
          )}
          <SectionTitle>共享</SectionTitle>
          <div className="card"><ListRow icon="link" title="返回账本详情获取邀请码" onClick={() => nav.push('BookDetail', { bookId: id })} /></div>
        </div>
      </div>
      <ConfirmDialog open={!!confirm} title="移除这位成员？" message="本地账本里的成员关系会被删除，账单数据不受影响。" onConfirm={remove} onCancel={() => setConfirm(null)} />
    </div>
  );
}
