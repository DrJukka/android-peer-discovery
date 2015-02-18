package org.briarproject.bonjour;


import static android.widget.Toast.LENGTH_LONG;
import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity  {

	private static final String TAG = MainActivity.class.getPackage().getName();

    public BroadcastReceiver receiver = null;
    public static boolean isService = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        final Context that = this;
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String s = intent.getStringExtra(WifiDirectService.DSS_WIFIDIRECT_MESSAGE);
                ((TextView)findViewById(R.id.debugdataBox)).append(s + "\n");
            }
        };

        Button clearButton = (Button) findViewById(R.id.button2);
        clearButton.setOnClickListener(new OnClickListener() {
          @Override
          public void onClick(View v) {
              ((TextView)findViewById(R.id.debugdataBox)).setText("");
          }
        });

        Button showIPButton = (Button) findViewById(R.id.button3);
        showIPButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                MyP2PHelper.printLocalIpAddresses(that);
            }
        });

        Button startserviceButton = (Button) findViewById(R.id.button1);
        startserviceButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

                TextView tv = (TextView) findViewById(R.id.textView1);

                if(isService){
                    stopService(new Intent(MainActivity.this,WifiDirectService.class));
                    isService = false;
                    tv.setText("Service stopped");
                }else {
                    startService(new Intent(MainActivity.this, WifiDirectService.class));
                    isService = true;
                    tv.setText("Service running");
                 /* Intent startMain = new Intent(Intent.ACTION_MAIN);
                    startMain.addCategory(Intent.CATEGORY_HOME);
                    startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(startMain);
                    */
                }
            }
        });

    }


	@Override
	public void onResume() {
		super.onResume();
        TextView tv = (TextView) findViewById(R.id.textView1);
        if(isService)
        {
            tv.setText("Service Still running");
        }else{
            tv.setText("Service is not running !");
        }
	}

	@Override
	public void onPause() {
		super.onPause();

	}

	@Override
	public void onDestroy() {
		super.onDestroy();

	}

    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(this).registerReceiver((receiver), new IntentFilter(WifiDirectService.DSS_WIFIDIRECT_VALUES));
    }

    @Override
    protected void onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        super.onStop();
    }
}
