package com.motonav.app.ride

import android.location.Location
import com.stadiamaps.ferrostar.core.location.NavigationLocationProviding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

// Bridges the existing FusedLocationProviderClient feed (RideSessionService, stage 3) into
// Ferrostar's location interface, instead of Ferrostar's own AndroidLocationProvider — that would
// open a second, competing LocationManager subscription. See
// docs/MotoNav_REBUILD_PLAN_OSM.md Phase A item 3.
class RideLocationProvider : NavigationLocationProviding {
    private val locationFlow = MutableStateFlow<Location?>(null)

    fun update(location: Location) {
        locationFlow.value = location
    }

    override suspend fun lastLocation(): Location = locationFlow.filterNotNull().first()

    override fun locationUpdates(intervalMillis: Long): Flow<Location> = locationFlow.filterNotNull()
}
