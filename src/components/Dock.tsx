/** 底部玻璃 Dock：常驻、当前项高亮（用更亮更实的玻璃面，不用墨色实底）。 */
import { Icon, type IconName } from './Icon';
import type { TabKey } from '../app/navigation';

const ITEMS: { key: TabKey; label: string; icon: IconName }[] = [
  { key: 'bills', label: '账单', icon: 'receipt' },
  { key: 'stats', label: '统计', icon: 'chart' },
  { key: 'auto', label: '自动记账', icon: 'auto' },
  { key: 'mine', label: '我的', icon: 'mine' },
];

export function Dock({ tab, onSwitch, onQuickAdd }: {
  tab: TabKey; onSwitch: (t: TabKey) => void; onQuickAdd: () => void;
}) {
  return (
    <nav className="dock glass glass-strong" aria-label="主导航">
      {ITEMS.slice(0, 2).map((it) => <DockItem key={it.key} item={it} active={tab === it.key} onClick={() => onSwitch(it.key)} />)}
      <button className="dock-plus" onClick={onQuickAdd} aria-label="记一笔">
        <Icon name="plus" size={26} strokeWidth={2.2} />
      </button>
      {ITEMS.slice(2).map((it) => <DockItem key={it.key} item={it} active={tab === it.key} onClick={() => onSwitch(it.key)} />)}
    </nav>
  );
}

function DockItem({ item, active, onClick }: {
  item: { key: TabKey; label: string; icon: IconName }; active: boolean; onClick: () => void;
}) {
  return (
    <button className={`dock-item ${active ? 'active' : ''}`} onClick={onClick} aria-current={active ? 'page' : undefined}>
      <span className="dock-icon"><Icon name={item.icon} size={21} strokeWidth={active ? 2.1 : 1.8} /></span>
      <span className="t-label-small">{item.label}</span>
    </button>
  );
}
