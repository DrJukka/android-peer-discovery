package org.briarproject.bonjour;


import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;

import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

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
public class WifiDirectService extends Service implements WifiP2pManager.ConnectionInfoListener{

    final public WifiDirectService that = this;

    static final public String DSS_WIFIDIRECT_VALUES = "test.microsoft.com.wifidirecttest.DSS_WIFIDIRECT_VALUES";
    static final public String DSS_WIFIDIRECT_MESSAGE = "test.microsoft.com.wifidirecttest.DSS_WIFIDIRECT_MESSAGE";

    private static final String TAG = MainActivity.class.getPackage().getName();

    LocalBroadcastManager broadcaster;
    MyTextSpeech mySpeech = null;

    private WifiP2pManager p2p;
    private WifiP2pManager.Channel channel;

    //change me  to be dynamic!!
    public String CLIENT_PORT_INSTANCE = "38765";
    public String SERVICE_PORT_INSTANCE = "38765";

    public static final String TXTRECORD_PROP_AVAILABLE = "available";
    private static final String SERVICE_TYPE = "_p2p._tcp";

    private BroadcastReceiver receiver;
    private IntentFilter filter;

    private WifiP2pManager.PeerListListener peerListListener;
    private WifiP2pManager.DnsSdServiceResponseListener serviceListener;

    private final IBinder mBinder = new LocalBinder();

    enum ServiceState{
        NONE,
        DiscoverPeer,
        DiscoverService,
        ConnectingWifi,
        QueryConnection,
        ConnectedAsOwner,
        ConnectedAsClient
    }
    public ServiceState  myServiceState = ServiceState.NONE;

    public static final int MESSAGE_READ = 0x400 + 1;
    public static final int MY_HANDLE = 0x400 + 2;

    GroupOwnerSocketHandler  groupSocket = null;
    ClientSocketHandler clientSocket = null;

    ChatManager chat = null;
    Handler myHandler  = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    sendResult("Buddy: " + readMessage);
                    mySpeech.speak("Got message " + readMessage);
                    break;

                case MY_HANDLE:
                    Object obj = msg.obj;
                    chat = (ChatManager) obj;

                    String helloBuffer = "Hello ";
                    if(clientSocket != null){
                        helloBuffer = helloBuffer + "From Client!";
                    }else{
                        helloBuffer = helloBuffer + "From Groupowner!";
                    }

