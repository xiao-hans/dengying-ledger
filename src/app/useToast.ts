import { createContext, useContext } from 'react';

export const ToastCtx = createContext<{ show: (t: string) => void }>({ show: () => {} });
export const useToast = () => useContext(ToastCtx);
