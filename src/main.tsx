import React from 'react';
import ReactDOM from 'react-dom/client';
import { App } from './app/App';
import { bootstrap } from './db/bootstrap';
import './theme/tokens.css';
import './components/components.css';

// 本地优先：先安装 mock 后端并初始化传输，再挂载 UI（对齐 Application.onCreate）。
bootstrap().finally(() => {
  ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>,
  );
});
