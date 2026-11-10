/**
 * 自动记账引擎 —— 三路采集（通知 / 无障碍 / 截屏 OCR）共用的解析、规则匹配、
 * 去重与待确认队列。对齐样本 AutoBillService / ScreenCaptureService /
 * MatchingRules* / BillIdentifiedActivity 的链路。
 *
 * 说明：Web 无系统级通知监听、无障碍节点树、MediaProjection 权限，也不捆绑
 * OcrLite so；这里以「粘贴通知文本 / 页面文本 / OCR 文本」作为等价输入，
 * 解析结果一律进入待确认队列，绝不静默入账。规则与去重键口径不变：
 *   hash = MD5 等价摘要(sourceApp + money + floor(occurredAt/60000) + merchant)
 *   规则：key_word 命中商户文本 → 分类 / 账户 / 标签
 */
import { all, put, insert, get, remove, newClientId, nowSec } from '../../db/client';
import { api, type Dict } from '../common/api';
import { N, S, toCents } from '../common/format';

export type CaptureSource = 'notification' | 'accessibility' | 'ocr';
export const SOURCE_LABEL: Record<CaptureSource, string> = {
  notification: '通知识别', accessibility: '无障碍识别', ocr: '截屏 OCR',
};
/** bill.source：0 手工 / 1 通知 / 2 截屏 / 3 OCR / 4 导入 / 5 周期（04 §3 DDL）。 */
export const SOURCE_TO_BILL: Record<CaptureSource, number> = {
  notification: 1, accessibility: 2, ocr: 3,
};

/** 样本 queries 声明确式列出的监听目标 App（01 §2）；云闪付/银行为扩展白名单。 */
export const WATCH_APPS = [
  { pkg: 'com.eg.android.AlipayGphone', name: '支付宝', icon: 'wallet' },
  { pkg: 'com.tencent.mm', name: '微信', icon: 'link' },
  { pkg: 'com.tencent.mobileqq', name: 'QQ', icon: 'users' },
  { pkg: 'com.unionpay', name: '云闪付', icon: 'coin' },
  { pkg: 'com.chinamobile.bank', name: '银行 App', icon: 'book' },
  { pkg: 'com.smile.gifmaker', name: '快手', icon: 'camera' },
  { pkg: 'com.kuaishou.nebula', name: '快手极速版', icon: 'camera' },
] as const;

/** 引导页 5 步（对齐 GuideAutoBillActivity 的权限漏斗）。 */
export const GUIDE_STEPS = [
  { title: '认识自动记账', icon: 'info', body: '喵啊记账只在支付发生后读取你主动提供的通知、页面或截图文本，提取金额与商户；不会替你付款，也不会读取通讯录。' },
  { title: '选择识别方式', icon: 'bell', body: '通知识别、无障碍文本、截屏 OCR 三路可单独开关；不需要全部开启，也可以随时关闭。' },
  { title: '选择监听的 App', icon: 'grid', body: '默认列出支付宝、微信、QQ、云闪付、银行与快手。白名单之外的文本不会被识别。' },
  { title: '设置匹配规则', icon: 'note', body: '按商户关键词自动带入分类、账户与标签；没有命中规则时，由你在确认页手动选择。' },
  { title: '确认后才入账', icon: 'check', body: '识别结果先进入待确认队列，重复事件会折叠。你可以逐条修改或批量通过，绝不会静默写进账本。' },
] as const;

/** 稳定摘要（FNV-1a 128 位拼接，等价样本用 MD5 生成 uniqueId 的确定性去重语义）。 */
export function digest(text: string): string {
  let h1 = 0x811c9dc5, h2 = 0x01000193, h3 = 0x9e3779b9, h4 = 0x85ebca6b;
  for (let i = 0; i < text.length; i++) {
    const c = text.charCodeAt(i);
    h1 = Math.imul(h1 ^ c, 16777619) >>> 0;
    h2 = Math.imul(h2 + c + i, 2246822519) >>> 0;
    h3 = Math.imul(h3 ^ (c << 1), 3266489917) >>> 0;
    h4 = Math.imul(h4 ^ (c >>> 1), 668265263) >>> 0;
  }
  const hex = (n: number) => n.toString(16).padStart(8, '0');
  return hex(h1) + hex(h2) + hex(h3) + hex(h4);
}

export interface Parsed {
  money: number;        // 整数分
  merchant: string;
  occurredAt: number;   // 毫秒
  type: 1 | 2;          // 1 支出 / 2 收入
  sourceApp: string;
  matchedRule: number;  // 命中的规则 id，0 表示未命中
  cateId: number;
  assetsId: number;
  tagIds: string;
}

