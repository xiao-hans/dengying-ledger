/** GuideAutoBillActivity：5 步权限/流程引导，全程可跳过，最后一步才开启。 */
import { useState } from 'react';
import type { PageProps } from '../../../app/App';
import { useNav } from '../../../app/navigation';
import { useToast } from '../../../app/useToast';
import { Icon, type IconName } from '../../../components/Icon';
import { TopBar, GlassCard, EmptyState } from '../../../components/ui';
import { GUIDE_STEPS, saveSetting, SOURCE_LABEL } from '../engine';
import '../auto.css';

export function GuideAutoBill(_: PageProps) {
  const nav = useNav();
  const toast = useToast();
  const [step, setStep] = useState(0);
  const s = GUIDE_STEPS[step];
  async function finish() {
    await saveSetting('master', true);
    await saveSetting('notification', true);
    toast.show('自动记账已开启，识别结果需你确认');
    nav.pop(); nav.push('AutoBillSetting');
  }
  return (
    <div className="page">
      <TopBar title="开启自动记账" subtitle={`第 ${step + 1} / ${GUIDE_STEPS.length} 步`} onBack={nav.pop}
        right={<button className="btn btn-ghost" style={{ padding: '6px 10px' }} onClick={() => { toast.show('已跳过，随时可在自动记账里开启'); nav.pop(); }}>跳过</button>} />
      <div className="page-scroll">
        <div className="page-pad">
          <div className="guide-progress" aria-label={`进度 ${step + 1}/${GUIDE_STEPS.length}`}>
            {GUIDE_STEPS.map((_, i) => <span key={i} className={`guide-dot ${i <= step ? 'on' : ''}`} />)}
          </div>
          <GlassCard strong>
            <span className="guide-icon"><Icon name={s.icon as IconName} size={30} /></span>
            <div className="t-title-medium" style={{ marginTop: 14 }}>{s.title}</div>
            <div className="t-body-medium t-secondary" style={{ marginTop: 10, lineHeight: 1.7 }}>{s.body}</div>
          </GlassCard>
          {step === 1 && (
            <div className="card">
              {(Object.keys(SOURCE_LABEL) as (keyof typeof SOURCE_LABEL)[]).map((k) => (
                <div className="list-row" key={k}>
                  <span className="list-row-icon"><Icon name={k === 'notification' ? 'bell' : k === 'accessibility' ? 'shield' : 'camera'} size={18} /></span>
                  <div className="list-row-main"><div className="t-body-medium">{SOURCE_LABEL[k]}</div><div className="t-label-medium t-secondary">可单独开关，随时关闭</div></div>
                </div>
              ))}
            </div>
          )}
          {step === 4 && (
            <div className="card"><EmptyState icon="check" title="不会静默入账" hint="每条识别结果都会先进入「待确认队列」，由你逐条或批量确认" /></div>
          )}
          <div className="row" style={{ marginTop: 18, gap: 10 }}>
            {step > 0 && <button className="btn btn-ghost" onClick={() => setStep((v) => v - 1)}>上一步</button>}
            {step < GUIDE_STEPS.length - 1
              ? <button className="btn btn-primary grow" onClick={() => setStep((v) => v + 1)}>下一步</button>
              : <button className="btn btn-primary grow" onClick={finish}>开启自动记账</button>}
          </div>
          <div className="t-label-medium t-secondary" style={{ marginTop: 16, lineHeight: 1.8 }}>
            Web 版不申请真实的通知读取、无障碍或屏幕录制权限；三路采集均由你主动粘贴文本触发，用于完整体验识别与确认流程。
          </div>
        </div>
      </div>
    </div>
  );
}
