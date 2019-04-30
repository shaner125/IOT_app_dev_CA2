package com.example.shane.smartbulb;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TimePicker;

import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobile.client.Callback;
import com.amazonaws.mobile.client.UserStateDetails;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttNewMessageCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static com.example.shane.smartbulb.SetBulb.LOG_TAG;

public class ProgramSunrise extends AppCompatActivity implements View.OnClickListener {

    private static final String CUSTOMER_SPECIFIC_IOT_ENDPOINT = "a2a4apg8zaw7mm-ats.iot.eu-west-1.amazonaws.com";
    String topic;
    AWSIotMqttManager mqttManager;
    String clientId;
    Switch simpleSwitch;
    Button btnBack;
    TimePicker picker;
    String tvw;
    Button btnTimePicker;
    EditText txtTime;
    private int mHour, mMinute;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_program_sunrise);

        topic = "$aws/things/piCA2/shadow/update";

        simpleSwitch = (Switch) findViewById(R.id.simpleSwitch); // initiate Switch
        btnBack = (Button) findViewById(R.id.btn_back);

        btnTimePicker=(Button)findViewById(R.id.btn_time);
        txtTime=(EditText)findViewById(R.id.in_time);
        btnTimePicker.setOnClickListener(this);
        simpleSwitch.setOnClickListener(this);

        SharedPreferences settings = getSharedPreferences("myPref", 0);
        boolean silent = settings.getBoolean("switchkey", false);
        simpleSwitch.setChecked(silent);

        txtTime.setText("08:30");
        // MQTT client IDs are required to be unique per AWS IoT account.
        // This UUID is "practically unique" but does not _guarantee_
        // uniqueness.
        clientId = UUID.randomUUID().toString();
        // Initialize the credentials provider
        final CountDownLatch latch = new CountDownLatch(1);
        AWSMobileClient.getInstance().initialize(
                getApplicationContext(),
                new Callback<UserStateDetails>() {
                    @Override
                    public void onResult(UserStateDetails result) {
                        latch.countDown();
                    }

                    @Override
                    public void onError(Exception e) {
                        latch.countDown();
                        Log.e(LOG_TAG, "onError: ", e);
                    }
                }
        );

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // MQTT Client
        mqttManager = new AWSIotMqttManager(clientId, CUSTOMER_SPECIFIC_IOT_ENDPOINT);

        // Attempting connection to client once app is loaded, buttons stil in place incase of failure.
        try {
            mqttManager.connect(AWSMobileClient.getInstance(), new AWSIotMqttClientStatusCallback() {
                @Override
                public void onStatusChanged(final AWSIotMqttClientStatus status,
                                            final Throwable throwable) {
                    Log.d(LOG_TAG, "Status = " + String.valueOf(status));
                    if(String.valueOf(status).equals("Connected")){
                        subscribe();
                        try {
                            mqttManager.publishString("{\"state\":{\"desired\":{\"test\":\"test\"}}}", topic, AWSIotMqttQos.QOS0);
                        }
                        catch (Exception e) {
                            Log.e(LOG_TAG, "Publish error.", e);
                        }
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (throwable != null) {
                                Log.e(LOG_TAG, "Connection error.", throwable);
                            }
                        }
                    });
                }
            });
        } catch (final Exception e) {
            Log.e(LOG_TAG, "Connection error.", e);
        }

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    public void subscribe() {
        // This method subscribes to the thing shadow and uses conditional statements to determine which sensor
        // is sending the message. It updates the UI appropriately.
        try {
            mqttManager.subscribeToTopic(topic, AWSIotMqttQos.QOS0,
                    new AWSIotMqttNewMessageCallback() {
                        @Override
                        public void onMessageArrived(final String topic, final byte[] data) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        String message = new String(data, "UTF-8");
                                        Log.d(LOG_TAG, "Message arrived:");
                                        Log.d(LOG_TAG, "   Topic: " + topic);
                                        Log.d(LOG_TAG, " Message: " + message);
                                        try {
                                            JSONObject obj = new JSONObject(message);
                                            if (obj.getJSONObject("state").getJSONObject("desired").getInt("alarmStatus") == 1){
                                                simpleSwitch.setChecked(true);
                                            }
                                            else if (obj.getJSONObject("state").getJSONObject("desired").getInt("alarmStatus") == 0){
                                                simpleSwitch.setChecked(false);
                                            }
                                        } catch (JSONException e) {
                                            e.printStackTrace();
                                        }

                                    } catch (UnsupportedEncodingException e) {
                                        Log.e(LOG_TAG, "Message encoding error.", e);
                                    }
                                }
                            });
                        }
                    });
        } catch (Exception e) {
            Log.e(LOG_TAG, "Subscription error.", e);
        }
    }

    @SuppressLint("ResourceAsColor")
    @Override
    public void onClick(View v) {
        // define the button that invoked the listener by id
        String statusSwitch = new String();

        if (v == btnTimePicker) {

            // Get Current Time
            final Calendar c = Calendar.getInstance();
            mHour = c.get(Calendar.HOUR_OF_DAY);
            mMinute = c.get(Calendar.MINUTE);

            // Launch Time Picker Dialog
            TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                    new TimePickerDialog.OnTimeSetListener() {

                        @Override
                        public void onTimeSet(TimePicker view, int hourOfDay,
                                              int minute) {
                            if (minute<10){
                                txtTime.setText(hourOfDay + ":0" + minute);
                            }
                            else {
                                txtTime.setText(hourOfDay + ":" + minute);
                            }
                            try {
                                String timeJSON = "{\"state\":{\"desired\":{\"alarmTime\":\""+hourOfDay + ":" + minute+"\"}}}";
                                mqttManager.publishString(timeJSON, topic, AWSIotMqttQos.QOS0);
                            }
                            catch (Exception e) {
                                Log.e(LOG_TAG, "Publish error.", e);
                            }
                        }

                    }, mHour, mMinute, true);
            timePickerDialog.show();

        }

        else if (v == simpleSwitch){
            tvw = txtTime.getText().toString();
            if (simpleSwitch.isChecked()){
                statusSwitch = "{\"state\":{\"desired\":{\"alarmStatus\":1, \"alarmTime\":\""+tvw+"\"}}}";
                Log.e(LOG_TAG, v.toString());
            }
            else{
                statusSwitch = "{\"state\":{\"desired\":{\"alarmStatus\":0}}}";
            }
            try {
                mqttManager.publishString(statusSwitch, topic, AWSIotMqttQos.QOS0);
                SharedPreferences settings = getSharedPreferences("myPref", 0);
                SharedPreferences.Editor editor = settings.edit();
                editor.putBoolean("switchkey", simpleSwitch.isChecked());
                editor.commit();
            }
            catch (Exception e) {
                Log.e(LOG_TAG, "Publish error.", e);
            }
        }
    }

}
