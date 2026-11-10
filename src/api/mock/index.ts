/**
 * M3：本地 mock 后端 —— 112 条注解路径 + 2 条动态 @Url 全量注册。
 *
 * 权威来源：jadx_out/sources/com/android/miaoa/achai/api/a.java（597 行）
 *          + spec/endpoints.json（112 条，含字段/body/返回类型）
 * 说明：Bills/ImportBills 在样本里有两个同名重载（ImportBillsReq / ImportOcrBillsReq），
 *       Retrofit 路径相同，故只注册一次，处理器按 body 形态兼容两种载荷。
 *
 * 所有处理器写本地 IndexedDB；返回统一信封 {code,message,data,trace_id}。
 */
import { registerAll, ok, type Handler, type RequestCtx, type ApiEnvelope } from '../client';
import type { LocalHandler } from './helpers';
import { handlers as accountHandlers } from './account';
import { handlers as billsHandlers } from './bills';
import { handlers as categoryHandlers } from './categories';
import { handlers as assetHandlers } from './assets';

/** 把「返回业务 data」的处理器包装成统一信封处理器。 */
function isEnvelope(r: unknown): r is ApiEnvelope<unknown> {
  return !!r && typeof r === 'object' && 'code' in r && 'data' in r && 'trace_id' in r;
}

function envelope(h: LocalHandler): Handler {
  return async (ctx) => {
    const r = await h(ctx);
    // 处理器已自行返回信封（如 fail(...)）时直接透传
    return isEnvelope(r) ? r : ok(r);
  };
}

function wrapAll(map: Record<string, LocalHandler>): Record<string, Handler> {
  const out: Record<string, Handler> = {};
  for (const [path, h] of Object.entries(map)) out[path] = envelope(h);
  return out;
}

/* 动态 @Url：@Streaming @GET @Url → ResponseBody 下载；@POST @Url + MultipartBody → OSS 直传 */
const DYNAMIC_PATHS = ['@Url:GET:download', '@Url:POST:uploadOss'];

const dynamicHandlers: Record<string, Handler> = {
  '@Url:GET:download': (ctx) => ok({
    url: ctx.url || '', size: ctx.raw instanceof ArrayBuffer ? ctx.raw.byteLength : 0,
    content: '', streamed: 1,
  }),
  '@Url:POST:uploadOss': (ctx) => ok({
    url: ctx.url || '', key: `oss/${Date.now()}`, uploaded: 1,
    size: ctx.raw instanceof Blob ? ctx.raw.size : 0,
  }),
};

let installed = false;

/** 幂等安装：可重复调用（HMR / 测试）。 */
export function installMockBackend(): { count: number; paths: string[] } {
  if (installed) return { count: 0, paths: [] };
  installed = true;
  const all = {
    ...wrapAll(accountHandlers),
    ...wrapAll(billsHandlers),
    ...wrapAll(categoryHandlers),
    ...wrapAll(assetHandlers),
    ...dynamicHandlers,
  };
  registerAll(all);
  return { count: Object.keys(all).length, paths: Object.keys(all) };
}

/** 与 spec/endpoints.json 做完整性校验，返回缺失/多余的端点。 */
export function verifyEndpoints(expected: { path: string }[]): { missing: string[]; extra: string[] } {
  const want = new Set(expected.map((e) => e.path));
  const got = new Set([
    ...Object.keys(accountHandlers), ...Object.keys(billsHandlers),
    ...Object.keys(categoryHandlers), ...Object.keys(assetHandlers),
    ...DYNAMIC_PATHS,
  ]);
  const missing = [...want].filter((p) => !got.has(p)).sort();
  const extra = [...got].filter((p) => !want.has(p)).sort();
  return { missing, extra };
}

export { DYNAMIC_PATHS };
