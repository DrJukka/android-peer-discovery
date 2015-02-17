package test.microsoft.com.wifidirecttest;


import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;


import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import static android.net.wifi.p2p.WifiP2pManager.EXTRA_WIFI_P2P_DEVICE;
import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION;
import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION;
import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION;
import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION;

/**
 * Created by juksilve on 17.2.2015.
 */
public class WifiDirectService extends Service {

    static final public String DSS_WIFIDIRECT_VALUES = "test.microsoft.com.wifidirecttest.DSS_WIFIDIRECT_VALUES";
    static final public String DSS_WIFIDIRECT_MESSAGE = "test.microsoft.com.wifidirecttest.DSS_WIFIDIRECT_MESSAGE";


    private static final String TAG = MainActivity.class.getPackage().getName();

    private static final String SERVICE_TYPE = "_p2p._tcp";

    LocalBroadcastManager broadcaster;
    MyTextSpeech mySpeech = null;

    private WifiP2pManager p2p;
    private WifiP2pManager.Channel channel;
    private volatile ServerSocket wifiSocket = null;

    //can we make this dynamic ?
    private static final int PORT = 45678;

    private BroadcastReceiver receiver;
    private IntentFilter filter;

    private WifiP2pManager.PeerListListener peerListListener;
    private WifiP2pManager.DnsSdServiceResponseListener serviceListener;
    private WifiP2pManager.DnsSdTxtRecordListener txtListener;

    private WifiP2pDnsSdServiceInfo serviceInfo;

    private final IBinder mBinder = new LocalBinder();
    public class LocalBinder extends Binder {
        WifiDirectService getService() {
            return WifiDirectService.this;
        }
    }
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        mySpeech = new MyTextSpeech(this);

        broadcaster = LocalBroadcastManager.getInstance(this);


        p2p = (WifiP2pManager) getSystemService(WIFI_P2P_SERVICE);
        if (p2p == null) {
            mySpeech.speak("This device does not support Wi-Fi Direct");
            sendResult("This device does not support Wi-Fi Direct");
        } else {
            channel = p2p.initialize(this, getMainLooper(), null);

            receiver = new PeerReceiver();
            filter = new IntentFilter();
            filter.addAction(WIFI_P2P_STATE_CHANGED_ACTION);
            filter.addAction(WIFI_P2P_PEERS_CHANGED_ACTION);
            filter.addAction(WIFI_P2P_CONNECTION_CHANGED_ACTION);
            filter.addAction(WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
            registerReceiver(receiver, filter);

            openWifiSocket();

            peerListListener = new WifiP2pManager.PeerListListener() {

                public void onPeersAvailable(WifiP2pDeviceList peers) {
                    sendResult("Discovered peers:");

                    final WifiP2pDeviceList pers = peers;
                    String allInOne = "";
                    int numm = 0;
                    for (WifiP2pDevice peer : pers.getDeviceList()) {
                        numm++;
                        allInOne = allInOne + MyP2PHelper.deviceToString(peer) + ", ";
                        sendResult("\t" + MyP2PHelper.deviceToString(peer));
                    }
                    mySpeech.speak(numm + " peers discovered, names: " + allInOne);
                }
            };

            serviceListener = new WifiP2pManager.DnsSdServiceResponseListener() {

                public void onDnsSdServiceAvailable(String instanceName, String serviceType, WifiP2pDevice device) {
                    sendResult("\t\n Service discovered, " + instanceName + " " + serviceType + " : " + MyP2PHelper.deviceToString(device));
                    mySpeech.speak(" Service discovered, " + instanceName + " " + serviceType + " : " + MyP2PHelper.deviceToString(device));
                }
            };

            txtListener = new WifiP2pManager.DnsSdTxtRecordListener() {

                public void onDnsSdTxtRecordAvailable(String domain, Map<String, String> txtMap, WifiP2pDevice device) {

                    // should show this on ui

                    final String hjepper = "" + MyP2PHelper.deviceToString(device) + " : " + domain;
                    sendResult("Discovered TXT record:"  + hjepper);

                    for (Map.Entry<String, String> e : txtMap.entrySet()) {
                        sendResult( "\t" + e.getKey() + " = " + e.getValue());
                    }
                    mySpeech.speak("Discovered TXT record " + hjepper);

                    if (domain.endsWith("." + SERVICE_TYPE + ".local.")) {
                        String ip = txtMap.get("ip");
                        if (ip != null) {
                            connectByWifi(ip);
                        }
                        String bt = txtMap.get("bt");
                        if (bt != null) {
                            //connectByBluetooth(bt);
                        }
                    }
                }
            };

            p2p.setDnsSdResponseListeners(channel, serviceListener, txtListener);

            String instanceName = "DSS";
            Map<String, String> txtMap = new HashMap<String, String>();
            InetAddress ipAddr = getLocalIpAddress();
            if (ipAddr != null) {
                txtMap.put("ip", MyP2PHelper.ipAddressToString(ipAddr));
            }
            /*String btAddr = getLocalBluetoothAddress();
            if(btAddr != null) txtMap.put("bt", btAddr);
            */
            serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(instanceName, SERVICE_TYPE, txtMap);

            startDiscovery();
        }
    }

    public void onDestroy() {
        stopDiscovery();
        closeWifiSocket();
    }

    public void sendResult(String message) {

        Log.d(TAG, message);

        Intent intent = new Intent(DSS_WIFIDIRECT_VALUES);
        if(message != null)
            intent.putExtra(DSS_WIFIDIRECT_MESSAGE, message);
        broadcaster.sendBroadcast(intent);
    }

