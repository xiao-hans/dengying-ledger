/**
 * 共享控件：玻璃卡、胶囊 chip、顶栏、列表行、空态、对话框、底部动作条、Toast。
 * 视觉口径全部来自 src/theme/tokens.ts，禁止在此硬编码颜色。
 */
import { useEffect, type ReactNode } from 'react';
import { Icon, type IconName } from './Icon';

/* ---------------- 顶栏 ---------------- */
export function TopBar({
  title, subtitle, onBack, right, brand = false,
}: {
  title: string; subtitle?: string; onBack?: () => void; right?: ReactNode; brand?: boolean;
}) {
  return (
    <header className="topbar">
      {onBack && (
        <button className="icon-btn" onClick={onBack} aria-label="返回"><Icon name="back" size={22} /></button>
      )}
      <div className="topbar-title">
        <div className={brand ? 't-title-large' : 't-title-medium'}>{title}</div>
        {subtitle && <div className="t-label-medium t-secondary">{subtitle}</div>}
      </div>
      <div className="topbar-right">{right}</div>
    </header>
  );
}

/* ---------------- 搜索胶囊 ---------------- */
export function SearchPill({
  value, onChange, placeholder = '搜索', onClear, onFocus,
}: {
  value: string; onChange: (v: string) => void; placeholder?: string;
  onClear?: () => void; onFocus?: () => void;
}) {
  return (
    <div className="search-pill">
      <Icon name="search" size={18} className="t-secondary" />
      <input
        value={value} onChange={(e) => onChange(e.target.value)}
        onFocus={onFocus} placeholder={placeholder} aria-label={placeholder}
      />
      {value && (
        <button aria-label="清除搜索" onClick={() => { onChange(''); onClear?.(); }}>
          <Icon name="close" size={15} />
        </button>
      )}
    </div>
  );
}

/* ---------------- 横向胶囊筛选 ---------------- */
export function ChipRow<T extends string | number>({
  items, value, onChange, allLabel,
}: {
  items: { key: T; label: string }[];
  value: T | 'all';
  onChange: (v: T | 'all') => void;
  allLabel?: string;
}) {
  const list = allLabel ? [{ key: 'all' as const, label: allLabel }, ...items] : items;
  return (
    <div className="chip-row">
      {list.map((it) => (
        <button
          key={String(it.key)}
          className={`chip ${value === it.key ? 'selected' : ''}`}
          onClick={() => onChange(it.key)}
        >{it.label}</button>
      ))}
    </div>
  );
}

/* ---------------- 玻璃卡片 ---------------- */
export function GlassCard({
  children, onClick, className = '', strong = false,
}: {
  children: ReactNode; onClick?: () => void; className?: string; strong?: boolean;
}) {
  return (
    <div
      className={`glass card-pad ${strong ? 'glass-strong' : ''} ${onClick ? 'clickable' : ''} ${className}`}
      style={{ borderRadius: 'var(--radius-card)' }}
      onClick={onClick}
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
    >{children}</div>
  );
}

/* ---------------- 列表行 ---------------- */
export function ListRow({
  icon, title, subtitle, right, onClick, danger = false, chevron = true,
}: {
  icon?: IconName; title: ReactNode; subtitle?: ReactNode; right?: ReactNode;
  onClick?: () => void; danger?: boolean; chevron?: boolean;
}) {
  return (
    <div className={`list-row ${onClick ? 'clickable' : ''} ${danger ? 't-danger' : ''}`} onClick={onClick} role={onClick ? 'button' : undefined}>
      {icon && <span className="list-row-icon"><Icon name={icon} size={19} /></span>}
      <div className="list-row-main">
        <div className="t-body-large">{title}</div>
        {subtitle && <div className="t-label-medium t-secondary" style={{ marginTop: 2 }}>{subtitle}</div>}
      </div>
      {right && <div className="list-row-right t-body-medium">{right}</div>}
      {chevron && onClick && <Icon name="chevron" size={17} className="t-secondary" />}
    </div>
  );
}

/* ---------------- 分区标题 ---------------- */
export function SectionTitle({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <div className="section-title">
      <span className="t-label-medium t-secondary t-semi">{children}</span>
      {action}
    </div>
  );
}

