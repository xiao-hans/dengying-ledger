/** 资金账户新建 / 编辑：余额（元）→ 整数分。 */
import { useEffect, useState } from 'react';
import type { PageProps } from '../../app/App';
import { useNav } from '../../app/navigation';
import { useRefresh } from '../../app/useRefresh';
import { useToast } from '../../app/useToast';
import { TopBar, GlassCard, SectionTitle, Switch, ConfirmDialog } from '../../components/ui';
import { useAsync } from '../common/hooks';
import { api, local, type Dict } from '../common/api';
import { N, S, centsToYuan } from '../common/format';
import { inputCents } from './common';
import './manage.css';

export function AssetEdit({ route }: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const refresh = useRefresh();
  const id = N(route.params?.assetId);
  const [name, setName] = useState('');
  const [typeId, setTypeId] = useState(1);
  const [balance, setBalance] = useState('0');
  const [credit, setCredit] = useState('0');
  const [repay, setRepay] = useState('0');
  const [statement, setStatement] = useState('0');
  const [total, setTotal] = useState(true);
  const [remind, setRemind] = useState(false);
  const [desc, setDesc] = useState('');
  const { data } = useAsync(async () => ({ types: await api.assetTypes(), assets: await local.assets() }));
  const [confirm, setConfirm] = useState(false);
  const row = (data?.assets || []).find((a: Dict) => N(a.id) === id);
  useEffect(() => {
    if (!row) return;
    setName(S(row.name)); setTypeId(N(row.type_id, 1));
    setBalance(centsToYuan(row.balance_cents)); setCredit(centsToYuan(row.credit_line));
    setRepay(String(N(row.repayment_date) || '')); setStatement(String(N(row.statement_date) || ''));
    setTotal(N(row.is_total_assets, 1) === 1); setRemind(N(row.is_remind) === 1); setDesc(S(row.desc));
  }, [row]);
  const types = data?.types || [];
  async function save() {
    if (!name.trim()) return toast.show('请输入账户名称');
    const b = inputCents(balance); const c = inputCents(credit);
    if (b === null || c === null) return toast.show('金额最多两位小数，例如 1234.56');
    const t = types.find((x: Dict) => N(x.id) === typeId);
    try {
      await api.saveAsset({
        name: name.trim(), typeId, type: typeId, typeName: S(t?.key, 'cash'), typeTitle: S(t?.title, '现金'),
        balance: b, creditLine: c, repaymentDate: N(repay), statementDate: N(statement),
        desc, isTotalAssets: total ? 1 : 0, isRemind: remind ? 1 : 0, isNextBills: 0, currency: 'CNY',
        ...(id ? { fundAccountId: id } : {}),
      }, !!id);
      toast.show(id ? '账户已更新' : '账户已创建'); refresh.bump(); nav.pop();
    } catch (e) { toast.show(`保存失败：${e instanceof Error ? e.message : String(e)}`); }
  }
  async function del() {
    if (!id) return;
    try { await api.deleteAsset(id); toast.show('账户已删除'); refresh.bump(); nav.pop(); }
    catch (e) { toast.show(`删除失败：${e instanceof Error ? e.message : String(e)}`); }
  }
  return (
    <div className="page">
      <TopBar title={id ? '编辑账户' : '新建账户'} onBack={nav.pop} />
      <div className="page-scroll">
        <div className="page-pad">
          <GlassCard>
            <div className="t-label-medium t-secondary">账户名称</div>
            <input className="field" style={{ marginTop: 8 }} value={name} onChange={(e) => setName(e.target.value)} placeholder="例如 工资卡 / 支付宝" />
            <div className="t-label-medium t-secondary" style={{ margin: '16px 0 8px' }}>账户类型</div>
            <div className="row" style={{ flexWrap: 'wrap', gap: 8 }}>{(types.length ? types : [{ id: 1, title: '现金' }, { id: 2, title: '银行卡' }, { id: 3, title: '信用卡' }, { id: 4, title: '支付宝' }, { id: 5, title: '微信' }, { id: 6, title: '借条' }]).map((t: Dict) => (
              <button key={N(t.id)} className={`chip ${typeId === N(t.id) ? 'selected' : ''}`} onClick={() => setTypeId(N(t.id))}>{S(t.title)}</button>
            ))}</div>
            <div className="t-label-medium t-secondary" style={{ margin: '16px 0 8px' }}>当前余额（元）</div>
            <input className="field" inputMode="decimal" value={balance} onChange={(e) => setBalance(e.target.value)} placeholder="0.00" />
            <div className="t-label-medium t-secondary" style={{ margin: '16px 0 8px' }}>信用额度（元）</div>
            <input className="field" inputMode="decimal" value={credit} onChange={(e) => setCredit(e.target.value)} placeholder="0.00" />
          </GlassCard>
          <SectionTitle>还款与账单日</SectionTitle>
          <div className="card card-pad">
            <div className="grid-2" style={{ gap: 10 }}>
              <label className="t-label-medium t-secondary">还款日<input className="field" style={{ marginTop: 6 }} inputMode="numeric" value={repay} onChange={(e) => setRepay(e.target.value)} /></label>
              <label className="t-label-medium t-secondary">账单日<input className="field" style={{ marginTop: 6 }} inputMode="numeric" value={statement} onChange={(e) => setStatement(e.target.value)} /></label>
            </div>
            <div className="t-label-medium t-secondary" style={{ marginTop: 12 }}>备注</div>
            <input className="field" style={{ marginTop: 6 }} value={desc} onChange={(e) => setDesc(e.target.value)} placeholder="选填" />
            <div className="row-between" style={{ marginTop: 16 }}><span className="t-body-medium">计入总资产</span><Switch checked={total} onChange={setTotal} /></div>
            <div className="row-between" style={{ marginTop: 12 }}><span className="t-body-medium">还款提醒</span><Switch checked={remind} onChange={setRemind} /></div>
          </div>
          <div className="t-label-medium t-secondary manage-note">信用卡余额可为负；借条账户用于记录他人借款，不与现金资产混算。</div>
          {id > 0 && <button className="btn btn-ghost t-danger" style={{ width: '100%', marginTop: 12 }} onClick={() => setConfirm(true)}>删除账户</button>}
          <ConfirmDialog open={confirm} title={`删除账户「${name || '未命名'}」？`} message="已有账单不会删除，但账户不再出现在列表里。" onConfirm={() => { setConfirm(false); void del(); }} onCancel={() => setConfirm(false)} />
        </div>
      </div>
      <div className="page-fixed-bottom"><div className="row" style={{ gap: 10 }}><button className="btn btn-ghost" onClick={nav.pop}>取消</button><button className="btn btn-primary grow" onClick={save}>保存账户</button></div></div>
    </div>
  );
}
