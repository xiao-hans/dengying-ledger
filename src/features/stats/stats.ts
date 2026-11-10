/** 统计纯函数：所有报表共用，金额单位一律整数分。 */
import type { Dict } from '../common/api';
import { N, S, dayMonth } from '../common/format';

export interface Summary {
  expense: number; income: number; net: number; count: number;
  byCate: { id: number; name: string; cents: number; count: number; pct: number }[];
  byDay: { day: string; expense: number; income: number }[];
}

export function summarize(rows: Dict[], type: 1 | 2, label: (id: number) => string): Summary {
  const picked = rows.filter((r) => N(r.type, 1) === type);
  const expense = type === 1 ? picked.reduce((s, r) => s + N(r.money), 0) : 0;
  const income = type === 2 ? picked.reduce((s, r) => s + N(r.money), 0) : 0;
  const all = rows.filter((r) => N(r.type, 1) === 1 || N(r.type, 1) === 2);
  const byCateMap = new Map<number, { cents: number; count: number }>();
  for (const r of picked) {
    const id = N(r.member_cate_id, 9);
    const cur = byCateMap.get(id) || { cents: 0, count: 0 };
    cur.cents += N(r.money); cur.count += 1;
    byCateMap.set(id, cur);
  }
  const total = picked.reduce((s, r) => s + N(r.money), 0) || 1;
  const byCate = [...byCateMap.entries()].map(([id, v]) => ({
    id, name: label(id), cents: v.cents, count: v.count, pct: v.cents / total,
  })).sort((a, b) => b.cents - a.cents);
  const byDayMap = new Map<string, { expense: number; income: number }>();
  for (const r of all) {
    const k = S(r.day);
    if (!byDayMap.has(k)) byDayMap.set(k, { expense: 0, income: 0 });
    const v = byDayMap.get(k)!;
    if (N(r.type, 1) === 1) v.expense += N(r.money);
    else v.income += N(r.money);
  }
  const byDay = [...byDayMap.entries()].map(([day, v]) => ({ day, ...v })).sort((a, b) => a.day.localeCompare(b.day));
  const totalExpense = all.filter((r) => N(r.type, 1) === 1).reduce((s, r) => s + N(r.money), 0);
  const totalIncome = all.filter((r) => N(r.type, 1) === 2).reduce((s, r) => s + N(r.money), 0);
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
