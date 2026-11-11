/** 底部玻璃 Dock：对齐灯影 BottomDock。图标横排，只有选中项展开文字，高光玻璃条滑到选中项背后。 */
import { Icon, type IconName } from './Icon';
import type { TabKey } from '../app/navigation';

const ITEMS: { key: TabKey; label: string; icon: IconName }[] = [
  { key: 'bills', label: '账单', icon: 'receipt' },
  { key: 'stats', label: '统计', icon: 'chart' },
  { key: 'auto', label: '自动记账', icon: 'auto' },
  { key: 'mine', label: '我的', icon: 'mine' },
];

export function Dock({ tab, onSwitch }: {
  tab: TabKey; onSwitch: (t: TabKey) => void;
}) {
  const index = Math.max(0, ITEMS.findIndex((it) => it.key === tab));
  return (
    <nav className="dock glass glass-strong" aria-label="主导航">
      <span
        className="dock-highlight"
        style={{ transform: `translateX(${index * 100}%)` }}
        aria-hidden
      />
      {ITEMS.map((it) => (
        <button
          key={it.key}
          className={`dock-item ${tab === it.key ? 'active' : ''}`}
          onClick={() => onSwitch(it.key)}
          aria-current={tab === it.key ? 'page' : undefined}
        >
          <Icon name={it.icon} size={21} strokeWidth={tab === it.key ? 2.1 : 1.8} />
          {tab === it.key && <span className="dock-label">{it.label}</span>}
        </button>
      ))}
    </nav>
  );
}
