package system.tracking;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.http.GET;
import retrofit.http.Path;
import retrofit.http.Query;

public class StatusActivity extends GcmActivity {
    static final String TAG = "StatusActivity";
    public static final String PROPERTY_OBJECT_ID = "object_id";
    public static final String ALARM = "alarm";

    interface Device {
        @GET("/api/v1/devices/current.json")
        DeviceResponse get(@Query("api_key") String api_key);
    }

    interface ObjectElement {
        @GET("/api/v1/objects/{id}/status.json")
        StatusResponse getStatus(@Path("id") Integer id, @Query("api_key") String api_key);
    }

    static class StatusResponse {
        PositionResponse position;
        int status;
    }

    static class PositionResponse {
        Integer id;
        ObjectResponse object;
        float latitude;
        float longitude;
        String date_created;
        String date_fixed;
        String date_satellite;
        float speed;
        float altitude;
        float course;
    }

    static class DeviceResponse {
        String api_key;
        String reg_id;
        List<ObjectResponse> objects;
    }

    static class ObjectResponse {
        Integer id;
        String name;
    }

    static class RestError {
        @SerializedName("code")
        public int code;
        @SerializedName("message")
        public String message;
    }

    GetObjectsTask mGetObjects;
    GetStatusTask mGetStatus;
    Context context;
    private Spinner mSpinner;

    private View mProgressView;
    private View mStatusView;

    private TextView mStatus;
    private TextView mCoordinates;
    private TextView mDate;

