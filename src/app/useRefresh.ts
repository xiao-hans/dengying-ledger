/** 全局数据版本号：任何写操作后广播，首页/报表切回来自动刷新。 */
import { createContext, useContext } from 'react';

export const RefreshCtx = createContext<{ version: number; bump: () => void }>({ version: 0, bump: () => {} });
export const useRefresh = () => useContext(RefreshCtx);
