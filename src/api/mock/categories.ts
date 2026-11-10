/** Cate(6) + MemberTag(4) + Budget(9) + KeyWordMatchingRule(4) --------------- */
import {
  ok, fail, all, get, put, remove, nextId, newClientId, nowSec, todayDay,
  params, str, num, int, arr, itemsOf, save, drop, empty, sortBy, moneyCents,
  type RequestCtx,
} from './helpers';
import { LOCAL_MEMBER_ID } from '../../db/schema';

export const handlers: Record<string, (ctx: RequestCtx) => Promise<unknown> | unknown> = {
  /* ---------------- Cate ---------------- */
  'Cate/Create': async (ctx) => {
    const id = await nextId('category');
    const row = {
      id, cate_id: id, member_id: LOCAL_MEMBER_ID, type: int(ctx, 'type', 1),
      title: str(ctx, 'title', '新分类'), icon_id: int(ctx, 'iconId', id),
      base_icon: 'more', icon: 'more', is_custom: 1, status: 1,
      sort: id, hidden: 0, create_time: nowSec(),
    };
    await save('category', row);
    return ok(row);
  },
  'Cate/Update': async (ctx) => {
    const id = int(ctx, 'id');
    const old = await get<Record<string, unknown>>('category', [id, LOCAL_MEMBER_ID]);
    if (!old) return fail(404, '分类不存在');
    const row = {
      ...old, cate_id: int(ctx, 'cateId', Number(old.cate_id ?? id)),
      title: str(ctx, 'title', String(old.title ?? '')), type: int(ctx, 'type', Number(old.type ?? 1)),
      icon_id: int(ctx, 'iconId', Number(old.icon_id ?? 1)), update_time: nowSec(),
    };
    await save('category', row);
    return ok(row);
  },
  'Cate/Delete': async (ctx) => {
    const id = int(ctx, 'id');
    await remove('category', [id, LOCAL_MEMBER_ID]);
    return empty();
  },
  'Cate/GetMainCates': async () => {
    const rows = await all<Record<string, unknown>>('category');
    return ok({
      expense: sortBy(rows.filter((r) => Number(r.type) === 1 && Number(r.hidden) === 0), 'sort'),
      income: sortBy(rows.filter((r) => Number(r.type) === 2 && Number(r.hidden) === 0), 'sort'),
      transfer: [],
    });
  },
  'Cate/GetCateIcons': async () => {
    const rows = await all<Record<string, unknown>>('icon_dict');
    return ok(rows.filter((r) => r.type === 'cate' || r.type === undefined || r.type === 'book'));
  },
  'Cate/UpdateExtend': async (ctx) => {
    const list = itemsOf<Record<string, unknown>>(ctx);
    for (const r of list) {
      await put('cate_extend', {
        member_cate_id: Number(r.memberCateId ?? r.member_cate_id ?? 0),
        book_id: Number(r.bookId ?? r.billsBookId ?? 1),
        member_id: LOCAL_MEMBER_ID, hidden: Number(r.hidden ?? 0), sort: Number(r.sort ?? 0),
      });
    }
    return empty();
  },

  /* ---------------- MemberTag ---------------- */
  'MemberTag/Create': async (ctx) => {
    const id = await nextId('tag');
    const row = {
      tag_id: id, book_id: int(ctx, 'BillBookId', int(ctx, 'billsBookId', 1)),
      member_id: LOCAL_MEMBER_ID, name: str(ctx, 'name', '新标签'),
      sort: id, create_date: nowSec(), update_date: nowSec(),
    };
    await save('tag', row);
    return ok(row);
  },
  'MemberTag/Update': async (ctx) => {
    const id = int(ctx, 'tagId');
    const old = await get<Record<string, unknown>>('tag', id);
    if (!old) return fail(404, '标签不存在');
    const row = { ...old, name: str(ctx, 'name', String(old.name ?? '')), update_date: nowSec() };
    await save('tag', row);
    return ok(row);
  },
  'MemberTag/Delete': async (ctx) => drop('tag', int(ctx, 'tagId')),
  'MemberTag/UpdateSort': async (ctx) => {
    const list = itemsOf<Record<string, unknown>>(ctx);
    for (const [i, item] of list.entries()) {
      const id = Number(item.tagId ?? item.tag_id ?? 0);
      const old = await get<Record<string, unknown>>('tag', id);
      if (old) await save('tag', { ...old, sort: Number(item.sort ?? i + 1) });
    }
    return empty();
  },

  /* ---------------- Budget ---------------- */
  'Budget/CreateBudget': async (ctx) => budgetWrite(ctx, false),
  'Budget/UpdateBudget': async (ctx) => budgetWrite(ctx, true),
  'Budget/DeleteBudget': async (ctx) => drop('budget', [int(ctx, 'billsBookId', 1), int(ctx, 'day', todayMonth())]),
  'Budget/GetBudget': async (ctx) => ok(await budget(ctx)),
  'Budget/GetBudgetList': async () => ok(sortBy(await all('budget'), 'day', true).map(withDetail)),
  'Budget/GetYearlyBudgets': async (ctx) => {
    const year = int(ctx, 'year', new Date().getFullYear());
    const rows = (await all<Record<string, unknown>>('budget')).filter((r) => Math.trunc(Number(r.day) / 100) >= year * 100 && Math.trunc(Number(r.day) / 100) < (year + 1) * 100);
    return ok(sortBy(rows, 'day'));
  },
  'Budget/CreateBudgetCate': async (ctx) => cateBudgetWrite(ctx),
  'Budget/UpdateBudgetCate': async (ctx) => cateBudgetWrite(ctx),
  'Budget/DeleteBudgetCate': async (ctx) => drop('cate_budget', [int(ctx, 'billsBookId', 1), int(ctx, 'day', todayMonth()), int(ctx, 'cateId')]),

  /* ---------------- KeyWordMatchingRule ---------------- */
  'KeyWordMatchingRule/Create': async (ctx) => ruleWrite(ctx, false),
  'KeyWordMatchingRule/Update': async (ctx) => ruleWrite(ctx, true),
  'KeyWordMatchingRule/Delete': async (ctx) => drop('rule', int(ctx, 'id')),
  'KeyWordMatchingRule/List': async () => ok(sortBy(await all('rule'), 'create_date', true)),
};

