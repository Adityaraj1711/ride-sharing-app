package com.mindorks.ridesharing.ui.maps

import com.google.android.gms.maps.model.LatLng

// its a presenter of the MVP model
// to show the nearby cabs
interface MapsView {
    fun showNearByCabs(latLngList: List<LatLng>)

}