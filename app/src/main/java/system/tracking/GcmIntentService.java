package system.tracking;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.gson.Gson;
import com.google.gson.JsonObject;


/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class GcmIntentService extends IntentService {
    static final String TAG = "IntentService";

    public GcmIntentService() {
        super("GcmIntentService");
    }

    static class Message {
        Integer id;
        String action;
        JsonObject parameters;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            Bundle extras = intent.getExtras();
            GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);
            String messageType = gcm.getMessageType(intent);

            if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
                final String action = intent.getAction();

                Gson gson = new Gson();
                Message message = gson.fromJson(extras.getString("message"), Message.class);

                if(message.action.equals("is_near_object")){
                    handleIsNearObject(message.id, message.parameters);
                }
                else if(message.action.equals("alert") || message.action.equals("alert_critical") || message.action.equals("alert_in_move")){
                    handleAlert(message.id, message.parameters);
                }
                else{
                    Log.d(TAG, "unknown message type: "+message.action);
                }
            }
        }
    }

    private void handleAlert(Integer id, JsonObject parameters) {
        Log.d(TAG, "alert!! "+parameters.get("message").getAsString());

        Intent intent = new Intent(this, StatusActivity.class);
        intent.setAction(StatusActivity.ALARM);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("message", parameters.get("message").getAsString());
        startActivity(intent);

        /*Intent rt = new Intent(StatusActivity.ALARM);
        rt.setAction(StatusActivity.ALARM);
        rt.putExtra("message", parameters.get("message").getAsString());
        LocalBroadcastManager.getInstance(this).sendBroadcast(rt);*/
    }

    private void handleIsNearObject(Integer id, JsonObject parameters) {
        float latitude = parameters.getAsJsonObject("position").get("latitude").getAsFloat();
        float longitude = parameters.getAsJsonObject("position").get("longitude").getAsFloat();

        Log.d(TAG, "checking if device is near object");
        Log.d(TAG, String.format("%fx%f - position from object", latitude, longitude));

        LocationCheckerService.checkLocation(getApplicationContext(), id, latitude, longitude);
    }
}
