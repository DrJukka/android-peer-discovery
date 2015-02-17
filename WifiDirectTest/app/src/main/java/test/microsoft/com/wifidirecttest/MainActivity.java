package test.microsoft.com.wifidirecttest;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity  {

    public BroadcastReceiver receiver = null;
    public static boolean isService = false;

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
    protected void onResume() {
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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_clear) {
            ((TextView)findViewById(R.id.debugdataBox)).setText("");
            return true;
        }else if (id == R.id.action_showIp) {
            MyP2PHelper.printLocalIpAddresses(this);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
