package system.tracking;

import android.app.IntentService;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.http.Body;
import retrofit.http.PUT;
import retrofit.http.Path;
import retrofit.http.Query;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class LocationCheckerService extends Service {
    static final String TAG = "LocationChecker";

    private static final String ACTION_ID = "system.tracking.extra.action_id";
    private static final String LATITUDE = "system.tracking.extra.lat";
    private static final String LONGITUDE = "system.tracking.extra.lng";

    private LocationManager mLocationManager = null;

    private static final int LOCATION_INTERVAL = 1000;

    private static final float LOCATION_DISTANCE = 10f;

    private double lat;

    private double lng;

    private int message_id;

    private MessageReplyTask mReply;

    interface Api {
        @PUT("/api/v1/messages/{id}.json")
        MessageResponse putMessage(@Path("id") Integer id, @Body MessageRequest message, @Query("api_key") String api_key);
    }

    static class MessageResponse {
        Integer id;
        String action;
    }

    static class MessageRequest {
        String response;
    }

    public static void checkLocation(Context context, int action_id, float latitude, float longitude) {
        Intent intent = new Intent(context, LocationCheckerService.class);

        intent.putExtra(ACTION_ID, action_id);
        intent.putExtra(LATITUDE, latitude);
        intent.putExtra(LONGITUDE, longitude);

        context.startService(intent);
    }

    private void checkLocation(Location location){
        Log.d(TAG, String.format("device: geo fix %f %f",  location.getLongitude(), location.getLatitude()));

        float[] result = new float[1];
        Location.distanceBetween(location.getLatitude(), location.getLongitude(), lat, lng, result);

        Log.d(TAG, String.format("distance %f", result[0]));

        if(mReply == null) {
            final SharedPreferences prefs = getSharedPreferences(LoginActivity.class.getSimpleName(), Context.MODE_PRIVATE);
            String apiKey = prefs.getString(GcmActivity.PROPERTY_API_KEY, "");

            mReply = new MessageReplyTask(apiKey, message_id, (result[0] < 20 ? "1" : "0"));
            mReply.execute((Void) null);
        }
    }

    public class MessageReplyTask extends AsyncTask<Void, Void, Boolean> {
        private final String mRequest;
        private final String mApiKey;
        private final int mMessageId;

        MessageReplyTask(String api_key, int message_id, String request){
            mApiKey    = api_key;
            mRequest   = request;
            mMessageId = message_id;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            RestAdapter restAdapter = new RestAdapter.Builder()
                    .setEndpoint(LoginActivity.API_URL)
                    .setLogLevel(RestAdapter.LogLevel.FULL)
                    .build();

            Api api = restAdapter.create(Api.class);

            try {
                MessageRequest mr = new MessageRequest();
                mr.response = mRequest;

                api.putMessage(mMessageId, mr, mApiKey);
            }
            catch(RetrofitError e){
                Log.d(TAG, e.getMessage().toString());
            }

            return true;
        }

        @Override
        protected void onPostExecute(final Boolean status) {
            mReply = null;
            stopSelf();
        }

        @Override
        protected void onCancelled() {
            mReply = null;
            stopSelf();
        }
    }

    LocationListener[] mLocationListeners = new LocationListener[] {
            new LocationListener(LocationManager.GPS_PROVIDER),
            new LocationListener(LocationManager.NETWORK_PROVIDER)
    };

    @Override
    public IBinder onBind(Intent arg0)
    {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.e(TAG, "onStartCommand");

        super.onStartCommand(intent, flags, startId);

        message_id = intent.getIntExtra(ACTION_ID, 0);
        lat = intent.getFloatExtra(LATITUDE, 0);
        lng = intent.getFloatExtra(LONGITUDE, 0);

        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.e(TAG, "onCreate");

        initializeLocationManager();

        try {
            mLocationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                    mLocationListeners[1]);
        } catch (java.lang.SecurityException ex) {
            Log.i(TAG, "fail to request location update, ignore", ex);
        } catch (IllegalArgumentException ex) {
            Log.d(TAG, "network provider does not exist, " + ex.getMessage());
        }
        try {
            mLocationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                    mLocationListeners[0]);
        } catch (java.lang.SecurityException ex) {
            Log.i(TAG, "fail to request location update, ignore", ex);
        } catch (IllegalArgumentException ex) {
            Log.d(TAG, "gps provider does not exist " + ex.getMessage());
        }
    }
    @Override
    public void onDestroy()
    {
        Log.e(TAG, "onDestroy");

        super.onDestroy();
        if (mLocationManager != null) {
            for (int i = 0; i < mLocationListeners.length; i++) {
                try {
                    mLocationManager.removeUpdates(mLocationListeners[i]);
                } catch (Exception ex) {
                    Log.i(TAG, "fail to remove location listners, ignore", ex);
                }
            }
        }
    }
    private void initializeLocationManager() {
        Log.e(TAG, "initializeLocationManager");
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        }
    }

    private class LocationListener implements android.location.LocationListener{
        Location mLastLocation;

        public LocationListener(String provider)
        {
            Log.e(TAG, "LocationListener " + provider);

            mLastLocation = new Location(provider);
        }
        @Override
        public void onLocationChanged(Location location)
        {
            Log.e(TAG, "onLocationChanged: " + location);

            mLastLocation.set(location);

            checkLocation(location);
        }
        @Override
        public void onProviderDisabled(String provider)
        {
            Log.e(TAG, "onProviderDisabled: " + provider);
        }
        @Override
        public void onProviderEnabled(String provider)
        {
            Log.e(TAG, "onProviderEnabled: " + provider);
        }
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras)
        {
            Log.e(TAG, "onStatusChanged: " + provider);
        }
    }
}
