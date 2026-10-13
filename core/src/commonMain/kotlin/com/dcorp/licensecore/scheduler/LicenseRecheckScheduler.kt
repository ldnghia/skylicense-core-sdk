package com.dcorp.licensecore.scheduler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Drives periodic re-checks. Normally waits [intervalMinutes]; while the last check left the
 * client in `NetworkRequired`, retries every minute instead so connectivity recovery is picked up
 * quickly without hammering the server at full speed.
 */
internal class LicenseRecheckScheduler(
    private val intervalMinutes: () -> Int,
    private val isNetworkRequired: () -> Boolean,
    private val onTick: suspend () -> Unit,
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            while (true) {
                val minutes = if (isNetworkRequired()) 1 else intervalMinutes().coerceIn(1, 1440)
                delay(minutes * 60_000L)
                onTick()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    val isRunning: Boolean get() = job?.isActive == true
}
