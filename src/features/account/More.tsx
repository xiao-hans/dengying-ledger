/**
 * M7 补齐：合约与老用户迁移、支付宝解签、换绑手机、应用锁、
 * 意见反馈、权限逐项说明、推送说明、远程开关。
 * 推送为 no-op：页面只说明本复刻不接收系统推送，不申请通知权限。
 */
import { useState } from 'react';
import type { PageProps } from '../../app/App';
import { useNav } from '../../app/navigation';
import { useRefresh } from '../../app/useRefresh';
import { useToast } from '../../app/useToast';
import { ConfirmDialog, EmptyState, GlassCard, ListRow, SectionTitle, Switch, TopBar } from '../../components/ui';
import { all, get, put } from '../../db/client';
import { LOCAL_MEMBER_ID } from '../../db/schema';
import { api } from '../common/api';
import { useAsync, useFormDraft } from '../common/hooks';
import { N, S } from '../common/format';
import './account.css';

function errText(e: unknown): string {
  return e instanceof Error ? e.message : '操作失败';
}

/* ---------------- 合约信息 + 老用户迁移 + 解签 ---------------- */

const CONTRACT_DRAFT = {
  contractProvider: '', productName: '', productId: '', money: '', nextContractDate: '',
};

export function ContractInfo(_props: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const { data, loading, error, reload } = useAsync(async () => {
    const [contract, member] = await Promise.all([
      api.contractInfo(),
      get<Record<string, unknown>>('member', LOCAL_MEMBER_ID),
    ]);
    return { contract: (contract ?? {}) as Record<string, unknown>, member: member ?? {} };
  }, [refresh.version]);
  const draft = useFormDraft('contract-sign', CONTRACT_DRAFT);
  const [askUnsign, setAskUnsign] = useState(false);
  const signed = N(data?.contract.signed) === 1;
  const migrated = N(data?.member.migrated) === 1;

  const sign = () => {
    const money = draft.value.money.trim();
    if (money && !/^\d+(\.\d{1,2})?$/.test(money)) { toast.show('金额格式不对，例如 12.8'); return; }
    if (!draft.value.productName.trim()) { toast.show('先填产品名称'); return; }
    void put('contract_info', {
      id: 1,
      contractProvider: draft.value.contractProvider.trim() || '本地模拟',
      productName: draft.value.productName.trim(),
      productId: draft.value.productId.trim(),
      money: money ? Math.round(Number(money) * 100) / 100 : 0,
      nextContractDate: draft.value.nextContractDate.trim(),
      signed: 1,
    }).then(() => { draft.reset(); refresh.bump(); toast.show('已记录签约'); })
      .catch((e) => toast.show(errText(e)));
  };

  return (
    <div className="page">
      <TopBar title="合约信息" onBack={() => nav.pop()} />
      {error && <EmptyState title="合约加载失败" actionLabel="重试" onAction={reload} />}
      <GlassCard>
        <SectionTitle>自动续费</SectionTitle>
        {loading && <p className="account-note">加载中</p>}
        {!loading && !signed && <p className="account-note">当前没有生效中的签约。下面可以记一条本地模拟签约，用来验证解签。</p>}
        {signed && (
          <>
            <ListRow title="签约方" subtitle={S(data?.contract.contractProvider) || '未填'} />
            <ListRow title="产品" subtitle={S(data?.contract.productName)} />
            <ListRow title="每期金额" subtitle={data?.contract.money ? `¥${S(data?.contract.money)}` : '未填'} />
            <ListRow title="下次扣款" subtitle={S(data?.contract.nextContractDate) || '未填'} />
          </>
        )}
      </GlassCard>
      {!signed && (
        <GlassCard>
          <SectionTitle>记一条模拟签约</SectionTitle>
          <label className="field"><span>产品名称</span>
            <input value={draft.value.productName} placeholder="例如 年度会员" onChange={(e) => draft.patch({ productName: e.target.value })} />
          </label>
          <label className="field"><span>签约方</span>
            <input value={draft.value.contractProvider} placeholder="留空则记为本地模拟" onChange={(e) => draft.patch({ contractProvider: e.target.value })} />
          </label>
          <label className="field"><span>每期金额（元）</span>
            <input inputMode="decimal" value={draft.value.money} placeholder="12.8" onChange={(e) => draft.patch({ money: e.target.value })} />
          </label>
          <label className="field"><span>下次扣款日期</span>
            <input value={draft.value.nextContractDate} placeholder="2026-12-01" onChange={(e) => draft.patch({ nextContractDate: e.target.value })} />
          </label>
          <button className="btn btn-primary" onClick={sign}>保存签约</button>
        </GlassCard>
      )}
      {signed && <ListRow title="解除自动续费" subtitle="解签后下个周期不再扣款" onClick={() => setAskUnsign(true)} />}
      <ConfirmDialog
        open={askUnsign}
        title="解除自动续费"
        message="解签后本地签约记录会清空，已付费的会员时长不受影响。此操作只改本机数据。"
        confirmLabel="解签"
        danger
        onCancel={() => setAskUnsign(false)}
        onConfirm={() => {
          setAskUnsign(false);
          void api.alipayUnsign().then(() => { refresh.bump(); toast.show('已解签'); })
            .catch((e) => toast.show(errText(e)));
        }}
      />
      <GlassCard>
        <SectionTitle>老用户数据</SectionTitle>
        <p className="account-note">
          {migrated ? '本机账本已完成迁移，再次执行不会重复写入。' : '把旧版本留在本机的账本标记为已迁移。不会删除任何账单。'}
        </p>
        <button className="btn btn-ghost" disabled={migrated} onClick={() => {
          void api.migrateOldUser().then(() => { refresh.bump(); toast.show('迁移完成'); })
            .catch((e) => toast.show(errText(e)));
        }}>{migrated ? '已迁移' : '迁移本机数据'}</button>
      </GlassCard>
    </div>
  );
}

