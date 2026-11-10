import { useState } from 'react';
import type { PageProps } from '../../app/App';
import { useNav } from '../../app/navigation';
import { useRefresh } from '../../app/useRefresh';
import { useToast } from '../../app/useToast';
import { ConfirmDialog, EmptyState, GlassCard, ListRow, SectionTitle, TopBar, formatMoney } from '../../components/ui';
import { all, get } from '../../db/client';
import { LOCAL_MEMBER_ID } from '../../db/schema';
import { api } from '../common/api';
import { useAsync } from '../common/hooks';
import { N, S } from '../common/format';
import './account.css';

function errText(e: unknown): string {
  return e instanceof Error ? e.message : '操作失败';
}

export function VipHome(_props: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const { data, error, reload } = useAsync(async () => {
    const [cfg, discount, member, dialog] = await Promise.all([
      api.vipConfigs(),
      api.discountConfig(),
      get<Record<string, unknown>>('member', LOCAL_MEMBER_ID),
      api.dialog(),
    ]);
    return { cfg: cfg as Record<string, unknown>, discount: discount as Record<string, unknown>, member, dialog: dialog as Record<string, unknown> };
  }, [refresh.version]);
  const [busy, setBusy] = useState(false);
  const [orderId, setOrderId] = useState('');
  const [askPay, setAskPay] = useState(false);

  if (error) {
    return (
      <div className="page">
        <TopBar title="会员" onBack={() => nav.pop()} />
        <EmptyState title="加载失败" actionLabel="重试" onAction={reload} />
      </div>
    );
  }

  const price = N(data?.cfg.vip_price_cents);
  const original = N(data?.cfg.original_price_cents);
  const days = N(data?.cfg.days) || 365;
  const vip = N((data?.member as { vip?: number } | undefined)?.vip);
  const rate = N(data?.discount.rate);
  const showDialog = N(data?.dialog.show) === 1;

  const pay = async () => {
    setBusy(true);
    try {
      const order = (await api.createOrder({
        money: price,
        productId: 'vip-year',
        type: 1,
        method: 'local',
        orderSource: 'vip',
      })) as { id?: string };
      const id = S(order.id);
      setOrderId(id);
      const checked = (await api.checkOrder(id)) as { status?: number };
      if (N(checked.status) === 1) {
        await api.activate('local');
        refresh.bump();
        nav.push('PayResult', { orderId: id, ok: '1' });
      } else {
        nav.push('PayResult', { orderId: id, ok: '0' });
      }
    } catch (e) {
      toast.show(errText(e));
    } finally {
      setBusy(false);
      setAskPay(false);
    }
  };

  return (
    <div className="page">
      <TopBar title="会员" onBack={() => nav.pop()} />
      <GlassCard>
        <div className="vip-price">
          <span className="now">{formatMoney(price)}</span>
          {original > price && <span className="old">{formatMoney(original)}</span>}
        </div>
        <p className="account-note">{days} 天 · {vip ? '当前已是会员' : '当前未开通'}。支付只在本机记账，不会跳转支付宝或微信。</p>
        {N(data?.discount.enabled) === 1 && rate > 0 && rate < 100 && (
          <p className="account-note">折扣 {rate}% · {S(data?.discount.label)}</p>
        )}
        <button type="button" className="btn primary" disabled={busy || vip === 1} onClick={() => setAskPay(true)}>
          {vip ? '已开通' : '本地模拟开通'}
        </button>
        {orderId && <p className="account-note">最近订单 {orderId}</p>}
      </GlassCard>
      {showDialog && (
        <GlassCard>
          <SectionTitle>{S(data?.dialog.title) || '公告'}</SectionTitle>
          <p className="account-note">{S(data?.dialog.content)}</p>
        </GlassCard>
      )}
      <ListRow title="拼团" subtitle="本地模拟，不发起真实分享" onClick={() => nav.push('GroupVip')} />
      <ListRow title="订单" onClick={() => nav.push('OrderList')} />
      <ListRow title="广告位说明" subtitle="当前为空实现" onClick={() => nav.push('AdSlots')} />
      <ConfirmDialog
        open={askPay}
        title="确认开通"
        message={`将记一笔 ${formatMoney(price)} 的本地订单并开通会员。不会产生真实扣款。`}
        confirmLabel="开通"
        onCancel={() => setAskPay(false)}
        onConfirm={() => void pay()}
      />
    </div>
  );
}

