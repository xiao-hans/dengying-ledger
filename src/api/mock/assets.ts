/** FundAccount(6) + ConsumptionInstallment(6) + SavePlan(14) + Order(5) + VipGroupBuy(3) + Operation(2) + PersonCenter(1) --- */
import {
  ok, fail, all, get, put, remove, nextId, newClientId, nowMs, nowSec, todayDay,
  params, str, num, int, arr, itemsOf, save, drop, empty, pageOf, sortBy,
  moneyCents, dayFrom, type RequestCtx,
} from './helpers';
import { LOCAL_MEMBER_ID } from '../../db/schema';

export const handlers: Record<string, (ctx: RequestCtx) => Promise<unknown> | unknown> = {
  /* ---------------- FundAccount ---------------- */
  'FundAccount/Create': async (ctx) => assetWrite(ctx, false),
  'FundAccount/Update': async (ctx) => assetWrite(ctx, true),
  'FundAccount/Delete': async (ctx) => drop('assets', int(ctx, 'id')),
  'FundAccount/UpdateStatus': async (ctx) => {
    const id = int(ctx, 'id');
    const old = await get<Record<string, unknown>>('assets', id);
    if (!old) return fail(404, '账户不存在');
    const row = { ...old, status: int(ctx, 'status', 1) };
    await save('assets', row);
    return empty();
  },
  'FundAccount/GetFundAccountTypes': async () => ok(sortBy(await all('assets_type'), 'sort')),
  'FundAccount/GetIOU': async (ctx) => {
    const status = int(ctx, 'status', 0);
    const rows = (await all<Record<string, unknown>>('assets')).filter((r) => Number(r.type) === 6 && (status === 0 || Number(r.status) === status));
    return ok(sortBy(rows, 'id').map((r) => String(r.name ?? '')).join(','));
  },

  /* ---------------- ConsumptionInstallment ---------------- */
  'ConsumptionInstallment/Create': async (ctx) => {
    const p = params(ctx);
    const id = newClientId();
    const money = moneyCents(ctx) || Number(p.totalMoney ?? p.money ?? 0);
    const count = int(ctx, 'totalCount', Number(p.totalCount ?? 12)) || 12;
    const row = {
      id, member_id: LOCAL_MEMBER_ID, name: str(ctx, 'name', str(ctx, 'desc', '分期计划')),
      deduction_asset_id: int(ctx, 'formFundAccountId', int(ctx, 'fundAccountId', 1)),
      start_date: str(ctx, 'startDate') || new Date().toISOString().slice(0, 10),
      money, total_count: count, executed_count: 0,
      interest: moneyCents(ctx, 'interest'), interest_type: int(ctx, 'interestType'),
      remainder_process_type: int(ctx, 'remainderProcessType'),
      remaining_money: money, remaining_interest: moneyCents(ctx, 'interest'),
      next_execute_date: nowSec(), book_id: int(ctx, 'billBookId', 1),
      member_cate_id: int(ctx, 'memberCateId', 9), not_in_budget: 0,
      desc: str(ctx, 'desc'), images: '', tag_ids: '', status: 1,
      create_time: nowSec(), update_time: nowSec(),
    };
    await save('installment_plan', row);
    for (let i = 1; i <= count; i++) {
      await put('installment_item', {
        id: `${id}_${i}`, plan_id: id, num: i,
        money: Math.floor(money / count), interest: Math.floor(Number(row.interest) / count),
        status: 0, execute_date: 0,
      });
    }
    return ok(row);
  },
  'ConsumptionInstallment/AllPlan': async () => ok(sortBy(await all('installment_plan'), 'create_time', true)),
  'ConsumptionInstallment/GetByPlanId': async (ctx) => planWithItems(str(ctx, 'planId')),
  'ConsumptionInstallment/GetByClientId': async (ctx) => planWithItems(str(ctx, 'clientId')),
  'ConsumptionInstallment/Delete': async (ctx) => {
    const id = str(ctx, 'planId');
    const saveBill = int(ctx, 'isSaveBill', 1);
    const plan = await get<Record<string, unknown>>('installment_plan', id);
    if (plan && !saveBill) {
      const bills = await all<Record<string, unknown>>('bill');
      for (const b of bills.filter((b) => b.installment_plan_id === id)) {
        await remove('bill', [Number(b.member_id) || 0, String(b.client_id ?? '')]);
      }
    }
    await remove('installment_plan', id);
    return ok({ items: [id] });
  },
  'ConsumptionInstallment/PayOff': async (ctx) => {
    const id = str(ctx, 'planId');
    const plan = await get<Record<string, unknown>>('installment_plan', id);
    if (!plan) return fail(404, '分期计划不存在');
    const row = { ...plan, status: 2, remaining_money: 0, remaining_interest: 0, update_time: nowSec() };
    await save('installment_plan', row);
    return ok(row);
  },

  /* ---------------- SavePlan ---------------- */
  'SavePlan/CreateSavePlan': async (ctx) => {
    const p = params(ctx);
    const id = newClientId();
    const row = {
      id, name: str(ctx, 'name', String(p.title ?? '存钱计划')), icon: str(ctx, 'icon', 'star'),
      type_id: int(ctx, 'typeId', int(ctx, 'type', 1)), target_money: moneyCents(ctx, 'targetMoney', moneyCents(ctx)),
      saved_money: 0, status: 1, archived: 0, desc: str(ctx, 'desc'),
      create_time: nowSec(), update_time: nowSec(),
    };
    await save('save_plan', row);
    return ok(row);
  },
  'SavePlan/UpdateSavePlan': async (ctx) => planPatch(ctx),
  'SavePlan/UpdateSavePlanStatus': async (ctx) => {
    const ids = arr<string>(ctx, 'ids', 'planIds', 'items');
    for (const id of ids) {
      const old = await get<Record<string, unknown>>('save_plan', id);
      if (old) await save('save_plan', { ...old, status: int(ctx, 'status', 1), update_time: nowSec() });
    }
    return ok(ids);
  },
  'SavePlan/GetSavePlans': async () => ok(sortBy(await all('save_plan'), 'create_time', true)),
  'SavePlan/GetFileSavePlans': async () => ok((await all<Record<string, unknown>>('save_plan')).filter((r) => Number(r.archived) === 1)),
  'SavePlan/GetSavePlansByType': async (ctx) => {
    const type = int(ctx, 'type', 0);
    return ok((await all<Record<string, unknown>>('save_plan')).filter((r) => type === 0 || Number(r.type_id) === type));
  },
  'SavePlan/GetSavePlansByQuery': async (ctx) => {
    const kw = str(ctx, 'keyword', str(ctx, 'name'));
    const rows = (await all<Record<string, unknown>>('save_plan')).filter((r) => !kw || String(r.name ?? '').includes(kw));
    return ok(sortBy(rows, 'create_time', true));
  },
  'SavePlan/SavePlanItemsById': async (ctx) => planWithItems(str(ctx, 'planId', str(ctx, 'id'))),
  'SavePlan/CreateSavePlanItem': async (ctx) => {
    const p = params(ctx);
    const id = newClientId();
    const planId = str(ctx, 'planId', str(ctx, 'savePlanId'));
    const plan = await get<Record<string, unknown>>('save_plan', planId);
    const money = moneyCents(ctx);
    const row = {
      id, plan_id: planId, num: int(ctx, 'num', 1), money, status: 1,
      remark: str(ctx, 'remark', str(ctx, 'desc')), create_time: nowSec(), update_time: nowSec(),
    };
    await save('save_plan_item', row);
    if (plan) await save('save_plan', { ...plan, saved_money: Number(plan.saved_money ?? 0) + money, update_time: nowSec() });
    return ok(row);
  },
  'SavePlan/UpdateSavePlanItem': async (ctx) => planItemPatch(ctx),
  'SavePlan/UpdateSavePlanItemStatus': async (ctx) => planItemPatch(ctx),
  'SavePlan/Wishes': async () => ok((await all<{ name: string }>('wish')).map((r) => r.name)),
  'SavePlan/GetIcon': async () => ok(sortBy(await all('plan_cover'), 'id')),
  'SavePlan/GetSavePlanType': async () => ok(sortBy(await all('plan_type'), 'id')),

  /* ---------------- Order ---------------- */
  'Order/v2/VipConfigs': async () => ok((await all('order_config'))[0] || {}),
  'Order/v2/CreateOrder': async (ctx) => {
    const id = newClientId();
    const row = {
      id, addFreeId: str(ctx, 'addFreeId'), money: moneyCents(ctx),
      method: str(ctx, 'method', 'local'), type: int(ctx, 'type', 1),
      productId: str(ctx, 'productId'), orderSource: str(ctx, 'orderSource'),
      useRedEnvelope: int(ctx, 'useRedEnvelope'), status: 1, create_time: nowSec(),
    };
    await save('order', row);
    return ok(row);
  },
  'Order/CheckStatus': async (ctx) => {
    const row = await get<Record<string, unknown>>('order', str(ctx, 'orderId'));
    return ok(row || { orderId: str(ctx, 'orderId'), status: 0, paid: 0 });
  },
  'Order/GetRechargeDiscountConfig': async () => ok((await all('discount_config'))[0] || {}),
  'Order/AlipayUnSign': async () => empty(),

  /* ---------------- VipGroupBuy ---------------- */
  'VipGroupBuy/List': async () => ok(sortBy(await all('group_vip'), 'id')),
  'VipGroupBuy/Bind': async (ctx) => {
    const showId = str(ctx, 'showId');
    const row = { id: showId || newClientId(), showId, member_id: LOCAL_MEMBER_ID, status: 1, create_time: nowSec() };
    await save('group_vip', row);
    return ok([row]);
  },
  'VipGroupBuy/GetByShowId': async (ctx) => {
    const row = await get('group_vip', str(ctx, 'showId'));
    return ok(row || { isVip: 0, expireTime: 0, showId: str(ctx, 'showId') });
  },

  /* ---------------- Operation / PersonCenter ---------------- */
  'Operation/GetDialog': async () => ok((await all('operate_dialog'))[0] || { show: 0, title: '', content: '', image: '', button: '' }),
  'Operation/GetUpdateRemind': async () => ok((await all('update_remind'))[0] || { show: 0, version: '1.8.1', content: '', force: 0, url: '' }),
  'PersonCenter/GetPersonCenterIcons': async () => ok(sortBy(await all('person_center_icon'), 'sort')),
};

