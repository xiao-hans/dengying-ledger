/** 业务模型类型（对齐 spec/ddl.sql 字段；金额一律整数分）。 */
import type { Entity } from './client';

export type BillType = 1 | 2 | 3;   // 1 支出 / 2 收入 / 3 转账
export type BillSource = 0 | 1 | 2 | 3 | 4 | 5; // 手工/通知/截屏/OCR/导入/周期

export interface Bill extends Entity {
  member_id: number;
  client_id: string;
  money: number;               // 整数分
  refund_money: number;        // 整数分
  type: BillType;
  exchange: number;
  book_id: number;
  member_cate_id: number;
  currency: string;
  day: number;                 // yyyyMMdd
  desc: string;
  not_in_budget: number;
  images: string;
  update_date: number;
  create_date: number;
  status: number;
  to_assets_id: number;
  from_assets_id: number;
  interest: number;
  interest_reckon_type: number;
  tag_ids: string;
  source: BillSource;
  import_id: number;
  repayment_day: number;
  local_create_time: number;
  local_update_time: number;
  installment_plan_id: string;
  installment_num: number;
  installment_money: number;
  installment_interest: number;
}

export interface BillBook extends Entity {
  id: number; name: string; icon_id: number; icon: string; tag: string;
  create_date: number; month_start_date: number;
}

export interface Category extends Entity {
  id: number; cate_id: number; type: 1 | 2; title: string; icon_id: number;
  base_icon: string; icon: string; is_custom: number; status: number;
  member_id: number; sort: number; hidden: number;
}

export interface Assets extends Entity {
  id: number; type_id: number; type: number; type_name: string; type_title: string;
  name: string; desc: string; icon: string; currency: string;
  create_date: number; update_date: number; credit_line: number;
  repayment_date: number; statement_date: number; is_remind: number;
  is_total_assets: number; is_next_bills: number; status: number;
  balance_cents: number;
}

export interface Rule extends Entity {
  id: number; key_word: string; source: number; member_id: number; type: number;
  book_id: number; member_cate_id: number; assets_id: number; tag_ids: string;
  create_date: number; update_date: number;
}

export interface Tag extends Entity {
  tag_id: number; book_id: number; member_id: number; name: string;
  sort: number; create_date: number; update_date: number;
}

export type AutoEventStatus = 0 | 1 | 2; // 0 待确认 / 1 已入账 / 2 已忽略

export interface AutoEvent extends Entity {
  id: string;
  hash: string;               // hash(sourceApp + money + occurredAt/60s + merchant)
  source: 'notification' | 'accessibility' | 'ocr';
  source_app: string;
  merchant: string;
  money: number;              // 整数分
  type: BillType;
  occurred_at: number;        // 秒
  raw_text: string;
  status: AutoEventStatus;
  member_cate_id: number;
  assets_id: number;
  tag_ids: string;
  book_id: number;
  desc: string;
  rule_id: number;
  create_time: number;
}

export interface Budget extends Entity {
  book_id: number; day: number; money: number; acf_money: number;
  is_remind: number; remind_rate: number; carry_down: number; is_copy: number;
}

export interface CateBudget extends Entity {
  book_id: number; day: number; member_cate_id: number; money: number; acf_money: number;
}

export interface RefundBill extends Entity {
  id: string; client_id: string; member_id: number; money: number;
  asset_id: number; day: number; desc: string; images: string;
  source: number; status: number; create_date: number; update_date: number;
}

export interface InstallmentPlan extends Entity {
  id: string; member_id: number; name: string; deduction_asset_id: number;
  start_date: number; money: number; total_count: number; executed_count: number;
  interest: number; interest_type: number; remainder_process_type: number;
  remaining_money: number; remaining_interest: number; next_execute_date: number;
  book_id: number; member_cate_id: number; not_in_budget: number;
  desc: string; images: string; tag_ids: string; status: number;
  create_time: number; update_time: number;
}

export interface SavePlan extends Entity {
  id: string; name: string; icon: string; type_id: number; target_money: number;
  saved_money: number; status: number; archived: number; desc: string;
  create_time: number; update_time: number;
}

export interface SavePlanItem extends Entity {
  id: string; plan_id: string; num: number; money: number; status: number;
  remark: string; create_time: number; update_time: number;
}

export interface FixedBill extends Entity {
  id: string; member_cate_id: number; money: number; type: BillType;
  fixed_type: number; fixed_day: number; book_id: number; assets_id: number;
  desc: string; tag_ids: string; status: number; next_execute_date: number;
  create_time: number; update_time: number;
}

export interface BillRemind extends Entity {
  id: string; title: string; time: string; repeat_type: number;
  enabled: number; create_time: number; update_time: number;
}

export interface MemberImportBillFile extends Entity {
  id: string; name: string; type: string; count: number;
  status: number; create_time: number; content: string;
}

export interface RemoteConfig extends Entity {
  key: string; value: unknown; update_time: number;
}

export interface BehaviorEvent extends Entity {
  id: string; event: string; params: string; create_time: number; reported: number;
}
