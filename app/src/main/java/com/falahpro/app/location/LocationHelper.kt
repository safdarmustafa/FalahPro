package com.falahpro.app.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val LAST_KNOWN_TIMEOUT_MS = 1_500L

/**
 * Waits briefly for last known location (so emulator mock location is used when set).
 * Never waits unbounded — if Play Services does not complete in [LAST_KNOWN_TIMEOUT_MS],
 * returns null so the caller can request a fresh fix.
 */
@SuppressLint("MissingPermission")
suspend fun getLastKnownLocationOrAwait(context: Context): Pair<Double, Double>? {

    val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    val task = fusedClient.lastLocation

    if (task.isComplete) {
        val location = task.result
        return location?.let { Pair(it.latitude, it.longitude) }
    }

    return withTimeoutOrNull(LAST_KNOWN_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            task.addOnCompleteListener {
                if (cont.isActive) {
                    val location = task.result
                    cont.resume(location?.let { Pair(it.latitude, it.longitude) })
                }
            }
        }
    }
}