async function assetWrite(ctx: RequestCtx, update: boolean) {
  const id = update ? int(ctx, 'fundAccountId') : await nextId('assets');
  const old = (await get<Record<string, unknown>>('assets', id)) || {};
  if (update && !(await get('assets', id))) return fail(404, '账户不存在');
  const row = {
    ...old, id,
    type_id: int(ctx, 'typeId', Number(old.type_id ?? 1)), type: int(ctx, 'type', Number(old.type ?? 1)),
    type_name: String(old.type_name ?? 'cash'), type_title: String(old.type_title ?? '现金'),
    name: str(ctx, 'name', String(old.name ?? '新账户')),
    desc: str(ctx, 'desc', String(old.desc ?? '')), icon: str(ctx, 'icon', String(old.icon ?? 'wallet')),
    currency: str(ctx, 'currency', String(old.currency ?? 'CNY')),
    balance_cents: moneyCents(ctx, 'balance', Number(old.balance_cents ?? 0)),
    credit_line: int(ctx, 'creditLine', Number(old.credit_line ?? 0)),
    repayment_date: int(ctx, 'repaymentDate', Number(old.repayment_date ?? 0)),
    statement_date: int(ctx, 'statementDate', Number(old.statement_date ?? 0)),
    is_remind: int(ctx, 'isRemind', Number(old.is_remind ?? 0)),
    is_total_assets: int(ctx, 'isTotalAssets', Number(old.is_total_assets ?? 1)),
    is_next_bills: int(ctx, 'isNextBills', Number(old.is_next_bills ?? 0)),
    clientId: str(ctx, 'clientId', String(old.clientId ?? '')),
    status: Number(old.status ?? 1), create_date: Number(old.create_date ?? nowMs()), update_date: nowMs(),
  };
  await save('assets', row);
  return ok(row);
}

async function planWithItems(id: string) {
  const plan = (await get<Record<string, unknown>>('save_plan', id)) || (await get('installment_plan', id));
  if (!plan) return fail(404, '计划不存在');
  const items = (await all<Record<string, unknown>>('save_plan_item')).filter((r) => r.plan_id === id);
  return ok({ ...plan, items: sortBy(items, 'num') });
}

async function planPatch(ctx: RequestCtx) {
  const id = str(ctx, 'planId', str(ctx, 'id', str(ctx, 'savePlanId')));
  const old = await get<Record<string, unknown>>('save_plan', id);
  if (!old) return fail(404, '存钱计划不存在');
  await save('save_plan', { ...old, ...params(ctx), id, update_time: nowSec() });
  return empty();
}

async function planItemPatch(ctx: RequestCtx) {
  const id = str(ctx, 'itemId', str(ctx, 'id'));
  const old = await get<Record<string, unknown>>('save_plan_item', id);
  if (!old) return fail(404, '存钱记录不存在');
  await save('save_plan_item', { ...old, ...params(ctx), id, update_time: nowSec() });
  return empty();
}
