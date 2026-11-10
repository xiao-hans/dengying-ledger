/** 导出账单：CSV（UTF-8 BOM，Excel 直开）+ 全量 JSON 备份；同样可再导入。 */
import { useState } from 'react';
import type { PageProps } from '../../app/App';
import { useNav } from '../../app/navigation';
import { useToast } from '../../app/useToast';
import { TopBar, GlassCard, SectionTitle, ListRow, ChipRow, formatMoney } from '../../components/ui';
import { api, type Dict } from '../common/api';
import { useAsync } from '../common/hooks';
import { N, S, cateName, cateMap } from '../common/format';

const TYPE: Record<number, string> = { 1: '支出', 2: '收入', 3: '转账' };
const esc = (v: unknown) => `"${S(v).replace(/"/g, '""')}"`;

function download(name: string, content: string, mime: string) {
  const blob = new Blob([content], { type: mime });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url; a.download = name; a.click();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}

export function ExportBills(_: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const { data } = useAsync(async () => {
    const [page, cates] = await Promise.all([api.billsPage(1), api.mainCates()]);
    return { rows: (page.bills as Dict[]) || (page.items as Dict[]) || [], cates: [...(cates.expense || []), ...(cates.income || [])] };
  });
  const [period, setPeriod] = useState<'all' | 'month' | 'year'>('all');
  const cmap = cateMap(data?.cates || []);
  const now = new Date();
  const ym = now.getFullYear() * 100 + now.getMonth() + 1;
  const rows = (data?.rows || []).filter((r) => period === 'all' ? true : period === 'year' ? S(r.day).slice(0, 4) === String(now.getFullYear()) : Number(S(r.day).slice(0, 6)) === ym);

  function exportCsv() {
    const header = '日期,类型,分类,金额(元),备注,账本,账户,标签,来源,账单编号';
    const lines = rows.map((r) => [
      S(r.day), TYPE[N(r.type, 1)] || '账单', cateName(cmap, r.member_cate_id),
      (N(r.money) / 100).toFixed(2), S(r.desc), S(r.book_id), S(r.from_assets_id),
      S(r.tag_ids), S(r.source), S(r.client_id),
    ].map(esc).join(','));
    download(`灯影账单_${period}_${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}.csv`,
      '﻿' + [header, ...lines].join('\r\n'), 'text/csv;charset=utf-8');
    toast.show(`已导出 ${rows.length} 笔账单`);
  }
  async function exportJson() {
    const all = await api.exportAll();
    download('灯影记账_完整备份.json', JSON.stringify({ app: '灯影记账', version: '1.8.1', exportedAt: new Date().toISOString(), data: all }, null, 2), 'application/json');
    toast.show('完整备份已导出');
  }

  return (
    <div className="page">
      <TopBar title="导出账单" onBack={nav.pop} />
      <div className="page-scroll">
        <div className="page-pad">
          <ChipRow value={period} onChange={(v) => setPeriod(v as typeof period)} items={[{ key: 'month', label: '本月' }, { key: 'year', label: '本年' }]} allLabel="全部" />
          <GlassCard>
            <div className="t-label-medium t-secondary">将导出</div>
            <div className="t-title-large" style={{ marginTop: 4 }}>{rows.length} 笔账单</div>
            <div className="t-label-medium t-secondary" style={{ marginTop: 8 }}>
              支出 ¥{formatMoney(rows.filter((r) => N(r.type) === 1).reduce((s, r) => s + N(r.money), 0))} · 收入 ¥{formatMoney(rows.filter((r) => N(r.type) === 2).reduce((s, r) => s + N(r.money), 0))}
            </div>
          </GlassCard>
          <SectionTitle>导出格式</SectionTitle>
          <div className="card">
            <ListRow icon="file" title="CSV 表格" subtitle="Excel / 数字表格可直接打开，可再导入" chevron={false} onClick={exportCsv} />
            <ListRow icon="download" title="完整备份 JSON" subtitle="包含账本、分类、预算、规则、计划等全部本地数据" chevron={false} onClick={exportJson} />
          </div>
          <div className="t-label-medium t-secondary" style={{ padding: '14px 4px' }}>
            数据只保存在此设备；导出文件由你自行保管，不会上传到任何服务器。
          </div>
        </div>
      </div>
    </div>
  );
}
