import { hasFirebaseConfig } from './config'
import { FirestoreDataSource } from './firestore'
import { MockDataSource } from './mock'
import type { DataSource } from './types'

/** True when running against the in-memory mock (no Firebase config present). */
export const isMockMode = !hasFirebaseConfig()

/**
 * The single app-wide data source.
 *
 * With no Firebase env vars present this is the in-memory MockDataSource, so
 * the app builds & runs anywhere with zero config. When VITE_FIREBASE_API_KEY /
 * _PROJECT_ID / _APP_ID are set, it switches to the real Firestore source.
 *
 * Note: FirestoreDataSource only touches the firebase SDK in its constructor,
 * which we only call when config is present — so mock mode never initializes
 * Firebase.
 */
export const data: DataSource = isMockMode
  ? new MockDataSource()
  : new FirestoreDataSource()

export type { DataSource, DeviceStatus, NoiseEvent, ReportRequest } from './types'
