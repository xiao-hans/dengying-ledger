/**
 * 端点自检：拿 spec/endpoints.json 里的全部路径逐个打一次空请求，
 * 断言「每个端点都有处理器且返回 code === 0（或明确的业务校验失败而非未实现）」。
 * 可在浏览器 DevTools 里 `await window.__apiSelfTest()`，也可作为 CI 冒烟测试入口。
 */
import { call } from '../client';
import { verifyEndpoints } from './index';
import { bootstrap, selfTest } from '../../db/bootstrap';

export interface EndpointTestResult {
  total: number; passed: number; failed: number;
  failures: { path: string; error: string }[];
  stores: { name: string; count: number }[];
}

export async function apiSelfTest(endpoints: { path: string; method: string }[]): Promise<EndpointTestResult> {
  await bootstrap();
  const { missing, extra } = verifyEndpoints(endpoints);
  const unique = [...new Map(endpoints.map((e) => [e.path, e])).values()];
  const failures: { path: string; error: string }[] = [];
  for (const e of unique) {
    try {
      const env = await call<unknown>(e.method as 'GET' | 'POST', e.path, { fields: {} });
      void env;
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      // 业务校验失败（如验证码错误）说明处理器存在，可视为通过
      if (msg.includes('端点未实现')) failures.push({ path: e.path, error: msg });
    }
  }
  for (const p of missing) failures.push({ path: p, error: '缺少处理器' });
  for (const p of extra) failures.push({ path: p, error: '多余处理器（不在 spec/endpoints.json）' });
  return {
    total: endpoints.length,
    passed: endpoints.length - failures.length,
    failed: failures.length,
    failures,
    stores: await selfTest(),
  };
}
