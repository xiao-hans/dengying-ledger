/** 账本新建 / 编辑。 */
import { useEffect, useState } from 'react';
import type { PageProps } from '../../app/App';
import { useNav } from '../../app/navigation';
import { useRefresh } from '../../app/useRefresh';
import { useToast } from '../../app/useToast';
import { TopBar, GlassCard, SectionTitle } from '../../components/ui';
import { useAsync } from '../common/hooks';
import { api } from '../common/api';
import { N, S } from '../common/format';
import { safeIcon } from './common';
import './manage.css';

const FALLBACK = [
  { id: 1, name: '账簿', icon: 'book' }, { id: 2, name: '钱包', icon: 'wallet' },
  { id: 3, name: '餐饮', icon: 'wallet' }, { id: 4, name: '旅行', icon: 'star' },
  { id: 5, name: '家庭', icon: 'users' }, { id: 6, name: '心愿', icon: 'heart' },
];

export function BookEdit({ route }: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const id = N(route.params?.bookId);
  const [name, setName] = useState(S(route.params?.name, ''));
  const [iconId, setIconId] = useState(N(route.params?.iconId, 1));
  const [start, setStart] = useState(N(route.params?.monthStartDate, 1));
  const { data } = useAsync(async () => ({ books: await api.bookList(), icons: await api.bookIcons() }));
  const row = (data?.books || []).find((b) => N(b.id) === id);
  useEffect(() => {
    if (!row) return;
    setName(S(row.name));
    setIconId(N(row.icon_id, 1));
    setStart(N(row.month_start_date, 1));
  }, [row]);
  const icons = (data?.icons || []).length ? data!.icons : FALLBACK;
  async function save() {
    if (!name.trim()) return toast.show('请输入账本名称');
    try {
      if (id) await api.bookUpdate({ billsBookId: id, name: name.trim(), iconId, monthStartDate: start });
      else await api.bookCreate({ name: name.trim(), iconId, monthStartDate: start });
      toast.show(id ? '账本已更新' : '账本已创建'); refresh.bump(); nav.pop();
    } catch (e) { toast.show(`保存失败：${e instanceof Error ? e.message : String(e)}`); }
  }
  return (
    <div className="page">
      <TopBar title={id ? '编辑账本' : '新建账本'} onBack={nav.pop} />
      <div className="page-scroll">
        <div className="page-pad">
          <GlassCard>
            <div className="t-label-medium t-secondary">账本名称</div>
            <input className="field" style={{ marginTop: 8 }} value={name} onChange={(e) => setName(e.target.value)} placeholder="例如 日常 / 旅行 / 家庭" />
            <div className="t-label-medium t-secondary" style={{ margin: '16px 0 8px' }}>每月起始日</div>
            <select className="field" value={start} onChange={(e) => setStart(N(e.target.value, 1))} aria-label="每月起始日">
              {Array.from({ length: 28 }, (_, i) => i + 1).map((d) => <option key={d} value={d}>每月 {d} 号</option>)}
            </select>
            <div className="t-label-medium t-secondary manage-note">月起始日用于月度报表与预算区间；已有账单的日期不会被改写。</div>
          </GlassCard>
          <SectionTitle>账本图标</SectionTitle>
          <div className="card card-pad"><div className="grid-3" style={{ gap: 8 }}>{icons.map((it) => {
            const key = N(it.id); const ic = safeIcon(it.icon);
            return <button key={key} className={`manage-tile ${iconId === key ? 'selected' : ''}`} onClick={() => setIconId(key)}>{S(it.name ?? ic)}</button>;
          })}</div></div>
        </div>
      </div>
      <div className="page-fixed-bottom"><div className="row" style={{ gap: 10 }}><button className="btn btn-ghost" onClick={nav.pop}>取消</button><button className="btn btn-primary grow" onClick={save}>保存账本</button></div></div>
    </div>
  );
}
