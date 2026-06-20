package com.quietkeeper.app.ui

import com.quietkeeper.app.audio.MeasurementService

/** Tiny in-memory holder so the save screen can read the just-finished session summary. */
object SessionStore {
    var last: MeasurementService.SessionSummary = MeasurementService.SessionSummary()
}