/* ---------------- 换绑手机 ---------------- */

export function ChangePhone(_props: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const { data } = useAsync(() => get<Record<string, unknown>>('member', LOCAL_MEMBER_ID), [refresh.version]);
  const current = S(data?.phone);
  const draft = useFormDraft('change-phone', { oldCode: '', phone: '', code: '' });
  const [sending, setSending] = useState<'old' | 'new' | null>(null);

  const send = (which: 'old' | 'new') => {
    const target = which === 'old' ? current : draft.value.phone.trim();
    if (!/^1\d{10}$/.test(target)) { toast.show(which === 'old' ? '当前没有可验证的手机号' : '请输入 11 位手机号'); return; }
    setSending(which);
    void api.sendCode(target).then((res) => toast.show(`验证码 ${S((res as Record<string, unknown>)?.code) || '已发送'}（仅本机）`))
      .catch((e) => toast.show(errText(e)))
      .finally(() => setSending(null));
  };
  const submit = () => {
    if (!current) { toast.show('还没有绑定手机，请先去绑定'); return; }
    const phone = draft.value.phone.trim();
    if (!/^1\d{10}$/.test(phone)) { toast.show('新手机号应为 11 位'); return; }
    if (phone === current) { toast.show('新手机号与当前相同'); return; }
    if (!draft.value.oldCode.trim() || !draft.value.code.trim()) { toast.show('两个验证码都要填'); return; }
    void api.bindPhone({ phone, code: draft.value.code.trim(), oldPhone: current })
      .then(() => { draft.reset(); refresh.bump(); toast.show('已换绑'); nav.pop(); })
      .catch((e) => toast.show(errText(e)));
  };

  return (
    <div className="page">
      <TopBar title="换绑手机" onBack={() => nav.pop()} />
      {!current
        ? <EmptyState title="还没有绑定手机" hint="先登录并绑定，再来换绑" actionLabel="去登录" onAction={() => nav.push('AccountLogin')} />
        : (
          <GlassCard>
            <p className="account-note">当前手机号 {current}。换绑需要旧号和新号各一次验证码，都只在本机生成。</p>
            <div className="row">
              <label className="field grow"><span>旧号验证码</span>
                <input inputMode="numeric" value={draft.value.oldCode} onChange={(e) => draft.patch({ oldCode: e.target.value })} />
              </label>
              <button className="btn btn-ghost" disabled={sending === 'old'} onClick={() => send('old')}>发送</button>
            </div>
            <label className="field"><span>新手机号</span>
              <input inputMode="numeric" maxLength={11} value={draft.value.phone} onChange={(e) => draft.patch({ phone: e.target.value })} />
            </label>
            <div className="row">
              <label className="field grow"><span>新号验证码</span>
                <input inputMode="numeric" value={draft.value.code} onChange={(e) => draft.patch({ code: e.target.value })} />
              </label>
              <button className="btn btn-ghost" disabled={sending === 'new'} onClick={() => send('new')}>发送</button>
            </div>
            <button className="btn btn-primary" onClick={submit}>确认换绑</button>
          </GlassCard>
        )}
    </div>
  );
}