export function OrderList(_props: PageProps) {
  const nav = useNav();
  const { data, error, reload } = useAsync(() => all<Record<string, unknown>>('order'), []);
  const rows = (data ?? []) as { id?: string; money?: number; status?: number; product_id?: string; create_time?: string }[];
  return (
    <div className="page">
      <TopBar title="订单" onBack={() => nav.pop()} />
      {error && <EmptyState title="加载失败" actionLabel="重试" onAction={reload} />}
      {!error && rows.length === 0 && <EmptyState title="还没有订单" hint="开通会员后会出现在这里" actionLabel="去开通" onAction={() => nav.push('VipHome')} />}
      {rows.map((row) => (
        <ListRow
          key={S(row.id)}
          title={S(row.product_id) || '会员'}
          subtitle={`${row.create_time ?? ''} · ${N(row.status) === 1 ? '已支付' : '未支付'}`}
          right={formatMoney(N(row.money))}
        />
      ))}
    </div>
  );
}

export function GroupVip(_props: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const { data, reload } = useAsync(() => api.groupVipList(), [refresh.version]);
  const rows = (Array.isArray(data) ? data : []) as { id?: string; showId?: string; isVip?: number }[];
  const [code, setCode] = useState('');

  return (
    <div className="page">
      <TopBar title="拼团" onBack={() => nav.pop()} />
      <GlassCard>
        <p className="account-note">拼团只写本地记录。没有分享、没有支付渠道。</p>
        <label className="field">
          <span>展示码</span>
          <input value={code} maxLength={32} placeholder="自拟一串，用来回查" onChange={(e) => setCode(e.target.value)} />
        </label>
        <button type="button" className="btn primary" onClick={() => {
          const showId = code.trim();
          if (!showId) { toast.show('先填写展示码'); return; }
          void api.groupVipBind(showId).then(() => {
            toast.show('已记录');
            setCode('');
            refresh.bump();
          }).catch((e) => toast.show(errText(e)));
        }}>加入本地拼团</button>
      </GlassCard>
      {rows.length === 0 && <EmptyState title="还没有拼团记录" />}
      {rows.map((row) => (
        <ListRow
          key={S(row.id)}
          title={S(row.showId) || S(row.id)}
          subtitle={N(row.isVip) ? '已标记会员' : '未标记'}
          onClick={() => {
            void api.groupVipCheck(S(row.showId) || S(row.id)).then((res) => {
              const hit = res as { isVip?: number };
              toast.show(N(hit.isVip) ? '记录在册' : '没有这条记录');
              reload();
            }).catch((e) => toast.show(errText(e)));
          }}
        />
      ))}
    </div>
  );
}

export function AdSlots(_props: PageProps) {
  const nav = useNav();
  return (
    <div className="page">
      <TopBar title="广告位" onBack={() => nav.pop()} />
      <div className="ad-slot">开屏 · 空实现</div>
      <div className="ad-slot">信息流 · 空实现</div>
      <div className="ad-slot">激励 · 空实现</div>
      <GlassCard>
        <p className="account-note">广告、推送和埋点保持可插拔的空实现。表还在（ad_data、ad_info、ad_config、ad_event），但不会加载任何广告 SDK，也不会上报。</p>
      </GlassCard>
    </div>
  );
}

/** 支付结果引导。只展示本地模拟订单的结果，不接真实支付渠道。 */
export function PayResult({ route }: PageProps) {
  const nav = useNav();
  const id = S(route.params?.orderId);
  const ok = S(route.params?.ok) !== '0';
  return (
    <div className="page">
      <TopBar title="支付结果" onBack={() => nav.pop()} />
      <GlassCard>
        <SectionTitle>{ok ? '开通成功' : '未完成支付'}</SectionTitle>
        <p className="account-note">{ok ? '会员已在本机生效。没有发生真实扣款，也没有跳转支付宝或微信。' : '订单还是未支付。可以回到会员页重试。'}</p>
        {id && <p className="account-note">订单号 {id}</p>}
        <button type="button" className="btn primary" onClick={() => nav.push('BillEdit')}>去记一笔</button>
        <button type="button" className="btn" style={{ marginLeft: 8 }} onClick={() => nav.pop()}>返回</button>
      </GlassCard>
    </div>
  );
}
