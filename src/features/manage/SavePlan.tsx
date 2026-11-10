/** 存钱计划：列表 / 归档 / 新建编辑 / 明细。金额全程整数分。 */
import { useEffect, useMemo, useState } from 'react';
import type { PageProps } from '../../app/App';
import { useNav } from '../../app/navigation';
import { useRefresh } from '../../app/useRefresh';
import { useToast } from '../../app/useToast';
import { Icon } from '../../components/Icon';
import {
  TopBar, GlassCard, SectionTitle, ListRow, EmptyState, BottomSheet, ConfirmDialog, formatMoney,
} from '../../components/ui';
import { useAsync } from '../common/hooks';
import { api, type Dict } from '../common/api';
import { N, S, toCents, centsToYuan } from '../common/format';
import { pct, safeIcon } from './common';
import './manage.css';

const FALLBACK_TYPES: Dict[] = [
  { id: 1, name: '目标计划', key: 'goal' },
  { id: 2, name: '定额储蓄', key: 'fixed' },
  { id: 3, name: '自由储蓄', key: 'free' },
];
const FALLBACK_WISHES = ['旅行', '买电脑', '换新手机', '应急基金'];

function planName(row: Dict): string {
  return S(row.name) || '未命名计划';
}
function targetOf(row: Dict): number {
  return N(row.target_money, N(row.targetMoney));
}
function savedOf(row: Dict): number {
  return N(row.saved_money, N(row.savedMoney));
}

export function SavePlanList(_: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const [tab, setTab] = useState<'active' | 'file'>('active');
  const [typeId, setTypeId] = useState(0);
  const { data, loading, error, reload } = useAsync(async () => {
    const [plans, archived, types] = await Promise.all([
      api.savePlans(), api.fileSavePlans(), api.planTypes(),
    ]);
    return {
      plans: Array.isArray(plans) ? plans : [],
      archived: Array.isArray(archived) ? archived : [],
      types: Array.isArray(types) && types.length ? types : FALLBACK_TYPES,
    };
  }, [refresh.version]);
  const rows = useMemo(() => {
    const src = tab === 'file' ? (data?.archived || []) : (data?.plans || []);
    return src.filter((r) => !typeId || N(r.type_id, N(r.type)) === typeId);
  }, [data, tab, typeId]);
  const types = data?.types || FALLBACK_TYPES;

  async function archive(row: Dict, status: number) {
    try {
      await api.updateSavePlanStatus({ ids: [S(row.id)], status, archived: status === 2 ? 1 : 0 });
      toast.show(status === 2 ? '已放入归档' : '已恢复');
      reload(); refresh.bump();
    } catch (e) { toast.show(`操作失败：${e instanceof Error ? e.message : String(e)}`); }
  }

  return (
    <div className="page">
      <TopBar title="存钱计划" subtitle="按目标存，达成后可归档" onBack={nav.pop}
        right={<button className="icon-btn" aria-label="新建计划" onClick={() => nav.push('SavePlanEdit', { mode: 'create' })}><Icon name="plus" /></button>} />
      <div className="page-scroll">
        <div className="page-pad">
          <div className="row" style={{ gap: 8 }}>
            {([['active', '进行中'], ['file', '归档']] as const).map(([k, label]) => (
              <button key={k} className={`chip ${tab === k ? 'selected' : ''}`} onClick={() => setTab(k)}>{label}</button>
            ))}
          </div>
          <div className="row" style={{ gap: 8, marginTop: 10, flexWrap: 'wrap' }}>
            <button className={`chip ${typeId === 0 ? 'selected' : ''}`} onClick={() => setTypeId(0)}>全部类型</button>
            {types.map((t) => (
              <button key={S(t.id)} className={`chip ${typeId === N(t.id) ? 'selected' : ''}`} onClick={() => setTypeId(N(t.id))}>{S(t.name)}</button>
            ))}
          </div>
          {loading && <div className="t-body-medium t-secondary" style={{ marginTop: 16 }}>加载中…</div>}
          {error && (
            <div className="card" style={{ marginTop: 12 }}>
              <EmptyState icon="warning" title="计划加载失败" hint={error} actionLabel="重试" onAction={reload} />
            </div>
          )}
          {!loading && !error && rows.length === 0 && (
            <div className="card" style={{ marginTop: 12 }}>
              <EmptyState icon="star" title={tab === 'file' ? '还没有归档计划' : '还没有存钱计划'}
                hint="给一个目标，每次存一笔都看得见进度" actionLabel="新建计划" onAction={() => nav.push('SavePlanEdit', { mode: 'create' })} />
            </div>
          )}
          {rows.length > 0 && <SectionTitle>共 {rows.length} 个</SectionTitle>}
          {rows.map((row) => {
            const target = targetOf(row);
            const saved = savedOf(row);
            const p = target > 0 ? pct(saved, target) : 0;
            return (
              <GlassCard key={S(row.id)} onClick={() => nav.push('SavePlanDetail', { planId: S(row.id) })}>
                <div className="row-between">
                  <div className="row" style={{ gap: 10 }}>
                    <span className="manage-icon"><Icon name={safeIcon(S(row.icon), 'star')} size={18} /></span>
                    <div>
                      <div className="t-body-large t-semi">{planName(row)}</div>
                      <div className="t-label-medium t-secondary">{S(row.desc) || '点击查看明细'}</div>
                    </div>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <div className="t-body-medium t-semi">¥ {formatMoney(saved)}</div>
                    <div className="t-label-medium t-secondary">/ ¥ {formatMoney(target)}</div>
                  </div>
                </div>
                <div className="manage-progress" style={{ marginTop: 10 }}><div className="manage-bar" style={{ width: `${Math.min(100, p)}%` }} /></div>
                <div className="row-between" style={{ marginTop: 8 }}>
                  <span className="t-label-medium t-secondary">{p}%</span>
                  <button className="btn btn-ghost" style={{ padding: '4px 10px' }} onClick={(e) => { e.stopPropagation(); void archive(row, tab === 'file' ? 1 : 2); }}>
                    {tab === 'file' ? '恢复' : '归档'}
                  </button>
                </div>
              </GlassCard>
            );
          })}
        </div>
      </div>
    </div>
  );
}