/* ---------------- 空态（必须给下一步出口） ---------------- */
export function EmptyState({
  icon = 'file', title, hint, actionLabel, onAction,
}: {
  icon?: IconName; title: string; hint?: string; actionLabel?: string; onAction?: () => void;
}) {
  return (
    <div className="empty-state">
      <div className="empty-icon"><Icon name={icon} size={30} /></div>
      <div className="t-body-large t-semi">{title}</div>
      {hint && <div className="t-body-small t-secondary">{hint}</div>}
      {actionLabel && onAction && (
        <button className="btn btn-primary" style={{ marginTop: 16 }} onClick={onAction}>{actionLabel}</button>
      )}
    </div>
  );
}

/* ---------------- 二次确认对话框 ---------------- */
export function ConfirmDialog({
  open, title, message, confirmLabel = '确认删除', danger = true,
  onConfirm, onCancel,
}: {
  open: boolean; title: string; message: string; confirmLabel?: string; danger?: boolean;
  onConfirm: () => void; onCancel: () => void;
}) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onCancel(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onCancel]);
  if (!open) return null;
  return (
    <div className="modal-scrim" onClick={onCancel}>
      <div className="modal-box glass glass-strong" onClick={(e) => e.stopPropagation()}>
        <div className="t-title-small">{title}</div>
        <div className="t-body-medium t-secondary" style={{ marginTop: 10 }}>{message}</div>
        <div className="modal-actions">
          <button className="btn btn-ghost" onClick={onCancel}>取消</button>
          <button className={`btn ${danger ? 'btn-danger' : 'btn-primary'}`} onClick={onConfirm}>{confirmLabel}</button>
        </div>
      </div>
    </div>
  );
}

/* ---------------- 底部动作条（Sheet） ---------------- */
export function BottomSheet({
  open, title, onClose, children,
}: {
  open: boolean; title: string; onClose: () => void; children: ReactNode;
}) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);
  if (!open) return null;
  return (
    <div className="modal-scrim sheet-scrim" onClick={onClose}>
      <div className="sheet" onClick={(e) => e.stopPropagation()}>
        <div className="sheet-handle" />
        <div className="sheet-head">
          <span className="t-title-small">{title}</span>
          <button className="icon-btn" onClick={onClose} aria-label="关闭"><Icon name="close" size={20} /></button>
        </div>
        <div className="sheet-body">{children}</div>
      </div>
    </div>
  );
}

/* ---------------- 开关 ---------------- */
export function Switch({ checked, onChange, label }: { checked: boolean; onChange: (v: boolean) => void; label?: string }) {
  return (
    <button className="switch-wrap" onClick={() => onChange(!checked)} role="switch" aria-checked={checked}>
      {label && <span className="t-body-medium">{label}</span>}
      <span className={`switch ${checked ? 'on' : ''}`}><span className="switch-knob" /></span>
    </button>
  );
}

/* ---------------- 徽标 ---------------- */
export function Badge({ children, tone = 'neutral' }: { children: ReactNode; tone?: 'neutral' | 'danger' | 'brand' }) {
  return <span className={`badge badge-${tone}`}>{children}</span>;
}

/* ---------------- 金额展示 ---------------- */
export function Money({ cents, type, sign = true }: { cents: number; type?: 1 | 2 | 3; sign?: boolean }) {
  const prefix = !sign ? '' : type === 2 ? '+' : type === 1 ? '-' : '';
  return <span className="money">{prefix}{formatMoney(cents)}</span>;
}

/** 整数分 → 展示串（保留两位，不四舍五入）。 */
export function formatMoney(cents: number): string {
  const neg = cents < 0;
  const abs = Math.abs(cents);
  const s = `${Math.floor(abs / 100)}.${String(abs % 100).padStart(2, '0')}`;
  return neg ? `-${s}` : s;
}

/* ---------------- Toast ---------------- */
export function Toast({ text, onDone }: { text: string | null; onDone?: () => void }) {
  useEffect(() => {
    if (!text) return;
    const t = setTimeout(() => onDone?.(), 2200);
    return () => clearTimeout(t);
  }, [text, onDone]);
  if (!text) return null;
  return <div className="toast glass glass-dark">{text}</div>;
}
