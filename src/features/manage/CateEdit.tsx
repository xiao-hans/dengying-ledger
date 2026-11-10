/** 分类新建 / 编辑：名称、类型、图标。 */
import { useEffect, useState } from 'react';
import type { PageProps } from '../../app/App';
import { useNav } from '../../app/navigation';
import { useRefresh } from '../../app/useRefresh';
import { useToast } from '../../app/useToast';
import { TopBar, GlassCard, SectionTitle, ChipRow } from '../../components/ui';
import { useAsync } from '../common/hooks';
import { api } from '../common/api';
import { N, S } from '../common/format';
import { safeIcon } from './common';
import './manage.css';

export function CateEdit({ route }: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const id = N(route.params?.cateId);
  const [title, setTitle] = useState(S(route.params?.title));
  const [type, setType] = useState<1 | 2>(N(route.params?.type, 1) === 2 ? 2 : 1);
  const [icon, setIcon] = useState(S(route.params?.icon, 'grid'));
  const { data } = useAsync(async () => {
    const [cates, icons] = await Promise.all([api.mainCates(), api.cateIcons()]);
    return { cates: [...cates.expense, ...cates.income], icons };
  });
  const row = (data?.cates || []).find((c) => N(c.id) === id);
  useEffect(() => {
    if (!row) return;
    setTitle(S(row.title));
    setType(N(row.type, 1) === 2 ? 2 : 1);
    setIcon(safeIcon(row.icon ?? row.base_icon));
  }, [row]);
  const FALLBACK_ICONS = [
    { id: 'grid', name: '通用', icon: 'grid' }, { id: 'wallet', name: '钱包', icon: 'wallet' },
    { id: 'book', name: '账簿', icon: 'book' }, { id: 'star', name: '心愿', icon: 'star' },
    { id: 'heart', name: '健康', icon: 'heart' }, { id: 'coin', name: '收入', icon: 'coin' },
    { id: 'pet', name: '宠物', icon: 'pet' }, { id: 'note', name: '记录', icon: 'note' },
    { id: 'image', name: '票据', icon: 'image' },
  ];
  const iconList = (data?.icons || []).length ? data!.icons : FALLBACK_ICONS;

  async function save() {
    if (!title.trim()) return toast.show('请输入分类名称');
    try {
      await api.saveCate({ title: title.trim(), type, iconId: icon, ...(id ? { id, cateId: id } : {}) }, !!id);
      toast.show(id ? '分类已更新' : '分类已创建'); refresh.bump(); nav.pop();
    } catch (e) { toast.show(`保存失败：${e instanceof Error ? e.message : String(e)}`); }
  }
  return (
    <div className="page">
      <TopBar title={id ? '编辑分类' : '新建分类'} onBack={nav.pop} />
      <div className="page-scroll">
        <div className="page-pad">
          <GlassCard>
            <div className="t-label-medium t-secondary">分类名称</div>
            <input className="field" style={{ marginTop: 8 }} value={title} onChange={(e) => setTitle(e.target.value)} placeholder="例如 咖啡 / 通勤 / 兼职" />
            <div className="t-label-medium t-secondary" style={{ margin: '16px 0 8px' }}>账单类型</div>
            <ChipRow value={type} onChange={(v) => setType(v as 1 | 2)} items={[{ key: 1, label: '支出' }, { key: 2, label: '收入' }]} />
          </GlassCard>
          <SectionTitle>选择图标</SectionTitle>
          <div className="card card-pad">
            <div className="grid-3" style={{ gap: 8 }}>{iconList.map((it) => {
              const name = safeIcon(it.icon ?? it.name ?? it.id);
              return <button key={S(it.id ?? name)} className={`manage-tile ${icon === name ? 'selected' : ''}`} onClick={() => setIcon(name)}>{S(it.name ?? name)}</button>;
            })}</div>
          </div>
          <div className="t-label-medium t-secondary manage-note">图标使用内联 SVG，随浅色 / 深色主题自动换色；不复制原 App 资源。</div>
        </div>
      </div>
      <div className="page-fixed-bottom"><div className="row" style={{ gap: 10 }}><button className="btn btn-ghost" onClick={nav.pop}>取消</button><button className="btn btn-primary grow" onClick={save}>保存分类</button></div></div>
    </div>
  );
}
