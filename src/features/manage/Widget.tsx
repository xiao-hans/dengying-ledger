/** 桌面小组件的 Web 等价：可配置预览，不声称系统桌面挂件。数据只写本地 widget_* 表。 */
import { useEffect, useState } from 'react';
import type { PageProps } from '../../app/App';
import { useNav } from '../../app/navigation';
import { useRefresh } from '../../app/useRefresh';
import { useToast } from '../../app/useToast';
import { Icon } from '../../components/Icon';
import { TopBar, GlassCard, SectionTitle, ListRow, EmptyState, ConfirmDialog } from '../../components/ui';
import { useAsync } from '../common/hooks';
import { type Dict } from '../common/api';
import { all, get, put, remove as dbRemove } from '../../db/client';
import { N, S } from '../common/format';
import './manage.css';

const SIZES = [
  { id: 'small', name: '小' },
  { id: 'medium', name: '中' },
  { id: 'large', name: '大' },
];
const KINDS = [
  { id: 'month', name: '本月收支' },
  { id: 'budget', name: '预算剩余' },
  { id: 'plan', name: '存钱进度' },
];

function newId(): string {
  return `w_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 6)}`;
}

export function WidgetHome(_: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const { data, loading, error, reload } = useAsync(() => all<Dict>('widget_user'), [refresh.version]);
  const rows = data || [];
  const [pending, setPending] = useState<Dict | null>(null);

  async function remove() {
    const row = pending;
    setPending(null);
    if (!row) return;
    try {
      await dbRemove('widget_user', S(row.id));
      toast.show('已移除');
      reload(); refresh.bump();
    } catch (e) { toast.show(`移除失败：${e instanceof Error ? e.message : String(e)}`); }
  }

  return (
    <div className="page">
      <TopBar title="小组件" subtitle="网页里预览，不能挂到系统桌面" onBack={nav.pop}
        right={<button className="icon-btn" aria-label="添加组件" onClick={() => nav.push('WidgetEdit')}><Icon name="plus" /></button>} />
      <div className="page-scroll">
        <div className="page-pad">
          {loading && <div className="t-body-medium t-secondary">加载中…</div>}
          {error && <div className="card"><EmptyState icon="warning" title="加载失败" hint={error} actionLabel="重试" onAction={reload} /></div>}
          {!loading && !error && rows.length === 0 && (
            <div className="card">
              <EmptyState icon="widget" title="还没有组件" hint="选一个尺寸，看看它在桌面上的样子" actionLabel="添加组件" onAction={() => nav.push('WidgetEdit')} />
            </div>
          )}
          {rows.map((row) => {
            const size = S(row.size) || 'medium';
            return (
              <GlassCard key={S(row.id)} onClick={() => nav.push('WidgetEdit', { id: S(row.id) })}>
                <div className={`widget-preview size-${size}`}>
                  <div className="t-label-medium t-secondary">{SIZES.find((s) => s.id === size)?.name || '中'} · {KINDS.find((k) => k.id === S(row.kind))?.name || '本月收支'}</div>
                  <div className="t-body-large t-semi" style={{ marginTop: 6 }}>{S(row.title) || '我的组件'}</div>
                  <div className="t-label-medium t-secondary">预览，数据随账本更新</div>
                </div>
                <div className="row" style={{ gap: 8, marginTop: 8 }}>
                  <button className="btn btn-ghost" style={{ padding: '4px 10px' }} onClick={(e) => { e.stopPropagation(); setPending(row); }}>移除</button>
                </div>
              </GlassCard>
            );
          })}
          <SectionTitle>说明</SectionTitle>
          <div className="card">
            <ListRow icon="info" title="VIP 权益位" subtitle="本复刻不接支付，组件尺寸全部可用" chevron={false} />
          </div>
          <p className="t-label-medium t-secondary manage-note">配置只存在本机。浏览器没有系统桌面挂件能力，这里只做外观和字段预览。</p>
        </div>
      </div>
      <ConfirmDialog open={Boolean(pending)} title="移除这个组件？" message="只删除本机配置，不影响账单。" danger confirmLabel="移除" onConfirm={remove} onCancel={() => setPending(null)} />
    </div>
  );
}

export function WidgetEdit({ route }: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const id = S(route.params?.id);
  const { data } = useAsync(async () => (id ? get<Dict>('widget_user', id) : null), [id]);
  const [title, setTitle] = useState('');
  const [size, setSize] = useState('medium');
  const [kind, setKind] = useState('month');
  useEffect(() => {
    if (!data) return;
    setTitle(S(data.title));
    setSize(S(data.size) || 'medium');
    setKind(S(data.kind) || 'month');
  }, [data]);

  async function save() {
    const row = {
      id: id || newId(),
      title: title.trim() || (KINDS.find((k) => k.id === kind)?.name || '组件'),
      size, kind, member_id: 0, sort: N(data?.sort),
    };
    try {
      await put('widget_user', row);
      toast.show('组件已保存');
      refresh.bump(); nav.pop();
    } catch (e) { toast.show(`保存失败：${e instanceof Error ? e.message : String(e)}`); }
  }

  return (
    <div className="page">
      <TopBar title={id ? '编辑组件' : '添加组件'} onBack={nav.pop} />
      <div className="page-scroll">
        <div className="page-pad">
          <div className={`widget-preview size-${size}`}>
            <div className="t-label-medium t-secondary">预览</div>
            <div className="t-title-large" style={{ marginTop: 4 }}>{title.trim() || '未命名'}</div>
          </div>
          <SectionTitle>名称</SectionTitle>
          <input className="field" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="可选" />
          <SectionTitle>尺寸</SectionTitle>
          <div className="row" style={{ gap: 8 }}>
            {SIZES.map((s) => (
              <button key={s.id} className={`chip ${size === s.id ? 'selected' : ''}`} onClick={() => setSize(s.id)}>{s.name}</button>
            ))}
          </div>
          <SectionTitle>内容</SectionTitle>
          <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
            {KINDS.map((k) => (
              <button key={k.id} className={`chip ${kind === k.id ? 'selected' : ''}`} onClick={() => setKind(k.id)}>{k.name}</button>
            ))}
          </div>
          <button className="btn btn-primary" style={{ marginTop: 18, width: '100%' }} onClick={save}>保存</button>
        </div>
      </div>
    </div>
  );
}