export function SavePlanEdit({ route }: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const planId = S(route.params?.planId);
  const editing = Boolean(planId);
  const { data } = useAsync(async () => {
    const [types, icons, wishes, plans] = await Promise.all([
      api.planTypes(), api.planIcons(), api.wishes(), editing ? api.savePlans() : Promise.resolve([] as Dict[]),
    ]);
    return {
      types: Array.isArray(types) && types.length ? types : FALLBACK_TYPES,
      icons: Array.isArray(icons) ? icons : [],
      wishes: Array.isArray(wishes) && wishes.length ? wishes : FALLBACK_WISHES,
      row: (Array.isArray(plans) ? plans : []).find((r) => S(r.id) === planId) || null,
    };
  }, [planId]);
  const row = data?.row || null;
  const [name, setName] = useState('');
  const [typeId, setTypeId] = useState(1);
  const [icon, setIcon] = useState('star');
  const [money, setMoney] = useState('');
  const [desc, setDesc] = useState('');
  const [ready, setReady] = useState(!editing);
  useEffect(() => {
    if (!editing) return;
    if (!row) return;
    setName(planName(row));
    setTypeId(N(row.type_id, N(row.type, 1)));
    setIcon(S(row.icon) || 'star');
    setMoney(centsToYuan(targetOf(row)));
    setDesc(S(row.desc));
    setReady(true);
  }, [editing, row]);

  async function save() {
    const n = name.trim();
    if (!n) return toast.show('请填写计划名称');
    const cents = toCents(money);
    if (cents === null || cents <= 0) return toast.show('目标金额要大于 0');
    const body: Dict = { name: n, icon, typeId, targetMoney: cents, desc: desc.trim() };
    try {
      if (editing) await api.updateSavePlan({ ...body, id: planId });
      else await api.createSavePlan(body);
      toast.show(editing ? '计划已更新' : '计划已创建');
      refresh.bump();
      nav.pop();
    } catch (e) { toast.show(`保存失败：${e instanceof Error ? e.message : String(e)}`); }
  }

  const icons = (data?.icons || []).map((r) => S(r.icon) || 'star');
  const iconChoices = icons.length ? [...new Set(icons)] : ['star', 'heart', 'gift', 'coin', 'wallet'];

  return (
    <div className="page">
      <TopBar title={editing ? '编辑计划' : '新建存钱计划'} onBack={nav.pop} />
      <div className="page-scroll">
        <div className="page-pad">
          {editing && !ready && <div className="t-body-medium t-secondary">读取中…</div>}
          <SectionTitle>名称</SectionTitle>
          <input className="field" value={name} onChange={(e) => setName(e.target.value)} placeholder="例如 旅行基金" />
          <div className="row" style={{ gap: 8, marginTop: 10, flexWrap: 'wrap' }}>
            {(data?.wishes || FALLBACK_WISHES).map((w) => (
              <button key={w} className="chip" onClick={() => setName(w)}>{w}</button>
            ))}
          </div>
          <SectionTitle>类型</SectionTitle>
          <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
            {(data?.types || FALLBACK_TYPES).map((t) => (
              <button key={S(t.id)} className={`chip ${typeId === N(t.id) ? 'selected' : ''}`} onClick={() => setTypeId(N(t.id))}>{S(t.name)}</button>
            ))}
          </div>
          <SectionTitle>图标</SectionTitle>
          <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
            {iconChoices.map((ic) => (
              <button key={ic} className={`manage-tile ${icon === ic ? 'selected' : ''}`} onClick={() => setIcon(ic)} aria-label={ic}>
                <Icon name={safeIcon(ic, 'star')} size={18} />
              </button>
            ))}
          </div>
          <SectionTitle>目标金额（元）</SectionTitle>
          <input className="field" inputMode="decimal" value={money} onChange={(e) => setMoney(e.target.value)} placeholder="0.00" />
          <SectionTitle>备注</SectionTitle>
          <input className="field" value={desc} onChange={(e) => setDesc(e.target.value)} placeholder="可选" />
          <button className="btn btn-primary" style={{ marginTop: 18, width: '100%' }} onClick={save}>保存</button>
        </div>
      </div>
    </div>
  );
}

