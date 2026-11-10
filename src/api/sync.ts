/**
 * 本地优先同步队列 —— 对齐 02 业务架构：所有写先落本地，云端不可达不阻塞 UI。
 * 每个端点写入成功后把 (path, fields/body) 入队；有网时按序回放，成功出队。
 */
import { call, getTransport, HttpTransport, LocalTransport } from './client';
import { put, all, remove, newClientId, nowSec, type Entity } from '../db/client';

export interface SyncTask extends Entity {
  id: string;
  path: string;
  method: 'GET' | 'POST';
  fields: Record<string, unknown>;
  body: Record<string, unknown> | null;
  create_time: number;
  retry: number;
}

const STORE = 'sync_queue';
const MAX_RETRY = 5;

export async function enqueue(path: string, method: 'GET' | 'POST', fields: Record<string, unknown> = {}, body: Record<string, unknown> | null = null): Promise<void> {
  await put<SyncTask>(STORE, {
    id: newClientId(), path, method, fields, body,
    create_time: nowSec(), retry: 0,
  });
}

export async function pendingCount(): Promise<number> {
  return (await all<SyncTask>(STORE)).length;
}

export interface SyncResult { sent: number; failed: number; skipped: boolean }

/** 仅在配置了真实 HTTP 传输时回放；本地 mock 模式下跳过（数据本来就在本地）。 */
export async function flush(): Promise<SyncResult> {
  const t = getTransport();
  if (!t || t instanceof LocalTransport) return { sent: 0, failed: 0, skipped: true };
  const tasks = (await all<SyncTask>(STORE)).sort((a, b) => a.create_time - b.create_time);
  let sent = 0, failed = 0;
  for (const task of tasks) {
    try {
      await call(task.method, task.path, { fields: task.fields, body: task.body });
      await remove(STORE, task.id);
      sent++;
    } catch {
      failed++;
      if (task.retry + 1 >= MAX_RETRY) await remove(STORE, task.id);
      else await put(STORE, { ...task, retry: task.retry + 1 });
    }
  }
  return { sent, failed, skipped: false };
}

export function enableRemote(base: string, configBase?: string): void {
  const http = new HttpTransport(base, configBase);
  // 真实传输下 LocalTransport 仍作为前端兜底：注册表 > HTTP 的优先级由 setTransport 决定。
  setTransport(http);
}

export function enableLocal(): void { setTransport(new LocalTransport()); }
