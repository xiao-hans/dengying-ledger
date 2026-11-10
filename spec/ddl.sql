-- 账单主表：client_id 端侧生成，离线幂等
CREATE TABLE IF NOT EXISTS `bill` (
  `client_id` TEXT NOT NULL,
  `money` REAL NOT NULL,
  `refund_money` REAL NOT NULL DEFAULT 0,
  `type` INTEGER NOT NULL,                 -- 1支出 2收入 3转账
  `exchange` REAL NOT NULL,
  `book_id` INTEGER NOT NULL,
  `member_cate_id` INTEGER NOT NULL,
  `currency` TEXT NOT NULL,
  `day` INTEGER NOT NULL,                  -- yyyyMMdd
  `desc` TEXT,
  `not_in_budget` INTEGER NOT NULL DEFAULT 0,
  `images` TEXT,
  `update_date` INTEGER NOT NULL,
  `create_date` INTEGER NOT NULL DEFAULT 0,
  `member_id` INTEGER NOT NULL,
  `status` INTEGER NOT NULL,
  `to_assets_id` INTEGER NOT NULL DEFAULT 0,
  `from_assets_id` INTEGER NOT NULL DEFAULT 0,
  `interest` REAL NOT NULL DEFAULT 0,
  `interest_reckon_type` INTEGER NOT NULL DEFAULT 0,
  `tag_ids` TEXT,
  `source` INTEGER NOT NULL DEFAULT 0,    -- 0手工 1通知 2截屏 3OCR 4导入 5周期
  `import_id` INTEGER NOT NULL DEFAULT 0,
  `repayment_day` INTEGER NOT NULL DEFAULT 0,
  `local_create_time` INTEGER NOT NULL DEFAULT 0,
  `local_update_time` INTEGER NOT NULL DEFAULT 0,
  `installment_plan_id` TEXT,
  `installment_num` INTEGER NOT NULL DEFAULT 0,
  `installment_money` REAL NOT NULL DEFAULT 0,
  `installment_interest` REAL NOT NULL DEFAULT 0,
  PRIMARY KEY(`member_id`, `client_id`)
);
CREATE INDEX IF NOT EXISTS idx_bill_day ON `bill`(`day`);
CREATE INDEX IF NOT EXISTS idx_bill_book_day ON `bill`(`book_id`, `day`);

CREATE TABLE IF NOT EXISTS `bill_book` (
  `id` INTEGER NOT NULL, `name` TEXT NOT NULL, `icon_id` INTEGER NOT NULL,
  `icon` TEXT NOT NULL, `tag` TEXT NOT NULL, `create_date` INTEGER,
  `month_start_date` INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(`id`)
);

CREATE TABLE IF NOT EXISTS `member_book` (
  `member_id` INTEGER NOT NULL, `book_id` INTEGER NOT NULL,
  `appear` REAL NOT NULL, `income` REAL NOT NULL,
  `is_master` INTEGER NOT NULL, `join_date` INTEGER,
  PRIMARY KEY(`member_id`, `book_id`)
);

CREATE TABLE IF NOT EXISTS `category` (
  `id` INTEGER NOT NULL, `cate_id` INTEGER NOT NULL, `type` INTEGER NOT NULL,
  `title` TEXT NOT NULL, `icon_id` INTEGER NOT NULL, `base_icon` TEXT NOT NULL,
  `icon` TEXT NOT NULL, `is_custom` INTEGER NOT NULL, `status` INTEGER NOT NULL,
  `member_id` INTEGER NOT NULL, PRIMARY KEY(`id`, `member_id`)
);

CREATE TABLE IF NOT EXISTS `cate_extend` (
  `member_id` INTEGER NOT NULL, `book_id` INTEGER NOT NULL,
  `member_cate_id` INTEGER NOT NULL, `type` INTEGER NOT NULL,
  `sort` INTEGER NOT NULL, `hidden` INTEGER NOT NULL, `status` INTEGER NOT NULL,
  `create_date` INTEGER, `update_date` INTEGER,
  PRIMARY KEY(`member_cate_id`, `book_id`, `member_id`)
);

CREATE TABLE IF NOT EXISTS `budget` (
  `book_id` INTEGER NOT NULL, `day` INTEGER NOT NULL, `money` REAL NOT NULL,
  `acf_money` REAL NOT NULL, `is_remind` INTEGER NOT NULL,
  `remind_rate` REAL NOT NULL, `carry_down` INTEGER NOT NULL,
  `is_copy` INTEGER NOT NULL, PRIMARY KEY(`book_id`, `day`)
);

