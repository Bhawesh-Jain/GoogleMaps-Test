package com.smtlabs.googlemapstest;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.Task;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;
import com.smtlabs.googlemapstest.databinding.ActivityMapsBinding;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MapsActivity extends AppCompatActivity implements RoutingListener {

    private final float zoom = 17;
    private GoogleMap mMap;
    private ActivityMapsBinding binding;
    private ArrayList<Polyline> polylines = null;
    private FusedLocationProviderClient client;
    private SupportMapFragment mapFragment;
    private LatLng currentLocation;
    private LocationManager locationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        client = LocationServices.getFusedLocationProviderClient(this);

        Dexter.withContext(getApplicationContext())
                .withPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                .withListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse permissionGrantedResponse) {
                        getMyLocation();
                    }

                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse permissionDeniedResponse) {
                        Toast.makeText(MapsActivity.this, "Permission Denied!", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permissionRequest, PermissionToken permissionToken) {
                        permissionToken.continuePermissionRequest();
                    }
                }).check();

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
    }

    private void getMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }

        Task<Location> task = client.getLastLocation();

        task.addOnSuccessListener(location -> mapFragment.getMapAsync(googleMap -> {
            mMap = googleMap;

            if (location != null) {
                if (gpsEnabled()) {
                    try {
                        currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                        mMap.addMarker(new MarkerOptions().position(currentLocation).title(getCompleteAddressString(location.getLatitude(), location.getLongitude())));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else {
                currentLocation = new LatLng(22.73250448383884, 75.85013067265271);
                Toast.makeText(this, "Gps Disabled. Enable to Get Live Location", Toast.LENGTH_SHORT).show();
            }
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, zoom));

            try {
                googleMap.getUiSettings().setMyLocationButtonEnabled(true);
            } catch (Exception e) {
                e.printStackTrace();
            }

            mMap.setOnMapLongClickListener(latLng -> {
                mMap.clear();
                googleMap.addMarker(new MarkerOptions().position(currentLocation));
                googleMap.addMarker(new MarkerOptions().position(latLng).title(getCompleteAddressString(latLng.latitude, latLng.longitude)));

                findRoutes(currentLocation, latLng);
            });
        }));
        task.addOnFailureListener(e -> mapFragment.getMapAsync(googleMap -> {
            mMap = googleMap;

            LatLng home = new LatLng(22.73250448383884, 75.85013067265271);
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(home, zoom));

            mMap.setOnMapLongClickListener(latLng -> {
                Geocoder geocoder = new Geocoder(this);
                String s = null;

                try {
                    s = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1).get(0).getAddressLine(0);
                } catch (IOException t) {
                    t.printStackTrace();
                }
                googleMap.addMarker(new MarkerOptions().position(latLng).title(s));
            });
        }));
    }

    private String getCompleteAddressString(double LATITUDE, double LONGITUDE) {
        String strAdd = "";
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(LATITUDE, LONGITUDE, 1);
            if (addresses != null) {
                Address returnedAddress = addresses.get(0);
                StringBuilder strReturnedAddress = new StringBuilder();

                for (int i = 0; i <= returnedAddress.getMaxAddressLineIndex(); i++) {
                    strReturnedAddress.append(returnedAddress.getAddressLine(i)).append("\n");
                }
                strAdd = strReturnedAddress.toString();
                Log.w("CurrentLocation", strReturnedAddress.toString());
            } else {
                Log.w("CurrentLocation", "No Address returned!");
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.w("CurrentLocation", "Cannot get Address!");
        }
        return strAdd;
    }

    private boolean gpsEnabled() {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.map_options, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.normal_map) {
            mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            return true;
        } else if (itemId == R.id.hybrid_map) {
            mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
            return true;
        } else if (itemId == R.id.satellite_map) {
            mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
            return true;
        } else if (itemId == R.id.terrain_map) {
            mMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void findRoutes(LatLng home, LatLng latLng) {
        Routing routing = new Routing.Builder()
                .travelMode(AbstractRouting.TravelMode.DRIVING)
                .withListener(this)
                .alternativeRoutes(true)
                .waypoints(home, latLng)
                .key("${MAPS_API_KEY}")  //also define your api key here.
                .build();
        routing.execute();
    }

    @Override
    public void onRoutingFailure(RouteException e) {
        Toast.makeText(this, "Routing Failed - " + e, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRoutingStart() {
        Toast.makeText(this, "Finding Route", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRoutingSuccess(ArrayList<Route> route, int shortestRouteIndex) {
        if (polylines != null) {
            polylines.clear();
        }
        PolylineOptions polyOptions = new PolylineOptions();
        LatLng polylineStartLatLng = null;
        LatLng polylineEndLatLng = null;


        polylines = new ArrayList<>();
        //add route(s) to the map using polyline
        for (int i = 0; i < route.size(); i++) {

            if (i == shortestRouteIndex) {
                polyOptions.color(getResources().getColor(R.color.purple_500));
                polyOptions.width(7);
                polyOptions.addAll(route.get(shortestRouteIndex).getPoints());
                Polyline polyline = mMap.addPolyline(polyOptions);
                polylineStartLatLng = polyline.getPoints().get(0);
                int k = polyline.getPoints().size();
                polylineEndLatLng = polyline.getPoints().get(k - 1);
                polylines.add(polyline);
            }
        }

        //Add Marker on route starting position
        MarkerOptions startMarker = new MarkerOptions();
        startMarker.position(polylineStartLatLng);
        startMarker.title("My Location");
        mMap.addMarker(startMarker);

        //Add Marker on route ending position
        MarkerOptions endMarker = new MarkerOptions();
        endMarker.position(polylineEndLatLng);
        endMarker.title("Destination");
        mMap.addMarker(endMarker);
    }

    @Override
    public void onRoutingCancelled() {
        Toast.makeText(this, "Cancelled!", Toast.LENGTH_SHORT).show();
    }
}