package com.pawil31.parkridekrk.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import com.pawil31.parkridekrk.data.AvailabilityRepository
import com.pawil31.parkridekrk.data.Favorites

class ParkingSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen =
        AvailabilityScreen(carContext, AvailabilityRepository(carContext), Favorites(carContext))
}
