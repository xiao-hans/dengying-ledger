/** 导入：粘贴/上传确认、历史、重复处理、OCR 文本确认。不静默入账。 */
import { useMemo, useState } from 'react';
import type { PageProps } from '../../app/App';
import { useNav } from '../../app/navigation';
import { useRefresh } from '../../app/useRefresh';
import { useToast } from '../../app/useToast';
import { Icon } from '../../components/Icon';
import {
  TopBar, GlassCard, SectionTitle, ListRow, EmptyState, ConfirmDialog, formatMoney,
} from '../../components/ui';
import { useAsync } from '../common/hooks';
import { api, type Dict } from '../common/api';
import { N, S, toCents } from '../common/format';
import './manage.css';

interface Draft {
  key: string;
  desc: string;
  money: string;
  day: string;
  skip: boolean;
}

function today(): string {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

/** 每行：说明,金额 或 说明 金额。金额按元解析成整数分。 */
function parseLines(text: string): Draft[] {
  return text.split(/\r?\n/).map((line) => line.trim()).filter(Boolean).map((line, i) => {
    const parts = line.split(/[,，\t]/).map((s) => s.trim()).filter(Boolean);
    let desc = line;
    let money = '';
    if (parts.length >= 2) {
      desc = parts[0];
      money = parts[1];
    } else {
      const m = line.match(/(-?\d+(?:\.\d{1,2})?)\s*$/);
      if (m && m.index !== undefined) {
        money = m[1];
        desc = line.slice(0, m.index).trim() || '导入账单';
      }
    }
    return { key: `${i}-${desc}`, desc: desc || '导入账单', money, day: today(), skip: false };
  });
}

function ConfirmList({ drafts, setDrafts }: { drafts: Draft[]; setDrafts: (d: Draft[]) => void }) {
  if (drafts.length === 0) return null;
  return (
    <div className="card" style={{ marginTop: 12 }}>
      {drafts.map((d, i) => (
        <div key={d.key} className="row" style={{ gap: 8, padding: '8px 0', opacity: d.skip ? 0.45 : 1 }}>
          <input className="field" style={{ flex: 2 }} value={d.desc} aria-label="说明"
            onChange={(e) => setDrafts(drafts.map((x, j) => j === i ? { ...x, desc: e.target.value } : x))} />
          <input className="field" style={{ flex: 1 }} inputMode="decimal" value={d.money} aria-label="金额（元）"
            onChange={(e) => setDrafts(drafts.map((x, j) => j === i ? { ...x, money: e.target.value } : x))} />
          <button className="btn btn-ghost" style={{ padding: '4px 8px' }} onClick={() => setDrafts(drafts.map((x, j) => j === i ? { ...x, skip: !x.skip } : x))}>{d.skip ? '恢复' : '跳过'}</button>
        </div>
      ))}
    </div>
  );
}

export function ImportHome(_: PageProps) {
  const nav = useNav();
  return (
    <div className="page">
      <TopBar title="导入账单" subtitle="先预览，确认后才写入" onBack={nav.pop} />
      <div className="page-scroll">
        <div className="page-pad">
          <div className="card">
            <ListRow icon="upload" title="粘贴文本导入" subtitle="每行：说明,金额（元）" onClick={() => nav.push('ImportPaste')} />
            <ListRow icon="receipt" title="OCR 文本确认" subtitle="把识别出的文字贴进来逐条核对" onClick={() => nav.push('ImportOcr')} />
            <ListRow icon="folder" title="导入历史" subtitle="查看或删除某次导入" onClick={() => nav.push('ImportHistory')} />
            <ListRow icon="download" title="导出 CSV" subtitle="已有导出页" onClick={() => nav.push('ExportBills')} />
          </div>
          <p className="t-label-medium t-secondary manage-note">
            重复判断用「说明 + 金额（分）+ 日期」。同一行再次导入会计入重复，不会覆盖已有账单。Web 端不读取相册，OCR 用粘贴文本等价。
          </p>
        </div>
      </div>
    </div>
  );
}

function useImportCommit(kind: 'paste' | 'ocr') {
  const toast = useToast();
  const refresh = useRefresh();
  const nav = useNav();
  return async (drafts: Draft[]) => {
    const items = drafts.filter((d) => !d.skip).map((d) => {
      const cents = toCents(d.money);
      return { desc: d.desc.trim() || '导入账单', money: cents, day: d.day, billType: 1 };
    }).filter((d) => d.money !== 0);
    if (items.length === 0) return toast.show('没有可导入的行');
    try {
      const r = await api.importBills({
        items, fileName: kind === 'ocr' ? 'ocr.txt' : 'paste.csv', type: kind === 'ocr' ? 3 : 1,
      });
      toast.show(`成功 ${N(r.success)} · 失败 ${N(r.failed)} · 重复 ${N(r.repeated)}`);
      refresh.bump();
      nav.pop();
    } catch (e) { toast.show(`导入失败：${e instanceof Error ? e.message : String(e)}`); }
  };
}

export function ImportPaste(_: PageProps) {
  const nav = useNav();
  const [text, setText] = useState('');
  const [drafts, setDrafts] = useState<Draft[]>([]);
  const commit = useImportCommit('paste');
  return (
    <div className="page">
      <TopBar title="粘贴导入" onBack={nav.pop} />
      <div className="page-scroll">
        <div className="page-pad">
          <SectionTitle>每行一条</SectionTitle>
          <textarea className="field" rows={6} value={text} onChange={(e) => setText(e.target.value)} placeholder={'午饭,28.5\n地铁 4'} />
          <button className="btn btn-ghost" style={{ marginTop: 10, width: '100%' }} onClick={() => setDrafts(parseLines(text))}>解析预览</button>
          <ConfirmList drafts={drafts} setDrafts={setDrafts} />
          {drafts.length > 0 && (
            <button className="btn btn-primary" style={{ marginTop: 12, width: '100%' }} onClick={() => commit(drafts)}>确认导入</button>
          )}
        </div>
      </div>
    </div>
  );
}

export function ImportOcr(_: PageProps) {
  const nav = useNav();
  const [text, setText] = useState('');
  const [drafts, setDrafts] = useState<Draft[]>([]);
  const commit = useImportCommit('ocr');
  return (
    <div className="page">
      <TopBar title="OCR 确认" subtitle="识别结果需逐条核对" onBack={nav.pop} />
      <div className="page-scroll">
        <div className="page-pad">
          <p className="t-label-medium t-secondary">Web 不能直接读相册。把识别出的文字贴在下面，核对金额后再写入。</p>
          <textarea className="field" style={{ marginTop: 10 }} rows={6} value={text} onChange={(e) => setText(e.target.value)} placeholder="识别文本，每行一笔" />
          <button className="btn btn-ghost" style={{ marginTop: 10, width: '100%' }} onClick={() => setDrafts(parseLines(text))}>生成待确认</button>
          <ConfirmList drafts={drafts} setDrafts={setDrafts} />
          {drafts.length > 0 && (
            <button className="btn btn-primary" style={{ marginTop: 12, width: '100%' }} onClick={() => commit(drafts)}>确认写入</button>
          )}
        </div>
      </div>
    </div>
  );
}

export function ImportHistory(_: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const { data, loading, error, reload } = useAsync(() => api.importFileList(), [refresh.version]);
  const rows = data || [];
  const [pending, setPending] = useState<Dict | null>(null);
  const total = useMemo(() => rows.reduce((s, r) => s + N(r.success), 0), [rows]);

  async function remove() {
    const row = pending;
    setPending(null);
    if (!row) return;
    try {
      await api.importFileDelete(S(row.id));
      toast.show('已删除这条导入记录');
      reload(); refresh.bump();
    } catch (e) { toast.show(`删除失败：${e instanceof Error ? e.message : String(e)}`); }
  }

  return (
    <div className="page">
      <TopBar title="导入历史" subtitle={`累计成功 ${total} 笔`} onBack={nav.pop} />
      <div className="page-scroll">
        <div className="page-pad">
          {loading && <div className="t-body-medium t-secondary">加载中…</div>}
          {error && <div className="card"><EmptyState icon="warning" title="加载失败" hint={error} actionLabel="重试" onAction={reload} /></div>}
          {!loading && !error && rows.length === 0 && (
            <div className="card"><EmptyState icon="folder" title="还没有导入记录" hint="从导入主页开始" actionLabel="去导入" onAction={() => nav.push('ImportPaste')} /></div>
          )}
          {rows.map((row) => (
            <GlassCard key={S(row.id)}>
              <div className="row-between">
                <div>
                  <div className="t-body-large t-semi">{S(row.file_name) || '导入'}</div>
                  <div className="t-label-medium t-secondary">成功 {N(row.success)} · 失败 {N(row.failed)} · 重复 {N(row.repeated)}</div>
                </div>
                <button className="btn btn-ghost" style={{ padding: '4px 10px' }} onClick={() => setPending(row)}>删除记录</button>
              </div>
            </GlassCard>
          ))}
          <p className="t-label-medium t-secondary manage-note">删除只去掉导入记录，不会撤回已经写入的账单。要改账单请到账单详情。</p>
        </div>
      </div>
      <ConfirmDialog open={Boolean(pending)} title="删除这条导入记录？" message="只删除记录本身，已经入账的账单会保留。" danger confirmLabel="删除" onConfirm={remove} onCancel={() => setPending(null)} />
    </div>
  );
}