                    chat.write(helloBuffer.getBytes());
            }
        }
    };

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
            filter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
            registerReceiver(receiver, filter);

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
                    mySpeech.speak(numm + " peers discovered.");
                    sendResult(numm + " peers discovered.");

                    if(numm > 0){
                        startServiceDiscovery();
                        //stopPeerDiscovery(); //TODO: See if this is needed later
                    }else{
                        //TODO, add timer here to start peer discovery
                        //startPeerDiscovery();
                    }
                }
            };

            serviceListener = new WifiP2pManager.DnsSdServiceResponseListener() {

                public void onDnsSdServiceAvailable(String instanceName, String serviceType, WifiP2pDevice device) {

                    sendResult("Service discovered, " + instanceName + " " + serviceType + " : " + MyP2PHelper.deviceToString(device));
                    if (serviceType.startsWith(SERVICE_TYPE)) {

                        CLIENT_PORT_INSTANCE = instanceName;

                        WifiP2pConfig config = new WifiP2pConfig();
                        config.deviceAddress = device.deviceAddress;
                        config.wps.setup = WpsInfo.PBC;


                        if(myServiceState == ServiceState.ConnectingWifi){
                            //sometimes we might be getting multiple calls with same device & service
                            sendResult("Already connecting, not re-connecting to service");
                        }else{
                            //Connection attempt Will Stop PeerDiscovery, so need to indicate state weel ahead.
                            myServiceState = ServiceState.ConnectingWifi;
                            p2p.connect(channel, config, new WifiP2pManager.ActionListener() {
                                @Override
                                public void onSuccess() {
                                    sendResult("Connecting to service");
                                }

                                @Override
                                public void onFailure(int errorCode) {
                                    sendResult("Failed connecting to service");
                                    startPeerDiscovery();
                                }
                            });
                        }
                    } else {
                        sendResult("Not our Service, :" + SERVICE_TYPE + "!=" + serviceType + ":");
                        startPeerDiscovery();
                    }
                }
            };

            p2p.setDnsSdResponseListeners(channel, serviceListener, null);

            startLocalService();
            startPeerDiscovery();
        }
    }

    public void onDestroy() {
        // TODO: clear sockets & Chat manager here

        stopServiceDiscovery();
        stopPeerDiscovery();
        stopLocalServices();
        unregisterReceiver(receiver);

    }


    public void sendResult(String message) {

        Log.d(TAG, message);

        Intent intent = new Intent(DSS_WIFIDIRECT_VALUES);
        if(message != null)
            intent.putExtra(DSS_WIFIDIRECT_MESSAGE, message);
        broadcaster.sendBroadcast(intent);
    }

    private void startPeerDiscovery() {
        myServiceState = ServiceState.DiscoverPeer;
        p2p.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            public void onSuccess() {
                sendResult("Started peer discovery");
            }

            public void onFailure(int reason) {
                sendResult("Starting peer discovery failed, error code " + reason);
            }
        });
    }


    private void stopPeerDiscovery() {
        p2p.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
            public void onSuccess() {sendResult("Stopped peer discovery");}
            public void onFailure(int reason) {sendResult("Stopping peer discovery failed, error code " + reason);}
        });
    }

    private void startLocalService() {

        Map<String, String> record = new HashMap<String, String>();
        record.put(TXTRECORD_PROP_AVAILABLE, "visible");

        WifiP2pDnsSdServiceInfo service = WifiP2pDnsSdServiceInfo.newInstance( SERVICE_PORT_INSTANCE, SERVICE_TYPE, record);

        p2p.addLocalService(channel, service, new WifiP2pManager.ActionListener() {
            public void onSuccess() {
                sendResult("Added local service.");
            }

            public void onFailure(int reason) {
                sendResult("Adding local service failed, error code " + reason);
            }
        });
    }

    private void stopLocalServices() {
        p2p.clearLocalServices(channel, new WifiP2pManager.ActionListener() {
            public void onSuccess() {
                sendResult("Cleared local services");
            }
            public void onFailure(int reason) {sendResult("Clearing local services failed, error code " + reason);}
        });
    }

    private void startServiceDiscovery() {

        myServiceState = ServiceState.DiscoverService;
        WifiP2pDnsSdServiceRequest request = WifiP2pDnsSdServiceRequest.newInstance();
        final Handler handler = new Handler();
        p2p.addServiceRequest(channel, request, new WifiP2pManager.ActionListener() {

            public void onSuccess() {
                sendResult("Added service request");
                // Calling discoverServices() too soon can result in a
                // NO_SERVICE_REQUESTS failure - looks like a race condition
                // http://p2feed.com/wifi-direct.html#bugs

                final  ServiceState tmpService = myServiceState;

                handler.postDelayed(new Runnable() {
                    public void run() {
                        //incase we get connection etc. while waiting
                        //this did happen when I was testing
                        if(tmpService == myServiceState) {
                            p2p.discoverServices(channel, new WifiP2pManager.ActionListener() {
                                public void onSuccess() {
                                    sendResult("Started service discovery");
                                }

                                public void onFailure(int reason) {
                                    sendResult("discoverServices request failed, error code " + reason);
                                    if (reason == WifiP2pManager.NO_SERVICE_REQUESTS) {
                                        // initiate a stop on service discovery
                                        p2p.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
                                            @Override
                                            public void onSuccess() {
                                                // initiate clearing of the all service requests
                                                p2p.clearServiceRequests(channel, new WifiP2pManager.ActionListener() {
                                                    @Override
                                                    public void onSuccess() {
                                                        sendResult("Cleared service discovery");
                                                        startPeerDiscovery();
                                                    }

                                                    @Override
                                                    public void onFailure(int reason) {
                                                        sendResult("Stop service discovery failed, error code " + reason);
                                                    }
                                                });
                                            }

                                            @Override
                                            public void onFailure(int reason) {
                                                sendResult("Stop peer discovery failed, error code " + reason);
                                            }
                                        });
                                    }
                                }
                            });
                        }
                    }
                }, 1000);
            }

            public void onFailure(int reason) {
                sendResult("Adding service request failed, error code " + reason);
                startPeerDiscovery();
            }
        });
    }

    private void stopServiceDiscovery() {
        p2p.clearServiceRequests(channel, new WifiP2pManager.ActionListener() {
            public void onSuccess() {
                sendResult("Cleared service requests");
            }
            public void onFailure(int reason) {sendResult("Clearing service requests failed, error code " + reason);}
        });
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
        try {
            if (wifiP2pInfo.isGroupOwner) {
                clientSocket = null;

                sendResult("Connected as group owner");
                groupSocket = new GroupOwnerSocketHandler(myHandler,Integer.parseInt(SERVICE_PORT_INSTANCE),this);
                groupSocket.start();

            } else {
                sendResult("will now do socket connection with port : " + CLIENT_PORT_INSTANCE);
                groupSocket = null;

                clientSocket = new ClientSocketHandler(myHandler,wifiP2pInfo.groupOwnerAddress,Integer.parseInt(CLIENT_PORT_INSTANCE),this);
                clientSocket.start();
            }
        } catch(Exception e) {
            sendResult("onConnectionInfoAvailable, error: " + e.toString());
        }
    }

    private class PeerReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            sendResult("BR: " + action);

            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {

                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {

                    if( myServiceState != ServiceState.NONE
                    &&  myServiceState != ServiceState.DiscoverPeer) {
                        // if we are starting, we can ignore this,
                        startPeerDiscovery();
                    }
                } else {
                    myServiceState = ServiceState.NONE;
                    sendResult("Wifi, is disabled, waiting for enabling event");
                    // TODO: clear sockets & Chat manager here
                }
            }else if(WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                if(myServiceState == ServiceState.DiscoverPeer) {
                    //lets ignore this in all other states
                    p2p.requestPeers(channel, peerListListener);
                }
            }else if(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION.equals(action)) {

                int state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED);

                String persTatu = "Discovery state changed to ";

                if(state == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED){
                    persTatu = persTatu + "Stopped.";
                    if(myServiceState == ServiceState.DiscoverPeer){
                        persTatu = persTatu + "and Peer Discovery RE-Started.";
                        startPeerDiscovery();
                    }else if(myServiceState == ServiceState.DiscoverService) {
                        persTatu = persTatu + "and PeereDiscovery RE-Started.";
                        startPeerDiscovery();
                    }
                }else if(state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED){
                    persTatu = persTatu + "Started.";
                }else{
                    persTatu = persTatu + "unknown  " + state;
                }
                sendResult(persTatu);
            }else if(WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                WifiP2pDevice device = intent.getParcelableExtra(EXTRA_WIFI_P2P_DEVICE);
                sendResult("Local device: " + MyP2PHelper.deviceToString(device));
            }else if (WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                if (networkInfo.isConnected()) {
                    sendResult("PeerReceiver, we are connected NOW !!!");
                    if( myServiceState != ServiceState.NONE
                    &&  myServiceState != ServiceState.DiscoverPeer) {
                        // if we are starting, we can ignore this,
                        myServiceState = ServiceState.QueryConnection;
                        p2p.requestConnectionInfo(channel, that);
                    }
                } else {
                    sendResult("PeerReceiver, we are now DISCONNECTED.");
                    if(  myServiceState != ServiceState.NONE
                     &&  myServiceState != ServiceState.DiscoverPeer) {
                       // if we are starting, we can ignore this,
                        startPeerDiscovery();
                    }
                    // TODO: clear sockets & Chat manager here
                }
            }
        }
    }
}
