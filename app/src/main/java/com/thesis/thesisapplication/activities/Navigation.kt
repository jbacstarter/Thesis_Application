package com.thesis.thesisapplication.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
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
import com.mapbox.navigation.ui.maps.camera.state.NavigationCameraState
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import java.util.Locale

@OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
@SuppressLint("RestrictedApi", "MissingPermission")
class Navigation : ComponentActivity() {
    private lateinit var mapView: MapView
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource
    private lateinit var navigationCamera: NavigationCamera
    private lateinit var routeLineApi: MapboxRouteLineApi
    private lateinit var routeLineView: MapboxRouteLineView
    private val navigationLocationProvider = NavigationLocationProvider()

    private lateinit var recenterButton: FloatingActionButton
    private lateinit var textToSpeech: TextToSpeech
    private var lastInstruction: String? = null

    private var routeRequested = false
    private var hasArrived = false

    private val uscTalambanPoint = Point.fromLngLat(123.9127959, 10.3526954)

    private val locationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            when {
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true -> {
                    initializeMapComponents()
                }
                else -> Toast.makeText(this, "Location permissions denied.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
        val masterLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        ViewCompat.setOnApplyWindowInsetsListener(masterLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        mapView = MapView(this, MapInitOptions(
            this,
            cameraOptions = CameraOptions.Builder().center(uscTalambanPoint).zoom(16.0).pitch(45.0).build()
        ))

        mapView.scalebar.marginTop = 200f
        mapView.logo.marginBottom = 140f
        mapView.attribution.marginBottom = 140f
        mapView.compass.apply {
            marginTop = 200f
            fadeWhenFacingNorth = false
        }

        mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            locationPuck = LocationPuck2D()
            enabled = true
        }

        masterLayout.addView(mapView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        recenterButton = FloatingActionButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_mylocation)
            backgroundTintList = ColorStateList.valueOf(Color.WHITE)
            imageTintList = ColorStateList.valueOf("#1C1C1C".toColorInt())
            hide()

            setOnClickListener {
                navigationCamera.requestNavigationCameraToFollowing()
                Toast.makeText(this@Navigation, "Camera Re-centered", Toast.LENGTH_SHORT).show()
            }
        }

        val density = resources.displayMetrics.density
        val btnParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            bottomMargin = (140 * density).toInt()
            marginEnd = (16 * density).toInt()
        }

        masterLayout.addView(recenterButton, btnParams)
        setContentView(masterLayout)

        viewportDataSource = MapboxNavigationViewportDataSource(mapView.mapboxMap)
        viewportDataSource.followingPadding = EdgeInsets(180.0 * density, 40.0 * density, 150.0 * density, 40.0 * density)

        navigationCamera = NavigationCamera(mapView.mapboxMap, mapView.camera, viewportDataSource)
        routeLineApi = MapboxRouteLineApi(MapboxRouteLineApiOptions.Builder().build())
        routeLineView = MapboxRouteLineView(MapboxRouteLineViewOptions.Builder(this).build())

        navigationCamera.registerNavigationCameraStateChangeObserver { cameraState ->
            if (cameraState == NavigationCameraState.FOLLOWING || cameraState == NavigationCameraState.TRANSITION_TO_FOLLOWING) {
                recenterButton.hide()
            } else {
                recenterButton.show()
            }
        }
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

    private val routeProgressObserver = RouteProgressObserver { routeProgress ->

        // --- Erase the blue line behind the car ---
        routeLineApi.updateWithRouteProgress(routeProgress) { result ->
            mapView.mapboxMap.style?.apply {
                routeLineView.renderRouteLineUpdate(this, result)
            }
        }

        val currentInstruction = routeProgress.currentLegProgress?.currentStepProgress?.step?.maneuver()?.instruction()
        if (currentInstruction != null && currentInstruction != lastInstruction) {
            lastInstruction = currentInstruction
            if (::textToSpeech.isInitialized) {
                textToSpeech.speak(currentInstruction, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }

        if (routeProgress.distanceRemaining < 15f && !hasArrived) {
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

            // Fetch the route immediately on real GPS ping
            if (!routeRequested) {
                routeRequested = true
                val currentUserPoint = Point.fromLngLat(enhancedLocation.longitude, enhancedLocation.latitude)
                fetchRouteToUSC(currentUserPoint)
            }
        }
    }

    private val mapboxNavigation: MapboxNavigation by requireMapboxNavigation(
        onResumedObserver = object : MapboxNavigationObserver {
            override fun onAttached(mapboxNavigation: MapboxNavigation) {
                mapboxNavigation.registerRoutesObserver(routesObserver)
                mapboxNavigation.registerLocationObserver(locationObserver)
                mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)

                mapboxNavigation.startTripSession()
            }
            override fun onDetached(mapboxNavigation: MapboxNavigation) {}
        },
        onInitialize = this::initNavigation
    )

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

    override fun onDestroy() {
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        super.onDestroy()
    }
}