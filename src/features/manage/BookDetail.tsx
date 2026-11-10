/** 账本详情：邀请码、成员、编辑、退出。 */
import { useState } from 'react';
import type { PageProps } from '../../app/App';
import { useNav } from '../../app/navigation';
import { useRefresh } from '../../app/useRefresh';
import { useToast } from '../../app/useToast';
import { Icon } from '../../components/Icon';
import { TopBar, GlassCard, SectionTitle, ListRow, ConfirmDialog } from '../../components/ui';
import { useAsync } from '../common/hooks';
import { api } from '../common/api';
import { N, S } from '../common/format';
import './manage.css';

export function BookDetail({ route }: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const id = N(route.params?.bookId, 1);
  const { data, reload } = useAsync(() => api.bookList(), [refresh.version]);
  const book = (data || []).find((b) => N(b.id) === id);
  const [code, setCode] = useState('');
  const [exit, setExit] = useState(false);
  async function invite() {
    try { const c = await api.bookInviteCode(id); setCode(S(c)); toast.show('邀请码已生成'); }
    catch (e) { toast.show(`生成失败：${e instanceof Error ? e.message : String(e)}`); }
  }
  async function copy() {
    if (!code) return;
    try { await navigator.clipboard.writeText(code); toast.show('邀请码已复制'); }
    catch { toast.show(`请手动复制：${code}`); }
  }
  async function doExit() {
    setExit(false);
    try { await api.bookExit(id); toast.show('已退出该账本'); refresh.bump(); nav.pop(); }
    catch (e) { toast.show(`退出失败：${e instanceof Error ? e.message : String(e)}`); }
  }
  return (
    <div className="page">
      <TopBar title={S(book?.name, '账本详情')} onBack={nav.pop}
        right={<button className="icon-btn" aria-label="编辑账本" onClick={() => nav.push('BookEdit', { bookId: id })}><Icon name="edit" /></button>} />
      <div className="page-scroll">
        <div className="page-pad">
          <GlassCard strong>
            <div className="row"><span className="manage-icon"><Icon name="book" /></span><div className="grow"><div className="t-body-large t-semi">{S(book?.name, '账本')}</div><div className="t-label-medium t-secondary">{N(book?.member_count, 1)} 位成员 · {S(book?.role, 'owner') === 'owner' ? '我创建' : '我加入'}</div></div></div>
            <div className="divider" style={{ margin: '14px 0' }} />
            <div className="row-between"><span className="t-body-medium t-secondary">每月起始日</span><span className="t-body-medium t-bold">{N(book?.month_start_date, 1)} 号</span></div>
          </GlassCard>
          <SectionTitle>共享账本</SectionTitle>
          <div className="card card-pad">
            <div className="t-body-medium t-semi">邀请码</div>
            {code ? <div className="manage-code" style={{ marginTop: 10 }}>{code}</div> : <div className="t-label-medium t-secondary manage-note">生成后分享给他人；本地版不连接真实协作服务端。</div>}
            <div className="row" style={{ gap: 10, marginTop: 12 }}><button className="btn btn-ghost grow" onClick={invite}>{code ? '重新生成' : '生成邀请码'}</button><button className="btn btn-primary grow" disabled={!code} onClick={copy}>复制</button></div>
          </div>
          <SectionTitle>管理</SectionTitle>
          <div className="card">
            <ListRow icon="users" title="账本成员" subtitle="查看成员与移除权限" onClick={() => nav.push('BookMemberList', { bookId: id })} />
            <ListRow icon="bills" title="查看本账本账单" subtitle="按账本筛选账单列表" onClick={() => nav.push('BillList', { bookId: id })} />
            <ListRow icon="logout" title="退出账本" subtitle={id === 1 ? '默认账本不可退出' : '共享账本可退出'} danger={id !== 1} onClick={() => { if (id === 1) return toast.show('默认账本不可退出'); setExit(true); }} />
          </div>
          <div className="t-label-medium t-secondary manage-note">修改账本信息后，所有页面会自动刷新，不需要重新进入。</div>
          <button className="btn btn-ghost" style={{ width: '100%' }} onClick={reload}>刷新账本信息</button>
        </div>
      </div>
      <ConfirmDialog open={exit} title="退出这本账本？" message="账单数据仍保留在设备上，只是不再显示这本账本。" onConfirm={doExit} onCancel={() => setExit(false)} />
    </div>
  );
}