/* ---------------- 应用锁（4 位数字，本地） ---------------- */

const LOCK_KEY = 'app_lock_pin';

function readPin(): Promise<string> {
  return get<Record<string, unknown>>('user_setting', LOCK_KEY).then((row) => S(row?.value));
}

export function AppLock(_props: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const { data, reload } = useAsync(readPin, [refresh.version]);
  const enabled = Boolean(data);
  const [step, setStep] = useState<'idle' | 'new' | 'confirm' | 'verify' | 'off'>('idle');
  const [first, setFirst] = useState('');
  const [digits, setDigits] = useState('');

  const reset = () => { setStep('idle'); setFirst(''); setDigits(''); };
  const submit = (pin: string) => {
    if (step === 'new') { setFirst(pin); setDigits(''); setStep('confirm'); return; }
    if (step === 'confirm') {
      if (pin !== first) { toast.show('两次输入不一致，请重新设置'); reset(); return; }
      void put('user_setting', { key: LOCK_KEY, value: pin })
        .then(() => { toast.show('应用锁已开启'); reset(); reload(); })
        .catch((e) => toast.show(errText(e)));
      return;
    }
    if (pin !== data) { toast.show('密码不对'); setDigits(''); return; }
    if (step === 'off') {
      void put('user_setting', { key: LOCK_KEY, value: '' })
        .then(() => { toast.show('应用锁已关闭'); reset(); reload(); })
        .catch((e) => toast.show(errText(e)));
      return;
    }
    toast.show('验证通过'); reset();
  };
  const press = (d: string) => {
    if (step === 'idle') return;
    const next = (digits + d).slice(0, 4);
    setDigits(next);
    if (next.length === 4) submit(next);
  };

  const prompt = step === 'new' ? '设置 4 位密码'
    : step === 'confirm' ? '再输一次'
      : step === 'verify' ? '输入密码验证'
        : step === 'off' ? '输入密码以关闭' : '';

  return (
    <div className="page">
      <TopBar title="应用锁" onBack={() => nav.pop()} />
      <GlassCard>
        <SectionTitle>{enabled ? '已开启' : '未开启'}</SectionTitle>
        <p className="account-note">密码只保存在这台设备上。本复刻不接指纹。忘记密码只能在设置里清空本地数据后重置。</p>
        {step === 'idle' && (
          <div className="row">
            {enabled
              ? <>
                <button className="btn btn-ghost" onClick={() => setStep('verify')}>验证一次</button>
                <button className="btn btn-primary" onClick={() => setStep('off')}>关闭应用锁</button>
              </>
              : <button className="btn btn-primary" onClick={() => setStep('new')}>开启应用锁</button>}
          </div>
        )}
        {step !== 'idle' && (
          <>
            <p className="account-note">{prompt}</p>
            <div className="pin-dots" aria-label={`已输入 ${digits.length} 位`}>
              {[0, 1, 2, 3].map((i) => <span key={i} className={i < digits.length ? 'on' : ''} />)}
            </div>
            <div className="pin-pad">
              {['1', '2', '3', '4', '5', '6', '7', '8', '9', '', '0', 'del'].map((d) => (
                d === '' ? <span key="gap" /> : (
                  <button key={d} className="pin-key" onClick={() => (d === 'del' ? setDigits((v) => v.slice(0, -1)) : press(d))}>
                    {d === 'del' ? '删除' : d}
                  </button>
                )
              ))}
            </div>
            <button className="btn btn-ghost" onClick={reset}>取消</button>
          </>
        )}
      </GlassCard>
    </div>
  );
}

/* ---------------- 意见反馈 ---------------- */

const FEEDBACK_DRAFT = { content: '', contact: '' };

