/** 管理类页面共享小工具。 */
import type { IconName } from '../../components/Icon';
import { S, N, toCents } from '../common/format';
import type { Dict } from '../common/api';

const ICONS = new Set<string>([
  'bills', 'stats', 'plus', 'auto', 'mine', 'back', 'search', 'settings', 'chevron', 'close',
  'check', 'trash', 'edit', 'wallet', 'book', 'tag', 'calendar', 'bell', 'shield', 'camera',
  'upload', 'download', 'filter', 'more', 'arrow', 'moon', 'sun', 'eye', 'lock', 'gift',
  'heart', 'clock', 'refresh', 'scan', 'pet', 'chart', 'pie', 'grid', 'users', 'link',
  'copy', 'info', 'warning', 'folder', 'file', 'star', 'coin', 'transfer', 'cycle',
  'receipt', 'widget', 'help', 'logout', 'phone', 'sort', 'image', 'note', 'minus', 'menu',
]);

export function safeIcon(v: unknown, fallback: IconName = 'grid'): IconName {
  const s = S(v);
  return (ICONS.has(s) ? s : fallback) as IconName;
}
export function idOf(v: unknown, fallback = 0): number { const n = N(v, fallback); return Number.isFinite(n) ? n : fallback; }
export function centsOf(v: unknown, fallback = 0): number { return N(v, fallback) || 0; }
/** 金额输入（元）→ 整数分；空串返回 0，非法返回 null。 */
export function inputCents(v: string): number | null {
  if (!String(v).trim()) return 0;
  return toCents(String(v).trim());
}
export function rowId(row: Dict): string { return S(row.id ?? row.cate_id ?? row.tag_id ?? row.fundAccountId ?? row.billsBookId ?? row.planId); }
export function pct(done: number, total: number): number {
  return total > 0 ? Math.min(100, Math.round((done / total) * 100)) : 0;
}
