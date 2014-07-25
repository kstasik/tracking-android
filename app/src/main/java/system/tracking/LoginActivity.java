package system.tracking;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.gson.annotations.SerializedName;

import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.http.Body;
import retrofit.http.GET;
import retrofit.http.POST;
import retrofit.http.Query;

/**
 * A login screen that offers login via email/password.

 */
public class LoginActivity extends GcmActivity{
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    static final String TAG = "LoginActivity";
    /**
     * Keep track of the login task to ensure we can cancel it if requested.
     */
    private UserLoginTask mAuthTask = null;
    private ApiKeyTask mCheckTask = null;

    // UI references.
    private AutoCompleteTextView mUsernameView;
    private EditText mPasswordView;
    private View mProgressView;
    private View mLoginFormView;

    interface RegisterDevice {
        @POST("/api/v1/devices.json")
        DeviceResponse create(@Body DeviceRequest device); // , Callback<DeviceResponse> cb

        @GET("/api/v1/devices/current.json")
        DeviceResponse get(@Query("api_key") String api_key);
    }

    static class DeviceRequest {
        String name;
        String username;
        String password;
        String system;
        String reg_id;
    }

    static class DeviceResponse {
        String api_key;
        String reg_id;
    }

    static class RestError {
        @SerializedName("code")
        public int code;
        @SerializedName("message")
        public String message;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        context = getApplicationContext();

        if (checkPlayServices()) {
            gcmCreate();

            // Set up the login form.
            mUsernameView = (AutoCompleteTextView) findViewById(R.id.username);

            mPasswordView = (EditText) findViewById(R.id.password);
            mPasswordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                    if (id == R.id.login || id == EditorInfo.IME_NULL) {
                        attemptLogin();
                        return true;
                    }
                    return false;
                }
            });

            Button mEmailSignInButton = (Button) findViewById(R.id.email_sign_in_button);
            mEmailSignInButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    attemptLogin();
                }
            });

            mLoginFormView = findViewById(R.id.login_form);
            mProgressView = findViewById(R.id.login_progress);

            // test api key
            if(!getApiKey(context).isEmpty()) {
                Log.d(TAG, "Api Key exists");

                checkApiKey();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPlayServices();
    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Log.i("PLAY SERVICES", "This device is not supported.");
                finish();
            }
            return false;
        }

        return true;
    }

    public void checkApiKey(){
        if (mCheckTask != null) {
            return;
        }

        showProgress(true);
        mCheckTask = new ApiKeyTask(getApiKey(context));
        mCheckTask.execute((Void) null);

    }

    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    public void attemptLogin() {
        if (mAuthTask != null) {
            return;
        }

        // Reset errors.
        mUsernameView.setError(null);
        mPasswordView.setError(null);

        // Store values at the time of the login attempt.
        String email = mUsernameView.getText().toString();
        String password = mPasswordView.getText().toString();

        boolean cancel = false;
        View focusView = null;

        // Check for a valid password, if the user entered one.
        if (!TextUtils.isEmpty(password) && !isPasswordValid(password)) {
            mPasswordView.setError(getString(R.string.error_invalid_password));
            focusView = mPasswordView;
            cancel = true;
        }

        // Check for a valid email address.
        if (TextUtils.isEmpty(email)) {
            mUsernameView.setError(getString(R.string.error_field_required));
            focusView = mUsernameView;
            cancel = true;
        } else if (!isUsernameValid(email)) {
            mUsernameView.setError(getString(R.string.error_invalid_username));
            focusView = mUsernameView;
            cancel = true;
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        } else {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            showProgress(true);
            mAuthTask = new UserLoginTask(email, password);
            mAuthTask.execute((Void) null);
        }
    }
    private boolean isUsernameValid(String email) {
        return email.length() > 4;
    }

    private boolean isPasswordValid(String password) {
        return password.length() > 4;
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

            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
            mLoginFormView.animate().setDuration(shortAnimTime).alpha(
                    show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
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
            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    public class ApiKeyTask extends AsyncTask<Void, Void, Boolean> {
        private final String mApiKey;

        ApiKeyTask(String api_key){
            mApiKey = api_key;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            RestAdapter restAdapter = new RestAdapter.Builder()
                    .setEndpoint(GcmActivity.getApiUrl(LoginActivity.this))
                    .setLogLevel(RestAdapter.LogLevel.FULL)
                    .build();

            RegisterDevice registration = restAdapter.create(RegisterDevice.class);

            try {
                registration.get(getApiKey(context));
                return true;
            }
            catch(RetrofitError e){}

            return false;
        }

        @Override
        protected void onPostExecute(final Boolean status) {
            mCheckTask = null;

            showProgress(false);

            if (status) {
                Log.d(TAG, "API KEY found");

                Toast.makeText(LoginActivity.this, "Api Key Valid", Toast.LENGTH_SHORT).show();

                goToStatus();
            }
            else{
                Log.d(TAG, "API KEY Incorrect");

                Toast.makeText(LoginActivity.this, "Api Key Invalid", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        protected void onCancelled() {
            mCheckTask = null;
            showProgress(false);
        }
    }

    /**
     * Represents an asynchronous login/registration task used to authenticate
     * the user.
     */
    public class UserLoginTask extends AsyncTask<Void, Void, String> {
        private final String mUsername;
        private final String mPassword;

        UserLoginTask(String username, String password) {
            mUsername = username;
            mPassword = password;
        }

        @Override
        protected String doInBackground(Void... params) {
            Log.d("MyApp", "attempt login");

            RestAdapter restAdapter = new RestAdapter.Builder()
                    .setEndpoint(GcmActivity.getApiUrl(LoginActivity.this))
                    .setLogLevel(RestAdapter.LogLevel.FULL)
                    .build();

            RegisterDevice registration = restAdapter.create(RegisterDevice.class);

            DeviceRequest rq = new DeviceRequest();

            rq.username = mUsername;
            rq.password = mPassword;
            rq.reg_id   = regid;
            rq.system   = "android";

            // get device info
            String manufacturer = Build.MANUFACTURER;
            String model = Build.MODEL;
            if (model.startsWith(manufacturer)) {
                rq.name = model;
            }else{
                rq.name = manufacturer + " " + model;
            }

            try {
                DeviceResponse device = registration.create(rq);

                storeApiKey(context, device.api_key);
            }
            catch(RetrofitError error){
                if (error.getResponse() != null) {
                    RestError body = (RestError) error.getBodyAs(RestError.class);

                    return body.message;
                }
                else{
                    return "unknown error";
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(final String error) {
            mAuthTask = null;
            showProgress(false);

            if (error == null) {
                goToStatus();
            } else {
                mPasswordView.setError(error);
                mPasswordView.requestFocus();
            }
        }

        @Override
        protected void onCancelled() {
            mAuthTask = null;
            showProgress(false);
        }
    }

    private void goToStatus(){
        finish();
        startActivity(new Intent(LoginActivity.this, StatusActivity.class));
    }
}



