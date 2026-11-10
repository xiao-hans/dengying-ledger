/**
 * 轻量 IndexedDB DAO —— 对齐 Room 的本地优先语义。
 * 全部写操作立即落库；同步队列由 api 层消费，云端不可达不阻塞 UI。
 */
import { DB_NAME, DB_VERSION, STORES, LOCAL_MEMBER_ID, type StoreDef } from './schema';

let dbPromise: Promise<IDBDatabase> | null = null;

function openDB(): Promise<IDBDatabase> {
  if (dbPromise) return dbPromise;
  dbPromise = new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      for (const def of STORES) {
        if (db.objectStoreNames.contains(def.name)) continue;
        const store = db.createObjectStore(def.name, {
          keyPath: def.keyPath,
          autoIncrement: def.autoIncrement,
        });
        for (const idx of def.indexes || []) {
          store.createIndex(idx.name, idx.keyPath, { unique: idx.unique ?? false });
        }
      }
      void seedDefaults(db);
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
    req.onblocked = () => reject(new Error('数据库升级被其它标签页阻塞，请关闭其它页面后重试'));
  });
  return dbPromise;
}

/** 首次启动写入内置字典（默认账本 / 分类 / 账户），对齐样本首次启动行为。 */
function seedDefaults(db: IDBDatabase): void {
  const tx = db.transaction([
    'bill_book', 'category', 'assets', 'member', 'assets_type', 'icon_dict',
    'plan_type', 'plan_cover', 'wish', 'person_center_icon', 'contract_info',
    'order_config', 'discount_config', 'user_setting', 'remote_config',
  ], 'readwrite');
  const now = Date.now();
  tx.objectStore('bill_book').put({
    id: 1, name: '日常账本', icon_id: 1, icon: 'book', tag: '默认',
    create_date: now, month_start_date: 1,
  });
  const cats: [number, number, string, string][] = [
    // id, type(1支出 2收入), title, icon
    [1, 1, '餐饮', 'wallet'], [2, 1, '交通', 'transfer'], [3, 1, '购物', 'gift'],
    [4, 1, '居住', 'folder'], [5, 1, '娱乐', 'star'], [6, 1, '医疗', 'heart'],
    [7, 1, '教育', 'book'], [8, 1, '人情', 'users'], [9, 1, '其他支出', 'more'],
    [101, 2, '工资', 'coin'], [102, 2, '奖金', 'gift'], [103, 2, '理财', 'chart'],
    [104, 2, '其他收入', 'more'],
  ];
  for (const [id, type, title, icon] of cats) {
    tx.objectStore('category').put({
      id, cate_id: id, type, title, icon_id: id, base_icon: icon, icon,
      is_custom: 0, status: 1, member_id: LOCAL_MEMBER_ID, sort: id, hidden: 0,
    });
  }
  tx.objectStore('assets').put({
    id: 1, type_id: 1, type: 1, type_name: 'cash', type_title: '现金', name: '现金',
    desc: '默认账户', icon: 'wallet', currency: 'CNY',
    create_date: now, update_date: now, credit_line: 0, repayment_date: 0,
    statement_date: 0, is_remind: 0, is_total_assets: 1, is_next_bills: 0,
    balance_cents: 0, status: 1,
  });
  tx.objectStore('member').put({
    id: LOCAL_MEMBER_ID, name: '本地用户', nickname: '本地用户',
    phone: '', avatar: '', vip: 0, create_time: now,
  });
  const assetTypes: [number, string, string][] = [
    [1, 'cash', '现金'], [2, 'bank', '银行卡'], [3, 'credit', '信用卡'],
    [4, 'alipay', '支付宝'], [5, 'wechat', '微信'], [6, 'iou', '借条'],
  ];
  for (const [id, key, title] of assetTypes) {
    tx.objectStore('assets_type').put({ id, key, title, icon: 'wallet', sort: id });
  }
  for (const [id, name, icon] of [
    [1, 'book', 'book'], [2, 'wallet', 'wallet'], [3, 'food', 'wallet'],
  ] as [number, string, string][]) {
    tx.objectStore('icon_dict').put({ id, name, icon, type: 'book' });
  }
  for (const [id, name] of [['目标计划', 'goal'], ['定额储蓄', 'fixed'], ['自由储蓄', 'free']].entries()) {
    tx.objectStore('plan_type').put({ id: id + 1, name: name[0], key: name[1] });
  }
  for (let i = 1; i <= 6; i++) {
    tx.objectStore('plan_cover').put({ id: i, name: `封面${i}`, icon: 'star', url: '' });
  }
  for (const [i, w] of ['旅行', '买电脑', '换新手机', '应急基金'].entries()) {
    tx.objectStore('wish').put({ id: i + 1, name: w });
  }
  for (const [i, item] of [
    ['账本管理', 'book'], ['资产与账户', 'wallet'], ['分类与标签', 'tag'],
    ['存钱计划', 'star'], ['周期账与提醒', 'cycle'], ['消费分期', 'receipt'],
    ['导入账单', 'upload'], ['桌面小组件', 'widget'], ['会员中心', 'gift'],
    ['设置', 'settings'], ['帮助与反馈', 'help'], ['关于', 'info'],
  ].entries()) {
    tx.objectStore('person_center_icon').put({ id: i + 1, title: item[0], icon: item[1], route: item[1], sort: i + 1 });
  }
  tx.objectStore('contract_info').put({ id: 1, title: '用户协议与隐私政策', version: '1.8.1', agreed: 0 });
  tx.objectStore('order_config').put({ id: 1, vip_price_cents: 12800, original_price_cents: 19800, days: 365 });
  tx.objectStore('discount_config').put({ id: 1, rate: 100, label: '无折扣', enabled: 0 });
  for (const [key, value] of Object.entries({
    auto_bill: '0', notification_capture: '0', accessibility_capture: '0',
    screenshot_ocr: '0', auto_confirm: '0', gesture_password: '0',
    currency: 'CNY', month_start_date: '1',
  })) {
    tx.objectStore('user_setting').put({ key, value });
  }
  for (const [key, value] of Object.entries({
    ad_enabled: '0', force_update: '0', min_version: '1.0.0',
    latest_version: '1.8.1', dialog_enabled: '0',
    api_base: 'https://puppy-api.cqmiaoa.com/api/',
    config_base: 'https://meow-config.aiyouaiyou.cn/api/',
  })) {
    tx.objectStore('remote_config').put({ key, value });
  }
}

