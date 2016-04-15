/*
  Android_Pebble_sd - Android alarm client for openseizuredetector..

  See http://openseizuredetector.org for more information.

  Copyright Graham Jones, 2015.

  This file is part of pebble_sd.

  Android_Pebble_sd is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  Android_Pebble_sd is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with Android_pebble_sd.  If not, see <http://www.gnu.org/licenses/>.

*/
package uk.org.openseizuredetector;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.format.Time;
import android.util.Log;
import android.widget.Toast;

import com.getpebble.android.kit.Constants;
import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;



/**
 * Abstract class for a seizure detector data source.  Subclasses include a pebble smart watch data source and a
 * network data source.
 */
public class SdDataSourceAngel extends SdDataSource {
    private Timer mSettingsTimer;
    private Timer mStatusTimer;
    private Time mStatusTime;
    private int mDataPeriod = 5;    // Period at which data is sent from watch to phone (sec)
    private int mAppRestartTimeout = 10;  // Timeout before re-starting watch app (sec) if we have not received
                                           // data after mDataPeriod
    //private Looper mServiceLooper;
    private int mFaultTimerPeriod = 30;  // Fault Timer Period in sec

    private String TAG = "SdDataSourceAngel";

    private int NSAMP = 512;   // Number of samples in fft input dataset.

    public SdDataSourceAngel(Context context, SdDataReceiver sdDataReceiver) {
        super(context,sdDataReceiver);
        mName = "Angel";
    }


