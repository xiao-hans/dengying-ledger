/** 展示层纯函数：账单行字段兼容服务端/本地两种命名。 */
export type Row = Record<string, unknown>;

export const S = (v: unknown, fallback = ''): string => (v === undefined || v === null ? '' : String(v));
export const N = (v: unknown, fallback = 0): number => {
  const n = Number(v);
  return Number.isFinite(n) ? n : fallback;
};

/** 样本 day = yyyyMMdd（整数），也可能带 '-'。 */
export function dayText(day: unknown): string {
  const s = S(day).replace(/-/g, '');
  if (s.length >= 8) return `${s.slice(0, 4)}年${Number(s.slice(4, 6))}月${Number(s.slice(6, 8))}日`;
  return S(day, '未注明日期');
}
export function dayMonth(day: unknown): number {
  const s = S(day).replace(/-/g, '');
  return s.length >= 6 ? Number(s.slice(0, 6)) : 0;
}
export function timeText(v: unknown): string {
  const ts = N(v, 0);
  if (!ts) return '';
  const d = new Date(ts > 1e12 ? ts : ts * 1000);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getHours())}:${p(d.getMinutes())}`;
}

export function jsonArray(v: unknown): string[] {
  if (Array.isArray(v)) return v.map((x) => S(x));
  if (typeof v === 'string' && v.trim()) {
    try { const p: unknown = JSON.parse(v); return Array.isArray(p) ? p.map((x) => S(x)) : [v]; }
    catch { return [v]; }
  }
  return [];
}

/** 整数分 → 元（展示用，不四舍五入）。空值返回空串，避免编辑框被填成 "0"。 */
export function centsToYuan(cents: unknown): string {
  const n = N(cents, 0);
  if (!n) return '';
  const neg = n < 0;
  const abs = Math.abs(n);
  const yuan = `${Math.floor(abs / 100)}${abs % 100 ? `.${String(abs % 100).padStart(2, '0')}` : ''}`;
  return neg ? `-${yuan}` : yuan;
}

/** 金额输入 → 整数分；严格校验，非法输入返回 null（UI 会提示而不是静默吞字符）。 */
export function toCents(input: string): number | null {
  const s = input.trim();
  if (!/^-?\d+(\.\d{0,2})?$/.test(s)) return null;
  const neg = s.startsWith('-');
  const [a, b = ''] = (neg ? s.slice(1) : s).split('.');
  return (neg ? -1 : 1) * (Number(a) * 100 + Number(b.padEnd(2, '0')));
}

/** 分类 id → 名称/图标，供账单列表与统计共用。 */
export function cateMap(cates: Row[]): Map<number, Row> {
  const m = new Map<number, Row>();
  for (const c of cates) m.set(N(c.cate_id ?? c.id), c);
  return m;
}
export function cateName(m: Map<number, Row>, id: unknown): string {
  return S(m.get(N(id))?.title ?? '未分类');
}
export function cateIcon(m: Map<number, Row>, id: unknown): string {
  return S(m.get(N(id))?.icon ?? m.get(N(id))?.base_icon ?? 'more', 'more');
}