export function SavePlanDetail({ route }: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const planId = S(route.params?.planId);
  const { data, loading, error, reload } = useAsync(() => api.planItems(planId), [planId, refresh.version]);
  const plan = (data?.plan as Dict) || null;
  const items = (data?.items as Dict[]) || [];
  const [sheet, setSheet] = useState(false);
  const [money, setMoney] = useState('');
  const [remark, setRemark] = useState('');
  const [confirm, setConfirm] = useState(false);

  async function addItem() {
    const cents = toCents(money);
    if (cents === null || cents <= 0) return toast.show('请输入本次存入金额');
    try {
      await api.createPlanItem({ planId, money: cents, num: items.length + 1, remark: remark.trim() });
      toast.show('已记一笔存入');
      setSheet(false); setMoney(''); setRemark('');
      reload(); refresh.bump();
    } catch (e) { toast.show(`保存失败：${e instanceof Error ? e.message : String(e)}`); }
  }
  async function archive() {
    setConfirm(false);
    try {
      await api.updateSavePlanStatus({ ids: [planId], status: 2, archived: 1 });
      toast.show('已归档');
      refresh.bump(); nav.pop();
    } catch (e) { toast.show(`归档失败：${e instanceof Error ? e.message : String(e)}`); }
  }

  const target = plan ? targetOf(plan) : 0;
  const saved = plan ? savedOf(plan) : 0;
  const p = target > 0 ? pct(saved, target) : 0;

  return (
    <div className="page">
      <TopBar title={plan ? planName(plan) : '计划详情'} onBack={nav.pop}
        right={<button className="icon-btn" aria-label="编辑" onClick={() => nav.push('SavePlanEdit', { planId })}><Icon name="edit" /></button>} />
      <div className="page-scroll">
        <div className="page-pad">
          {loading && <div className="t-body-medium t-secondary">加载中…</div>}
          {error && <div className="card"><EmptyState icon="warning" title="加载失败" hint={error} actionLabel="重试" onAction={reload} /></div>}
          {plan && (
            <GlassCard>
              <div className="t-label-medium t-secondary">已存 / 目标</div>
              <div className="t-title-large" style={{ marginTop: 4 }}>¥ {formatMoney(saved)}</div>
              <div className="t-body-medium t-secondary">目标 ¥ {formatMoney(target)} · {p}%</div>
              <div className="manage-progress" style={{ marginTop: 10 }}><div className={`manage-bar ${p >= 100 ? '' : ''}`} style={{ width: `${Math.min(100, p)}%` }} /></div>
              {S(plan.desc) && <div className="t-body-medium manage-note">{S(plan.desc)}</div>}
            </GlassCard>
          )}
          <SectionTitle action={<button onClick={() => setSheet(true)}>存一笔</button>}>存入明细</SectionTitle>
          {items.length === 0 ? (
            <div className="card"><EmptyState icon="coin" title="还没有存入" hint="第一笔可以很小" actionLabel="存一笔" onAction={() => setSheet(true)} /></div>
          ) : (
            <div className="card">{items.map((it, i) => (
              <ListRow key={S(it.id, String(i))} icon="coin" title={`¥ ${formatMoney(N(it.money))}`} subtitle={S(it.remark) || `第 ${N(it.num, i + 1)} 期`} chevron={false} />
            ))}</div>
          )}
          <SectionTitle>其他</SectionTitle>
          <div className="card">
            <ListRow icon="folder" title="归档此计划" subtitle="归档后仍可在列表里恢复" danger onClick={() => setConfirm(true)} chevron={false} />
          </div>
        </div>
      </div>
      <BottomSheet open={sheet} title="存一笔" onClose={() => setSheet(false)}>
        <div className="t-label-medium t-secondary">金额（元）</div>
        <input className="field" style={{ marginTop: 8 }} inputMode="decimal" value={money} onChange={(e) => setMoney(e.target.value)} placeholder="0.00" />
        <div className="t-label-medium t-secondary" style={{ marginTop: 12 }}>备注</div>
        <input className="field" style={{ marginTop: 8 }} value={remark} onChange={(e) => setRemark(e.target.value)} placeholder="可选" />
        <div className="row" style={{ gap: 10, marginTop: 18 }}>
          <button className="btn btn-ghost" onClick={() => setSheet(false)}>取消</button>
          <button className="btn btn-primary grow" onClick={addItem}>保存</button>
        </div>
      </BottomSheet>
      <ConfirmDialog open={confirm} title="归档这个计划？" message="归档不会删除已存入的明细，可以随时恢复。" confirmLabel="归档" onConfirm={archive} onCancel={() => setConfirm(false)} />
    </div>
  );
}

