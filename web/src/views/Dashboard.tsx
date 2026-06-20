import { useEffect, useState } from 'react'
import { GlassCard } from '../components/GlassCard'
import { data } from '../data'
import type { DeviceStatus } from '../data/types'

const DB_MIN = 30
const DB_MAX = 90

function pct(db: number) {
  const clamped = Math.max(DB_MIN, Math.min(DB_MAX, db))
  return ((clamped - DB_MIN) / (DB_MAX - DB_MIN)) * 100
}

export function Dashboard() {
  const [status, setStatus] = useState<DeviceStatus | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    const unsub = data.watchDevice(setStatus)
    return unsub
  }, [])

  async function control(cmd: 'pause' | 'resume') {
    setBusy(true)
    try {
      await data.sendCommand(cmd)
    } finally {
      setBusy(false)
    }
  }

  const online = status?.online ?? false
  const measuring = status?.measuring ?? false

  return (
    <div className="grid">
      <h1 className="view-title">실시간 상태 / 제어</h1>

      <GlassCard>
        <div
          style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}
        >
          <strong>기기 상태</strong>
          <span className={`status-pill ${online ? 'on' : 'off'}`}>
            <span className="led" />
            {online ? '온라인' : '오프라인'}
            {online && (measuring ? ' · 측정 중' : ' · 일시정지')}
          </span>
        </div>

        <div className="db-readout">
          <div>
            <span className="value">
              {status ? status.currentDb.toFixed(1) : '--'}
            </span>
            <span className="unit">dB(A)</span>
          </div>
          <div className="db-bar">
            <span style={{ width: `${status ? pct(status.currentDb) : 0}%` }} />
          </div>
          <div className="muted" style={{ fontSize: 12, marginTop: 10 }}>
            {measuring ? '약 1초마다 갱신됩니다' : '측정이 일시정지되었습니다'}
          </div>
        </div>

        <div className="row" style={{ marginTop: 4 }}>
          <div className="stat glass">
            <div className="label">Leq (등가소음도)</div>
            <div className="num">{status ? status.leq.toFixed(1) : '--'}</div>
          </div>
          <div className="stat glass">
            <div className="label">Lmax (최고소음도)</div>
            <div className="num">{status ? status.lmax.toFixed(1) : '--'}</div>
          </div>
        </div>
      </GlassCard>

      <GlassCard>
        <strong>원격 제어</strong>
        <p className="muted" style={{ fontSize: 13, margin: '6px 0 14px' }}>
          기기에 측정 일시정지 / 재개 명령을 전송합니다.
        </p>
        <div className="row">
          <button
            className="btn danger"
            disabled={busy || !online || !measuring}
            onClick={() => control('pause')}
          >
            측정 일시정지
          </button>
          <button
            className="btn accent"
            disabled={busy || !online || measuring}
            onClick={() => control('resume')}
          >
            측정 재개
          </button>
        </div>
      </GlassCard>
    </div>
  )
}
