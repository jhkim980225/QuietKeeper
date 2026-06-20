import type { DataSource, DeviceStatus, NoiseEvent, ReportRequest } from './types'

const ADDRESSES = [
  '서울특별시 마포구 월드컵북로 12길 7, 302호',
  '서울특별시 마포구 월드컵북로 12길 7, 402호',
  '경기도 성남시 분당구 정자일로 95, 1201호',
  '서울특별시 관악구 봉천로 45길 12, 503호',
]

const TAGS = ['발걸음', '망치질', '가구 끌기', '뛰는 소리', '문 쾅 닫음', '악기 연주']

function hashDay(dayIso: string): number {
  let h = 0
  for (let i = 0; i < dayIso.length; i++) h = (h * 31 + dayIso.charCodeAt(i)) >>> 0
  return h
}

/** Deterministic pseudo-random in [0,1) seeded by day so a day looks stable. */
function seeded(seed: number): () => number {
  let s = seed || 1
  return () => {
    s = (s * 1103515245 + 12345) & 0x7fffffff
    return s / 0x7fffffff
  }
}

/**
 * In-memory data source with believable fake data. No network, no Firebase.
 * The device "ticks" a fluctuating dB roughly every second via the watch
 * callback; commands/updates/submits mutate in-memory state so the UI reacts.
 */
export class MockDataSource implements DataSource {
  private status: DeviceStatus = {
    online: true,
    measuring: true,
    currentDb: 38,
    leq: 41.2,
    lmax: 67.8,
  }
  private watchers = new Set<(s: DeviceStatus) => void>()
  private timer: ReturnType<typeof setInterval> | null = null
  private eventsByDay = new Map<string, NoiseEvent[]>()
  private submittedRequests: ReportRequest[] = []

  private ensureTicking() {
    if (this.timer) return
    this.timer = setInterval(() => {
      if (this.status.measuring && this.status.online) {
        // Idle hum ~36-44 dB, with occasional spikes.
        const spike = Math.random() < 0.12
        const base = 36 + Math.random() * 8
        const next = spike ? base + 18 + Math.random() * 12 : base
        this.status.currentDb = Math.round(next * 10) / 10
        // Slowly drift Leq toward the running level.
        this.status.leq =
          Math.round((this.status.leq * 0.92 + next * 0.08) * 10) / 10
        this.status.lmax = Math.max(this.status.lmax, this.status.currentDb)
      }
      this.emit()
    }, 1000)
  }

  private emit() {
    const snapshot = { ...this.status }
    this.watchers.forEach((cb) => cb(snapshot))
  }

  watchDevice(cb: (s: DeviceStatus) => void): () => void {
    this.watchers.add(cb)
    this.ensureTicking()
    cb({ ...this.status }) // immediate first value
    return () => {
      this.watchers.delete(cb)
      if (this.watchers.size === 0 && this.timer) {
        clearInterval(this.timer)
        this.timer = null
      }
    }
  }

  async sendCommand(cmd: 'pause' | 'resume'): Promise<void> {
    await delay(180)
    this.status.measuring = cmd === 'resume'
    this.emit()
  }

  async listEventsForDay(dayIso: string): Promise<NoiseEvent[]> {
    await delay(220)
    let events = this.eventsByDay.get(dayIso)
    if (!events) {
      events = this.generateDay(dayIso)
      this.eventsByDay.set(dayIso, events)
    }
    // Return copies so callers can't mutate internal state directly.
    return events.map((e) => ({ ...e }))
  }

  async updateEvent(
    id: string,
    patch: Partial<Pick<NoiseEvent, 'tag' | 'note'>>,
  ): Promise<void> {
    await delay(150)
    for (const events of this.eventsByDay.values()) {
      const ev = events.find((e) => e.id === id)
      if (ev) {
        Object.assign(ev, patch)
        return
      }
    }
  }

  async submitReportRequest(req: ReportRequest): Promise<void> {
    await delay(300)
    this.submittedRequests.push(req)
    // eslint-disable-next-line no-console
    console.info('[mock] reportRequest stored:', req)
  }

  private generateDay(dayIso: string): NoiseEvent[] {
    const rand = seeded(hashDay(dayIso))
    const count = 2 + Math.floor(rand() * 5) // 2-6 events
    const dayStart = new Date(dayIso + 'T00:00:00').getTime()
    const out: NoiseEvent[] = []
    for (let i = 0; i < count; i++) {
      const hour = 7 + Math.floor(rand() * 16) // 07:00 - 22:59
      const minute = Math.floor(rand() * 60)
      const ts = dayStart + (hour * 60 + minute) * 60_000
      const peak = Math.round((52 + rand() * 28) * 10) / 10
      out.push({
        id: `${dayIso}-${i}`,
        timestamp: ts,
        peakDb: peak,
        leq: Math.round((peak - 8 - rand() * 6) * 10) / 10,
        address: ADDRESSES[Math.floor(rand() * ADDRESSES.length)],
        tag: TAGS[Math.floor(rand() * TAGS.length)],
        note: '',
        moved: rand() < 0.3,
        // Placeholder: real wav served by device local web server (not built).
        wavUrl: `local://device/${dayIso}-${i}.wav`,
      })
    }
    return out.sort((a, b) => a.timestamp - b.timestamp)
  }
}

function delay(ms: number) {
  return new Promise<void>((r) => setTimeout(r, ms))
}
