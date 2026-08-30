package com.falahpro.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val LOCATION_TIMEOUT_MS = 15_000L

/**
 * One-shot location for callers that only need a single Pair.
 * Last-known (≤1.5s) if available; otherwise one bounded high-accuracy request (≤15s).
 *
 * For last-known now + a later current fix, call [getLastKnownLocationOrAwait] then
 * [requestFreshUserLocation] so the UI can apply the first result without waiting on GPS.
 */
@SuppressLint("MissingPermission")
suspend fun getUserLocation(context: Context): Pair<Double, Double>? {
    val last = getLastKnownLocationOrAwait(context)
    if (last != null) return last
    return requestFreshUserLocation(context)
}

/**
 * Single high-accuracy update. No continuous tracking. Same 15s cap as before.
 */
@SuppressLint("MissingPermission")
suspend fun requestFreshUserLocation(context: Context): Pair<Double, Double>? {
    val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    return withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->

            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                2000
            )
                .setMaxUpdates(1)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location: Location? = result.lastLocation
                    if (location != null) {
                        fusedClient.removeLocationUpdates(this)
                        if (continuation.isActive) {
                            continuation.resume(Pair(location.latitude, location.longitude))
                        }
                    }
                }
            }

            fusedClient.requestLocationUpdates(
                locationRequest,
                callback,
                context.mainLooper
            )

            continuation.invokeOnCancellation {
                fusedClient.removeLocationUpdates(callback)
            }
        }
    }
}
