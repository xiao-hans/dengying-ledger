/** 页面通用 hooks：异步数据加载 + 刷新计数（写操作后统一 reload）。 */
import { useCallback, useEffect, useState } from 'react';
import { useRefresh } from '../../app/useRefresh';

export function useAsync<T>(loader: () => Promise<T>, deps: unknown[] = []) {
  const { version } = useRefresh();
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);
  const reload = useCallback(() => setTick((t) => t + 1), []);
  useEffect(() => {
    let alive = true;
    setLoading(true);
    loader().then((d) => { if (alive) { setData(d); setError(null); } })
      .catch((e) => { if (alive) setError(e instanceof Error ? e.message : String(e)); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick, version]);
  return { data, loading, error, reload, setData };
}

/** 表单草稿：切页/切分类不丢输入（AGENTS.md §6.2）。 */
import { useDraft } from '../../app/navigation';
export function useFormDraft<T extends Record<string, unknown>>(key: string, fallback: T) {
  const draft = useDraft();
  const [value, setValue] = useState<T>(() => draft.get<T>(key, fallback));
  const patch = (p: Partial<T>) => setValue((v) => {
    const next = { ...v, ...p };
    draft.set(key, next as unknown as Record<string, unknown>);
    return next;
  });
  const reset = () => { draft.clear(key); setValue({ ...fallback }); };
  return { value, patch, reset, setValue };
}
