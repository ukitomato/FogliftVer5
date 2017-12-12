package com.google.watermap.p.gary.fogliftver5;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.maps.android.SphericalUtil;

public class CurrentLocationService extends Service {


    //Share
    private static final String TAG = MapsActivity.class.getSimpleName();
    private FusedLocationProviderClient mFusedLocationClient;


    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 5000;
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2;

    private SettingsClient mSettingsClient;
    private LocationRequest mLocationRequest;
    private LocationSettingsRequest mLocationSettingsRequest;
    private LocationCallback mLocationCallback;
    private Location mCurrentLocation;

    private Boolean mRequestingLocationUpdates;

    private final LatLng mDefaultLocation = new LatLng(35.652832, 139.839478);
    private final LatLng tsukuba = new LatLng(36.082736, 140.111592);
    private double distance;
    private Marker mMarker;

    private HandlerThread handlerThread;

    private NotificationManager mNotificationManager;
    private final int nID = 18734264;

    public CurrentLocationService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mRequestingLocationUpdates = false;

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mSettingsClient = LocationServices.getSettingsClient(this);

        //Thread
        handlerThread = new HandlerThread("service");
        mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        //コールバック作成
        createLocationCallback();
        //リクエスト作成
        createLocationRequest();
        //セッティングリクエストのビルド
        buildLocationSettingsRequest();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
        } else {
            startThread();
            notification();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
        mNotificationManager.cancel(nID);
    }

    /**
     * リクエスト作成
     */
    private void createLocationRequest() {
        //リクエストを作成
        mLocationRequest = new LocationRequest();
        //インターバル設定
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        //ファストインターバル設定
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        //優先度設定
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    /**
     * コールバック作成
     */
    private void createLocationCallback() {
        //コースバック生成
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                //現在地取得
                mCurrentLocation = locationResult.getLastLocation();
                Log.i("Location Callback", mCurrentLocation.getLatitude() + "," + mCurrentLocation.getLongitude() + ":" + formatNumber(calcDistance(tsukuba)));
            }
        };
    }

    /**
     * セッティングリクエストのビルド
     */
    private void buildLocationSettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        //リクエストの追加
        builder.addLocationRequest(mLocationRequest);
        //ビルド
        mLocationSettingsRequest = builder.build();
    }


    /**
     * 位置更新メソッド
     */
    private void startLocationUpdates() {
        mSettingsClient.checkLocationSettings(mLocationSettingsRequest).addOnSuccessListener(new OnSuccessListener<LocationSettingsResponse>() {
            @SuppressLint("MissingPermission")
            @Override
            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                mRequestingLocationUpdates = true;
                mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                        mLocationCallback, Looper.myLooper());
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                mRequestingLocationUpdates = false;

            }
        });
    }

    /**
     * 位置情報更新停止
     */
    private void stopLocationUpdates() {
        if (!mRequestingLocationUpdates) {
            Log.d(TAG, "stopLocationUpdates: updates never requested, no-op.");
            return;
        }
        //位置情報更新を削除
        mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        mRequestingLocationUpdates = false;
    }

    /**
     * 距離計算
     */
    private double calcDistance(LatLng pos) {
        double distance = SphericalUtil.computeDistanceBetween(pos,
                new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude()));
        this.distance = distance;
        return distance;
    }
    private String formatNumber(double distance) {
        String unit = "m";
        if (distance < 1) {
            distance *= 1000;
            unit = "mm";
        } else if (distance > 1000) {
            distance /= 1000;
            unit = "km";
        }

        return String.format("%4.3f%s", distance, unit);
    }

    /**
     * Thread実行
     */
    private void startThread() {
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                startLocationUpdates();
            }
        });
    }

    private void notification() {
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.googleg_standard_color_18)
                        .setOngoing(true)
                        .setContentTitle("Location updating now")
                        .setContentText("位置情報更新サービスを起動中です");
        //Intent作成
        Intent resultIntent = new Intent(getApplicationContext(), MapsActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MapsActivity.class);

        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );

        mBuilder.setContentIntent(resultPendingIntent);
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        //ビルド
        mNotificationManager.notify(nID, mBuilder.build());
    }

    private void dangerNotification() {
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.googleg_standard_color_18)
                        .setOngoing(true)
                        .setAutoCancel(true)
                        .setPriority(Notification.PRIORITY_HIGH)
                        .setColor(Color.argb(127, 255, 0, 0))
                        .setColorized(true)
                        .setContentTitle("危険レベル3")
                        .setContentText("この道はスリが非常に多いです．道を進む場合は貴重品に気を付け人混みは避け周囲に警戒しましょう");

        //Intent作成
        Intent resultIntent = new Intent(getApplicationContext(), MapsActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MapsActivity.class);

        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );

        mBuilder.setContentIntent(resultPendingIntent);
        mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        //ビルド
        mNotificationManager.notify(nID, mBuilder.build());
    }


}
