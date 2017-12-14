package com.google.watermap.p.gary.fogliftver5;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import android.location.Location;
import android.net.Uri;

import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.LongSparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;


import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.VisibleRegion;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.maps.android.SphericalUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static java.lang.Math.cos;
import static java.lang.Math.sin;


public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMarkerDragListener {

    private GoogleMap mMap;

    //Share
    private static final String TAG = MapsActivity.class.getSimpleName();
    private final static String KEY_LOCATION = "location";
    private Location mCurrentLocation;
    private FusedLocationProviderClient mFusedLocationClient;
    private float mCameraDefaultZoom = 15;

    //Current Position
    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 34;
    private static final int REQUEST_CHECK_SETTINGS = 0x1;

    // UI
    private final static String KEY_CAMERA_LOCATION = "camera_location";
    private final static String KEY_CAMERA_ZOOM = "camera_zoom";

    private LatLng mCameraLocation;
    private float mCameraZoom;

    // Label


    private final LatLng mDefaultLocation = new LatLng(35.652832, 139.839478);
    private final LatLng tsukuba = new LatLng(36.082736, 140.111592);
    private double distance;
    private Marker mMarker;
    private Polyline mPolyline;

    //Notification
    private static NotificationManager mNotificationManager;
    private final static int dnID = 8736418;

    //Preference
    private SharedPreferences preferences;
    private Boolean serviceAvailble;
    private MenuItem serviceSwitch;

    //Firebase
    private FragmentActivity fragmentActivity = this;

    private FirebaseDatabase mDatabase;

    private DatabaseReference mDatabaseReference;
    private List<DatabasePlace> dbPlaceList = new ArrayList<>();
    private boolean onDataChange = false;
    private List<Marker> markersList = new ArrayList<>();
    private int markersCount = 0;
    private LongSparseArray<Marker> markerHashArray = new LongSparseArray<>();

    private static final String KEY_MARKER_MAP = "maker_map";
    Intent intent;
    private double earth_dis = 6378137;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        preferences = getSharedPreferences("DATA", Context.MODE_PRIVATE);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);


        //UI指定


        //ラベル指定


        serviceAvailble = false;

        updateValuesFromSharedPreferences(preferences);
        updateValuesFromBundle(savedInstanceState);


        mDatabase = FirebaseDatabase.getInstance();
        mDatabaseReference = mDatabase.getReference("Places");


        mDatabaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Log.i(TAG, "onDataChange");
                onDataChange = true;
                for (DataSnapshot data : dataSnapshot.getChildren()) {
                    putPlaceList(data, dbPlaceList);
                }
                if (mMap != null) {
                    addMakerAll();
                    updateUI();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        intent = getIntent();

    }

    /**
     * 以前の情報の復元
     *
     * @param savedInstanceState
     */
    private void updateValuesFromBundle(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            if (savedInstanceState.keySet().contains(KEY_LOCATION)) {
                mCurrentLocation = savedInstanceState.getParcelable(KEY_LOCATION);
            }
            if (savedInstanceState.keySet().contains(KEY_CAMERA_ZOOM)) {
                mCameraZoom = savedInstanceState.getFloat(KEY_CAMERA_ZOOM);
            }
            if (savedInstanceState.keySet().contains(KEY_CAMERA_LOCATION)) {
                mCurrentLocation = savedInstanceState.getParcelable(KEY_CAMERA_LOCATION);
            }
            //UIの更新
            updateUI();
        }
    }

    private void updateValuesFromSharedPreferences(SharedPreferences data) {
        serviceAvailble = data.getBoolean("SERVICE", false);
    }


    /**
     * 現在の状態の保存
     *
     * @param savedInstanceState
     */
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putFloat(KEY_CAMERA_ZOOM, mCameraZoom);
        savedInstanceState.putParcelable(KEY_CAMERA_LOCATION, mCameraLocation);
        savedInstanceState.putParcelable(KEY_LOCATION, mCurrentLocation);

        super.onSaveInstanceState(savedInstanceState);
    }

    /**
     * UI更新
     */
    private void updateUI() {
        intent = getIntent();
        Log.i(TAG, "updateUI");
        long dangerPlaceId = intent.getLongExtra("DANGER_MARKER_ID", 0);
        boolean fromNotificationCheck = intent.getBooleanExtra("FROM_NOTIFICATION", false);
        if (mMap != null) {
            if (fromNotificationCheck) {
                Log.i("updateUI", "fromNotification");
                if (onDataChange) {
                    Log.i("updateUI", "dangerPlaceId:" + dangerPlaceId);
                    if (markerHashArray.get(dangerPlaceId) != null) {
                        Log.i("updateUI", "NOT_NULL");
                    }
                    Marker dangerMarker = markerHashArray.get(dangerPlaceId);
                    dangerMarker.showInfoWindow();
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(dangerMarker.getPosition().latitude + 0.007, dangerMarker.getPosition().longitude)));
                    mMap.moveCamera(CameraUpdateFactory.zoomTo(mCameraDefaultZoom));
                }
            } else {
                if (mCameraLocation != null) {
                    Log.i("updateUI", "mCameraLocation");
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mCameraLocation, mCameraZoom));
                } else if (mCurrentLocation != null) {
                    Log.i("updateUI", "mCurrentLocation");
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude())));
                    mMap.moveCamera(CameraUpdateFactory.zoomTo(mCameraDefaultZoom));
                } else {
                    Log.i("updateUI", "else");
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(tsukuba, mCameraDefaultZoom));
                }
            }

        }

    }


    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        if (!checkPermissions()) {
            requestPermissions();
        }
        updateUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
        if (mMap != null) {
            CameraPosition mCameraPosition = mMap.getCameraPosition();
            mCameraLocation = mCameraPosition.target;
            Log.i(TAG, mCameraLocation.latitude + ":" + mCameraLocation.longitude);
            mCameraZoom = mCameraPosition.zoom;
            Log.i(TAG, mCameraZoom + "");
        }
    }


    /**
     * Snackbar表示
     *
     * @param mainTextStringId
     * @param actionStringId
     * @param listener
     */
    private void showSnackbar(final int mainTextStringId, final int actionStringId,
                              View.OnClickListener listener) {
        Snackbar.make(
                findViewById(android.R.id.content),
                getString(mainTextStringId),
                Snackbar.LENGTH_INDEFINITE)
                .setAction(getString(actionStringId), listener).show();
    }

    /**
     * 権限確認
     *
     * @return
     */
    private boolean checkPermissions() {
        int permissionState = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION);
        return permissionState == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 権限リクエスト
     */
    private void requestPermissions() {
        boolean shouldProvideRationale =
                ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.ACCESS_FINE_LOCATION);

        if (shouldProvideRationale) {
            Log.i(TAG, "Displaying permission rationale to provide additional context.");
            showSnackbar(R.string.permission_rationale,
                    android.R.string.ok, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            ActivityCompat.requestPermissions(MapsActivity.this,
                                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                    REQUEST_PERMISSIONS_REQUEST_CODE);
                        }
                    });
        } else {
            Log.i(TAG, "Requesting permission");
            ActivityCompat.requestPermissions(MapsActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }

    /**
     * 権限リクエスト後のコールバック
     *
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.i(TAG, "onRequestPermissionResult");
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length <= 0) {
                Log.i(TAG, "User interaction was cancelled.");
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                Log.i(TAG, "Permission granted, updates requested, starting location updates");
                if (checkPermissions()) {
                    mMap.setMyLocationEnabled(true);
                    mMap.getUiSettings().setMyLocationButtonEnabled(true);
                    mMap.getUiSettings().setZoomControlsEnabled(true);
                    mMap.getUiSettings().setCompassEnabled(true);
                }
            } else {
                showSnackbar(R.string.permission_denied_explanation,
                        R.string.settings, new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                Intent intent = new Intent();
                                intent.setAction(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package",
                                        BuildConfig.APPLICATION_ID, null);
                                intent.setData(uri);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                            }
                        });
            }
        }
    }


    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(GoogleMap googleMap) {
        Log.i(TAG, "onMapReady");
        mMap = googleMap;

        getMaker();

        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setIndoorLevelPickerEnabled(true);

        mMap.setInfoWindowAdapter(new CustomWindowViewer(fragmentActivity));
        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                Log.i(TAG, "onMarkerClick");
                CameraPosition cameraPosition = mMap.getCameraPosition();
                VisibleRegion screenRegion = mMap.getProjection().getVisibleRegion();
                LatLng topRight = screenRegion.latLngBounds.northeast;
                LatLng bottomLeft = screenRegion.latLngBounds.southwest;
                double screenDistance = SphericalUtil.computeDistanceBetween(topRight, bottomLeft) * sin(40) * 25;
                double theta = cameraPosition.tilt;
                double distance = screenDistance / earth_dis;
                double moveLat = distance * cos(theta);
                double moveLng = distance * sin(theta);
                Log.i(TAG, moveLat + ":"+moveLng);

                marker.showInfoWindow();
                mMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(marker.getPosition().latitude + moveLat, marker.getPosition().longitude + moveLng)));
                return true;
            }
        });

        getDeviceLocation();

        if (checkPermissions()) {
            mMap.setMyLocationEnabled(true);
        }
    }

    private void getMaker() {
        mMap.setOnMarkerDragListener(this);

        mMarker = mMap.addMarker(new MarkerOptions().position(tsukuba).draggable(true));
    }

    @Override
    public void onMarkerDragStart(Marker marker) {

    }

    @Override
    public void onMarkerDrag(Marker marker) {
    }

    @Override
    public void onMarkerDragEnd(Marker marker) {
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        serviceSwitch = menu.findItem(R.id.location_switch_appbar);
        serviceSwitch.setChecked(serviceAvailble);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.settings) {
            startActivity(new Intent(getApplication(), SettingsActivity.class));
            return true;
        }
        if (id == R.id.location_switch_appbar) {
            if (item.isChecked()) {
                item.setChecked(false);
                serviceAvailble = false;
                stopService(new Intent(getBaseContext(), CurrentLocationService.class));
            } else {
                item.setChecked(true);
                serviceAvailble = true;
                startService(new Intent(getBaseContext(), CurrentLocationService.class));
            }
            preferences.edit().putBoolean("SERVICE", serviceAvailble).apply();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressLint("MissingPermission")
    private void getDeviceLocation() {
        if (checkPermissions()) {
            mFusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    mCurrentLocation = location;
                    updateUI();
                }
            });
        }
    }

    public void addMakerAll() {
        Log.i(TAG, "addMarkerAll");
        for (DatabasePlace dbPlace : dbPlaceList) {
            markerHashArray.put(dbPlace.getId(), mMap.addMarker(new MarkerOptions().position(dbPlace.getLocation()).title(dbPlace.getName())
                    .icon(BitmapDescriptorFactory.defaultMarker(dbPlace.getMakerColor()))));
            markerHashArray.get(dbPlace.getId()).setTag(dbPlace);
        }
    }

    private void putPlaceList(DataSnapshot dataSnapshot, List<DatabasePlace> dbPlaceList) {
        Log.i(TAG, "putPlaceList");
        String key = dataSnapshot.getKey();
        Log.i("putPlaceList", key);
        Object kind = dataSnapshot.child("Kind").getValue();
        Object level = dataSnapshot.child("Level").getValue();
        Object latitude = dataSnapshot.child("Location").child("Latitude").getValue();
        Object longitude = dataSnapshot.child("Location").child("Longitude").getValue();
        Object uri = dataSnapshot.child("ImageURI").getValue();
        Object id = dataSnapshot.child("ID").getValue();
        Log.i("Value", kind + ":" + level + ":" + latitude + ":" + longitude + ":" + id);
        if (latitude != null && longitude != null) {
            DatabasePlace dbPlace = new DatabasePlace(key, (String) kind, (long) level, (Double) latitude, (Double) longitude, (long) id);
            dbPlaceList.add(dbPlace);
        }
    }

    private void putMarkerHashMap(String name, Marker marker) {

    }


}