    private void startDiscovery() {

        openWifiSocket();

        sendResult("Starting discovery");
        p2p.addLocalService(channel, serviceInfo, new WifiP2pManager.ActionListener() {
            public void onSuccess() {
                sendResult("Added local service");
                startPeerDiscovery();
            }

            public void onFailure(int reason) {
                sendResult("Adding local service failed, error code " + reason);
                startPeerDiscovery();
            }
        });
    }


    private void startPeerDiscovery() {
        p2p.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            public void onSuccess() {
                sendResult("Started peer discovery");
                addServiceRequest();
            }

            public void onFailure(int reason) {
                sendResult("Starting peer discovery failed, error code " + reason);
                addServiceRequest();
            }
        });
    }

    private void addServiceRequest() {
        WifiP2pDnsSdServiceRequest request = WifiP2pDnsSdServiceRequest.newInstance();
        final Handler handler = new Handler();
        p2p.addServiceRequest(channel, request, new WifiP2pManager.ActionListener() {

             public void onSuccess() {
                 sendResult("Added service request");
                // Calling discoverServices() too soon can result in a
                // NO_SERVICE_REQUESTS failure - looks like a race condition
                // http://p2feed.com/wifi-direct.html#bugs
                handler.postDelayed(new Runnable() {
                    public void run() {
                         startServiceDiscovery();
                    }
                }, 1000);
            }

            public void onFailure(int reason) {
                sendResult("Adding service request failed, error code " + reason);
                // No point starting service discovery
            }
        });
    }

    private void startServiceDiscovery() {
        p2p.discoverServices(channel, new WifiP2pManager.ActionListener() {

            public void onSuccess() {
                sendResult("Started service discovery");
            }

            public void onFailure(int reason) {
                sendResult("Starting service discovery failed, error code " + reason);
            }
        });
    }

    private void stopDiscovery() {
        closeWifiSocket();

        sendResult("Stopping discovery");
        p2p.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {

            public void onSuccess() {
                sendResult("Stopped peer discovery");
                clearServiceRequests();
            }

            public void onFailure(int reason) {
                sendResult("Stopping peer discovery failed, error code " + reason);
             }
        });
    }

    private void clearServiceRequests() {
        p2p.clearServiceRequests(channel, new WifiP2pManager.ActionListener() {
            public void onSuccess() {
                sendResult("Cleared service requests");
                clearLocalServices();
            }

            public void onFailure(int reason) {
                sendResult("Clearing service requests failed, error code " + reason);
            }
        });
    }

    private void clearLocalServices() {
        p2p.clearLocalServices(channel, new WifiP2pManager.ActionListener() {
            public void onSuccess() {
                sendResult("Cleared local services");
            }

            public void onFailure(int reason) {
                sendResult("Clearing local services failed, error code " + reason);
            }
        });
    }


    private void openWifiSocket() {
        final InetAddress ip = getLocalIpAddress();
        if(ip == null) return;
        new Thread() {
            @Override
            public void run() {
                try {
                    sendResult("Opening wifi socket");
                    ServerSocket socket = new ServerSocket();
                    socket.bind(new InetSocketAddress(ip, PORT));
                    sendResult("Wifi socket opened");
                    wifiSocket = socket;
                    while(true) {
                        Socket s = socket.accept();
                        InetAddress remoteIp = s.getInetAddress();
                        final String addr = MyP2PHelper.ipAddressToString(remoteIp);

                        sendResult("Incoming connection from " + addr);
                        mySpeech.speak("Incoming connection from " + addr);
                        s.close();
                    }
                } catch(IOException e) {
                    Log.e(TAG, e.toString());
                }
            }
        }.start();
    }

    private void closeWifiSocket() {
        if(wifiSocket == null)
            return;
        sendResult("Closing wifi socket");
        try {
            wifiSocket.close();
            wifiSocket = null;
        } catch(IOException e) {
            Log.e(TAG, e.toString());
        }
    }

    private void connectByWifi(final String ip) {
        if(!MyP2PHelper.isValidIpAddress(ip)) {
            sendResult(ip + " is not a valid IP address");
            return;
        }

        // show in UI
        //print("Connecting to " + ip);
        new Thread() {

            @Override
            public void run() {
                try {
                    Socket s = new Socket(ip, PORT);
                    final String local = MyP2PHelper.ipAddressToString(s.getLocalAddress());
                    mySpeech.speak("Connected to " + ip + " from " + local);
                    s.close();
                } catch(IOException e) {
                    Log.e(TAG, e.toString());
                }
            }
        }.start();
    }

    private InetAddress getLocalIpAddress() {
        WifiManager wifi = (WifiManager) getSystemService(WIFI_SERVICE);
        if(wifi == null) return null;
        WifiInfo info = wifi.getConnectionInfo();
        if(info == null || info.getNetworkId() == -1) return null;
        int ipInt = info.getIpAddress(); // What if it's an IPv6 address?
        byte[] ip = MyP2PHelper.ipIntToBytes(ipInt);
        try {
            return InetAddress.getByAddress(ip);
        } catch(UnknownHostException e) {
            Log.e(TAG, e.toString());
            return null;
        }
    }

    private class PeerReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            sendResult("Received intent: " + action);
            if(WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                p2p.requestPeers(channel, peerListListener);
            } else if(WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                WifiP2pDevice device = intent.getParcelableExtra(EXTRA_WIFI_P2P_DEVICE);
                sendResult("Local device: " + MyP2PHelper.deviceToString(device));
            } else if (WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                if (networkInfo.isConnected()) {
                    sendResult("PeerReceiver, we are connected !!!");
                } else {
                    sendResult("PeerReceiver, DISCONNECTED event !!");
                }
            }
        }
    }

}