function reqToPromise<T>(req: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

function txDone(tx: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error || new Error('事务被中止'));
  });
}

export type Entity = Record<string, unknown> & { [key: string]: unknown };

/** put：主键冲突时覆盖（Room @Insert(onConflict=REPLACE) 语义）。 */
export async function put<T extends Entity>(store: string, value: T): Promise<T> {
  const db = await openDB();
  const tx = db.transaction(store, 'readwrite');
  tx.objectStore(store).put(value);
  await txDone(tx);
  return value;
}

/** insert：主键冲突时抛错（用于 client_id 幂等去重）。 */
export async function insert<T extends Entity>(store: string, value: T): Promise<T> {
  const db = await openDB();
  const tx = db.transaction(store, 'readwrite');
  tx.objectStore(store).add(value);
  await txDone(tx);
  return value;
}

export async function get<T = Entity>(store: string, key: IDBValidKey | IDBValidKey[]): Promise<T | undefined> {
  const db = await openDB();
  const tx = db.transaction(store, 'readonly');
  const res = await reqToPromise(tx.objectStore(store).get(key as IDBValidKey) as IDBRequest<T | undefined>);
  return res;
}

export async function remove(store: string, key: IDBValidKey | IDBValidKey[]): Promise<void> {
  const db = await openDB();
  const tx = db.transaction(store, 'readwrite');
  tx.objectStore(store).delete(key as IDBValidKey);
  await txDone(tx);
}

export async function all<T = Entity>(store: string): Promise<T[]> {
  const db = await openDB();
  const tx = db.transaction(store, 'readonly');
  return reqToPromise(tx.objectStore(store).getAll() as IDBRequest<T[]>);
}

/** 按索引查询（等价 Room 的 WHERE + INDEX）。 */
export async function byIndex<T = Entity>(
  store: string, index: string, query: IDBValidKey | IDBKeyRange,
): Promise<T[]> {
  const db = await openDB();
  const tx = db.transaction(store, 'readonly');
  const idx = tx.objectStore(store).index(index);
  return reqToPromise(idx.getAll(query) as IDBRequest<T[]>);
}

/** 游标范围查询（day 用 yyyyMMdd 整数，可直接用数值区间）。 */
export async function range<T = Entity>(
  store: string, index: string, lower?: number, upper?: number,
): Promise<T[]> {
  const q = lower !== undefined && upper !== undefined
    ? IDBKeyRange.bound(lower, upper)
    : lower !== undefined ? IDBKeyRange.lowerBound(lower)
      : upper !== undefined ? IDBKeyRange.upperBound(upper) : undefined;
  if (!q) return all<T>(store);
  return byIndex<T>(store, index, q);
}

export async function count(store: string): Promise<number> {
  const db = await openDB();
  const tx = db.transaction(store, 'readonly');
  return reqToPromise(tx.objectStore(store).count());
}

export async function clear(store: string): Promise<void> {
  const db = await openDB();
  const tx = db.transaction(store, 'readwrite');
  tx.objectStore(store).clear();
  await txDone(tx);
}

/** 批量写（一个事务，保证一组账单原子落库）。 */
export async function batchPut<T extends Entity>(store: string, values: T[]): Promise<void> {
  if (!values.length) return;
  const db = await openDB();
  const tx = db.transaction(store, 'readwrite');
  const os = tx.objectStore(store);
  for (const v of values) os.put(v);
  await txDone(tx);
}

/** 生成端侧 client_id（对齐样本 UUID 风格）。 */
export function newClientId(): string {
  return `c_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 10)}`;
}

export function nowSec(): number { return Math.floor(Date.now() / 1000); }
export function todayDay(): number {
  const d = new Date();
  return d.getFullYear() * 10000 + (d.getMonth() + 1) * 100 + d.getDate();
}

/** 金额字符串（元）→ 整数分；不吞字符、不强制两位小数、不改数量级（交互纪律 §6.3）。 */
export function yuanToCents(input: string): number | null {
  const s = input.trim();
  if (!/^-?\d+(\.\d{0,2})?$/.test(s)) return null;
  const neg = s.startsWith('-');
  const [a, b = ''] = s.replace('-', '').split('.');
  const cents = Number(a) * 100 + Number(b.padEnd(2, '0'));
  return neg ? -cents : cents;
}

export function centsToYuan(cents: number): number { return cents / 100; }

/** 复合主键工具：bill / member_book / category / cate_extend / bill_tag 用。 */
export function compoundKey(...parts: (string | number)[]): (string | number)[] { return parts; }