CREATE TABLE IF NOT EXISTS `cate_budget` (
  `book_id` INTEGER NOT NULL, `day` INTEGER NOT NULL,
  `member_cate_id` INTEGER NOT NULL, `money` REAL NOT NULL,
  `acf_money` REAL NOT NULL, PRIMARY KEY(`book_id`, `day`, `member_cate_id`)
);

CREATE TABLE IF NOT EXISTS `assets` (
  `id` INTEGER NOT NULL, `type_id` INTEGER NOT NULL, `type` INTEGER NOT NULL,
  `type_name` TEXT NOT NULL, `type_title` TEXT NOT NULL, `name` TEXT NOT NULL,
  `desc` TEXT, `icon` TEXT NOT NULL, `currency` TEXT NOT NULL,
  `create_date` INTEGER, `update_date` INTEGER, `credit_line` REAL NOT NULL,
  `repayment_date` INTEGER NOT NULL, `statement_date` INTEGER NOT NULL,
  `is_remind` INTEGER NOT NULL, `is_total_assets` INTEGER NOT NULL,
  `is_next_bills` INTEGER NOT NULL, `status` INTEGER NOT NULL DEFAULT 1,
  PRIMARY KEY(`id`)
);

-- 自动记账规则：关键词 -> 分类/账户/标签
CREATE TABLE IF NOT EXISTS `rule` (
  `id` INTEGER NOT NULL, `key_word` TEXT NOT NULL DEFAULT '',
  `source` INTEGER NOT NULL DEFAULT 0, `member_id` INTEGER NOT NULL DEFAULT 0,
  `type` INTEGER NOT NULL, `book_id` INTEGER NOT NULL DEFAULT 0,
  `member_cate_id` INTEGER NOT NULL DEFAULT 0, `assets_id` INTEGER NOT NULL DEFAULT 0,
  `tag_ids` TEXT, `create_date` INTEGER, `update_date` INTEGER, PRIMARY KEY(`id`)
);

CREATE TABLE IF NOT EXISTS `tag` (
  `tag_id` INTEGER NOT NULL, `book_id` INTEGER NOT NULL,
  `member_id` INTEGER NOT NULL, `name` TEXT NOT NULL, `sort` INTEGER NOT NULL,
  `create_date` INTEGER, `update_date` INTEGER, PRIMARY KEY(`tag_id`)
);
CREATE TABLE IF NOT EXISTS `bill_tag` (
  `client_id` TEXT NOT NULL, `member_id` INTEGER NOT NULL, `tag_id` INTEGER NOT NULL,
  PRIMARY KEY(`client_id`, `member_id`, `tag_id`)
);
CREATE TABLE IF NOT EXISTS `refund_bill` (
  `id` TEXT NOT NULL, `client_id` TEXT NOT NULL, `member_id` INTEGER NOT NULL,
  `money` REAL NOT NULL, `asset_id` INTEGER NOT NULL, `day` INTEGER NOT NULL,
  `desc` TEXT, `images` TEXT, `source` INTEGER NOT NULL, `status` INTEGER NOT NULL,
  `create_date` INTEGER NOT NULL, `update_date` INTEGER NOT NULL, PRIMARY KEY(`id`)
);
CREATE TABLE IF NOT EXISTS `installment_plan` (
  `id` TEXT NOT NULL, `member_id` INTEGER NOT NULL, `name` TEXT NOT NULL,
  `deduction_asset_id` INTEGER NOT NULL, `start_date` INTEGER NOT NULL,
  `money` REAL NOT NULL, `total_count` INTEGER NOT NULL, `executed_count` INTEGER NOT NULL,
  `interest` REAL NOT NULL, `interest_type` INTEGER NOT NULL,
  `remainder_process_type` INTEGER NOT NULL, `remaining_money` REAL NOT NULL,
  `remaining_interest` REAL NOT NULL, `next_execute_date` INTEGER NOT NULL,
  `book_id` INTEGER NOT NULL, `member_cate_id` INTEGER NOT NULL,
  `not_in_budget` INTEGER NOT NULL, `desc` TEXT, `images` TEXT, `tag_ids` TEXT,
  `status` INTEGER NOT NULL, `create_time` INTEGER NOT NULL, `update_time` INTEGER NOT NULL,
  PRIMARY KEY(`id`)
);
CREATE TABLE IF NOT EXISTS `installment_item` (
  `id` TEXT NOT NULL, `plan_id` TEXT NOT NULL, `member_id` INTEGER NOT NULL,
  `client_id` TEXT, `num` INTEGER NOT NULL, `money` REAL NOT NULL,
  `interest` REAL NOT NULL, `executed_time` INTEGER NOT NULL,
  `executed_status` INTEGER NOT NULL, `create_time` INTEGER NOT NULL,
  `update_time` INTEGER NOT NULL, PRIMARY KEY(`id`)
);
