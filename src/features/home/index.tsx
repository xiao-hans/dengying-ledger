/**
 * 四个 Tab 首页：账单/统计已接真实数据（M4），自动记账/我的在后续里程碑接入。
 * 视觉全部走灯影设计 token 的玻璃组件（外部 UI 基准）与主题变量，没有硬编码颜色。
 */
import { Icon } from '../../components/Icon';
import { TopBar, SearchPill, ChipRow, GlassCard, EmptyState, SectionTitle, ListRow, Switch, formatMoney } from '../../components/ui';
import { useNav } from '../../app/navigation';
import { useToast } from '../../app/useToast';
import { useTheme } from '../../theme/ThemeProvider';
import type { PageProps } from '../../app/App';
import { useState, useMemo } from 'react';
import { api, type Dict } from '../common/api';
import { useAsync } from '../common/hooks';
import { get } from '../../db/client';
import { LOCAL_MEMBER_ID } from '../../db/schema';
import { useRefresh } from '../../app/useRefresh';
import { N, S, dayText, dayMonth, cateName, cateIcon, cateMap } from '../common/format';
import { summarize } from '../stats/stats';

const BrandHead = ({ title, right }: { title: string; right?: React.ReactNode }) => (
  <div className="brand-head">
    <span className="brand-mark"><Icon name="receipt" size={18} /></span>
    <span className="t-title-large brand-name grow">{title}</span>
    {right}
  </div>
);

