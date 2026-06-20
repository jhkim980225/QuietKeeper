export interface NoiseEvent {
  id: string
  timestamp: number
  peakDb: number
  leq: number
  address?: string
  tag?: string
  note?: string
  moved?: boolean
  wavUrl?: string
}

export interface DeviceStatus {
  online: boolean
  measuring: boolean
  currentDb: number
  leq: number
  lmax: number
}

export interface ReportRequest {
  start: string
  end: string
  consent: boolean
}

export interface DataSource {
  /** Subscribe to live device status. Returns an unsubscribe function. */
  watchDevice(cb: (s: DeviceStatus) => void): () => void
  /** Remote-control the device: pause or resume measurement. */
  sendCommand(cmd: 'pause' | 'resume'): Promise<void>
  /** List over-threshold events for a given day (ISO date "YYYY-MM-DD"). */
  listEventsForDay(dayIso: string): Promise<NoiseEvent[]>
  /** Update an event's editable fields (AI tag / user note). */
  updateEvent(
    id: string,
    patch: Partial<Pick<NoiseEvent, 'tag' | 'note'>>,
  ): Promise<void>
  /** Submit a report request (stored only — no payment / rendering). */
  submitReportRequest(req: ReportRequest): Promise<void>
}
