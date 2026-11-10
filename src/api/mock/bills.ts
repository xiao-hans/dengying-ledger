/** Bills(11 唯一路径 / 12 注解) + BillRefund(3) + BillsBook(10) + Upload(3) --------------- */
import {
  ok, fail, all, get, put, remove, nextId, newClientId, nowMs, nowSec, todayDay,
  params, str, num, int, boolish, arr, itemsOf, save, drop, empty, pageOf, sortBy,
  moneyCents, dayFrom, type RequestCtx,
} from './helpers';
import { LOCAL_MEMBER_ID } from '../../db/schema';

const billKey = (clientId: string, memberId = LOCAL_MEMBER_ID) => [memberId, clientId];

interface BillItem { clientId?: string; client_id?: string; [k: string]: unknown }

function clientOf(item: BillItem): string {
  return String(item.clientId ?? item.client_id ?? newClientId());
}

/** BillItem → bill 表行；金额统一整数分，day 由发生时间推导。 */
function toBill(item: BillItem, memberId = LOCAL_MEMBER_ID): Record<string, unknown> {
  const clientId = clientOf(item);
  const ts = Number(item.createDate ?? item.create_date ?? item.time ?? item.occurredAt ?? nowMs());
  const day = Number(item.day ?? dayFrom(Number.isFinite(ts) ? ts : nowMs()));
  const money = Math.round(Number(item.money ?? 0));
  return {
    member_id: memberId, client_id: clientId, money,
    refund_money: Math.round(Number(item.refundMoney ?? item.refund_money ?? 0)),
    type: Number(item.billType ?? item.type ?? 1) || 1,
    exchange: Number(item.exchange ?? 0), book_id: Number(item.billBookId ?? item.bookId ?? item.book_id ?? 1),
    member_cate_id: Number(item.memberCateId ?? item.cateId ?? item.member_cate_id ?? 9),
    currency: String(item.currency ?? 'CNY'), day,
    desc: String(item.desc ?? item.description ?? item.remark ?? ''),
    not_in_budget: Number(item.notInBudget ?? item.not_in_budget ?? 0),
    images: JSON.stringify(item.images ?? []),
    update_date: nowSec(), create_date: Math.floor(ts / 1000) || nowSec(),
    status: Number(item.status ?? 1),
    to_assets_id: Number(item.toFundAccountId ?? item.toAssetsId ?? item.to_assets_id ?? 0),
    from_assets_id: Number(item.fromFundAccountId ?? item.fromAssetsId ?? item.from_assets_id ?? 1),
    interest: Math.round(Number(item.interest ?? 0)),
    interest_reckon_type: Number(item.interestReckonType ?? item.interest_reckon_type ?? 0),
    tag_ids: JSON.stringify(toArray(item.tagIds ?? item.tag_ids)),
    source: Number(item.source ?? 0), import_id: Number(item.importId ?? item.import_id ?? 0),
    repayment_day: Number(item.repaymentDay ?? item.repayment_day ?? 0),
    local_create_time: nowSec(), local_update_time: nowSec(),
    installment_plan_id: String(item.installmentPlanId ?? ''),
    installment_num: Number(item.installmentNum ?? 0),
    installment_money: Math.round(Number(item.installmentMoney ?? 0)),
    installment_interest: Math.round(Number(item.installmentInterest ?? 0)),
  };
}

function toArray(v: unknown): unknown[] {
  if (Array.isArray(v)) return v;
  if (typeof v === 'string' && v.trim()) { try { const p = JSON.parse(v); return Array.isArray(p) ? p : [p]; } catch { return [v]; } }
  return [];
}

