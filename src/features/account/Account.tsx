import { useEffect, useState } from 'react';
import type { PageProps } from '../../app/App';
import { useNav } from '../../app/navigation';
import { useRefresh } from '../../app/useRefresh';
import { useToast } from '../../app/useToast';
import { ConfirmDialog, EmptyState, GlassCard, ListRow, SectionTitle, TopBar } from '../../components/ui';
import { all, get } from '../../db/client';
import { LOCAL_MEMBER_ID } from '../../db/schema';
import { api } from '../common/api';
import { useAsync } from '../common/hooks';
import { S } from '../common/format';
import './account.css';

function errText(e: unknown): string {
  return e instanceof Error ? e.message : '操作失败';
}

interface MemberRow {
  id?: number;
  nickname?: string;
  phone?: string;
  vip?: number;
  bind_type?: string;
  token?: string;
}

export function AccountLogin(_props: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const { data, reload } = useAsync(() => get<Record<string, unknown>>('member', LOCAL_MEMBER_ID), [refresh.version]);
  const member = (data ?? {}) as MemberRow;
  const logged = Boolean(member.token || member.phone);
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [sent, setSent] = useState('');
  const [busy, setBusy] = useState(false);
  const [askUnbind, setAskUnbind] = useState(false);

  const send = async () => {
    setBusy(true);
    try {
      const res = (await api.sendCode(phone)) as { code?: string };
      setSent(S(res.code));
      toast.show('验证码已生成（本地模拟）');
    } catch (e) {
      toast.show(errText(e));
    } finally {
      setBusy(false);
    }
  };

  const login = async () => {
    setBusy(true);
    try {
      await api.loginByPhone(phone, code);
      toast.show('已登录');
      refresh.bump();
      reload();
    } catch (e) {
      toast.show(errText(e));
    } finally {
      setBusy(false);
    }
  };

  const device = async () => {
    setBusy(true);
    try {
      await api.loginByDevice();
      toast.show('已用本机身份登录');
      refresh.bump();
    } catch (e) {
      toast.show(errText(e));
    } finally {
      setBusy(false);
    }
  };

  const third = async (type: 'wechat' | 'qq') => {
    setBusy(true);
    try {
      await api.loginThird(type, { openid: 'local-demo', qqId: 'local-demo' });
      toast.show(type === 'wechat' ? '已模拟微信登录' : '已模拟 QQ 登录');
      refresh.bump();
    } catch (e) {
      toast.show(errText(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page">
      <TopBar title="账号" onBack={() => nav.pop()} />
      <GlassCard>
        <div className="account-hero">
          <div className="name">{S(member.nickname) || '未登录'}</div>
          <div className="sub">
            {logged
              ? `${member.phone ? member.phone : '未绑定手机'} · ${member.vip ? '会员' : '普通用户'}`
              : '登录后账单仍先写在本机，云端只是同步副本'}
          </div>
        </div>
      </GlassCard>

      {!logged && (
        <GlassCard>
          <SectionTitle>手机号登录</SectionTitle>
          <label className="field">
            <span>手机号</span>
            <input value={phone} inputMode="numeric" maxLength={11} placeholder="11 位手机号" onChange={(e) => setPhone(e.target.value)} />
          </label>
          <label className="field">
            <span>验证码</span>
            <input value={code} inputMode="numeric" maxLength={6} placeholder="先获取再填写" onChange={(e) => setCode(e.target.value)} />
          </label>
          {sent && <p className="account-note">本地模拟验证码：{sent}。真实短信不会发出。</p>}
          <div className="row-actions">
            <button type="button" className="btn" disabled={busy} onClick={() => void send()}>获取验证码</button>
            <button type="button" className="btn primary" disabled={busy} onClick={() => void login()}>登录</button>
          </div>
          <div className="row-actions">
            <button type="button" className="btn" disabled={busy} onClick={() => void device()}>本机登录</button>
            <button type="button" className="btn" disabled={busy} onClick={() => void third('wechat')}>微信</button>
            <button type="button" className="btn" disabled={busy} onClick={() => void third('qq')}>QQ</button>
          </div>
        </GlassCard>
      )}

      {logged && (
        <>
          <ListRow title="绑定手机" subtitle={member.phone || '未绑定'} onClick={() => nav.push('BindPhone')} />
          {member.phone && <ListRow title="换绑手机" subtitle="旧号和新号都要验证" onClick={() => nav.push('ChangePhone')} />}
          <ListRow title="修改昵称" subtitle={S(member.nickname)} onClick={() => nav.push('NicknameEdit')} />
          <ListRow title="会员" subtitle={member.vip ? '已开通' : '未开通'} onClick={() => nav.push('VipHome')} />
          {member.bind_type && (
            <ListRow title="解除第三方绑定" subtitle={S(member.bind_type)} onClick={() => setAskUnbind(true)} />
          )}
        </>
      )}

      <ConfirmDialog
        open={askUnbind}
        title="解除绑定"
        message="只清除第三方绑定标记，手机号和本地账单都保留。"
        confirmLabel="解除"
        danger
        onCancel={() => setAskUnbind(false)}
        onConfirm={() => {
          setAskUnbind(false);
          void api.unbind().then(() => { toast.show('已解除'); refresh.bump(); }).catch((e) => toast.show(errText(e)));
        }}
      />
    </div>
  );
}

export function BindPhone(_props: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [sent, setSent] = useState('');
  const [busy, setBusy] = useState(false);

  return (
    <div className="page">
      <TopBar title="绑定手机" onBack={() => nav.pop()} />
      <GlassCard>
        <label className="field">
          <span>手机号</span>
          <input value={phone} inputMode="numeric" maxLength={11} onChange={(e) => setPhone(e.target.value)} />
        </label>
        <label className="field">
          <span>验证码</span>
          <input value={code} inputMode="numeric" maxLength={6} onChange={(e) => setCode(e.target.value)} />
        </label>
        {sent && <p className="account-note">本地模拟验证码：{sent}</p>}
        <div className="row-actions">
          <button type="button" className="btn" disabled={busy} onClick={() => {
            setBusy(true);
            void api.sendCode(phone).then((res) => {
              setSent(S((res as { code?: string }).code));
              toast.show('验证码已生成');
            }).catch((e) => toast.show(errText(e))).finally(() => setBusy(false));
          }}>获取验证码</button>
          <button type="button" className="btn primary" disabled={busy} onClick={() => {
            setBusy(true);
            void api.bindPhone({ phone, code }).then(() => {
              toast.show('已绑定');
              refresh.bump();
              nav.pop();
            }).catch((e) => toast.show(errText(e))).finally(() => setBusy(false));
          }}>绑定</button>
        </div>
      </GlassCard>
    </div>
  );
}

export function NicknameEdit(_props: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const { data } = useAsync(() => get<Record<string, unknown>>('member', LOCAL_MEMBER_ID), []);
  const [name, setName] = useState('');
  const [ready, setReady] = useState(false);
  useEffect(() => {
    if (!data || ready) return;
    setName(S((data as MemberRow).nickname));
    setReady(true);
  }, [data, ready]);

  return (
    <div className="page">
      <TopBar title="昵称" onBack={() => nav.pop()} />
      <GlassCard>
        <label className="field">
          <span>昵称</span>
          <input value={name} maxLength={20} onChange={(e) => setName(e.target.value)} />
        </label>
        <button type="button" className="btn primary" onClick={() => {
          const next = name.trim();
          if (!next) { toast.show('昵称不能为空'); return; }
          void api.updateNickname(next).then(() => {
            toast.show('已保存');
            refresh.bump();
            nav.pop();
          }).catch((e) => toast.show(errText(e)));
        }}>保存</button>
      </GlassCard>
    </div>
  );
}
