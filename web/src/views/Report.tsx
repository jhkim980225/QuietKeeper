import { useState } from 'react'
import { GlassCard } from '../components/GlassCard'
import { Toast } from '../components/Toast'
import { data } from '../data'

function todayIso() {
  return new Date().toISOString().slice(0, 10)
}

export function Report() {
  const [start, setStart] = useState('')
  const [end, setEnd] = useState('')
  const [consent, setConsent] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [toast, setToast] = useState<string | null>(null)

  const rangeValid = start !== '' && end !== '' && start <= end
  const canSubmit = rangeValid && consent && !submitting

  async function submit() {
    if (!canSubmit) return
    setSubmitting(true)
    try {
      await data.submitReportRequest({ start, end, consent })
      setToast('레포트 신청이 접수되었습니다.')
      setStart('')
      setEnd('')
      setConsent(false)
    } catch {
      setToast('신청 중 오류가 발생했습니다. 다시 시도해주세요.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="grid">
      <h1 className="view-title">레포트 신청</h1>

      <GlassCard>
        <p className="muted" style={{ marginTop: 0, fontSize: 13 }}>
          기간을 선택하고 음원 제공에 동의하시면 레포트 신청이 접수됩니다.
          (결제 및 레포트 생성은 추후 단계에서 진행됩니다.)
        </p>

        <div className="row" style={{ marginBottom: 8 }}>
          <div className="field" style={{ flex: '1 1 160px' }}>
            <label htmlFor="start">시작일</label>
            <input
              id="start"
              type="date"
              value={start}
              max={end || todayIso()}
              onChange={(e) => setStart(e.target.value)}
            />
          </div>
          <div className="field" style={{ flex: '1 1 160px' }}>
            <label htmlFor="end">종료일</label>
            <input
              id="end"
              type="date"
              value={end}
              min={start || undefined}
              max={todayIso()}
              onChange={(e) => setEnd(e.target.value)}
            />
          </div>
        </div>

        {start && end && !rangeValid && (
          <p style={{ color: '#ef6464', fontSize: 13, margin: '0 0 10px' }}>
            종료일은 시작일과 같거나 이후여야 합니다.
          </p>
        )}

        <label className="checkbox" style={{ margin: '8px 0 18px' }}>
          <input
            type="checkbox"
            checked={consent}
            onChange={(e) => setConsent(e.target.checked)}
          />
          음원 제공에 동의합니다.
        </label>

        <button className="btn" disabled={!canSubmit} onClick={submit}>
          {submitting ? '신청 중…' : '레포트 신청'}
        </button>
      </GlassCard>

      {toast && <Toast message={toast} onDone={() => setToast(null)} />}
    </div>
  )
}