export function Feedback(_props: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const draft = useFormDraft('feedback', FEEDBACK_DRAFT);
  const { data, reload } = useAsync(() => all<Record<string, unknown>>('behavior_event'), [refresh.version]);
  const mine = (data ?? []).filter((row) => row.kind === 'feedback').slice(-5).reverse();

  const submit = () => {
    const content = draft.value.content.trim();
    if (content.length < 5) { toast.show('内容至少 5 个字'); return; }
    void api.behavior({ kind: 'feedback', content, contact: draft.value.contact.trim(), time: Date.now() })
      .then(() => { draft.reset(); refresh.bump(); toast.show('已收到，只保存在本机'); })
      .catch((e) => toast.show(errText(e)));
  };

  return (
    <div className="page">
      <TopBar title="意见反馈" onBack={() => nav.pop()} />
      <GlassCard>
        <label className="field"><span>想说的内容</span>
          <textarea rows={4} maxLength={500} value={draft.value.content} placeholder="说明遇到的问题或建议" onChange={(e) => draft.patch({ content: e.target.value })} />
        </label>
        <label className="field"><span>联系方式（可留空）</span>
          <input value={draft.value.contact} placeholder="邮箱或手机号，只留在本机" onChange={(e) => draft.patch({ contact: e.target.value })} />
        </label>
        <button className="btn btn-primary" onClick={submit}>提交</button>
        <p className="account-note">不会发送到任何服务器。提交后可以在下面看到。</p>
      </GlassCard>
      {mine.length === 0
        ? <EmptyState title="还没有反馈" hint="提交后会出现在这里" actionLabel="刷新" onAction={reload} />
        : mine.map((row) => (
          <ListRow key={S(row.id)} title={S(row.content)} subtitle={S(row.contact) || '未留联系方式'} />
        ))}
    </div>
  );
}

/* ---------------- 权限逐项说明 ---------------- */

const PERMISSIONS: { title: string; why: string; web: string }[] = [
  { title: '通知使用权', why: '原版用来读取支付成功的通知，识别后进待确认队列。', web: '网页读不到系统通知。请用自动记账里的「模拟一条通知」。' },
  { title: '无障碍服务', why: '原版用来读取支付页的金额和商户。', web: '浏览器没有无障碍能力。识别结果同样进待确认，不会静默入账。' },
  { title: '截屏与相册', why: '原版用来对支付截图做文字识别。', web: '导入页的 OCR 选图只读你主动选的文件，不会后台截屏。' },
  { title: '相机', why: '原版用来拍小票并识别。', web: '网页版不调相机。可以把小票内容粘贴到导入页。' },
  { title: '存储', why: '原版用来保存导出的账单文件。', web: '导出会直接下载到浏览器的下载目录，不读取其它文件。' },
  { title: '悬浮窗', why: '原版用来在支付后弹出记一笔。', web: '浏览器不支持覆盖其它应用的悬浮窗。' },
];

export function PermissionExplain(_props: PageProps) {
  const nav = useNav();
  return (
    <div className="page">
      <TopBar title="权限说明" onBack={() => nav.pop()} />
      <GlassCard>
        <p className="account-note">下面是原版会申请的权限，以及网页复刻的对应做法。本页不会弹出任何系统授权框。</p>
      </GlassCard>
      {PERMISSIONS.map((item) => (
        <GlassCard key={item.title}>
          <SectionTitle>{item.title}</SectionTitle>
          <p className="account-note">{item.why}</p>
          <p className="account-note">本复刻：{item.web}</p>
        </GlassCard>
      ))}
    </div>
  );
}

/* ---------------- 推送（no-op） ---------------- */

export function PushSetting(_props: PageProps) {
  const nav = useNav();
  const toast = useToast();
  return (
    <div className="page">
      <TopBar title="消息推送" onBack={() => nav.pop()} />
      <GlassCard>
        <SectionTitle>未接入</SectionTitle>
        <p className="account-note">原版接入了多家厂商推送。本复刻不接收推送，也不会申请通知权限。账单提醒仍在「周期账与提醒」里按本机记录生效。</p>
      </GlassCard>
      <ListRow title="查看本机提醒" onClick={() => nav.push('ReminderList')} />
      <ListRow title="刷新登录态" subtitle="只换本地令牌，不注册推送" onClick={() => {
        void api.refreshToken('').then(() => toast.show('已刷新')).catch((e) => toast.show(errText(e)));
      }} />
    </div>
  );
}

/* ---------------- 远程开关（Config/GetV2 的服务端配置） ---------------- */

interface SwitchDef { key: string; label: string; hint: string; numeric?: boolean; toggle?: boolean; }

