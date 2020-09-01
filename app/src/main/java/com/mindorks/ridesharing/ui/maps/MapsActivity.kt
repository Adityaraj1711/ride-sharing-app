package com.mindorks.ridesharing.ui.maps

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.mindorks.ridesharing.R
import com.mindorks.ridesharing.data.network.NetworkService
import com.mindorks.ridesharing.utils.MapUtils
import com.mindorks.ridesharing.utils.PermissionUtils
import com.mindorks.ridesharing.utils.ViewUtils
import kotlinx.android.synthetic.main.activity_maps.*
import org.json.JSONObject

class MapsActivity : AppCompatActivity(), MapsView, OnMapReadyCallback {

    companion object {
        private const val TAG = "MapsActivity"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 999
        private const val PICKUP_REQUEST_CODE = 1
        private const val DROP_REQUEST_CODE = 2
    }
    private lateinit var presenter: MapsPresenter
    private lateinit var googleMap: GoogleMap
    private var fusedLocationProviderClient: FusedLocationProviderClient? = null
    private lateinit var locationCallback: LocationCallback
    private var currentLatLng: LatLng? = null
    private var pickupLatLng: LatLng? = null
    private var dropLatLng: LatLng? = null

    // this will represent all the cabs. all car basically would be a marker
    private val nearByCabMarkerList = arrayListOf<Marker>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        ViewUtils.enableTransparentStatusBar(window)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        presenter = MapsPresenter(NetworkService())
        presenter.onAttach(this)
        setUpClickListener()
    }

    private fun setUpClickListener(){
        pickUpTextView.setOnClickListener{
            launchLocationAutoCompleteActivity(PICKUP_REQUEST_CODE)
        }

        dropTextView.setOnClickListener{
            launchLocationAutoCompleteActivity(DROP_REQUEST_CODE)
        }
    }

    private fun launchLocationAutoCompleteActivity(requestCode: Int){
        val fields: List<Place.Field> = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
        // it will create the intent with autofilter in overlay and it will open the fields inside it with id, name and lat_lng
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields).build(this)
        // to detect from which intent(pickUp/drop) the callback has came, we pass request code for that
        startActivityForResult(intent, requestCode)
    }

    // some functions to show map only where the person is present :moveCamera, animateCamera, enableMyLocationMap
    private  fun moveCamera(latLng: LatLng?){
        googleMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))
    }

    private fun animateCamera(latLng: LatLng?){
        val cameraPosition = CameraPosition.Builder().target(latLng).zoom(15.5f).build()
        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }
    // to show the current location marker
    @SuppressLint("MissingPermission")
    private fun enableMyLocationMap(){
        // setPadding(left, top, right, bottom) for the location reset button
        googleMap.setPadding(0, ViewUtils.dpToPx(48f), 0, 0)
        googleMap.isMyLocationEnabled = true
    }

    private fun setCurrentLocationAsPickUp(){
        pickupLatLng = currentLatLng
        pickUpTextView.text = getString(R.string.current_location)
    }

    private fun setUpLocationListener(){
        fusedLocationProviderClient = FusedLocationProviderClient(this)
        val locationRequest = LocationRequest().setInterval(2000).setFastestInterval(2000)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        locationCallback = object : LocationCallback(){
            override fun onLocationResult(locationResult: LocationResult?) {
                super.onLocationResult(locationResult)
                if(currentLatLng == null){
                    if (locationResult != null) {
                        for(location in locationResult.locations){
                            if(currentLatLng == null){
                                currentLatLng = LatLng(location.latitude, location.longitude)
                                setCurrentLocationAsPickUp()
                                enableMyLocationMap()
                                moveCamera(currentLatLng)
                                animateCamera(currentLatLng)
                                // now here we can request near by cabs
                                presenter.requestNearByCabs(currentLatLng)
                            }
                        }
                    }
                }
                // update the location of user on the server

            }
        }
        fusedLocationProviderClient?.requestLocationUpdates(locationRequest, locationCallback,
            Looper.myLooper()
        )
    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap
    }

    override fun onStart() {
        super.onStart()
        when{
            PermissionUtils.isAccessFineLocationGranted(this) -> {
                when{
                    PermissionUtils.isLocationEnabled(this) -> {
                        // fetch the location
                        setUpLocationListener()
                    }
                    else ->{
                        PermissionUtils.showGPSNotEnabledDialog(this)
                    }
                }
            }
            else -> {
                PermissionUtils.requestAccessFineLocationPosition(this,
                    LOCATION_PERMISSION_REQUEST_CODE)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode){
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    when{
                        PermissionUtils.isLocationEnabled(this) -> {
                            // fetch the result

                        }
                        else -> {
                            PermissionUtils.showGPSNotEnabledDialog(this)
                        }
                    }
                } else {
                    Toast.makeText(this, "Location Permission not granted", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == PICKUP_REQUEST_CODE || requestCode == DROP_REQUEST_CODE){
            when(resultCode){
                Activity.RESULT_OK -> {
                    val place = Autocomplete.getPlaceFromIntent(data!!)
                    when(requestCode){
                        PICKUP_REQUEST_CODE -> {
                            pickUpTextView.text = place.name
                            pickupLatLng = place.latLng
                        }
                        DROP_REQUEST_CODE -> {
                            dropTextView.text = place.name
                            dropLatLng = place.latLng
                        }
                    }
                }
                AutocompleteActivity.RESULT_ERROR -> {
                    val status: Status = Autocomplete.getStatusFromIntent(data!!)
                    Log.d(TAG, status.statusMessage!!)
                }
                Activity.RESULT_CANCELED -> {
                    // logging
                    Log.d(TAG, "result canceled")
                }

            }
        }
    }

    override fun onDestroy() {
        presenter.onDetach()
        super.onDestroy()
    }

    // function to show bitmap car image as marker
    private fun addCarMarkerAndGet(latLng: LatLng): Marker{
        val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(MapUtils.getCarBitMap(this))
        return googleMap.addMarker(MarkerOptions().position(latLng).flat(true).icon(bitmapDescriptor))
    }

    override fun showNearByCabs(latLngList: List<LatLng>) {
        // to show cabs on the map
        nearByCabMarkerList.clear()
        for( latLng in latLngList){
            val nearByCabMarker = addCarMarkerAndGet(latLng)
            nearByCabMarkerList.add(nearByCabMarker)
        }
    }
}
