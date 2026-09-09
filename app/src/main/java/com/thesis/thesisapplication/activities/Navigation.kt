package com.thesis.thesisapplication.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.common.location.Location
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import java.util.Locale

class Navigation : ComponentActivity() {
    private lateinit var mapView: MapView
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource
    private lateinit var navigationCamera: NavigationCamera
    private lateinit var routeLineApi: MapboxRouteLineApi
    private lateinit var routeLineView: MapboxRouteLineView
    private val navigationLocationProvider = NavigationLocationProvider()

    // --- ZERO-LAG OFFLINE AI VOICE ENGINE ---
    private lateinit var textToSpeech: TextToSpeech
    private var lastInstruction: String? = null

    private var routeRequested = false
    private var hasArrived = false

    private val uscTalambanPoint = Point.fromLngLat(123.9135, 10.3526)

    private val locationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            when {
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true -> {
                    initializeMapComponents()
                }
                else -> {
                    Toast.makeText(this, "Location permissions denied.", Toast.LENGTH_LONG).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Android's Native AI Voice (Completely Offline & Instant)
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.US
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            initializeMapComponents()
        } else {
            locationPermissionRequest.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    private fun initializeMapComponents() {
        mapView = MapView(this, MapInitOptions(
            this,
            cameraOptions = CameraOptions.Builder()
                .center(uscTalambanPoint)
                .zoom(16.0)
                .pitch(45.0) // <--- GOOGLE MAPS 3D TILT PERSPECTIVE
                .build(),
        ))

        mapView.scalebar.marginTop = 200f
        mapView.logo.marginBottom = 140f
        mapView.attribution.marginBottom = 140f

        // --- MAPBOX COMPASS RE-CENTER OVERRIDE ---
        mapView.compass.apply {
            marginTop = 200f
            fadeWhenFacingNorth = false // Keep visible so user can always tap it
            addCompassClickListener {
                // When compass is clicked, instantly snap camera back to the car
                navigationCamera.requestNavigationCameraToFollowing()
                Toast.makeText(this@Navigation, "Re-centered", Toast.LENGTH_SHORT).show()
            }
        }

        mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            locationPuck = LocationPuck2D()
            enabled = true
        }

        // We can go back to directly using MapView as the ContentView!
        setContentView(mapView)

        viewportDataSource = MapboxNavigationViewportDataSource(mapView.mapboxMap)
        val pixelDensity = this.resources.displayMetrics.density
        viewportDataSource.followingPadding = EdgeInsets(
            180.0 * pixelDensity, 40.0 * pixelDensity, 150.0 * pixelDensity, 40.0 * pixelDensity
        )

        navigationCamera = NavigationCamera(mapView.mapboxMap, mapView.camera, viewportDataSource)
        routeLineApi = MapboxRouteLineApi(MapboxRouteLineApiOptions.Builder().build())
        routeLineView = MapboxRouteLineView(MapboxRouteLineViewOptions.Builder(this).build())
    }

    private val routesObserver = RoutesObserver { routeUpdateResult ->
        if (routeUpdateResult.navigationRoutes.isNotEmpty()) {
            routeLineApi.setNavigationRoutes(routeUpdateResult.navigationRoutes) { value ->
                mapView.mapboxMap.style?.apply { routeLineView.renderRouteDrawData(this, value) }
            }
            viewportDataSource.onRouteChanged(routeUpdateResult.navigationRoutes.first())
            viewportDataSource.evaluate()
            navigationCamera.requestNavigationCameraToFollowing()
        }
    }

    // --- BULLETPROOF PUBLIC API ROUTE PROGRESS OBSERVER ---
    private val routeProgressObserver = RouteProgressObserver { routeProgress ->
        // Grab the raw text instruction (e.g., "Turn right onto Main Street")
        val currentInstruction = routeProgress.currentLegProgress?.currentStepProgress?.step?.maneuver()?.instruction()

        // If it's a new instruction, pass it instantly to the Android AI Voice chip
        if (currentInstruction != null && currentInstruction != lastInstruction) {
            lastInstruction = currentInstruction
            if (::textToSpeech.isInitialized) {
                textToSpeech.speak(currentInstruction, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    }

    private val locationObserver = object : LocationObserver {
        override fun onNewRawLocation(rawLocation: Location) {}

        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val enhancedLocation = locationMatcherResult.enhancedLocation

            navigationLocationProvider.changePosition(
                location = enhancedLocation,
                keyPoints = locationMatcherResult.keyPoints,
            )

            viewportDataSource.onLocationChanged(enhancedLocation)
            viewportDataSource.evaluate()

            if (!routeRequested) {
                routeRequested = true
                val currentUserPoint = Point.fromLngLat(enhancedLocation.longitude, enhancedLocation.latitude)
                fetchRouteToUSC(currentUserPoint)
            }

            // --- DEPART TO 2D PARKING VIEW UPON ARRIVAL ---
            if (routeRequested && !hasArrived) {
                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    enhancedLocation.latitude, enhancedLocation.longitude,
                    uscTalambanPoint.latitude(), uscTalambanPoint.longitude(),
                    results
                )

                // Trigger Arrival at < 50 meters
                if (results[0] < 50f) {
                    hasArrived = true
                    Toast.makeText(this@Navigation, "Arrived at USC Campus!", Toast.LENGTH_LONG).show()

                    if (::textToSpeech.isInitialized) {
                        textToSpeech.speak("You have arrived at U.S.C. Talamban Campus. Switching to parking view.", TextToSpeech.QUEUE_FLUSH, null, null)
                    }

                    val intent = Intent(this@Navigation, ParkingMapActivity::class.java)
                    intent.putExtra("TARGET_SLOT_X", getIntent().getIntExtra("TARGET_SLOT_X", -1))
                    intent.putExtra("TARGET_SLOT_Y", getIntent().getIntExtra("TARGET_SLOT_Y", -1))
                    startActivity(intent)
                    finish()
                }
            }
        }
    }

    @OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
    private val mapboxNavigation: MapboxNavigation by requireMapboxNavigation(
        onResumedObserver = object : MapboxNavigationObserver {
            @SuppressLint("MissingPermission")
            override fun onAttached(mapboxNavigation: MapboxNavigation) {
                mapboxNavigation.registerRoutesObserver(routesObserver)
                mapboxNavigation.registerLocationObserver(locationObserver)

                // Register our custom, crash-proof voice tracker
                mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)

                mapboxNavigation.startTripSession()
            }
            override fun onDetached(mapboxNavigation: MapboxNavigation) {}
        },
        onInitialize = this::initNavigation
    )

    @OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
    private fun initNavigation() {
        MapboxNavigationApp.setup(NavigationOptions.Builder(this).build())

        mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            this.locationPuck = createDefault2DPuck(withBearing = true)
            enabled = true
        }
    }

    private fun fetchRouteToUSC(origin: Point) {
        mapboxNavigation.requestRoutes(
            RouteOptions.builder()
                .applyDefaultNavigationOptions()
                .coordinatesList(listOf(origin, uscTalambanPoint))
                .layersList(listOf(mapboxNavigation.getZLevel(), null))
                .build(),
            object : NavigationRouterCallback {
                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {}

                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                    Toast.makeText(this@Navigation, "Failed to find route.", Toast.LENGTH_SHORT).show()
                    routeRequested = false
                }

                override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: String) {
                    mapboxNavigation.setNavigationRoutes(routes)
                    Toast.makeText(this@Navigation, "Navigating to USC Talamban Campus", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Always release the AI Voice Engine when leaving the map to save phone RAM
    override fun onDestroy() {
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        super.onDestroy()
    }
}