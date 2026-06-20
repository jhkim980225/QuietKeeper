import { useEffect, useState } from 'react'
import { AudioPlayerStub } from '../components/AudioPlayerStub'
import { GlassCard } from '../components/GlassCard'
import { data } from '../data'
import type { NoiseEvent } from '../data/types'

function todayIso() {
  return new Date().toISOString().slice(0, 10)
}

function fmtTime(ts: number) {
  return new Date(ts).toLocaleTimeString('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function Calendar() {
  const [day, setDay] = useState(todayIso())
  const [events, setEvents] = useState<NoiseEvent[]>([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    let alive = true
    setLoading(true)
    data
      .listEventsForDay(day)
      .then((evts) => {
        if (alive) setEvents(evts)
      })
      .finally(() => {
        if (alive) setLoading(false)
      })
    return () => {
      alive = false
    }
  }, [day])

  function patchLocal(id: string, patch: Partial<NoiseEvent>) {
    setEvents((prev) => prev.map((e) => (e.id === id ? { ...e, ...patch } : e)))
  }

  return (
    <div className="grid">
      <h1 className="view-title">소음 캘린더 / 노트</h1>

      <GlassCard>
        <div className="field" style={{ maxWidth: 220, marginBottom: 0 }}>
          <label htmlFor="day">날짜 선택</label>
          <input
            id="day"
            type="date"
            value={day}
            max={todayIso()}
            onChange={(e) => setDay(e.target.value)}
          />
        </div>
      </GlassCard>

      {loading && <p className="muted">불러오는 중…</p>}

      {!loading && events.length === 0 && (
        <GlassCard>
          <p className="muted" style={{ margin: 0 }}>
            이 날짜에는 기준 초과 소음 이벤트가 없습니다.
          </p>
        </GlassCard>
      )}

      <div className="event-list">
        {events.map((ev) => (
          <EventCard key={ev.id} event={ev} onPatched={patchLocal} />
        ))}
      </div>
    </div>
  )
}

function EventCard({
  event,
  onPatched,
}: {
  event: NoiseEvent
  onPatched: (id: string, patch: Partial<NoiseEvent>) => void
}) {
  const [tag, setTag] = useState(event.tag ?? '')
  const [note, setNote] = useState(event.note ?? '')
  const [saving, setSaving] = useState(false)
  const [savedAt, setSavedAt] = useState(false)

  const dirty = tag !== (event.tag ?? '') || note !== (event.note ?? '')

  async function save() {
    setSaving(true)
    setSavedAt(false)
    try {
      await data.updateEvent(event.id, { tag, note })
      onPatched(event.id, { tag, note })
      setSavedAt(true)
    } finally {
      setSaving(false)
    }
  }

  return (
    <GlassCard>
      <div className="event-row">
        <div>
          <div className="time">{fmtTime(event.timestamp)}</div>
          <div className="peak">{event.peakDb.toFixed(1)} dB(A)</div>
          <div className="peak muted">Leq {event.leq.toFixed(1)}</div>
        </div>
        <div>
          <div className="addr">{event.address ?? '주소 정보 없음'}</div>

          <div className="field">
            <label>AI 태그</label>
            <input
              type="text"
              value={tag}
              placeholder="예: 발걸음, 망치질"
              onChange={(e) => setTag(e.target.value)}
            />
          </div>

          <div className="field">
            <label>메모</label>
            <textarea
              value={note}
              placeholder="상황 메모를 남겨보세요"
              onChange={(e) => setNote(e.target.value)}
            />
          </div>

          <AudioPlayerStub url={event.wavUrl} />

          <div
            className="row"
            style={{ marginTop: 12, alignItems: 'center', gap: 12 }}
          >
            <button className="btn" disabled={!dirty || saving} onClick={save}>
              {saving ? '저장 중…' : '저장'}
            </button>
            {savedAt && !dirty && <span className="saved-flag">저장됨 ✓</span>}
          </div>
        </div>
      </div>
    </GlassCard>
  )
}