function todayMonth(): number { return Math.trunc(todayDay() / 100); }

async function budget(ctx: RequestCtx): Promise<Record<string, unknown>> {
  const key = [int(ctx, 'billsBookId', 1), int(ctx, 'day', todayMonth())];
  const row = (await get<Record<string, unknown>>('budget', key)) || { ...emptyBudget(key[0] as number, key[1] as number) };
  return withDetail(row);
}

function emptyBudget(bookId: number, day: number): Record<string, unknown> {
  return { book_id: bookId, day, money: 0, acf_money: 0, is_remind: 0, remind_rate: 80, carry_down: 0, is_copy: 0 };
}

function withDetail(row: Record<string, unknown>): Record<string, unknown> {
  return { ...row, spent: 0, remaining: Number(row.money ?? 0) - Number(row.acf_money ?? 0), cateBudgets: [] };
}

async function budgetWrite(ctx: RequestCtx, update: boolean) {
  const key = [int(ctx, 'billsBookId', 1), int(ctx, 'day', todayMonth())];
  const old = (await get<Record<string, unknown>>('budget', key)) || emptyBudget(key[0], key[1]);
  if (update && !(await get('budget', key))) return fail(404, '预算不存在');
  await save('budget', {
    ...old, money: moneyCents(ctx), acf_money: moneyCents(ctx, 'acfMoney', Number(old.acf_money ?? 0)),
    is_remind: int(ctx, 'isRemind', Number(old.is_remind ?? 0)),
    remind_rate: int(ctx, 'remindRate', Number(old.remind_rate ?? 80)),
    carry_down: int(ctx, 'carryDown', Number(old.carry_down ?? 0)),
    is_copy: int(ctx, 'isCopy', Number(old.is_copy ?? 0)), update_date: nowSec(),
  });
  return empty();
}

async function cateBudgetWrite(ctx: RequestCtx) {
  const key = [int(ctx, 'billsBookId', 1), int(ctx, 'day', todayMonth()), int(ctx, 'cateId')];
  const old = (await get<Record<string, unknown>>('cate_budget', key)) || {
    book_id: key[0], day: key[1], member_cate_id: key[2], money: 0, acf_money: 0,
  };
  await save('cate_budget', { ...old, money: moneyCents(ctx), acf_money: Number(old.acf_money ?? 0) });
  return ok(await budget(ctx));
}

async function ruleWrite(ctx: RequestCtx, update: boolean) {
  const id = update ? int(ctx, 'id') : await nextId('rule');
  if (update && !(await get('rule', id))) return fail(404, '规则不存在');
  const row = {
    id, key_word: str(ctx, 'keyWord'), type: int(ctx, 'type', 1),
    member_cate_id: int(ctx, 'memberCateId', 9), book_id: int(ctx, 'billsBookId', 1),
    assets_id: int(ctx, 'fundAccountId', 1), member_tag_ids: str(ctx, 'memberTagIds'),
    tag_ids: str(ctx, 'memberTagIds'), source: 0, member_id: LOCAL_MEMBER_ID,
    create_date: nowSec(), update_date: nowSec(),
  };
  await save('rule', row);
  return ok(row);
}
