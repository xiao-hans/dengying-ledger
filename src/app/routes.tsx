/**
 * 路由注册表：route name → 页面组件。
 * 命名对齐样本 Activity（去掉 Activity 后缀），便于与 01-功能点全量清单.md 对照。
 * 各里程碑在此登记新页面；未登记时 App 显示开发期占位页（有明确返回出口）。
 */
import type { ComponentType } from 'react';
import type { PageProps } from './App';
import { BillsHome, StatsHome, AutoHome, MineHome } from '../features/home';
import { BillEdit } from '../features/bills/BillEdit';
import { BillList } from '../features/bills/BillList';
import { BillDetail } from '../features/bills/BillDetail';
import { GlobalSearch } from '../features/bills/GlobalSearch';
import { ReportCategory } from '../features/stats/ReportCategory';
import { ReportTrend } from '../features/stats/ReportTrend';
import { BudgetList } from '../features/stats/BudgetList';
import { ExportBills } from '../features/stats/ExportBills';

const registry: Record<string, ComponentType<PageProps>> = {
  BillsHome, StatsHome, AutoHome, MineHome,
  BillEdit, BillList, BillDetail, GlobalSearch,
  ReportCategory, ReportTrend, BudgetList, ExportBills,
};

/** 动态扩展：模块通过 registerRoutes 追加自己的页面。 */
export function registerRoutes(pages: Record<string, ComponentType<PageProps>>): void {
  Object.assign(registry, pages);
}

export const routes: Record<string, ComponentType<PageProps>> = registry;