    ObjectResponse currentObject;
    List<ObjectResponse> objects;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_status);

        context = getApplicationContext();

        mSpinner = (Spinner) findViewById(R.id.objects);
        mSpinner.setOnItemSelectedListener(new SpinnerListener());

        mProgressView = findViewById(R.id.api_progress);
        mStatusView = findViewById(R.id.status_view);

        mStatus = (TextView) findViewById(R.id.status);
        mCoordinates = (TextView) findViewById(R.id.coordinates);
        mDate = (TextView) findViewById(R.id.date);

        Button mRefresh = (Button) findViewById(R.id.refresh);
        mRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getStatus();
            }
        });

        getObjects();
    }

    @Override
    protected void onResume(){
        super.onResume();
        handleAlerts();
    }

    public void handleAlerts(){
        if(getIntent().getAction() == ALARM){
            Log.d(TAG, "ALARM received by activity");

            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            builder.setMessage(getIntent().getStringExtra("message"))
                    .setTitle(R.string.alert_title);


            Log.d(TAG, "ALARM message: "+getIntent().getStringExtra("message"));

            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.dismiss();
                }
            });

            AlertDialog dialog = builder.create();
            dialog.show();

            // ring
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
            r.play();
        }
    }

    public class SpinnerListener implements AdapterView.OnItemSelectedListener{
        @Override
        public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
            Log.d(TAG, "spinner changed");

            String selected = parentView.getItemAtPosition(position).toString();

            for(ObjectResponse o : objects){
                if(o.name.equals(selected)){
                    currentObject = o;
                    storeObjectId(context, currentObject.id);
                }
            }

            getStatus();
        }

        @Override
        public void onNothingSelected(AdapterView<?> parentView) {}
    }

    void getObjects(){
        if (mGetObjects != null) {
            return;
        }

        mGetObjects = new GetObjectsTask(getApiKey(context));
        mGetObjects.execute((Void) null);
    }

    void getStatus(){
        if (mGetStatus != null || currentObject == null) {
            return;
        }

        showProgress(true);
        mGetStatus = new GetStatusTask(getApiKey(context), currentObject);
        mGetStatus.execute((Void) null);
    }

    public class GetStatusTask extends AsyncTask<Void, Void, StatusResponse> {
        private final String mApiKey;
        private final ObjectResponse mObject;

        GetStatusTask(String api_key, ObjectResponse object){
            mApiKey = api_key;
            mObject = object;
        }

        @Override
        protected StatusResponse doInBackground(Void... params) {
            Log.d(TAG, "create status request");

            RestAdapter restAdapter = new RestAdapter.Builder()
                    .setEndpoint(GcmActivity.getApiUrl(StatusActivity.this))
                    .setLogLevel(RestAdapter.LogLevel.FULL)
                    .build();

            ObjectElement o = restAdapter.create(ObjectElement.class);

            try {
                Log.d(TAG, "object status downloaded");
                return o.getStatus(currentObject.id, getApiKey(context));
            }
            catch(RetrofitError e){
                Log.d(TAG, e.getMessage());
            }

            return null;
        }

        @Override
        protected void onPostExecute(final StatusResponse status) {
            mGetStatus = null;
            showProgress(false);

            if(status == null){
                mStatus.setText("Api Error");
                mStatus.setTextColor(Color.RED);
                return;
            }

            switch(status.status){
                case 0:
                    mStatus.setText("Not install");
                    mStatus.setTextColor(Color.RED);
                    break;
                case 1:
                    mStatus.setText("Not installed");
                    mStatus.setTextColor(Color.RED);
                    break;
                case 2:
                    mStatus.setText("Tracking inactive (15m)");
                    mStatus.setTextColor(Color.GRAY);
                    break;
                case 3:
                    mStatus.setText("Messages delayed (5m)");
                    mStatus.setTextColor(Color.YELLOW);
                    break;
                case 4:
                    mStatus.setText("Tracking active");
                    mStatus.setTextColor(Color.GREEN);
                    break;
            }

            if(status.position != null){
                mCoordinates.setText(String.format("%4f x %4f", status.position.latitude, status.position.longitude));
                mDate.setText(status.position.date_fixed);

                mCoordinates.setTextColor(Color.GREEN);
                mDate.setTextColor(Color.GREEN);
            }else{
                mCoordinates.setText("-");
                mDate.setText("-");

                mCoordinates.setTextColor(Color.GRAY);
                mDate.setTextColor(Color.GRAY);
            }
        }

        @Override
        protected void onCancelled() {
            mGetStatus = null;
            showProgress(false);
        }
    }

    public class GetObjectsTask extends AsyncTask<Void, Void, List<String>> {
        private final String mApiKey;

        GetObjectsTask(String api_key){
            mApiKey = api_key;
        }

        @Override
        protected List<String> doInBackground(Void... params) {
            RestAdapter restAdapter = new RestAdapter.Builder()
                    .setEndpoint(GcmActivity.getApiUrl(StatusActivity.this))
                    .setLogLevel(RestAdapter.LogLevel.FULL)
                    .build();

            Device devices = restAdapter.create(Device.class);

            try {
                Log.d(TAG, "devices downloaded");
                DeviceResponse dr = devices.get(getApiKey(context));

                // set vars
                objects = dr.objects;

                int cachedObjectId = getObjectId(context);
                if(cachedObjectId != 0) {
                    for(ObjectResponse o : objects){
                        if(o.id == cachedObjectId){
                            currentObject = o;

                            Log.d(TAG, "Cached object id: "+o.name);
                        }
                    }
                }
                else{
                    currentObject = dr.objects.get(0);
                }

                // populate spinner
                List<String> spinnerArray =  new ArrayList<String>();
                for (ObjectResponse object : dr.objects) {
                    spinnerArray.add(object.name);
                }

                return spinnerArray;
            }
            catch(RetrofitError e){}

            return null;
        }

        @Override
        protected void onPostExecute(final List<String> spinnerArray) {
            mGetObjects = null;

            if(spinnerArray != null) {
                ArrayAdapter<String> adapter = new ArrayAdapter<String>(StatusActivity.this, android.R.layout.simple_spinner_item, spinnerArray);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                mSpinner.setAdapter(adapter);

                for(int i = 0; i < spinnerArray.size(); i++){
                    if(spinnerArray.get(i).equals(currentObject.name)){
                        mSpinner.setSelection(i);
                    }
                }
            }
        }

        @Override
        protected void onCancelled() {
            mGetObjects = null;
        }
    }

    /**
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    public void showProgress(final boolean show) {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress spinner.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

            mStatusView.setVisibility(show ? View.GONE : View.VISIBLE);
            mStatusView.animate().setDuration(shortAnimTime).alpha(
                    show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mStatusView.setVisibility(show ? View.GONE : View.VISIBLE);
                }
            });

            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mProgressView.animate().setDuration(shortAnimTime).alpha(
                    show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            });
        } else {
            // The ViewPropertyAnimator APIs are not available, so simply show
            // and hide the relevant UI components.
            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mStatusView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }


    protected Integer getObjectId(Context context) {
        final SharedPreferences prefs = getPreferences(context);
        return prefs.getInt(PROPERTY_OBJECT_ID, 0);
    }

    /**
     * Stores the api key in the application's
     * {@code SharedPreferences}.
     *
     * @param context application's context.
     * @param id Current object
     */
    protected void storeObjectId(Context context, Integer id) {
        final SharedPreferences prefs = getPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(PROPERTY_OBJECT_ID, id);
        editor.commit();
    }
}
