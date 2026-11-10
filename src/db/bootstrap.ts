/**
 * 启动引导：打开本地库 → 初始化传输 → 自检。
 * 对齐样本 Application.onCreate 里的 Room + Retrofit 初始化顺序。
 */
import { count, all } from './client';
import { STORE_NAMES, LOCAL_MEMBER_ID } from './schema';
import { LocalTransport, setTransport } from '../api/client';

export interface DbDiagnostics {
  ready: boolean;
  stores: number;
  bills: number;
  books: number;
  categories: number;
  assets: number;
  memberId: number;
  error?: string;
}

let bootPromise: Promise<DbDiagnostics> | null = null;

export function bootstrap(): Promise<DbDiagnostics> {
  if (bootPromise) return bootPromise;
  bootPromise = (async () => {
    setTransport(new LocalTransport());
    try {
      // 触发 onupgradeneeded（首次）并确认内置字典已写入
      const [bills, books, categories, assets] = await Promise.all([
        count('bill'), count('bill_book'), count('category'), count('assets'),
      ]);
      return {
        ready: true, stores: STORE_NAMES.length, bills, books, categories, assets,
        memberId: LOCAL_MEMBER_ID,
      };
    } catch (e) {
      return {
        ready: false, stores: 0, bills: 0, books: 0, categories: 0, assets: 0,
        memberId: LOCAL_MEMBER_ID,
        error: e instanceof Error ? e.message : String(e),
      };
    }
  })();
  return bootPromise;
}

/** 开发期自检：对每个 store 做一次 count，确保建表与索引都可访问。 */
export async function selfTest(): Promise<{ name: string; count: number }[]> {
  const out: { name: string; count: number }[] = [];
  for (const name of STORE_NAMES) {
    try { out.push({ name, count: await count(name) }); }
    catch { out.push({ name, count: -1 }); }
  }
  return out;
}

/** 导出全部数据（供「导出账单 / 备份」使用，返回可 JSON 化对象）。 */
export async function exportAll(): Promise<Record<string, unknown[]>> {
  const out: Record<string, unknown[]> = {};
  for (const name of STORE_NAMES) out[name] = await all(name);
  return out;
}
