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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.format.Time;
import android.util.Log;
import android.widget.Toast;

import com.angel.sdk.BleScanner;
import com.angel.sdk.BluetoothInaccessibleException;

import java.util.List;
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

    private String mAngelSensorName;
    private String mAngelSensorAddress;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mAngelSensorDevice;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic mTempChar;
    private boolean mScanning;
    //private BleScanner mBleScanner;
    private final int SCAN_PERIOD = 30000;

    private Handler mHandler;

    private String TAG = "SdDataSourceAngel";

    private int NSAMP = 512;   // Number of samples in fft input dataset.

    private final String UUID_BATTERY = "0000180f-0000-1000-8000-00805f9b34fb";
    private final String UUID_THERMOMETER = "00001809-0000-1000-8000-00805f9b34fb";
    private final String UUID_HEARTRATE = "0000180d-0000-1000-8000-00805f9b34fb";
    private final String UUID_WAVEFORM = "481d178c-10dd-11e4-b514-b2227cce2b54";
    private final String UUID_TERMINAL = "41e1bd6a-9e39-441c-9312-b6e862472480";
    private final String UUID_ACTIVITY = "68b52738-4a04-40e1-8f83-337a29c3284d";
    private final String UUID_ALARMCLOCK = "7cd50edd-8bab-44ff-a8e8-82e19393af10";

    private final String UUID_TEMPMEAS_CHAR = "00002a1c-0000-1000-8000-00805f9b34fb";

    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    public SdDataSourceAngel(Context context, SdDataReceiver sdDataReceiver) {
        super(context, sdDataReceiver);
        mName = "Angel";
        mHandler = new Handler();
    }


    /**
     * Start the datasource updating - initialises from sharedpreferences first to
     * make sure any changes to preferences are taken into account.
     */
    public void start() {
        Log.v(TAG, "start()");
        updatePrefs();
        startAngelServer();
        Log.v(TAG,"Returned from startAngelServer()");
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
                    Log.v(TAG,"calling getStatus()");
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
     * Set this server to receive Angel Sensor Data by connecting to the Sensor and then
     * Registering to receive data.
     */
    private void startAngelServer() {
        Log.v(TAG, "StartAngelServer()");
        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        findAngelSensor();
        Log.v(TAG,"Returned from findAngelSensor()");
    }


    private void findAngelSensor() {
        Log.v(TAG,"findAngelSensor()");
        // Stops scanning after a pre-defined scan period.
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.v(TAG,"BLE Scan Timed Out");
                mScanning = false;
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            }
        }, SCAN_PERIOD);
        Log.v(TAG,"Starting BLE Scan");
        mScanning = true;
        mBluetoothAdapter.startLeScan(mLeScanCallback);
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi,
                                     byte[] scanRecord) {
                    Log.v(TAG,"onLeScan() - found "+device.getName());
                    if (device.getName() != null && device.getName().startsWith("Angel")) {
                        Log.v(TAG, "Found Angel Sensor - " + mAngelSensorName + ", " + mAngelSensorAddress);
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                        mAngelSensorName = device.getName();
                        mAngelSensorAddress = device.getAddress();
                        mAngelSensorDevice = device;
                        Log.v(TAG, "Found Angel Sensor - " + mAngelSensorName + ", " + mAngelSensorAddress);
                        mBluetoothGatt = mAngelSensorDevice.connectGatt(mContext,true,mGattCallback);
                    }
                }
            };


    // Various callback methods defined by the BLE API.
    private final BluetoothGattCallback mGattCallback =
            new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                                    int newState) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.i(TAG, "Connected to GATT server.");
                        Log.i(TAG, "Attempting to start service discovery:");
                        mBluetoothGatt.discoverServices();
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.i(TAG, "Disconnected from GATT server.");
                    }
                }

                @Override
                // New services discovered
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.v(TAG,"onServiceDiscovered() - GATT_SUCCESS");
                        List<BluetoothGattService> servList = gatt.getServices();
                        Log.v(TAG,servList.toString());
                        for (BluetoothGattService service : servList) {
                            if (service.getUuid().equals(UUID.fromString(UUID_BATTERY))) {
                                Log.v(TAG,"Found Battery Service");
                            } else if (service.getUuid().equals(UUID.fromString(UUID_THERMOMETER))) {
                                Log.v(TAG,"Found Thermometer Service");
                                mTempChar = service.getCharacteristic(
                                        UUID.fromString(UUID_TEMPMEAS_CHAR));
                                Log.v(TAG,"mTempChar = "+mTempChar.toString());
                                BluetoothGattDescriptor descriptor;
                                //mBluetoothGatt.setCharacteristicNotification(mTempChar,true);
                                //BluetoothGattDescriptor descriptor = mTempChar.getDescriptor(
                                //        UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                                //descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                //mBluetoothGatt.writeDescriptor(descriptor);

                                descriptor = mTempChar.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                                final byte NOTIFY_AND_INDICATE[] = {3,0};
                                descriptor.setValue(true ? NOTIFY_AND_INDICATE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                                //mWaitingForConfirmation = true;
                                if (!mBluetoothGatt.writeDescriptor(descriptor)) {
                                    throw new AssertionError("Failed to write BLE descriptor " + descriptor.getUuid() + " for UUID " + mTempChar.getUuid());
                                }

                                //try {
                                //    synchronized (this) {
                                //        wait(DESCRIPTOR_WRITE_TIMEOUT);
                                //        if (mWaitingForConfirmation) {
                                //            throw new AssertionError("Did not receive confirmation for mBluetoothGatt.writeDescriptor(" + characteristic.getUuid() + ")");
                                //        }
                                //    }
                                //} catch (InterruptedException e) {
                                //    throw new AssertionError("Interrupted while waiting for response to mBluetoothGatt.writeDescriptor");
                                //}


                            } else if (service.getUuid().equals(UUID.fromString(UUID_ACTIVITY))) {
                                Log.v(TAG,"Found Activity Service");
                            }else if (service.getUuid().equals(UUID.fromString(UUID_ALARMCLOCK))) {
                                Log.v(TAG,"Found Alarm Clock Service");
                            }else if (service.getUuid().equals(UUID.fromString(UUID_HEARTRATE))) {
                                Log.v(TAG,"Found Heart Rate Service");
                            }else if (service.getUuid().equals(UUID.fromString(UUID_TERMINAL))) {
                                Log.v(TAG,"Found Terminal Service");
                            } else if (service.getUuid().equals(UUID.fromString(UUID_WAVEFORM))) {
                                Log.v(TAG, "Found Waveform Service");
                            } else {
                                Log.v(TAG, "Unknown Service - type=" + service.getType() + " UUID=" + service.getUuid());
                            }
                        }
                    } else {
                        Log.w(TAG, "onServicesDiscovered received: " + status);
                    }
                }

                @Override
                // Result of a characteristic read operation
                public void onCharacteristicRead(BluetoothGatt gatt,
                                                 BluetoothGattCharacteristic characteristic,
                                                 int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.v(TAG,"onCharacteristicRead() - GATT_SUCCESS");
                        if (characteristic.getUuid().equals(UUID.fromString(UUID_TEMPMEAS_CHAR))) {
                            Log.v(TAG,"Got Temperature Measurement - "+characteristic.toString());
                        }
                    } else {
                        Log.v(TAG,"onCharacteristicRead() - status = "+status);
                    }
                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                    Log.v(TAG,"onCharacteristicChanged()");
                }
            };




    /**
     * De-register this server from receiving pebble data
     */
    public void stopAngelServer() {

        Log.v(TAG, "stopAngelServer(): Stopping Angel Sensor Server");
        mStatusTimer.cancel();
        mStatusTimer.purge();
    }


    /**
     * Checks the status of the connection to the pebble watch,
     * and sets class variables for use by other functions.
     * If the watch app is not running, it attempts to re-start it.
     */
    public void getStatus() {
        Log.v(TAG,"getStatus()");
        Time tnow = new Time(Time.getCurrentTimezone());
        long tdiff;
        tnow.setToNow();
        // get time since the last data was received from the Pebble watch.
        tdiff = (tnow.toMillis(false) - mStatusTime.toMillis(false));
        if (mTempChar == null) {
            Log.v(TAG,"mTempChar is Null, not reading data...");
        } else {
            // request characteristic read (asynchronous) - calls onCharacteristicRead
            Log.v(TAG, "getStatus() - calling ReadCharacteristic()");
            boolean retVal = mBluetoothGatt.readCharacteristic(mTempChar);
            float val = -1;
            //float val = mTempChar.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT,0);
            Log.v(TAG, "getStatus() - retVal = " + retVal);
        }

    }




}

