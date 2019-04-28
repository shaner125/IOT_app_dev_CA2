package com.example.shane.smartbulb;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import com.skydoves.colorpickerview.AlphaTileView;
import com.skydoves.colorpickerview.ColorEnvelope;
import com.skydoves.colorpickerview.ColorPickerDialog;
import com.skydoves.colorpickerview.ColorPickerView;
import com.skydoves.colorpickerview.flag.FlagMode;
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener;
import java.util.Arrays;

@SuppressWarnings("ConstantConditions")
public class SetBulb  extends AppCompatActivity {

    private ColorPickerView colorPickerView;

    static final String LOG_TAG = SetBulb.class.getCanonicalName();
    private static final String CUSTOMER_SPECIFIC_IOT_ENDPOINT = "a2a4apg8zaw7mm-ats.iot.eu-west-1.amazonaws.com";
    String topic;
    AWSIotMqttManager mqttManager;
    String clientId;
    TextView tvProgressLabel;
    private Button btnSetColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_set_bulb);

        topic = "$aws/things/piCA2/shadow/update";

        SeekBar seekBar = findViewById(R.id.seekBar);
        seekBar.setOnSeekBarChangeListener(seekBarChangeListener);

        int progress = seekBar.getProgress();
        tvProgressLabel = findViewById(R.id.textView1);
        tvProgressLabel.setText("Bulb Brightness: " + progress);
        btnSetColor = (Button) findViewById(R.id.btn_setColor);


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

        colorPickerView = findViewById(R.id.colorPickerView);
        BubbleFlag bubbleFlag = new BubbleFlag(this, R.layout.layout_flag);
        bubbleFlag.setFlagMode(FlagMode.FADE);
        colorPickerView.setFlagView(bubbleFlag);
        colorPickerView.setColorListener(
                new ColorEnvelopeListener() {
                    @Override
                    public void onColorSelected(ColorEnvelope envelope, boolean fromUser) {
                        setLayoutColor(envelope);
                    }
                });


        btnSetColor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mqttManager.publishString("{\"state\":{\"desired\":{\"colourSelected\":1}}}", topic, AWSIotMqttQos.QOS0);
            }
        });

    }

    SeekBar.OnSeekBarChangeListener seekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            // updated continuously as the user slides the thumb
            tvProgressLabel.setText("Bulb Brightness: " + progress);
            mqttManager.publishString("{\"state\":{\"desired\":{\"brightness\":"+progress+"}}}", topic, AWSIotMqttQos.QOS0);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // called when the user first touches the SeekBar
            mqttManager.publishString("{\"state\":{\"desired\":{\"brightnessSelected\":0}}}", topic, AWSIotMqttQos.QOS0);
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // called after the user finishes moving the SeekBar
            mqttManager.publishString("{\"state\":{\"desired\":{\"brightnessSelected\":1}}}", topic, AWSIotMqttQos.QOS0);
        }
    };

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

    /**
     * set layout color & textView html code
     *
     * @param envelope ColorEnvelope by ColorEnvelopeListener
     */
    @SuppressLint("SetTextI18n")
    private void setLayoutColor(ColorEnvelope envelope) {
        int[] colorValue = envelope.getArgb();
        TextView textView = findViewById(R.id.textView);
        textView.setText(Arrays.toString((colorValue)));
        mqttManager.publishString("{\"state\":{\"desired\":{\"r\":"+colorValue[1]+", \"g\":"+colorValue[2]+",\"b\":"+colorValue[3]+"}}}", topic, AWSIotMqttQos.QOS0);

        AlphaTileView alphaTileView = findViewById(R.id.alphaTileView);
        alphaTileView.setPaintColor(envelope.getColor());
    }

    /** shows ColorPickerDialog */
    private void dialog() {
        ColorPickerDialog.Builder builder =
                new ColorPickerDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                        .setTitle("Smart Bulb Color Chooser")
                        .setPreferenceName("Test")
                        .setPositiveButton(
                                getString(R.string.confirm),
                                new ColorEnvelopeListener() {
                                    @Override
                                    public void onColorSelected(ColorEnvelope envelope, boolean fromUser) {
                                        setLayoutColor(envelope);
                                    }
                                })
                        .setNegativeButton(
                                getString(R.string.cancel),
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        dialogInterface.dismiss();
                                    }
                                });
        ColorPickerView colorPickerView = builder.getColorPickerView();
        colorPickerView.setFlagView(new BubbleFlag(this, R.layout.layout_flag));
        builder.show();
    }
}