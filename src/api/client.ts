/**
 * 统一 API 契约层 —— 对齐 03 §2 样本请求管线。
 *
 * 样本：form/body 字段由 DataNative so 打包成 data=<密文>；本复刻按 04 §5 用明文 JSON 等价实现。
 * 统一响应：{ code: 0, message: "", data: {...}, trace_id: "..." }
 * 列表包装：Items<T> → { items: [...] }
 * 时间：yyyy-MM-dd HH:mm:ss；金额：整数分。
 *
 * 传输由 transport 注入：
 *  - 默认 LocalTransport（前端 mock 后端，M3 实现）——离线可用，写操作同步队列不阻塞 UI。
 *  - 置为 HttpTransport 即可切真实服务，业务代码零改动。
 */
import { newClientId } from '../db/client';

export const MAIN_BASE = 'https://puppy-api.cqmiaoa.com/api/';
export const CONFIG_BASE = 'https://meow-config.aiyouaiyou.cn/api/';

export interface ApiEnvelope<T = unknown> {
  code: number;
  message: string;
  data: T;
  trace_id: string;
}

export interface Items<T> { items: T[] }

export function ok<T>(data: T): ApiEnvelope<T> {
  return { code: 0, message: '', data, trace_id: newClientId() };
}

export function fail(code: number, message: string): ApiEnvelope<never> {
  return { code, message, data: null as never, trace_id: newClientId() };
}

export interface RequestCtx {
  method: 'GET' | 'POST';
  path: string;
  fields: Record<string, unknown>;
  body: Record<string, unknown> | null;
  urlType?: 'configManage' | 'main';
  url?: string;             // 动态 @Url
  multipart?: boolean;
  raw?: ArrayBuffer | Blob;
}

export type Handler = (ctx: RequestCtx) => Promise<ApiEnvelope<unknown>> | ApiEnvelope<unknown>;

export interface Transport {
  /** 返回统一信封 {code,message,data,trace_id}；业务失败用 code != 0 表达，不抛错。 */
  send(ctx: RequestCtx): Promise<ApiEnvelope<unknown>>;
}

/** 超时常量（对齐 d0/a.java OkHttp 配置）。 */
export const TIMEOUTS = {
  connect: 30_000, call: 120_000, read: 60_000, write: 60_000, ping: 5_000,
} as const;

/* ---------------- 真实 HTTP 传输（可插拔） ---------------- */
export class HttpTransport implements Transport {
  constructor(private base = MAIN_BASE, private configBase = CONFIG_BASE) {}
  async send(ctx: RequestCtx): Promise<ApiEnvelope<unknown>> {
    const base = ctx.urlType === 'configManage' ? this.configBase : this.base;
    const url = ctx.url || new URL(ctx.path.replace(/^\//, ''), base).toString();
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), TIMEOUTS.call);
    try {
      const init: RequestInit = {
        method: ctx.method, signal: controller.signal,
        headers: { 'Content-Type': 'application/json; charset=utf-8' },
      };
      if (ctx.raw) { init.body = ctx.raw as BodyInit; }
      else if (ctx.method === 'POST') { init.body = JSON.stringify(ctx.body ?? ctx.fields); }
      else if (Object.keys(ctx.fields).length) {
        (init as RequestInit & { headers: Record<string, string> }).headers['X-Fields'] =
          encodeURIComponent(JSON.stringify(ctx.fields));
      }
      const res = await fetch(url, init);
      const json = await res.json() as ApiEnvelope<unknown>;
      return json.code === 0 ? json : fail(json.code, json.message || `服务端错误 ${json.code}`);
    } catch (e) {
      return fail(-1, e instanceof Error ? e.message : String(e));
    } finally { clearTimeout(timer); }
  }
}

/* ---------------- 传输注册 ---------------- */
let transport: Transport | null = null;
const handlers = new Map<string, Handler>();

export function setTransport(t: Transport): void { transport = t; }
export function getTransport(): Transport | null { return transport; }

/** 注册端点实现：path 与 spec/endpoints.json 完全一致。 */
export function register(path: string, handler: Handler): void {
  if (handlers.has(path)) throw new Error(`端点重复注册：${path}`);
  handlers.set(path, handler);
}

export function registerAll(map: Record<string, Handler>): void {
  for (const [path, h] of Object.entries(map)) register(path, h);
}

export function registeredPaths(): string[] { return [...handlers.keys()]; }
export function hasHandler(path: string): boolean { return handlers.has(path); }

/** 前端 mock 后端：优先用注册表，未注册时回退真实传输（若已配置）。 */
export class LocalTransport implements Transport {
  async send(ctx: RequestCtx): Promise<ApiEnvelope<unknown>> {
    const h = handlers.get(ctx.path);
    if (!h) {
      if (transport && !(transport instanceof LocalTransport)) return transport.send(ctx);
      return fail(404, `端点未实现：${ctx.path}`);
    }
    try { return await h(ctx); }
    catch (e) { return fail(500, e instanceof Error ? e.message : String(e)); }
  }
}

/* ---------------- 调用入口 ---------------- */
export interface CallOptions {
  fields?: Record<string, unknown>;
  body?: Record<string, unknown> | null;
  urlType?: 'configManage' | 'main';
  url?: string;
  multipart?: boolean;
  raw?: ArrayBuffer | Blob;
}

/** 返回解包后的 data；失败时抛带 code 的 ApiError。 */
export async function call<T = unknown>(method: 'GET' | 'POST', path: string, opts: CallOptions = {}): Promise<T> {
  const ctx: RequestCtx = {
    method, path,
    fields: opts.fields ?? {},
    body: opts.body ?? null,
    urlType: opts.urlType,
    url: opts.url,
    multipart: opts.multipart,
    raw: opts.raw,
  };
  const t = transport ?? new LocalTransport();
  try {
    const env = await t.send(ctx);
    if (env.code !== 0) throw new ApiError(path, env.message || `code ${env.code}`, env.code);
    return env.data as T;
  } catch (e) {
    if (e instanceof ApiError) throw e;
    throw new ApiError(path, e instanceof Error ? e.message : String(e));
  }
}

export class ApiError extends Error {
  constructor(public path: string, message: string, public code = -1) {
    super(`${path}: ${message}`);
    this.name = 'ApiError';
  }
}

/** 便捷方法：对齐 Retrofit 的 @POST @Field / @Body 与 @GET 调用风格。 */
export const post = <T = unknown>(path: string, fields: Record<string, unknown> = {}, opts: CallOptions = {}) =>
  call<T>('POST', path, { ...opts, fields });
export const postBody = <T = unknown>(path: string, body: Record<string, unknown>, opts: CallOptions = {}) =>
  call<T>('POST', path, { ...opts, body });
export const get = <T = unknown>(path: string, fields: Record<string, unknown> = {}, opts: CallOptions = {}) =>
  call<T>('GET', path, { ...opts, fields });

/** 时间格式化（样本口径 yyyy-MM-dd HH:mm:ss）。 */
export function formatTime(ts: number | Date = Date.now()): string {
  const d = ts instanceof Date ? ts : new Date(ts * 1000);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

export function parseTime(s: string): number {
  return Math.floor(new Date(s.replace(' ', 'T')).getTime() / 1000);
}
