package com.pomli.www.notituser.ui.main

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.firebase.firestore.GeoPoint
import com.google.gson.Gson
import com.pomli.www.notituser.NearbyUtility.GeoUtils
import com.pomli.www.notituser.PaginationScrollListener
import com.pomli.www.notituser.R
import com.pomli.www.notituser.Utility
import com.pomli.www.notituser.custom_recycler.ClickListener
import com.pomli.www.notituser.custom_recycler.RecyclerTouchListener
import com.pomli.www.notituser.recycler_adapter.ShopAdapter
import kotlinx.android.synthetic.main.main_fragment.view.*


class MainFragment : Fragment() {

    private lateinit var mContext: Context
    private lateinit var viewModel: MainViewModel
    private lateinit var v: View

    private var MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 123
    private val REQUEST_CHECK_SETTINGS: Int = 0x1

    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var mLocationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        v = inflater.inflate(R.layout.main_fragment, container, false)
        return v
    }


    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)


        //Read saved location from shred preference
        readLocFromPref()

        viewModel = ViewModelProviders.of(activity!!).get(MainViewModel::class.java)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(activity!!)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult ?: return
                for (location in locationResult.locations) {
                    goOn(location)
                }
            }
        }

        triggerLocationService()

        viewModel.mLocation.observe(activity!!, Observer {
            if (it!=null){
                if (!viewModel.isRecyclerLoading){
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun createLocationRequest() {
        mLocationRequest = LocationRequest.create();
        mLocationRequest.priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
        mLocationRequest.interval = 10000

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(mLocationRequest)
        val client = LocationServices.getSettingsClient(activity!!)
        val task = client.checkLocationSettings(builder.build())
        task.addOnSuccessListener { locationSettingsResponse ->
            // All location settings are satisfied. The client can initialize
            // location requests here.
            // ...
            mFusedLocationClient.lastLocation?.addOnSuccessListener {
                // Got last known location. In some rare situations this can be null.
                if (it != null) {
                    goOn(it)
                }
            }
        }
        task.addOnFailureListener { e ->
            if (e is ResolvableApiException) {
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    this.startIntentSenderForResult(
                        e.resolution.intentSender,
                        REQUEST_CHECK_SETTINGS,
                        null,
                        0,
                        0,
                        0,
                        null
                    )


                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                }

            }
        }
    }

    private fun goOn(location: Location?) {
        val qq = viewModel.mLocation.value
        if (location != null) {
            if (qq != null) {
                if (qq.distanceTo(location) > 1000) {
                    viewModel.mLocation.value = location
                }
            } else viewModel.mLocation.value = location

        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            // Check for the integer request code originally supplied to startResolutionForResult().
            REQUEST_CHECK_SETTINGS -> when (resultCode) {
                Activity.RESULT_OK -> {
                    createLocationRequest()
                }
                Activity.RESULT_CANCELED -> Toast.makeText(
                    mContext,
                    "Change location settings",
                    Toast.LENGTH_SHORT
                ).show()
            }// Nothing to do. startLocationupdates() gets called in onResume again.
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    createLocationRequest()
                } else {
                    showPermissionBtn()
                }
                return
            }

            // Add other 'when' lines to check for other
            // permissions this app might request.
            else -> {
                // Ignore all other requests.
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context
    }

    override fun onResume() {
        super.onResume()
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(
                mContext,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
            == PackageManager.PERMISSION_GRANTED
        ) {
            mFusedLocationClient.requestLocationUpdates(
                mLocationRequest,
                locationCallback,
                null /* Looper */
            )
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    private fun stopLocationUpdates() {
        mFusedLocationClient.removeLocationUpdates(locationCallback)
    }
    private fun triggerLocationService() {
        if (ContextCompat.checkSelfPermission(
                mContext,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            if (shouldShowRequestPermissionRationale(
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                )
            ) {
                showPermissionBtn()
            } else {
                requestPermissions(
                    arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                    MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION
                )
            }
        } else {
            createLocationRequest()
        }
    }

    private fun readLocFromPref() {
        val jsonStrng = Utility.readSharedSetting(applicationContext, PREF_SETTING_NAME, "")
        if (jsonStrng != "") {
            val json = Gson()
            viewModel.mLocation.value = json.fromJson(jsonStrng, Location::class.java)
        }
    }
}