    /**
     * Start the datasource updating - initialises from sharedpreferences first to
     * make sure any changes to preferences are taken into account.
     */
    public void start() {
        Log.v(TAG, "start()");
        updatePrefs();
        startAngelServer();
        // Start timer to check status of pebble regularly.
        mStatusTime = new Time(Time.getCurrentTimezone());
        // use a timer to check the status of the pebble app on the same frequency
        // as we get app data.
        if (mStatusTimer == null) {
            Log.v(TAG, "onCreate(): starting status timer");
            mStatusTimer = new Timer();
            mStatusTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    getStatus();
                }
            }, 0, mDataPeriod * 1000);
        } else {
            Log.v(TAG, "onCreate(): status timer already running.");
        }
    }

    /**
     * Stop the datasource from updating
     */
    public void stop() {
        Log.v(TAG, "stop()");
        try {
            // Stop the status timer
            if (mStatusTimer != null) {
                Log.v(TAG, "onDestroy(): cancelling status timer");
                mStatusTimer.cancel();
                mStatusTimer.purge();
                mStatusTimer = null;
            }
            // Stop pebble message handler.
            Log.v(TAG, "onDestroy(): stopping pebble server");
            stopAngelServer();

        } catch (Exception e) {
            Log.v(TAG, "Error in stop() - " + e.toString());
        }


    }

    /**
     * updatePrefs() - update basic settings from the SharedPreferences
     * - defined in res/xml/SdDataSourcePebblePrefs.xml
     */
    public void updatePrefs() {
        Log.v(TAG, "updatePrefs()");
        SharedPreferences SP = PreferenceManager
                .getDefaultSharedPreferences(mContext);
        try {
            // Parse the AppRestartTimeout period setting.
            try {
                String appRestartTimeoutStr = SP.getString("AppRestartTimeout", "10");
                mAppRestartTimeout = Integer.parseInt(appRestartTimeoutStr);
                Log.v(TAG, "updatePrefs() - mAppRestartTimeout = " + mAppRestartTimeout);
            } catch (Exception ex) {
                Log.v(TAG, "updatePrefs() - Problem with AppRestartTimeout preference!");
                Toast toast = Toast.makeText(mContext, "Problem Parsing AppRestartTimeout Preference", Toast.LENGTH_SHORT);
                toast.show();
            }

            // Parse the DataPeriod setting.
            try {
                String dataPeriodStr = SP.getString("DataPeriod", "5");
                mDataPeriod = Integer.parseInt(dataPeriodStr);
                Log.v(TAG, "updatePrefs() - mDataPeriod = " + mDataPeriod);
            } catch (Exception ex) {
                Log.v(TAG, "updatePrefs() - Problem with DataPeriod preference!");
                Toast toast = Toast.makeText(mContext, "Problem Parsing DataPeriod Preference", Toast.LENGTH_SHORT);
                toast.show();
            }

            // Parse the FaultTimer period setting.
            try {
                String faultTimerPeriodStr = SP.getString("FaultTimerPeriod", "30");
                mFaultTimerPeriod = Integer.parseInt(faultTimerPeriodStr);
                Log.v(TAG, "updatePrefs() - mFaultTimerPeriod = " + mFaultTimerPeriod);
            } catch (Exception ex) {
                Log.v(TAG, "updatePrefs() - Problem with FaultTimerPeriod preference!");
                Toast toast = Toast.makeText(mContext, "Problem Parsing FaultTimerPeriod Preference", Toast.LENGTH_SHORT);
                toast.show();
            }


            // Watch Settings
            short intVal;
            String prefStr;

            prefStr = SP.getString("DataUpdatePeriod", "5");
            intVal = (short) Integer.parseInt(prefStr);
            Log.v(TAG, "updatePrefs() DataUpdatePeriod = " + intVal);

            prefStr = SP.getString("MutePeriod", "300");
            intVal = (short) Integer.parseInt(prefStr);
            Log.v(TAG, "updatePrefs() MutePeriod = " + intVal);

            prefStr = SP.getString("ManAlarmPeriod", "30");
            intVal = (short) Integer.parseInt(prefStr);
            Log.v(TAG, "updatePrefs() ManAlarmPeriod = " + intVal);

            prefStr = SP.getString("AlarmFreqMin", "5");
            intVal = (short) Integer.parseInt(prefStr);
            Log.v(TAG, "updatePrefs() AlarmFreqMin = " + intVal);

            prefStr = SP.getString("AlarmFreqMax", "10");
            intVal = (short) Integer.parseInt(prefStr);
            Log.v(TAG, "updatePrefs() AlarmFreqMax = " + intVal);

            prefStr = SP.getString("WarnTime", "5");
            intVal = (short) Integer.parseInt(prefStr);
            Log.v(TAG, "updatePrefs() WarnTime = " + intVal);

            prefStr = SP.getString("AlarmTime", "10");
            intVal = (short) Integer.parseInt(prefStr);
            Log.v(TAG, "updatePrefs() AlarmTime = " + intVal);

            prefStr = SP.getString("AlarmThresh", "70");
            intVal = (short) Integer.parseInt(prefStr);
            Log.v(TAG, "updatePrefs() AlarmThresh = " + intVal);

            prefStr = SP.getString("AlarmRatioThresh", "30");
            intVal = (short) Integer.parseInt(prefStr);
            Log.v(TAG, "updatePrefs() AlarmRatioThresh = " + intVal);

            boolean fallActiveBool = SP.getBoolean("FallActive", false);
            Log.v(TAG, "updatePrefs() FallActive = " + fallActiveBool);

            prefStr = SP.getString("FallThreshMin", "200");
            intVal = (short) Integer.parseInt(prefStr);
            Log.v(TAG, "updatePrefs() FallThreshMin = " + intVal);

            prefStr = SP.getString("FallThreshMax", "1200");
            intVal = (short) Integer.parseInt(prefStr);
            Log.v(TAG, "updatePrefs() FallThreshMax = " + intVal);

            prefStr = SP.getString("FallWindow", "1500");
            intVal = (short) Integer.parseInt(prefStr);
            Log.v(TAG, "updatePrefs() FallWindow = " + intVal);


        } catch (Exception ex) {
            Log.v(TAG, "updatePrefs() - Problem parsing preferences!");
            Toast toast = Toast.makeText(mContext, "Problem Parsing Preferences - Something won't work - Please go back to Settings and correct it!", Toast.LENGTH_SHORT);
            toast.show();
        }
    }


    /**
     * Set this server to receive pebble data by registering it as
     * A PebbleDataReceiver
     */
    private void startAngelServer() {
        Log.v(TAG, "StartAngelServer()");
    }

    /**
     * De-register this server from receiving pebble data
     */
    public void stopAngelServer() {
        Log.v(TAG, "stopAngelServer(): Stopping Pebble Server");
    }


    /**
     * Checks the status of the connection to the pebble watch,
     * and sets class variables for use by other functions.
     * If the watch app is not running, it attempts to re-start it.
     */
    public void getStatus() {
        Time tnow = new Time(Time.getCurrentTimezone());
        long tdiff;
        tnow.setToNow();
        // get time since the last data was received from the Pebble watch.
        tdiff = (tnow.toMillis(false) - mStatusTime.toMillis(false));
        Log.v(TAG, "getStatus()");
        // Check we are actually connected to the pebble.
        //mSdData.pebbleConnected = PebbleKit.isWatchConnected(mContext);
    }




}

