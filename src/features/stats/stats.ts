/** 统计纯函数：所有报表共用，金额单位一律整数分。 */
import type { Dict } from '../common/api';
import { N, S, dayMonth } from '../common/format';

export interface Summary {
  expense: number; income: number; net: number; count: number;
  byCate: { id: number; name: string; cents: number; count: number; pct: number }[];
  byDay: { day: string; expense: number; income: number }[];
}

/** 退款按账单 client_id 归集。统计一律用净额，避免退款后支出被多记。 */
export function refundByBill(refunds: Dict[]): Map<string, number> {
  const map = new Map<string, number>();
  for (const r of refunds) {
    const key = S(r.client_id);
    if (!key) continue;
    map.set(key, (map.get(key) ?? 0) + N(r.money));
  }
  return map;
}

export function netMoney(row: Dict, refunds: Map<string, number>): number {
  return Math.max(0, N(row.money) - (refunds.get(S(row.client_id)) ?? 0));
}

export function summarize(rows: Dict[], type: 1 | 2, label: (id: number) => string, refunds: Map<string, number> = new Map()): Summary {
  const amount = (r: Dict) => netMoney(r, refunds);
  const picked = rows.filter((r) => N(r.type, 1) === type);
  const expense = type === 1 ? picked.reduce((s, r) => s + amount(r), 0) : 0;
  const income = type === 2 ? picked.reduce((s, r) => s + amount(r), 0) : 0;
  const all = rows.filter((r) => N(r.type, 1) === 1 || N(r.type, 1) === 2);
  const byCateMap = new Map<number, { cents: number; count: number }>();
  for (const r of picked) {
    const id = N(r.member_cate_id, 9);
    const cur = byCateMap.get(id) || { cents: 0, count: 0 };
    cur.cents += amount(r); cur.count += 1;
    byCateMap.set(id, cur);
  }
  const total = picked.reduce((s, r) => s + amount(r), 0) || 1;
  const byCate = [...byCateMap.entries()].map(([id, v]) => ({
    id, name: label(id), cents: v.cents, count: v.count, pct: v.cents / total,
  })).sort((a, b) => b.cents - a.cents);
  const byDayMap = new Map<string, { expense: number; income: number }>();
  for (const r of all) {
    const k = S(r.day);
    if (!byDayMap.has(k)) byDayMap.set(k, { expense: 0, income: 0 });
    const v = byDayMap.get(k)!;
    if (N(r.type, 1) === 1) v.expense += amount(r);
    else v.income += amount(r);
  }
  const byDay = [...byDayMap.entries()].map(([day, v]) => ({ day, ...v })).sort((a, b) => a.day.localeCompare(b.day));
  const totalExpense = all.filter((r) => N(r.type, 1) === 1).reduce((s, r) => s + amount(r), 0);
  const totalIncome = all.filter((r) => N(r.type, 1) === 2).reduce((s, r) => s + amount(r), 0);
  return {
    expense: totalExpense, income: totalIncome, net: totalIncome - totalExpense,
    count: all.length, byCate, byDay,
  };
}
export function periodKey(day: unknown, period: 'month' | 'year'): number {
  const m = dayMonth(day);
  return period === 'year' ? Math.trunc(m / 100) : m;
}
export function periodLabel(key: number, period: 'month' | 'year'): string {
  return period === 'year' ? `${key}年` : `${Math.trunc(key / 100)}年${key % 100}月`;
}