export const handlers: Record<string, (ctx: RequestCtx) => Promise<unknown> | unknown> = {
  'Bills/Create': async (ctx) => {
    const list = itemsOf<BillItem>(ctx);
    for (const item of list) await save('bill', toBill(item));
    return ok({ count: list.length });
  },
  'Bills/Update': async (ctx) => {
    const list = itemsOf<BillItem>(ctx);
    for (const item of list) {
      const key = billKey(clientOf(item));
      const old = await get('bill', key);
      await save('bill', { ...toBill(item), ...(old || {}), local_update_time: nowSec() });
    }
    return ok({ count: list.length });
  },
  'Bills/Delete': async (ctx) => {
    const list = itemsOf<BillItem>(ctx);
    for (const item of list) await remove('bill', billKey(clientOf(item), int(ctx, 'memberId', LOCAL_MEMBER_ID)));
    return ok({ count: list.length });
  },
  'Bills/PageList': async (ctx) => {
    let rows = await all<Record<string, unknown>>('bill');
    const since = int(ctx, 'updateDate', 0);
    if (since > 0) rows = rows.filter((r) => Number(r.update_date ?? 0) >= since);
    rows = sortBy(rows, 'day', true);
    const page = pageOf(rows, int(ctx, 'pageNum', 1), 20);
    return ok({ ...page, bills: page.items, list: page.items, updateDate: nowSec() });
  },
  'Bills/BillsCountOfHasPic': async () => {
    const rows = await all<Record<string, unknown>>('bill');
    return ok(rows.filter((r) => r.images && String(r.images) !== '[]' && String(r.images) !== '').length);
  },
  'Bills/AutoBillsCallBack': async (ctx) => {
    const type = int(ctx, 'type', 0);
    const rows = await all<Record<string, unknown>>('auto_event');
    const done = rows.filter((r) => Number(r.status) === 1);
    return ok({ type, identified: rows.length, booked: done.length, callbackTime: nowSec() });
  },
  'Bills/CreateFixedBill': async (ctx) => createFixed(ctx, false),
  'Bills/UpdateFixedBill': async (ctx) => createFixed(ctx, true),
  'Bills/DeleteFixedBill': async (ctx) => drop('fixed_bill', str(ctx, 'id')),
  'Bills/GetFixedBills': async () => ok({
    items: sortBy(await all('fixed_bill'), 'create_time', true),
    total: (await all('fixed_bill')).length,
  }),
  // 两个同名重载：ImportBillsReq（账单列表）/ ImportOcrBillsReq（OCR 文本）→ 同一结果契约
  'Bills/ImportBills': async (ctx) => {
    const p = params(ctx);
    const rawList = itemsOf<BillItem>(ctx);
    const ocrText = String(p.ocrText ?? p.text ?? p.content ?? '');
    const list: BillItem[] = rawList.length ? rawList : ocrText
      ? ocrText.split(/[\r\n]+/).filter(Boolean).map((line) => ({ desc: line, money: 0, clientId: newClientId() }))
      : [];
    const created: string[] = [];
    for (const item of list) {
      const row = toBill({ ...item, source: 4, importId: Number(p.importId ?? p.fileId ?? 0) });
      await save('bill', row);
      created.push(String(row.client_id));
    }
    const fileId = String(p.fileId ?? p.importId ?? newClientId());
    await put('member_import_bill_file', {
      id: fileId, name: String(p.fileName ?? p.name ?? '导入账单'), type: String(p.type ?? 'file'),
      count: created.length, status: 1, create_time: nowSec(), content: ocrText,
    });
    return ok({ success: created.length, failed: 0, repeated: 0, clientIds: created, fileId });
  },

  'BillRefund/Create': async (ctx) => {
    const id = newClientId();
    const row = {
      id, client_id: str(ctx, 'clientId', str(ctx, 'billClientId')), member_id: LOCAL_MEMBER_ID,
      money: moneyCents(ctx), asset_id: int(ctx, 'assetId', int(ctx, 'fundAccountId', 1)),
      day: int(ctx, 'day', todayDay()), desc: str(ctx, 'desc'), images: str(ctx, 'images'),
      source: int(ctx, 'source', 0), status: 1, create_date: nowSec(), update_date: nowSec(),
    };
    await save('refund_bill', row);
    return ok(row);
  },
  'BillRefund/Update': async (ctx) => {
    const id = str(ctx, 'id', str(ctx, 'refundId'));
    const old = await get<Record<string, unknown>>('refund_bill', id);
    if (!old) return fail(404, '退款记录不存在');
    const row = { ...old, ...params(ctx), id, update_date: nowSec() };
    await save('refund_bill', row);
    return ok(row);
  },
  'BillRefund/Delete': async (ctx) => drop('refund_bill', str(ctx, 'id', str(ctx, 'refundId'))),

  'BillsBook/Create': async (ctx) => {
    const id = await nextId('bill_book');
    const row = {
      id, name: str(ctx, 'name', '新账本'), icon_id: int(ctx, 'iconId', 1), icon: 'book',
      tag: '', create_date: nowMs(), month_start_date: int(ctx, 'monthStartDate', 1),
      member_count: 1, role: 'owner',
    };
    await save('bill_book', row);
    await put('member_book', { member_id: LOCAL_MEMBER_ID, book_id: id, role: 'owner' });
    return ok(row);
  },
  'BillsBook/Update': async (ctx) => {
    const id = int(ctx, 'billsBookId');
    const old = await get<Record<string, unknown>>('bill_book', id);
    if (!old) return fail(404, '账本不存在');
    const row = {
      ...old, name: str(ctx, 'name', String(old.name ?? '')), icon_id: int(ctx, 'iconId', Number(old.icon_id ?? 1)),
      month_start_date: int(ctx, 'monthStartDate', Number(old.month_start_date ?? 1)),
    };
    await save('bill_book', row);
    return ok(row);
  },
  'BillsBook/Delete': async (ctx) => drop('bill_book', int(ctx, 'billsBookId')),
  'BillsBook/GetList': async () => ok(sortBy(await all('bill_book'), 'create_date')),
  'BillsBook/GetInfoByCode': async (ctx) => {
    const code = str(ctx, 'code');
    const row = { id: Math.abs(hashCode(code)) % 100000, name: '共享账本', code, icon_id: 1, icon: 'book', member_count: 1 };
    return ok(row);
  },
  'BillsBook/CreateInviteCode': async (ctx) => ok(`DY${int(ctx, 'billsBookId', 1).toString(36).toUpperCase()}${newClientId().slice(2, 8).toUpperCase()}`),
  'BillsBook/Join': async (ctx) => {
    const id = int(ctx, 'billsBookId', 1);
    await put('member_book', { member_id: LOCAL_MEMBER_ID, book_id: id, role: 'member' });
    const book = (await get('bill_book', id)) || { id, name: '共享账本', icon_id: 1, icon: 'book' };
    return ok(book);
  },
  'BillsBook/Exit': async (ctx) => {
    const id = int(ctx, 'billsBookId');
    await remove('member_book', [LOCAL_MEMBER_ID, id]);
    return empty();
  },
  'BillsBook/RemoveMember': async (ctx) => {
    await remove('member_book', [int(ctx, 'memberId'), int(ctx, 'billsBookId')]);
    return ok({ removed: 1 });
  },
  'BillsBook/GetBillsBookIcons': async () => {
    const rows = await all<Record<string, unknown>>('icon_dict');
    return ok(rows.filter((r) => r.type === 'book' || r.type === undefined));
  },

  'Upload/UploadImg': async (ctx) => ok({
    imgType: int(ctx, 'imgType'), provider: 'local', bucket: 'dengying-local',
    accessKeyId: '', policy: '', signature: '', key: `img/${newClientId()}.png`,
    host: '', expire: nowSec() + 3600,
  }),
  'Upload/UploadImages': async (ctx) => ok({
    urls: toArray((ctx.body as Record<string, unknown> | null)?.files ?? params(ctx).files).map((_, i) => `local://img/${newClientId()}_${i}.png`),
    keys: [],
  }),
  'Upload/UploadBills': async (ctx) => {
    const p = params(ctx);
    const fileId = newClientId();
    await put('member_import_bill_file', {
      id: fileId, name: String(p.fileName ?? p.name ?? '上传账单'), type: String(p.type ?? 'file'),
      count: 0, status: 1, create_time: nowSec(), content: '',
    });
    return ok({ fileId, success: 0, failed: 0, repeated: 0, needConfirm: 1 });
  },
};

async function createFixed(ctx: RequestCtx, update: boolean) {
  const id = update ? str(ctx, 'id') : newClientId();
  if (update && !(await get('fixed_bill', id))) return fail(404, '周期账不存在');
  const row = {
    id, member_cate_id: int(ctx, 'memberCateId', 9), money: moneyCents(ctx),
    type: int(ctx, 'billType', 1), fixed_type: int(ctx, 'fixedType', 1),
    fixed_day: int(ctx, 'fixedDay', 1), book_id: int(ctx, 'billBookId', 1),
    assets_id: int(ctx, 'fromFundAccountId', 1), desc: str(ctx, 'desc'),
    tag_ids: JSON.stringify(toArray(ctx.fields.tagIds ?? params(ctx).tagIds)),
    interest: moneyCents(ctx, 'interest'), not_in_budget: int(ctx, 'notInBudget', 0),
    currency: str(ctx, 'currency', 'CNY'), start_date: str(ctx, 'startDate'),
    end_date: str(ctx, 'endDate'), status: 1, next_execute_date: nowSec(),
    create_time: nowSec(), update_time: nowSec(),
  };
  await save('fixed_bill', row);
  return ok(row);
}

function hashCode(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0;
  return h;
}