/* ---------------- 账单首页（真实数据） ---------------- */
export function BillsHome(_: PageProps) {
  const nav = useNav();
  const [q, setQ] = useState('');
  const [filter, setFilter] = useState<'all' | 1 | 2 | 3>('all');
  const { data, loading, error, reload } = useAsync(async () => {
    const [page, cates] = await Promise.all([api.billsPage(1), api.mainCates()]);
    return {
      rows: (page.bills as Dict[]) || (page.items as Dict[]) || [],
      cates: [...(cates.expense || []), ...(cates.income || [])],
    };
  }); // 写操作后由全局版本号触发刷新
  const cmap = useMemo(() => cateMap(data?.cates || []), [data?.cates]);
  const rows = data?.rows || [];
  const thisMonth = new Date();
  const ym = thisMonth.getFullYear() * 100 + thisMonth.getMonth() + 1;
  const monthRows = rows.filter((r) => dayMonth(r.day) === ym);
  const expense = monthRows.filter((r) => N(r.type, 1) === 1).reduce((s, r) => s + N(r.money), 0);
  const income = monthRows.filter((r) => N(r.type, 1) === 2).reduce((s, r) => s + N(r.money), 0);
  const recent = rows.filter((r) => {
    if (filter !== 'all' && N(r.type, 1) !== filter) return false;
    if (q.trim()) return `${S(r.desc)} ${cateName(cmap, r.member_cate_id)}`.toLowerCase().includes(q.trim().toLowerCase());
    return true;
  }).slice(0, 12);
  const groups = useMemo(() => {
    const m = new Map<string, Dict[]>();
    for (const r of recent) { const k = S(r.day); if (!m.has(k)) m.set(k, []); m.get(k)!.push(r); }
    return [...m.entries()].sort((a, b) => b[0].localeCompare(a[0]));
  }, [recent]);

  return (
    <div className="page">
      <BrandHead
        title="喵啊记账"
        right={<button className="icon-btn" aria-label="刷新" onClick={reload}><Icon name="refresh" /></button>}
      />
      <SearchPill value={q} onChange={setQ} placeholder="搜索账单、分类、标签" onFocus={() => nav.push('GlobalSearch')} />
      <ChipRow
        value={filter}
        onChange={(v) => setFilter(v as typeof filter)}
        items={[{ key: 1, label: '支出' }, { key: 2, label: '收入' }, { key: 3, label: '转账' }]}
        allLabel="全部"
      />
      <div className="page-scroll">
        <div className="page-pad">
          <GlassCard>
            <div className="row-between">
              <div>
                <div className="t-label-medium t-secondary">本月支出</div>
                <div className="t-title-large" style={{ marginTop: 4 }}>¥ {formatMoney(expense)}</div>
              </div>
              <div style={{ textAlign: 'right' }}>
                <div className="t-label-medium t-secondary">本月收入</div>
                <div className="t-body-large t-semi" style={{ marginTop: 8 }}>¥ {formatMoney(income)}</div>
              </div>
            </div>
            <div className="t-label-medium t-secondary" style={{ marginTop: 10 }}>
              {thisMonth.getMonth() + 1}月结余 ¥ {formatMoney(income - expense)} · 共 {monthRows.length} 笔
            </div>
          </GlassCard>

          {error && <div className="card card-pad"><div className="t-body-medium t-danger">读取失败：{error}</div></div>}
          {loading && <div className="card card-pad"><div className="t-body-medium t-secondary">正在读取本地账本…</div></div>}
          <SectionTitle action={<button onClick={() => nav.push('BillList')}>查看全部 ›</button>}>最近账单</SectionTitle>
          {!loading && groups.length === 0 ? (
            <div className="card">
              <EmptyState
                icon="receipt" title={q ? '没有匹配的账单' : '还没有账单'}
                hint={q ? '清空搜索或换个关键词' : '记下第一笔，或开启自动记账让喵啊记账帮你记'}
                actionLabel={q ? '清空搜索' : '去记一笔'} onAction={() => q ? setQ('') : nav.push('BillEdit', { mode: 'create' })}
              />
            </div>
          ) : groups.map(([day, list]) => (
            <div key={day}>
              <div className="day-label t-label-medium t-secondary">{dayText(day)}</div>
              <div className="card">
                {list.map((r) => (
                  <ListRow key={S(r.client_id)}
                    icon={cateIcon(cmap, r.member_cate_id) as 'wallet'}
                    title={<span className="row-between"><span>{cateName(cmap, r.member_cate_id)}</span><span className={N(r.type) === 2 ? 't-semi' : ''}>{N(r.type) === 2 ? '+' : N(r.type) === 1 ? '-' : ''}¥{formatMoney(N(r.money))}</span></span>}
                    subtitle={[S(r.desc), S(r.tag_ids) && '含标签'].filter(Boolean).join(' · ') || '无备注'}
                    chevron={false}
                    onClick={() => nav.push('BillDetail', { clientId: S(r.client_id) })} />
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

/* ---------------- 统计首页 ---------------- */
export function StatsHome(_: PageProps) {
  const nav = useNav();
  const [period, setPeriod] = useState<'month' | 'year'>('month');
  const { data } = useAsync(async () => {
    const [page, cates, budgets] = await Promise.all([api.billsPage(1), api.mainCates(), api.budgetList()]);
    return {
      rows: (page.bills as Dict[]) || (page.items as Dict[]) || [],
      cates: [...(cates.expense || []), ...(cates.income || [])], budgets,
    };
  });
  const cmap = useMemo(() => cateMap(data?.cates || []), [data?.cates]);
  const now = new Date();
  const key = now.getFullYear() * 100 + now.getMonth() + 1;
  const scoped = (data?.rows || []).filter((r) => period === 'year' ? S(r.day).slice(0, 4) === String(now.getFullYear()) : Number(S(r.day).slice(0, 6)) === key);
  const stats = useMemo(() => summarize(scoped, 1, (id) => cateName(cmap, id)), [scoped, cmap]);
  const budget = (data?.budgets || []).find((b) => N(b.day) === key);
  return (
    <div className="page">
      <TopBar title="统计与报表" brand />
      <div className="page-scroll">
        <div className="page-pad">
          <ChipRow
            value={period} onChange={(v) => setPeriod(v as typeof period)}
            items={[{ key: 'month', label: '按月' }, { key: 'year', label: '按年' }]}
          />
          <GlassCard>
            <div className="t-label-medium t-secondary">{period === 'month' ? '本月' : '本年'}收支总览</div>
            <div className="row" style={{ gap: 24, marginTop: 6 }}>
              <div><div className="t-label-medium t-secondary">支出</div><div className="t-title-medium">¥ {formatMoney(stats.expense)}</div></div>
              <div><div className="t-label-medium t-secondary">收入</div><div className="t-title-medium">¥ {formatMoney(stats.income)}</div></div>
            </div>
            <div className="t-body-small t-secondary" style={{ marginTop: 8 }}>
              结余 ¥ {formatMoney(stats.net)} · {stats.count} 笔账单{stats.count === 0 ? '，记一笔后这里会生成分类占比与趋势' : ''}
            </div>
          </GlassCard>
          <SectionTitle>报表与工具</SectionTitle>
          <div className="card">
            <ListRow icon="pie" title="分类报表" subtitle="按分类 / 标签下钻" onClick={() => nav.push('ReportCategory')} />
            <ListRow icon="chart" title="收支趋势" subtitle="按月 / 按年" onClick={() => nav.push('ReportTrend')} />
            <ListRow icon="wallet" title="预算执行" subtitle={budget ? `本月预算 ¥${formatMoney(N(budget.money))} · 已用 ¥${formatMoney(stats.expense)}` : '总预算 / 分类预算'} onClick={() => nav.push('BudgetList')} />
            <ListRow icon="download" title="导出账单" subtitle="CSV，可再导入" onClick={() => nav.push('ExportBills')} />
          </div>
        </div>
      </div>
    </div>
  );
}

/* ---------------- 自动记账首页（真实设置与队列） ---------------- */
export function AutoHome(_: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const { data, reload } = useAsync(async () => {
    const { loadSettings, pendingEvents } = await import('../auto/engine');
    const [settings, events, rules] = await Promise.all([loadSettings(), pendingEvents(), api.ruleList()]);
    return { settings, pending: events.filter((e) => e.status === 0).length, rules: rules.length };
  });
  const s = data?.settings;
  const onCount = [s?.notification, s?.accessibility, s?.ocr].filter(Boolean).length;
  async function toggleMaster(v: boolean) {
    const { saveSetting } = await import('../auto/engine');
    await saveSetting('master', v);
    if (v && !onCount) { toast.show('先完成 5 步引导，选择要开启的识别方式'); nav.push('GuideAutoBill'); return; }
    toast.show(v ? '自动记账已开启' : '自动记账已关闭');
    reload();
  }
  return (
    <div className="page">
      <TopBar title="自动记账" brand right={<button className="icon-btn" aria-label="设置" onClick={() => nav.push('AutoBillSetting')}><Icon name="settings" /></button>} />
      <div className="page-scroll">
        <div className="page-pad">
          <GlassCard strong>
            <div className="row-between">
              <div className="row">
                <span className="list-row-icon"><Icon name="auto" size={19} /></span>
                <div>
                  <div className="t-body-large t-semi">自动记账</div>
                  <div className="t-label-medium t-secondary">{s?.master ? `已开启 · ${onCount} 路采集 · 结果需你确认` : '未开启'}</div>
                </div>
              </div>
              <Switch checked={!!s?.master} onChange={toggleMaster} />
            </div>
          </GlassCard>

          <SectionTitle action={<button onClick={() => nav.push('GuideAutoBill')}>开启引导 ›</button>}>三路采集</SectionTitle>
          <div className="card">
            <ListRow icon="bell" title="通知识别" subtitle={s?.notification ? '已开启 · 支付通知 → 金额与商户' : '未开启 · 支付通知 → 金额与商户'} right={s?.notification ? '开' : '关'} onClick={() => nav.push('AutoBillData', { source: 'notification' })} />
            <ListRow icon="shield" title="无障碍识别" subtitle={s?.accessibility ? '已开启 · 页面文本树 → 交易信息' : '未开启 · 页面文本树 → 交易信息'} right={s?.accessibility ? '开' : '关'} onClick={() => nav.push('AutoBillData', { source: 'accessibility' })} />
            <ListRow icon="camera" title="截屏 OCR" subtitle={s?.ocr ? '已开启 · 截图 → 文字识别' : '未开启 · 截图 → 文字识别'} right={s?.ocr ? '开' : '关'} onClick={() => nav.push('AutoBillData', { source: 'ocr' })} />
          </div>

          <SectionTitle>识别与确认</SectionTitle>
          <div className="card">
            <ListRow icon="check" title="待确认队列" subtitle="识别结果不会静默入账" right={`${data?.pending ?? 0} 条`} onClick={() => nav.push('BillIdentified')} />
            <ListRow icon="note" title="匹配规则" subtitle={`关键词 → 分类 / 账户 / 标签 · ${data?.rules ?? 0} 条`} onClick={() => nav.push('RuleList')} />
            <ListRow icon="grid" title="监听 App 白名单" subtitle={s ? `已选 ${s.apps.length} 个` : '读取中…'} onClick={() => nav.push('AppList')} />
            <ListRow icon="refresh" title="保活设置" subtitle="保证后台持续监听" onClick={() => nav.push('KeepAlive')} />
          </div>
          <div className="t-label-medium t-secondary" style={{ padding: '14px 4px', lineHeight: 1.8 }}>
            喵啊记账不会在未经你确认的情况下写入任何账单；Web 版以粘贴文本等价覆盖三路识别链路，不申请系统级权限。
          </div>
        </div>
      </div>
    </div>
  );
}

/* ---------------- 我的 ---------------- */
export function MineHome(_: PageProps) {
  const nav = useNav();
  const { mode, setMode, isDark } = useTheme();
  const refresh = useRefresh();
  const { data: member } = useAsync(() => get<Record<string, unknown>>('member', LOCAL_MEMBER_ID), [refresh.version]);
  const phone = String(member?.phone ?? '');
  const nick = String(member?.nickname ?? '');
  const signedIn = Boolean(phone || nick);
  const vip = Number(member?.vip ?? 0) === 1;
  return (
    <div className="page">
      <TopBar title="我的" brand right={<button className="icon-btn" aria-label="设置" onClick={() => nav.push('Settings')}><Icon name="settings" /></button>} />
      <div className="page-scroll">
        <div className="page-pad">
          <GlassCard>
            <div className="row">
              <span className="brand-mark" style={{ width: 46, height: 46, borderRadius: 16 }}><Icon name="mine" size={24} /></span>
              <div className="grow">
                <div className="t-body-large t-semi">{signedIn ? (nick || '已登录') : '本地账本'}</div>
                <div className="t-label-medium t-secondary">{signedIn ? `${phone || '未绑定手机'}${vip ? ' · 会员' : ''}` : '数据保存在此设备 · 未登录也可完整使用'}</div>
              </div>
              <button className="btn btn-ghost" style={{ padding: '8px 14px' }} onClick={() => nav.push('AccountLogin')}>{signedIn ? '账号' : '登录'}</button>
            </div>
          </GlassCard>

          <SectionTitle>账本与资产</SectionTitle>
          <div className="card">
            <ListRow icon="book" title="账本管理" subtitle="多账本 / 多人协作" onClick={() => nav.push('BookList')} />
            <ListRow icon="wallet" title="资产与账户" subtitle="余额 / 转账 / 借据" onClick={() => nav.push('AssetHome')} />
            <ListRow icon="tag" title="分类与标签" onClick={() => nav.push('CategoryManage')} />
            <ListRow icon="star" title="存钱计划" onClick={() => nav.push('SavePlanList')} />
            <ListRow icon="clock" title="周期账与提醒" onClick={() => nav.push('FixedBillList')} />
            <ListRow icon="coin" title="消费分期" onClick={() => nav.push('InstallmentList')} />
          </div>

          <SectionTitle>主题</SectionTitle>
          <div className="card card-pad">
            <div className="row-between">
              <div className="row">
                <Icon name={isDark ? 'moon' : 'sun'} size={18} />
                <span className="t-body-large">外观</span>
              </div>
              <div className="row" style={{ gap: 6 }}>
                {(['system', 'light', 'dark'] as const).map((m) => (
                  <button key={m} className={`chip ${mode === m ? 'selected' : ''}`} style={{ padding: '6px 12px', fontSize: 'var(--fs-label-medium)' }} onClick={() => setMode(m)}>
                    {m === 'system' ? '跟随系统' : m === 'light' ? '浅色' : '深色'}
                  </button>
                ))}
              </div>
            </div>
          </div>

          <SectionTitle>导入导出与更多</SectionTitle>
          <div className="card">
            <ListRow icon="upload" title="账单导入" subtitle="CSV / OCR 选图" onClick={() => nav.push('ImportHome')} />
            <ListRow icon="widget" title="桌面小组件" onClick={() => nav.push('WidgetHome')} />
            <ListRow icon="gift" title="会员与订单" onClick={() => nav.push('VipHome')} />
            <ListRow icon="users" title="账本成员" onClick={() => nav.push('BookMemberList')} />
            <ListRow icon="folder" title="协议与帮助" onClick={() => nav.push('HelpHome')} />
            <ListRow icon="shield" title="合约与解签" subtitle="自动续费 / 老用户迁移" onClick={() => nav.push('ContractInfo')} />
            <ListRow icon="lock" title="应用锁" onClick={() => nav.push('AppLock')} />
            <ListRow icon="note" title="意见反馈" onClick={() => nav.push('Feedback')} />
            <ListRow icon="info" title="权限说明" onClick={() => nav.push('PermissionExplain')} />
            <ListRow icon="bell" title="消息推送" subtitle="本复刻不接收推送" onClick={() => nav.push('PushSetting')} />
            <ListRow icon="settings" title="远程开关" subtitle="24 项服务端配置" onClick={() => nav.push('RemoteSwitches')} />
            <ListRow icon="info" title="关于喵啊记账" right="v1.8.1" onClick={() => nav.push('About')} chevron={false} />
          </div>
        </div>
      </div>
    </div>
  );
}