const REMOTE_SWITCHES: SwitchDef[] = [
  { key: 'ad_enabled', label: '广告总开关', hint: '关闭后不展示任何广告位', toggle: true },
  { key: 'dialog_enabled', label: '运营弹窗', hint: '启动时是否弹出运营内容', toggle: true },
  { key: 'force_update', label: '强制更新', hint: '低于最低版本时要求更新', toggle: true },
  { key: 'popupBackgroundEnable', label: '后台弹窗', hint: '切到后台时是否允许弹窗', toggle: true },
  { key: 'isInterceptStartActivity', label: '拦截启动页', hint: '自动记账是否拦截对方启动页', toggle: true },
  { key: 'memberServiceAgreementDialog', label: '会员协议弹窗', hint: '开通会员前是否弹出协议', toggle: true },
  { key: 'autoBillBgWhiteState', label: '后台白名单模式', hint: '只识别白名单里的应用', toggle: true },
  { key: 'yearlyReport', label: '年度报告', hint: '是否展示年度报告入口', toggle: true },
  { key: 'autoBillRemindCount', label: '自动记账提醒次数', hint: '未开启时的提醒上限', numeric: true },
  { key: 'autoBillShowNotificationDelay', label: '通知延迟（秒）', hint: '识别通知前等待的时间', numeric: true },
  { key: 'autoBookkeepingFreeCount', label: '免费自动记账次数', hint: '非会员可用次数', numeric: true },
  { key: 'autoBookkeepingPopupCycle', label: '自动记账弹窗周期', hint: '多少天弹一次', numeric: true },
  { key: 'assetBookkeepingPopupCycle', label: '资产记账弹窗周期', hint: '多少天弹一次', numeric: true },
  { key: 'evaluateDay', label: '评分间隔（天）', hint: '邀请评分的最小间隔', numeric: true },
  { key: 'hotSplashAdInterval', label: '热启动广告间隔', hint: '两次展示的最小间隔', numeric: true },
  { key: 'splashAdReqTime', label: '开屏广告超时', hint: '等待广告的最长毫秒数', numeric: true },
  { key: 'importBillsMaxCount', label: '导入次数上限', hint: '单个账本允许的导入次数', numeric: true },
  { key: 'importBillsMaxRow', label: '导入行数上限', hint: '单个文件的最大行数', numeric: true },
  { key: 'importBillsMaxSize', label: '导入文件上限（MB）', hint: '单个文件的最大体积', numeric: true },
  { key: 'ocrUpdateCount', label: 'OCR 次数上限', hint: '单日识别次数', numeric: true },
  { key: 'vipExpirationPopNum', label: '会员到期提醒次数', hint: '到期前弹出次数', numeric: true },
  { key: 'vipExpirationPopTime', label: '会员到期提醒间隔', hint: '两次提醒的间隔天数', numeric: true },
  { key: 'min_version', label: '最低可用版本', hint: '低于此版本提示更新' },
  { key: 'latest_version', label: '最新版本', hint: '检查更新时对照' },
];

export function RemoteSwitches(_props: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const { data, error, reload } = useAsync(() => api.serverConfig({}), [refresh.version]);
  const cfg = (data ?? {}) as Record<string, string>;
  const [editing, setEditing] = useState<string | null>(null);
  const [text, setText] = useState('');

  const save = (key: string, value: string) => {
    void put('remote_config', { key, value }).then(() => { refresh.bump(); setEditing(null); })
      .catch((e) => toast.show(errText(e)));
  };

  return (
    <div className="page">
      <TopBar title="远程开关" onBack={() => nav.pop()} />
      {error && <EmptyState title="配置加载失败" actionLabel="重试" onAction={reload} />}
      <GlassCard>
        <p className="account-note">这些值对应原版的远程配置，保存在本机。开关类直接切换，数字和文本点进去修改。</p>
      </GlassCard>
      {REMOTE_SWITCHES.map((item) => {
        const value = S(cfg[item.key]);
        return (
          <div key={item.key}>
            {item.toggle
              ? (
                <ListRow
                  title={item.label}
                  subtitle={item.hint}
                  right={<Switch checked={value === '1'} onChange={(on) => save(item.key, on ? '1' : '0')} label={item.label} />}
                />
              ) : (
                <ListRow
                  title={item.label}
                  subtitle={`${item.hint}：${value || '未设置'}`}
                  onClick={() => { setEditing(item.key); setText(value); }}
                />
              )}
            {editing === item.key && (
              <GlassCard>
                <label className="field"><span>{item.label}</span>
                  <input inputMode={item.numeric ? 'numeric' : 'text'} value={text} onChange={(e) => setText(e.target.value)} />
                </label>
                <div className="row">
                  <button className="btn btn-ghost" onClick={() => setEditing(null)}>取消</button>
                  <button className="btn btn-primary" onClick={() => {
                    if (item.numeric && text.trim() && !/^\d+$/.test(text.trim())) { toast.show('请输入非负整数'); return; }
                    save(item.key, text.trim());
                  }}>保存</button>
                </div>
              </GlassCard>
            )}
          </div>
        );
      })}
    </div>
  );
}
