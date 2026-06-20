import { initializeApp, type FirebaseApp } from 'firebase/app'
import { getAuth, signInAnonymously, type Auth } from 'firebase/auth'
import {
  addDoc,
  collection,
  doc,
  getDocs,
  getFirestore,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  updateDoc,
  where,
  type Firestore,
} from 'firebase/firestore'
import { DEVICE_ID, readFirebaseConfig } from './config'
import type { DataSource, DeviceStatus, NoiseEvent, ReportRequest } from './types'

/**
 * Real data source backed by Firebase Firestore. Activated only when the
 * Firebase env vars are present (see hasFirebaseConfig in config.ts).
 *
 * Expected Firestore shape:
 *   devices/{deviceId}   -> { online, measuring, currentDb, leq, lmax, command }
 *   events               -> { timestamp(ms), peakDb, leq, address, tag, note, moved, wavUrl }
 *   reportRequests       -> { start, end, consent, createdAt }
 */
export class FirestoreDataSource implements DataSource {
  private app: FirebaseApp
  private db: Firestore
  private auth: Auth
  private authReady: Promise<void>

  constructor() {
    this.app = initializeApp(readFirebaseConfig())
    this.db = getFirestore(this.app)
    this.auth = getAuth(this.app)
    // Anonymous auth so security rules can require an authenticated user.
    this.authReady = signInAnonymously(this.auth)
      .then(() => undefined)
      .catch((err) => {
        // eslint-disable-next-line no-console
        console.warn('[firestore] anonymous auth failed:', err)
      })
  }

  watchDevice(cb: (s: DeviceStatus) => void): () => void {
    const ref = doc(this.db, 'devices', DEVICE_ID)
    return onSnapshot(
      ref,
      (snap) => {
        const d = snap.data() || {}
        cb({
          online: Boolean(d.online),
          measuring: Boolean(d.measuring),
          currentDb: Number(d.currentDb ?? 0),
          leq: Number(d.leq ?? 0),
          lmax: Number(d.lmax ?? 0),
        })
      },
      (err) => {
        // eslint-disable-next-line no-console
        console.error('[firestore] watchDevice error:', err)
      },
    )
  }

  async sendCommand(cmd: 'pause' | 'resume'): Promise<void> {
    await this.authReady
    const ref = doc(this.db, 'devices', DEVICE_ID)
    // The device firmware listens for `command` and acts, then clears it.
    await updateDoc(ref, { command: cmd, commandAt: serverTimestamp() })
  }

  async listEventsForDay(dayIso: string): Promise<NoiseEvent[]> {
    await this.authReady
    const dayStart = new Date(dayIso + 'T00:00:00').getTime()
    const dayEnd = dayStart + 24 * 60 * 60 * 1000
    const q = query(
      collection(this.db, 'events'),
      where('timestamp', '>=', dayStart),
      where('timestamp', '<', dayEnd),
      orderBy('timestamp', 'asc'),
    )
    const snap = await getDocs(q)
    return snap.docs.map((docSnap) => {
      const d = docSnap.data()
      return {
        id: docSnap.id,
        timestamp: Number(d.timestamp),
        peakDb: Number(d.peakDb ?? 0),
        leq: Number(d.leq ?? 0),
        address: d.address,
        tag: d.tag,
        note: d.note,
        moved: Boolean(d.moved),
        wavUrl: d.wavUrl,
      } satisfies NoiseEvent
    })
  }

  async updateEvent(
    id: string,
    patch: Partial<Pick<NoiseEvent, 'tag' | 'note'>>,
  ): Promise<void> {
    await this.authReady
    await updateDoc(doc(this.db, 'events', id), { ...patch })
  }

  async submitReportRequest(req: ReportRequest): Promise<void> {
    await this.authReady
    await addDoc(collection(this.db, 'reportRequests'), {
      ...req,
      deviceId: DEVICE_ID,
      createdAt: serverTimestamp(),
    })
  }
}