const MERCHANT_NOISE = /(支付成功|付款成功|已支付|收款成功|已收款|交易成功|消费|支出|收入|到账|转账|订单|账单|您|你|已|成功|金额|余额|本次|完成|人民币|元整|[：:·,，。!！\s])/g;
const MONEY_RE = /(?:[¥￥]|RMB|人民币)?\s*(-?\d+(?:\.\d{1,2})?)\s*(?:元|块|¥|￥)?/g;

/** 从任意一路原始文本提取金额、商户与收支方向；无有效金额返回 null。 */
export function parseRaw(raw: string, source: CaptureSource): Omit<Parsed, 'matchedRule' | 'cateId' | 'assetsId' | 'tagIds'> | null {
  const text = raw.replace(/\s+/g, ' ').trim();
  if (!text) return null;
  let best: { cents: number; score: number } | null = null;
  for (const m of text.matchAll(MONEY_RE)) {
    const cents = toCents(m[1]);
    if (cents === null || Math.abs(cents) < 1) continue;
    // 优先取带货币符号/单位或显式金额标签的匹配（避免误取时间、订单号片段）
    const score = (m[0].includes('¥') || m[0].includes('￥') || /元|块/.test(m[0]) ? 1000 : 0) + Math.abs(cents);
    if (!best || score > best.score) best = { cents: Math.abs(cents), score };
  }
  if (!best) return null;
  const income = /收款|到账|已收|转入|退款|收入|红包已领|入账/.test(text);
  const expense = /付款|支付|消费|支出|转出|扣款|已付/.test(text);
  const type: 1 | 2 = income && !expense ? 2 : 1;
  const merchant = extractMerchant(text);
  return { money: best.cents, merchant, occurredAt: Date.now(), type, sourceApp: SOURCE_LABEL[source] };
}

