/**
 * mock 后端公共工具 —— 所有处理器都以「本地 IndexedDB 为真源」实现。
 * 字段来源：@Field → ctx.fields；@Body(Map/实体) → ctx.body。两者合并后按名取值。
 */
import type { RequestCtx, ApiEnvelope } from '../client';
import { ok, fail } from '../client';
export type LocalHandler = (ctx: RequestCtx) => Promise<unknown> | unknown;
import {
  all, get, put, remove, count, newClientId, nowSec, todayDay, type Entity,
} from '../../db/client';
import { LOCAL_MEMBER_ID } from '../../db/schema';

export { ok, fail, all, get, put, remove, count, newClientId, nowSec, todayDay, LOCAL_MEMBER_ID };
export type { RequestCtx, ApiEnvelope, Entity };

/** 合并 @Field / @Body，后续统一按字段名读取（对齐服务端按名映射的行为）。 */
export function params(ctx: RequestCtx): Record<string, unknown> {
  return { ...(ctx.fields || {}), ...(ctx.body || {}) };
}

export function p(ctx: RequestCtx, key: string, fallback: unknown = undefined): unknown {
  let v = params(ctx)[key];
  // 允许把普通对象当 ctx 传入（批量写场景）
  if (v && typeof v === 'object' && 'fields' in (v as RequestCtx)) v = (v as RequestCtx).fields[key];
  return v === undefined || v === null ? fallback : v;
}

export function str(ctx: RequestCtx, key: string, fallback = ''): string {
  const v = p(ctx, key, fallback);
  return v === undefined || v === null ? fallback : String(v);
}

export function num(ctx: RequestCtx, key: string, fallback = 0): number {
  const v = p(ctx, key, fallback);
  const n = typeof v === 'number' ? v : Number(v);
  return Number.isFinite(n) ? n : fallback;
}

export function int(ctx: RequestCtx, key: string, fallback = 0): number {
  return Math.trunc(num(ctx, key, fallback));
}

export function boolish(v: unknown): number {
  return v === true || v === 1 || v === '1' || v === 'true' ? 1 : 0;
}

export function arr<T = Record<string, unknown>>(ctx: RequestCtx, ...keys: string[]): T[] {
  for (const k of keys) {
    const v = p(ctx, k);
    if (Array.isArray(v)) return v as T[];
  }
  return [];
}

export function itemsOf<T = Record<string, unknown>>(ctx: RequestCtx): T[] {
  const body = ctx.body as Record<string, unknown> | null;
  if (body && Array.isArray(body.items)) return body.items as T[];
  if (Array.isArray(body)) return body as unknown as T[];
  return arr<T>(ctx, 'items', 'bills', 'list', 'data');
}

export function nowMs(): number { return Date.now(); }

export function dayFrom(ts: number | Date = Date.now()): number {
  const d = ts instanceof Date ? ts : new Date(ts);
  return d.getFullYear() * 10000 + (d.getMonth() + 1) * 100 + d.getDate();
}

export function monthFromDay(day: number): number { return Math.trunc(Number(day) / 100); }

/** 数值主键自增（对齐 SQLite AUTOINCREMENT 语义）。 */
export async function nextId(store: string): Promise<number> {
  const rows = await all<Entity & { id?: number }>(store);
  let max = 0;
  for (const r of rows) {
    const v = Number(r.id);
    if (Number.isFinite(v) && v > max) max = v;
  }
  return max + 1;
}

export async function save<T extends Entity>(store: string, value: T): Promise<T> {
  return put<T>(store, value);
}

export async function findOrFail<T = Entity>(store: string, key: IDBValidKey | IDBValidKey[], label: string): Promise<T> {
  const row = await get<T>(store, key);
  if (!row) throw new Error(`${label}不存在`);
  return row;
}

export function pageOf<T>(rows: T[], pageNum = 1, pageSize = 20): { items: T[]; total: number; pageNum: number; pageSize: number; hasMore: number } {
  const pn = Math.max(1, Math.trunc(pageNum) || 1);
  return {
    items: rows.slice((pn - 1) * pageSize, pn * pageSize),
    total: rows.length, pageNum: pn, pageSize,
    hasMore: rows.length > pn * pageSize ? 1 : 0,
  };
}

/** 金额：契约层统一整数分。UI 侧用 yuanToCents 转换。 */
export function moneyCents(ctx: RequestCtx, key = 'money', fallback = 0): number {
  return Math.round(num(ctx, key, fallback));
}

export function sortBy<T extends Entity>(rows: T[], key: string, desc = false): T[] {
  return [...rows].sort((a, b) => {
    const x = Number(a[key]) || 0, y = Number(b[key]) || 0;
    return desc ? y - x : x - y;
  });
}

export function text(v: unknown): string { return v === undefined || v === null ? '' : String(v); }

/** 把实体转为 Retrofit 返回风格（保留全部字段，追加 id 别名）。 */
export function resp<T extends Entity>(row: T): T { return row; }

/** 空对象返回（样本大量 Object> 端点）。 */
export function empty(): ApiEnvelope<Record<string, never>> { return ok({}); }

/** 通用删除：不存在时按样本静默成功。 */
export async function drop(store: string, key: IDBValidKey | IDBValidKey[]): Promise<ApiEnvelope<Record<string, never>>> {
  await remove(store, key);
  return empty();
}

export function timestamp(): number { return nowSec(); }
