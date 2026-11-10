/**
 * 四个 Tab 首页骨架（M1）。真实业务数据在 M2 接入 IndexedDB 后替换，
 * 此处保留完整页面结构与空态出口，保证「进得去出得来」。
 */
import { Icon } from '../../components/Icon';
import { TopBar, SearchPill, ChipRow, GlassCard, EmptyState, SectionTitle, ListRow, Switch } from '../../components/ui';
import { useNav } from '../../app/navigation';
import { useTheme } from '../../theme/ThemeProvider';
import type { PageProps } from '../../app/App';
import { useState } from 'react';

const BrandHead = ({ title, right }: { title: string; right?: React.ReactNode }) => (
  <div className="brand-head">
    <span className="brand-mark"><Icon name="receipt" size={18} /></span>
    <span className="t-title-large brand-name grow">{title}</span>
    {right}
  </div>
);

/* ---------------- 账单首页 ---------------- */
export function BillsHome(_: PageProps) {
  const nav = useNav();
  const [q, setQ] = useState('');
  const [filter, setFilter] = useState<'all' | 1 | 2 | 3>('all');
  return (
    <div className="page">
      <BrandHead
        title="灯影记账"
        right={<button className="icon-btn" aria-label="设置" onClick={() => nav.push('Settings')}><Icon name="settings" /></button>}
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
                <div className="t-title-large" style={{ marginTop: 4 }}>¥ 0.00</div>
              </div>
              <div style={{ textAlign: 'right' }}>
                <div className="t-label-medium t-secondary">本月收入</div>
                <div className="t-body-large t-semi" style={{ marginTop: 8 }}>¥ 0.00</div>
              </div>
            </div>
          </GlassCard>

          <SectionTitle action={<button onClick={() => nav.push('BillList')}>查看全部 ›</button>}>最近账单</SectionTitle>
          <div className="card">
            <EmptyState
              icon="receipt" title="还没有账单" hint="记下第一笔，或开启自动记账让灯影帮你记"
              actionLabel="去记一笔" onAction={() => nav.push('BillEdit', { mode: 'create' })}
            />
          </div>
        </div>
      </div>
    </div>
  );
}

/* ---------------- 统计首页 ---------------- */
export function StatsHome(_: PageProps) {
  const nav = useNav();
  const [period, setPeriod] = useState<'month' | 'year'>('month');
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
            <div className="t-label-medium t-secondary">收支总览</div>
            <div className="t-title-large" style={{ marginTop: 6 }}>¥ 0.00</div>
            <div className="t-body-small t-secondary" style={{ marginTop: 8 }}>记一笔后这里会生成分类占比与趋势</div>
          </GlassCard>
          <SectionTitle>报表与工具</SectionTitle>
          <div className="card">
            <ListRow icon="pie" title="分类报表" subtitle="按分类 / 标签下钻" onClick={() => nav.push('ReportCategory')} />
            <ListRow icon="chart" title="收支趋势" subtitle="按月 / 按年" onClick={() => nav.push('ReportTrend')} />
            <ListRow icon="wallet" title="预算执行" subtitle="总预算 / 分类预算" onClick={() => nav.push('BudgetList')} />
            <ListRow icon="download" title="导出账单" subtitle="CSV，可再导入" onClick={() => nav.push('ExportBills')} />
          </div>
        </div>
      </div>
    </div>
  );
}

/* ---------------- 自动记账首页 ---------------- */
export function AutoHome(_: PageProps) {
  const nav = useNav();
  const [enabled, setEnabled] = useState(false);
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
                  <div className="t-label-medium t-secondary">{enabled ? '已开启 · 识别结果需你确认' : '未开启'}</div>
                </div>
              </div>
              <Switch checked={enabled} onChange={(v) => { setEnabled(v); if (v) nav.push('GuideAutoBill'); }} />
            </div>
          </GlassCard>

          <SectionTitle>三路采集</SectionTitle>
          <div className="card">
            <ListRow icon="bell" title="通知识别" subtitle="支付通知 → 金额与商户" onClick={() => nav.push('AutoBillData', { source: 'notification' })} />
            <ListRow icon="shield" title="无障碍识别" subtitle="页面文本树 → 交易信息" onClick={() => nav.push('AutoBillData', { source: 'accessibility' })} />
            <ListRow icon="camera" title="截屏 OCR" subtitle="截图 → 文字识别" onClick={() => nav.push('AutoBillData', { source: 'ocr' })} />
          </div>

          <SectionTitle>识别与确认</SectionTitle>
          <div className="card">
            <ListRow icon="check" title="待确认队列" subtitle="识别结果不会静默入账" right="0 条" onClick={() => nav.push('BillIdentified')} />
            <ListRow icon="note" title="匹配规则" subtitle="关键词 → 分类 / 账户 / 标签" onClick={() => nav.push('RuleList')} />
            <ListRow icon="refresh" title="保活设置" subtitle="保证后台持续监听" onClick={() => nav.push('KeepAlive')} />
          </div>
          <div className="t-label-medium t-secondary" style={{ padding: '14px 4px' }}>
            灯影不会在未经你确认的情况下写入任何账单；Web 版以模拟采集等价覆盖交互链路，不申请系统级权限。
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
  return (
    <div className="page">
      <TopBar title="我的" brand right={<button className="icon-btn" aria-label="设置" onClick={() => nav.push('Settings')}><Icon name="settings" /></button>} />
      <div className="page-scroll">
        <div className="page-pad">
          <GlassCard>
            <div className="row">
              <span className="brand-mark" style={{ width: 46, height: 46, borderRadius: 16 }}><Icon name="mine" size={24} /></span>
              <div className="grow">
                <div className="t-body-large t-semi">本地账本</div>
                <div className="t-label-medium t-secondary">数据保存在此设备 · 未登录也可完整使用</div>
              </div>
              <button className="btn btn-ghost" style={{ padding: '8px 14px' }} onClick={() => nav.push('AccountLogin')}>登录</button>
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
            <ListRow icon="info" title="关于灯影记账" right="v1.8.1" onClick={() => nav.push('About')} chevron={false} />
          </div>
        </div>
      </div>
    </div>
  );
}