function extractMerchant(text: string): string {
  // 优先「商户：xxx」「付款给 xxx」「在 xxx 消费」等显式结构
  const labeled = text.match(/(?:商户|商家|收款方|付款给|收款人|店铺|店名|交易对方)\s*[：:为]?\s*([^\s,，。;；:：¥￥\d]{2,18})/);
  if (labeled) return labeled[1].trim();
  const paid = text.match(/(?:在|向|给)\s*([^\s,，。;；:：¥￥\d]{2,18})\s*(?:消费|付款|支付|完成)/);
  if (paid) return paid[1].trim();
  const cleaned = text.replace(MONEY_RE, ' ').replace(MERCHANT_NOISE, ' ').replace(/[\[\]()（）【】"'`]/g, ' ').trim();
  const parts = cleaned.split(/[\s,，。;；|/]+/).filter((p) => p.length >= 2 && p.length <= 18 && !/^\d+$/.test(p));
  return parts[0] || '未识别商户';
}

/** 规则匹配：最长关键词优先；命中后覆盖分类/账户/标签。 */
export function matchRule(rules: Dict[], merchant: string, text: string): Dict | undefined {
  const hay = `${merchant} ${text}`.toLowerCase();
  return rules
    .filter((r) => S(r.key_word).trim() && hay.includes(S(r.key_word).trim().toLowerCase()))
    .sort((a, b) => S(b.key_word).length - S(a.key_word).length)[0];
}

export interface Candidate extends Parsed {
  id: string;
  hash: string;
  source: CaptureSource;
  rawText: string;
  status: 0 | 1 | 2;   // 0 待确认 / 1 已入账 / 2 已忽略
  create_time: number;
  bill_client_id: string;
  desc: string;
}

export function dedupKey(p: { sourceApp: string; money: number; occurredAt: number; merchant: string }): string {
  return digest(`${p.sourceApp}|${p.money}|${Math.floor(p.occurredAt / 60000)}|${p.merchant}`);
}

/**
 * 采集入口：原始文本 → 解析 → 规则 → 去重 → 写 auto_raw + auto_event。
 * 返回 { created, duplicate, unsupported }；重复事件不重复入队。
 */
export async function capture(rawText: string, source: CaptureSource, occurredAt = Date.now(), sourceApp = SOURCE_LABEL[source]): Promise<{ created: number; duplicate: number; unsupported: number }> {
  const rawId = newClientId();
  await put('auto_raw', { id: rawId, source, text: rawText, source_app: sourceApp, occurred_at: occurredAt, create_time: nowSec() });
  const lines = rawText.split(/[\r\n]+/).map((l) => l.trim()).filter(Boolean);
  const base = parseRaw(rawText, source);
  const segments = lines.length > 1 ? lines : [rawText];
  let created = 0, duplicate = 0, unsupported = 0;
  const rules = await api.ruleList();
  for (const seg of segments) {
    const parsed = (segments.length > 1 ? parseRaw(seg, source) : base);
    if (!parsed) { unsupported++; continue; }
    const full: Omit<Parsed, 'matchedRule' | 'cateId' | 'assetsId' | 'tagIds'> = {
      ...parsed, occurredAt, sourceApp,
    };
    const rule = matchRule(rules, full.merchant, seg);
    const hash = dedupKey(full);
    const existing = await get<Candidate>('auto_event', hash);
    if (existing) { duplicate++; continue; }
    const ev: Candidate = {
      ...full,
      id: hash,
      hash,
      source,
      rawText: seg,
      status: 0,
      create_time: nowSec(),
      bill_client_id: '',
      matchedRule: N(rule?.id),
      cateId: rule ? N(rule.member_cate_id, 9) : full.type === 2 ? 101 : 9,
      assetsId: rule ? N(rule.assets_id, 1) : 1,
      tagIds: rule ? S(rule.tag_ids ?? rule.member_tag_ids) : '',
      desc: `${full.merchant} · ${SOURCE_LABEL[source]}`,
    };
    try { await insert('auto_event', ev as unknown as Record<string, unknown>); created++; }
    catch { duplicate++; }   // idx_hash unique：并发重复也折叠
  }
  if (created) await api.autoBillsCallback(SOURCE_TO_BILL[source]);
  return { created, duplicate, unsupported };
}

/** 待确认 / 已处理队列。 */
export async function pendingEvents(): Promise<Candidate[]> {
  const rows = await all<Candidate>('auto_event');
  return rows.sort((a, b) => N(b.create_time) - N(a.create_time));
}

/** 确认入账：写 bill（金额整数分）+ 更新事件状态 + 触发全局刷新由调用方执行。 */
export async function confirmEvent(ev: Candidate, patch: Partial<Candidate> = {}): Promise<void> {
  const row = { ...ev, ...patch };
  const clientId = ev.bill_client_id || newClientId();
  await api.billsCreate([{
    clientId, billType: row.type, money: row.money, memberCateId: row.cateId,
    fromFundAccountId: row.assetsId, billBookId: 1, day: dayFromTime(row.occurredAt),
    desc: row.desc || row.merchant, tagIds: parseTags(row.tagIds), source: SOURCE_TO_BILL[row.source],
    createDate: row.occurredAt,
  }]);
  await put('auto_event', { ...row, status: 1, bill_client_id: clientId, update_time: nowSec() } as unknown as Record<string, unknown>);
}
function dayFromTime(ts: number): number {
  const d = new Date(ts);
  return d.getFullYear() * 10000 + (d.getMonth() + 1) * 100 + d.getDate();
}
function parseTags(v: unknown): number[] {
  return S(v).split(/[,\s]+/).filter(Boolean).map((x) => Number(x)).filter((x) => Number.isFinite(x));
}

/** 忽略（不写账单，保留原始事件用于审计）。 */
export async function ignoreEvent(ev: Candidate): Promise<void> {
  await put('auto_event', { ...ev, status: 2, update_time: nowSec() } as unknown as Record<string, unknown>);
}
export async function deleteEvent(ev: Candidate): Promise<void> {
  await remove('auto_event', ev.id);
}

/* -------- 设置（走 Member/GetSetting|UpdateSetting，不绕过 API 层） -------- */
export const AUTO_SETTING_KEYS = {
  master: 'auto_bill', notification: 'notification_capture', accessibility: 'accessibility_capture',
  ocr: 'screenshot_ocr', autoConfirm: 'auto_confirm', apps: 'auto_watch_apps', keepAlive: 'keep_alive',
} as const;
export type AutoSettings = {
  master: boolean; notification: boolean; accessibility: boolean; ocr: boolean;
  autoConfirm: boolean; apps: string[]; keepAlive: boolean;
};
export async function loadSettings(): Promise<AutoSettings> {
  const raw = await api.memberSetting() as Dict;
  const on = (k: keyof typeof AUTO_SETTING_KEYS) => S(raw[AUTO_SETTING_KEYS[k]], '0') === '1';
  const apps = (() => {
    try {
      const p = JSON.parse(S(raw[AUTO_SETTING_KEYS.apps], '[]'));
      return Array.isArray(p) ? p.map((x) => S(x)).filter(Boolean) : [];
    } catch { return []; }
  })();
  return {
    master: on('master'), notification: on('notification'), accessibility: on('accessibility'),
    ocr: on('ocr'), autoConfirm: on('autoConfirm'), keepAlive: on('keepAlive'),
    // 未保存过白名单时用默认前三（支付宝/微信/QQ）；空数组是用户主动清空，需保留。
    apps: S(raw[AUTO_SETTING_KEYS.apps]) ? apps : WATCH_APPS.slice(0, 3).map((a) => a.pkg),
  };
}
export async function saveSetting<K extends keyof AutoSettings>(key: K, value: AutoSettings[K]): Promise<void> {
  await api.updateSetting(AUTO_SETTING_KEYS[key], Array.isArray(value) ? JSON.stringify(value) : value ? '1' : '0');
}
