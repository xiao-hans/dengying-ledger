/** Account(10) + Member(10) + MemberImportBillFile(2) + Behavior(1) + Config(1) --------------- */
import {
  ok, fail, all, get, put, remove, nextId, newClientId, nowMs, nowSec,
  params, str, num, int, boolish, save, drop, empty, pageOf, sortBy, type RequestCtx,
} from './helpers';
import { LOCAL_MEMBER_ID } from '../../db/schema';

async function currentUser(): Promise<Record<string, unknown>> {
  return (await get('member', LOCAL_MEMBER_ID)) || { id: LOCAL_MEMBER_ID, nickname: '本地用户', vip: 0 };
}

export const handlers: Record<string, (ctx: RequestCtx) => Promise<unknown> | unknown> = {
  'Account/ActivateV3': (ctx) => {
    const distinctId = str(ctx, 'distinctId');
    return ok({ distinctId, activated: 1, activateTime: nowSec() });
  },
  'Account/SendCode': (ctx) => {
    const phone = str(ctx, 'phone');
    if (!/^1\d{10}$/.test(phone)) return fail(400, '请输入正确的手机号');
    const code = String(Math.floor(100000 + Math.random() * 900000));
    void put('sms_code', { phone, code, create_time: nowMs(), expires: nowMs() + 300000 });
    return ok({ phone, expireIn: 300, code }); // 本地复刻直接回显验证码，便于离线登录
  },
  'Account/LoginByPhone': async (ctx) => {
    const phone = str(ctx, 'phone'), code = str(ctx, 'code');
    const rec = await get<{ code: string; expires: number }>('sms_code', phone);
    if (!rec || rec.code !== code || rec.expires < nowMs()) return fail(400, '验证码错误或已过期');
    const u = await currentUser();
    const user = { ...u, phone, token: `local_${newClientId()}`, login_type: 'phone', login_time: nowSec() };
    await save('member', { id: LOCAL_MEMBER_ID, ...user });
    await remove('sms_code', phone);
    return ok(user);
  },
  'Account/LoginByDevice': async () => {
    const u = await currentUser();
    const user = { ...u, token: `local_${newClientId()}`, login_type: 'device', login_time: nowSec() };
    await save('member', { id: LOCAL_MEMBER_ID, ...user });
    return ok(user);
  },
  'Account/LoginByWechat': async (ctx) => loginThird(ctx, 'wechat'),
  'Account/LoginByQQ': async (ctx) => loginThird(ctx, 'qq'),
  'Account/BindPhone': async (ctx) => {
    const phone = str(ctx, 'phone'), code = str(ctx, 'code');
    if (!/^1\d{10}$/.test(phone)) return fail(400, '请输入正确的手机号');
    const rec = await get<{ code: string; expires: number }>('sms_code', phone);
    if (!rec || rec.code !== code || rec.expires < nowMs()) return fail(400, '验证码错误或已过期');
    const u = await currentUser();
    const user = { ...u, phone, bind_type: 'phone', bind_id: phone, bind_time: nowSec() };
    await save('member', { id: LOCAL_MEMBER_ID, ...user });
    await remove('sms_code', phone);
    return ok(user);
  },
  'Account/BindWechat': async (ctx) => bindThird(str(ctx, 'openid'), str(ctx, 'unionId'), 'wechat'),
  'Account/BindQQ': async (ctx) => bindThird(str(ctx, 'qqId'), str(ctx, 'nickname'), 'qq'),
  'Account/Unbind': async () => {
    const u = await currentUser();
    return ok({ ...u, bind_type: '', bind_id: '' });
  },

  'Member/GetInfo': async () => ok(await currentUser()),
  'Member/GetSetting': async () => {
    const rows = await all<{ key: string; value: unknown }>('user_setting');
    const out: Record<string, unknown> = {};
    for (const r of rows) out[r.key] = r.value;
    return ok(out);
  },
  'Member/UpdateSetting': async (ctx) => {
    const key = str(ctx, 'key'), value = str(ctx, 'value');
    if (!key) return fail(400, '设置项不能为空');
    await put('user_setting', { key, value });
    return empty();
  },
  'Member/UpdateNickname': async (ctx) => {
    const u = await currentUser();
    const nickname = str(ctx, 'nickname', '本地用户');
    const user = { ...u, nickname, id: LOCAL_MEMBER_ID };
    await save('member', user);
    return empty();
  },
  'Member/RefreshToken': async (ctx) => ok({ token: `local_${newClientId()}`, gtCid: str(ctx, 'gtCid'), expireIn: 7200 }),
  'Member/InitSSOldUserInfo': async () => {
    const member = await get<Record<string, unknown>>('member', LOCAL_MEMBER_ID);
    const already = member?.migrated === 1;
    if (member && !already) await save('member', { ...member, migrated: 1, migrate_time: nowSec() });
    return ok({ migrated: 1, already: already ? 1 : 0, memberId: LOCAL_MEMBER_ID });
  },
  'Member/GetMemberContractInfo': async () => ok((await all('contract_info'))[0] || {}),
  'Member/CreateReminder': async (ctx) => {
    const id = newClientId();
    await save('reminder', {
      id, title: str(ctx, 'title'), contents: str(ctx, 'contents'),
      reminderTime: str(ctx, 'reminderTime'), status: int(ctx, 'status', 1),
      create_time: nowSec(),
    });
    return empty();
  },
  'Member/UpdateReminder': async (ctx) => {
    const id = str(ctx, 'reminderId');
    const old = await get('reminder', id);
    if (!old) return fail(404, '提醒不存在');
    await save('reminder', {
      ...old, title: str(ctx, 'title', String(old.title ?? '')),
      contents: str(ctx, 'contents', String(old.contents ?? '')),
      reminderTime: str(ctx, 'reminderTime', String(old.reminderTime ?? '')),
      status: int(ctx, 'status', Number(old.status ?? 1)),
      changeContent: int(ctx, 'changeContent', 0), update_time: nowSec(),
    });
    return empty();
  },
  'Member/MemberReminderList': async () => ok(sortBy(await all('reminder'), 'create_time', true)),

  'MemberImportBillFile/List': async () => ok(sortBy(await all('member_import_bill_file'), 'create_time', true)),
  'MemberImportBillFile/Delete': async (ctx) => drop('member_import_bill_file', str(ctx, 'id')),

  'Behavior/Add': async (ctx) => {
    const b = params(ctx);
    await put('behavior_event', {
      id: newClientId(), event: str(ctx, 'event', String(b.action ?? b.event ?? b.kind ?? 'unknown')),
      kind: b.kind ?? '', content: b.content ?? '', contact: b.contact ?? '',
      params: JSON.stringify(b), create_time: nowSec(), reported: 1,
    });
    return empty();
  },
  'Config/GetV2': async (ctx) => {
    const rows = await all<{ key: string; value: unknown }>('remote_config');
    const cfg: Record<string, unknown> = {};
    for (const r of rows) cfg[r.key] = r.value;
    return ok({
      ...cfg,
      appVersion: str(ctx, 'appVersion', '1.8.1'), brand: str(ctx, 'brand'),
      rom: str(ctx, 'rom'), romVersion: int(ctx, 'romVersion'),
      channel: str(ctx, 'channel'), reChannel: str(ctx, 'reChannel'),
      discountType: str(ctx, 'discountType'), androidVersion: int(ctx, 'androidVersion'),
      registrationTime: int(ctx, 'registrationTime'), memberId: int(ctx, 'memberId', LOCAL_MEMBER_ID),
      urlType: 'configManage', serverTime: nowSec(),
    });
  },
};

async function loginThird(ctx: RequestCtx, type: string) {
  const u = await currentUser();
  const id = type === 'qq' ? str(ctx, 'qqId') : str(ctx, 'openid');
  const user = {
    ...u, nickname: str(ctx, 'nickname', String(u.nickname ?? '本地用户')),
    avatar: str(ctx, 'avatar'), gender: int(ctx, 'gender'),
    third_id: id, login_type: type, token: `local_${newClientId()}`, login_time: nowSec(),
  };
  await save('member', { id: LOCAL_MEMBER_ID, ...user });
  return ok(user);
}

async function bindThird(id: string, second: string, type: string) {
  const u = await currentUser();
  const user = { ...u, bind_type: type, bind_id: id, bind_extra: second, bind_time: nowSec() };
  await save('member', { id: LOCAL_MEMBER_ID, ...user });
  return ok(user);
}

export const extraHandlers = {
  'Member/GetMemberContractInfo': handlers['Member/GetMemberContractInfo'],
};
