package com.example.bluetoothapp.utilities;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class BluetoothFacade {

    private OnBluetoothDeviceScanListener mScanListener;
    private OnBluetoothDevicePairingListener mPairingListener;
    private OnBluetoothClientConnListener mConnListener;
    private OnDeviceFollowedNotificationListener mNotificationListener;
    private OnBluetoothAdapterListener mAdapterListener;

    private Context mContext;

    private BluetoothAdapter mBluetoothAdapter;
    private ArrayList<BluetoothDevice> mBluetoothDevices;

    public static final String BLUETOOTH_PREFS_FILE = "com.example.bluetoothapp.preferences";
    public static final String BLUETOOTH_DEVICE = "bluetooth_device";
    public static final String BLUETOOTH_DEVICE_FOLLOWED = "bluetooth_device_followed";
    public static final String PAIRED_BLUETOOTH_DEVICE = "paired_bluetooth_device";
    public static final String AVAILABLE_BLUETOOTH_DEVICE = "available_bluetooth_device";

    private static final String BLUETOOTH_UTIL_TAG = "bluetooth_util";
    private static final String PAIRING_TAG = "pairing_tag";
    private static final String CONNECTING_TAG = "connecting_tag";
    private static final String DISCOVERING_TAG = "discovering_tag";

    private String mAction;
    public static String mUnpair = "bluetooth_facade_unpair";
    public static String mPair = "bluetooth_facade_pair";
    private static String mConnect = "bluetooth_facade_connect";

    private long mDiscoveryStartTime;
    private static final long mDiscoveryTimeout = 5000;

    public interface OnBluetoothDeviceScanListener {

        void onScanFinishedAndDevicesFound();

        void onScanFinishedAndDevicesNoFound();

        void onScanStarted();

        void onDeviceFound(ArrayList<BluetoothDevice> devices);

    }

    public interface OnBluetoothDevicePairingListener {

        void onPairingStart();

        void onWaitingForAuthorization();

        void onPairedDevice();

        void onUnpairedDevice(String action);

    }

    public interface OnBluetoothClientConnListener {

        void onConnectionStarted();

        void onSuccessfulConnection();

        void onFailedConnection();

    }

    public interface OnDeviceFollowedNotificationListener {

        void onDeviceConnected(String deviceName);

        void onDeviceDisconnected(String deviceName);

    }

    public interface OnBluetoothAdapterListener {

        void onEnable();

        void onDisable();

    }

    public BluetoothFacade(Context context) {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothDevices = new ArrayList<>();
        mContext = context;
        mAction = "";
    }

    public BluetoothFacade() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mAction = "";
    }

    public void setScanListener(OnBluetoothDeviceScanListener scanListener) {
        mScanListener = scanListener;
    }

    public void setPairingListener(OnBluetoothDevicePairingListener pairingListener) {
        mPairingListener = pairingListener;
    }

    public void setConnListener(OnBluetoothClientConnListener connListener) {
        mConnListener = connListener;
    }

    public void setNotificationListener(OnDeviceFollowedNotificationListener notificationListener) {
        mNotificationListener = notificationListener;
    }

    public void setAdapterListener(OnBluetoothAdapterListener adapterListener) {
        mAdapterListener = adapterListener;
    }

    public boolean isSupported() {
        return mBluetoothAdapter != null;
    }

    public boolean isEnabled() {
        return mBluetoothAdapter.isEnabled();
    }

    public void startDiscovery() {
        mBluetoothAdapter.startDiscovery();
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return mBluetoothAdapter;
    }

    public void disable() {
        cancelDiscovery();
        mBluetoothAdapter.disable();
    }

    public void cancelDiscovery() {
        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    public void connectTo(BluetoothDevice device) {
        mAction = mConnect;
        mConnListener.onConnectionStarted();
        device.fetchUuidsWithSdp();
    }

    private void addPairedBluetoothDevices() {
        Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : devices) {
            if (device.getName() != null && isNewDevice(device.getAddress())) {
                mBluetoothDevices.add(device);
            }
        }
    }

    public void manageDeviceDiscovery(Intent intent) {

        String action = intent.getAction();

        if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {

            Log.v(DISCOVERING_TAG, "ACTION_DISCOVERY_STARTED");

            mDiscoveryStartTime = System.currentTimeMillis();

            mBluetoothDevices.clear();
            addPairedBluetoothDevices();

            mScanListener.onScanStarted();

        } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {

            Log.v(DISCOVERING_TAG, "ACTION_DISCOVERY_FINISHED");
            toFinishDiscovery();

        } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {

            Log.v(DISCOVERING_TAG, "ACTION_FOUND");

            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device.getName() != null && isNewDevice(device.getName())) {
                Log.v(BLUETOOTH_UTIL_TAG, "dev: " + device.getName());
                mBluetoothDevices.add(device);
            }

            mScanListener.onDeviceFound(mBluetoothDevices);

            long discoveryEndTime = System.currentTimeMillis();
            if ((discoveryEndTime - mDiscoveryStartTime) >= mDiscoveryTimeout) {
                toFinishDiscovery();
            }

        }

    }

    private boolean isNewDevice(String deviceName) {
        for (int index = 0; index < mBluetoothDevices.size(); index++) {
            if (mBluetoothDevices.get(index).getName().equals(deviceName)) {
                return false;
            }
        }
        return true;
    }

    private void toFinishDiscovery() {
        cancelDiscovery();
        if (mBluetoothDevices.size() != 0) {
            mScanListener.onScanFinishedAndDevicesFound();
        } else {
            mScanListener.onScanFinishedAndDevicesNoFound();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void unpairDevice(BluetoothDevice device) {
        mAction = mUnpair;
        try {
            Method method = device.getClass().getMethod("removeBond", (Class[]) null);
            method.invoke(device, (Object[]) null);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            Log.v(BLUETOOTH_UTIL_TAG, e.getMessage());
        } catch (NoSuchMethodException | InvocationTargetException e) {
            Log.v(BLUETOOTH_UTIL_TAG, e.getMessage());
        }
    }

    public void pairDevice(BluetoothDevice device) {
        mAction = mPair;
        try {
            Method method = device.getClass().getMethod("createBond", (Class[]) null);
            method.invoke(device, (Object[]) null);

            byte[] pin = (byte[]) BluetoothDevice.class.getMethod("convertPinToBytes", String.class).invoke(BluetoothDevice.class, "0000");

            Log.v(PAIRING_TAG, "PIN: " + pin);

            method = device.getClass().getMethod("setPin", byte[].class);
            method.invoke(device, pin);

        } catch (Exception e) {
            Log.v(PAIRING_TAG, "pair: " + e.getMessage());
        }
    }

    public static String deviceType(BluetoothDevice device) {
        return device.getBondState() == BluetoothDevice.BOND_BONDED ? PAIRED_BLUETOOTH_DEVICE :
                AVAILABLE_BLUETOOTH_DEVICE;
    }

    public void manageDevicePairing(Intent intent) {

        String action = intent.getAction();

        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

        Log.v(PAIRING_TAG, "ac: " + action);

        if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {

            switch (device.getBondState()) {
                case BluetoothDevice.BOND_BONDING:
                    mPairingListener.onPairingStart();
                    Log.v(PAIRING_TAG, "BOND_BONDING");
                    break;
                case BluetoothDevice.BOND_BONDED:
                    mPairingListener.onPairedDevice();
                    Log.v(PAIRING_TAG, "BOND_BONDED");
                    mAction = "";
                    break;
                case BluetoothDevice.BOND_NONE:
                    Log.v(PAIRING_TAG, "Ac: " + mAction);
                    mPairingListener.onUnpairedDevice(mAction);
                    Log.v(PAIRING_TAG, "BOND_NONE");
                    mAction = "";
                    break;
                default:
                    break;
            }
        } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action) && (mAction.equals(mPair)
                || mAction.equals(mUnpair))) {
            mPairingListener.onWaitingForAuthorization();
            Log.v(PAIRING_TAG, "ACTION_ACL_CONNECTED");
        }

    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @RequiresApi(api = Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    public void manageDeviceConnection(Intent intent) {

        String action = intent.getAction();
        boolean successfulConnection = false;

        if (BluetoothDevice.ACTION_UUID.equals(action) && mAction.equals(mConnect)) {

            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            ArrayList<UUID> uuidCandidates = getUUIDCandidates(intent);

            for (UUID uuidCandidate : uuidCandidates) {

                Log.v(CONNECTING_TAG, "UUID: " + uuidCandidate);

                BluetoothClientConnection bcc = new BluetoothClientConnection(device,
                        mBluetoothAdapter, uuidCandidate);
                if (bcc.connect()) {
                    Log.v(CONNECTING_TAG, "UUID Conn: " + uuidCandidate);
                    successfulConnection = true;
                    //break;
                }

            }

            /*
                0000111e-0000-1000-8000-00805f9b34fb
                0000110b-0000-1000-8000-00805f9b34fb
                0000110e-0000-1000-8000-00805f9b34fb
                00001101-0000-1000-8000-00805f9b34fb
            /*
                0000110a-0000-1000-8000-00805f9b34fb
                00001105-0000-1000-8000-00805f9b34fb
                00001115-0000-1000-8000-00805f9b34fb
                00001116-0000-1000-8000-00805f9b34fb
                0000112f-0000-1000-8000-00805f9b34fb
                00001112-0000-1000-8000-00805f9b34fb
                0000111f-0000-1000-8000-00805f9b34fb
                0000111f-0000-1000-8000-00805f9b34fb
                00001132-0000-1000-8000-00805f9b34fb
                00000000-0000-1000-8000-00805f9b34fb
                a49eb41e-cb06-495c-9f4f-bb80a90cdf00
                00001101-0000-1000-8000-00805f9b34fb
            */

            /*
                0000110a-0000-1000-8000-00805f9b34fb
                00001105-0000-1000-8000-00805f9b34fb
                00001116-0000-1000-8000-00805f9b34fb
                00001117-0000-1000-8000-00805f9b34fb
                0000112f-0000-1000-8000-00805f9b34fb
                00001112-0000-1000-8000-00805f9b34fb
                0000111f-0000-1000-8000-00805f9b34fb
                00001101-0000-1000-8000-00805f9b34fb
            */

            if (successfulConnection) {
                mConnListener.onSuccessfulConnection();
                mAction = "";
            } else {
                mConnListener.onFailedConnection();
                mAction = "";
            }
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    private ArrayList<UUID> getUUIDCandidates(Intent intent) {

        ArrayList<UUID> uuids = new ArrayList<>();

        Parcelable[] extraUuids = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);

        for (int index = 0; index < extraUuids.length; index++) {
            //if (!extraUuids[index].toString().equals("0000111e-0000-1000-8000-00805f9b34fb")) {
            uuids.add(UUID.fromString(extraUuids[index].toString()));
            //}
        }

        //uuids.add(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));

        return uuids;
    }

    public void manageFollowedDevice(Intent intent) {

        String action = intent.getAction();

        Log.v(BLUETOOTH_UTIL_TAG, "action 2: " + action);

        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(BLUETOOTH_PREFS_FILE,
                Context.MODE_PRIVATE);


        Log.v(BLUETOOTH_UTIL_TAG, "Action: " + action);
        Log.v(BLUETOOTH_UTIL_TAG, "Device: " + device.getName() + ", " + device.getAddress() + ", " + device.getBondState());

        if (sharedPreferences.getString(BLUETOOTH_DEVICE_FOLLOWED, "").equals(device.getAddress())
                && (mAction.equals(""))) {
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                mNotificationListener.onDeviceConnected(device.getName());
                Log.v("Notifications", "ACTION_ACL_CONNECTED");
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                mNotificationListener.onDeviceDisconnected(device.getName());
                Log.v("Notifications", "ACTION_ACL_DISCONNECTED");
            }
        }

    }

    public void manageAdapter(Intent intent) {

        String action = intent.getAction();

        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
            switch (mBluetoothAdapter.getState()) {

                case BluetoothAdapter.STATE_ON:
                    Log.v(BLUETOOTH_UTIL_TAG, "STATE_ON");
                    mAdapterListener.onEnable();
                    break;
                case BluetoothAdapter.STATE_OFF:
                    Log.v(BLUETOOTH_UTIL_TAG, "STATE_OFF");
                    mAdapterListener.onDisable();
                    break;
                case BluetoothAdapter.STATE_TURNING_OFF:
                    Log.v(BLUETOOTH_UTIL_TAG, "STATE_TURNING_OFF");
                    break;
                case BluetoothAdapter.STATE_TURNING_ON:
                    Log.v(BLUETOOTH_UTIL_TAG, "STATE_TURNING_ON");
                    break;
            }
        }

    }




}
