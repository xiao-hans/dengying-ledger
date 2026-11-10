/**
 * IndexedDB 表结构 —— 对齐 spec/ddl.sql（Room 20+ 表）。
 * 约定：
 *  - 金额字段一律整数分（*_cents / money 语义仍是分），避免 double 累加误差（04 §7.5）。
 *  - bill 复合主键 (member_id, client_id)，client_id 为端侧幂等键。
 *  - 每张表用 keyPath 主键 + 业务索引（对应 DDL 的 CREATE INDEX）。
 *  - 附加若干运行期表（识别队列 / 远程配置 / 埋点 / 同步队列），用于覆盖功能面。
 */

export interface StoreDef {
  name: string;
  keyPath: string | string[];
  indexes?: { name: string; keyPath: string | string[]; unique?: boolean }[];
  autoIncrement?: boolean;
}

export const DB_NAME = 'meow_ledger';
// v2：修复 onupgradeneeded 内另开事务导致版本变更事务被中止的问题，种子数据改走升级事务。
// v3：合约信息改为签约实体字段，远程配置补齐服务端开关默认值。
export const DB_VERSION = 3;

/** 当前本地成员（未登录时为 0，对齐 member_id 默认 0 的离线语义）。 */
export const LOCAL_MEMBER_ID = 0;

export const STORES: StoreDef[] = [
  {
    name: 'bill', keyPath: ['member_id', 'client_id'],
    indexes: [
      { name: 'idx_day', keyPath: 'day' },
      { name: 'idx_book_day', keyPath: ['book_id', 'day'] },
      { name: 'idx_cate', keyPath: 'member_cate_id' },
      { name: 'idx_source', keyPath: 'source' },
      { name: 'idx_update', keyPath: 'local_update_time' },
    ],
  },
  { name: 'bill_book', keyPath: 'id' },
  { name: 'member_book', keyPath: ['member_id', 'book_id'] },
  { name: 'category', keyPath: ['id', 'member_id'], indexes: [{ name: 'idx_type', keyPath: 'type' }] },
  { name: 'cate_extend', keyPath: ['member_cate_id', 'book_id', 'member_id'] },
  { name: 'budget', keyPath: ['book_id', 'day'] },
  { name: 'cate_budget', keyPath: ['book_id', 'day', 'member_cate_id'] },
  {
    name: 'assets', keyPath: 'id',
    indexes: [{ name: 'idx_status', keyPath: 'status' }],
  },
  { name: 'rule', keyPath: 'id', indexes: [{ name: 'idx_keyword', keyPath: 'key_word' }] },
  { name: 'tag', keyPath: 'tag_id', indexes: [{ name: 'idx_book', keyPath: 'book_id' }] },
  { name: 'bill_tag', keyPath: ['client_id', 'member_id', 'tag_id'] },
  { name: 'bill_remark', keyPath: 'id' },
  { name: 'refund_bill', keyPath: 'id', indexes: [{ name: 'idx_client', keyPath: 'client_id' }] },
  { name: 'installment_plan', keyPath: 'id', indexes: [{ name: 'idx_status', keyPath: 'status' }] },
  { name: 'installment_item', keyPath: 'id', indexes: [{ name: 'idx_plan', keyPath: 'plan_id' }] },
  { name: 'fixed_bill', keyPath: 'id' },
  { name: 'bill_remind', keyPath: 'id' },
  { name: 'save_plan', keyPath: 'id' },
  { name: 'save_plan_item', keyPath: 'id', indexes: [{ name: 'idx_plan', keyPath: 'plan_id' }] },
  { name: 'member_import_bill_file', keyPath: 'id' },
  { name: 'widget_base', keyPath: 'id' },
  { name: 'widget_user', keyPath: 'id' },
  { name: 'widget_desktop', keyPath: 'id' },
  { name: 'widget_hot', keyPath: 'id' },
  { name: 'ad_data', keyPath: 'id' },
  { name: 'ad_info', keyPath: 'id' },
  { name: 'ad_config', keyPath: 'id' },
  { name: 'ad_event', keyPath: 'id' },
  { name: 'ad_click_event', keyPath: 'id' },
  { name: 'ad_trackurl', keyPath: 'id' },
  /* ---- 运行期附加表（功能等价所需，非 Room DDL 原表） ---- */
  { name: 'auto_event', keyPath: 'id', indexes: [{ name: 'idx_hash', keyPath: 'hash', unique: true }, { name: 'idx_status', keyPath: 'status' }] },
  { name: 'auto_raw', keyPath: 'id', indexes: [{ name: 'idx_source', keyPath: 'source' }] },
  { name: 'account', keyPath: 'id' },
  { name: 'member', keyPath: 'id' },
  { name: 'order', keyPath: 'id' },
  { name: 'behavior_event', keyPath: 'id' },
  { name: 'remote_config', keyPath: 'key' },
  { name: 'sync_queue', keyPath: 'id', indexes: [{ name: 'idx_created', keyPath: 'create_time' }] },
  /* ---- 纯配置/无主键字典表（对齐 Account、Config、PersonCenter 等返回实体） ---- */
  { name: 'sms_code', keyPath: 'phone' },
  { name: 'user_setting', keyPath: 'key' },
  { name: 'reminder', keyPath: 'id' },
  { name: 'icon_dict', keyPath: 'id' },
  { name: 'assets_type', keyPath: 'id' },
  { name: 'plan_cover', keyPath: 'id' },
  { name: 'plan_type', keyPath: 'id' },
  { name: 'wish', keyPath: 'id' },
  { name: 'group_vip', keyPath: 'id' },
  { name: 'order_config', keyPath: 'id' },
  { name: 'discount_config', keyPath: 'id' },
  { name: 'person_center_icon', keyPath: 'id' },
  { name: 'contract_info', keyPath: 'id' },
  { name: 'operate_dialog', keyPath: 'id' },
  { name: 'update_remind', keyPath: 'id' },
];

export const STORE_NAMES = STORES.map((s) => s.name) as string[];
export type StoreName = string;
