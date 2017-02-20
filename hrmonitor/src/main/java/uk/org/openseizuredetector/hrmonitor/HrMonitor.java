package uk.org.openseizuredetector.hrmonitor;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

public class HrMonitor extends Activity {

    private static String TAG = "HrMonitor";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hr_monitor);

        Button b;
        b = (Button) findViewById(R.id.scan_ble_button);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.v(TAG, "scan_ble_button");
                try {
                    Intent prefsIntent = new Intent(
                            HrMonitor.this,
                            HrMonitorScanner.class);
                    startActivity(prefsIntent);
                } catch (Exception ex) {
                    Log.v(TAG, "exception starting HR Monitor Scanner activity " + ex.toString());
                }
            }
        });

        b = (Button) findViewById(R.id.start_server_button);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.v(TAG, "start_server_button");
                try {
                    Intent prefsIntent = new Intent(
                            HrMonitor.this,
                            HrMonitorScanner.class);
                    startActivity(prefsIntent);
                } catch (Exception ex) {
                    Log.v(TAG, "exception starting HR Monitor Scanner activity " + ex.toString());
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.v(TAG,"onStart()");
        startServer();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.v(TAG,"onStop()");
        stopServer();
    }

    /**
     * Start the HrMonitorService service
     */
    public void startServer() {
        // Start the server
        Log.v(TAG,"startServer()");
        Intent hrMonitorServiceIntent;
        hrMonitorServiceIntent = new Intent(this, HrMonitorService.class);
        hrMonitorServiceIntent.setData(Uri.parse("Start"));
        this.startService(hrMonitorServiceIntent);
    }

    /**
     * Stop the HrMonitorService service
     */
    public void stopServer() {
        Log.v(TAG, "stopping Server...");

        // then send an Intent to stop the service.
        Intent hrmServerIntent;
        hrmServerIntent = new Intent(this, HrMonitorService.class);
        hrmServerIntent.setData(Uri.parse("Stop"));
        this.stopService(hrmServerIntent);
    }



}