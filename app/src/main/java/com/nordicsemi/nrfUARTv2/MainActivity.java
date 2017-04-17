
/*
 * Copyright (c) 2015, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.nordicsemi.nrfUARTv2;




import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int UART_PROFILE_READY = 10;
    public static final String TAG = "nRFUART";
    private static final int UART_PROFILE_CONNECTED = 20;
    private static final int UART_PROFILE_DISCONNECTED = 21;
    private static final int STATE_OFF = 10;

    private static byte tx_tick = 0;
    private static byte rx_tick = 0;
    private static int rx_pkt_num = 0;
    private static byte[] rx = new byte[256];
    private static boolean rx_received = false;


    private int mState = UART_PROFILE_DISCONNECTED;
    private int mRxStopFlag = 0;
    private UartService mService = null;
    private BluetoothDevice mDevice = null;
    private BluetoothAdapter mBtAdapter = null;
    private ScrollView scrollViewRecviver;
    private TextView textViewReciver;

    private Button btnSend, btnRxClear, btnStop, btnSave, btnSendClear;
    private EditText edtMessage;
    private Spinner spSendRecord;
    private List<String> listSendRecord;
    private ArrayAdapter<String> adapterSendRecord;
    private MenuItem mMenuTtemConnect;
    private InputMethodManager  manager;
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBtAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        scrollViewRecviver = (ScrollView) findViewById(R.id.textReceiveScroll);
        textViewReciver = (TextView) findViewById(R.id.textReceive);
        btnSend = (Button) findViewById(R.id.buttonSend);
        btnSend.setEnabled(false);
        btnRxClear = (Button) findViewById(R.id.buttonRxClear);
        btnRxClear.setOnClickListener(new buttonListener());
        btnStop = (Button) findViewById(R.id.buttonStop);
        btnStop.setOnClickListener(new buttonListener());
        btnStop.setTextColor(0xFF00FF00);
        btnSave = (Button) findViewById(R.id.buttonSave);
        btnSave.setOnClickListener(new buttonListener());
        btnSendClear = (Button) findViewById(R.id.buttonSendClear);
        btnSendClear.setOnClickListener(new buttonListener());
        edtMessage = (EditText) findViewById(R.id.editTextSend);
        edtMessage.setOnFocusChangeListener(new edtFocusChangeListener());
        edtMessage.setText("log bledump");
        spSendRecord = (Spinner) findViewById(R.id.spinnerSendRecord);
        listSendRecord = new ArrayList<String>();
        adapterSendRecord = new ArrayAdapter<String>(this, R.layout.item, R.id.text, listSendRecord);
        spSendRecord.setAdapter(adapterSendRecord);
        spSendRecord.setPrompt("history");
        service_init();
        recoverSendHistoryRecord();

        spSendRecord.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            public void onItemSelected(AdapterView<?> arg0, View arg1,
                                       int arg2, long arg3) {
                Log.i(TAG, "onItemSelected:" + arg0 + arg1 + arg2 + arg3);
                edtMessage.setText("");
                edtMessage.setText(arg0.getSelectedItem().toString());
            }

            public void onNothingSelected(AdapterView<?> arg0) {
                Log.e(TAG, "onNothingSelected");
            }
        });
        // Handle Send button
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText editText = (EditText) findViewById(R.id.editTextSend);
                String message = editText.getText().toString();
                byte[] value;
                byte[] tx = new byte[18];
                ;
                int max_seq;
                int value_length;
                int tx_length;
                SendRecordUpdate(message);
                try {
                    //send data to service
                    value = strAddPostfix(message).getBytes("UTF-8");
                    data2Display(message + "\r\n");
                    value_length = value.length;
                    if (value_length > 200)
                        value_length = 200;

                    if (value_length > 0) {
                        max_seq = (value_length + 14) / 15; // < send 15bytes per packet
                        for (int i = 1; i <= max_seq; i++) {
                            Arrays.fill(tx, (byte) 0);
                            tx[0] = 0x3a;
                            tx[1] = tx_tick; //tick
                            tx[2] = (byte) ((max_seq << 4) | i);
                            if (i != max_seq) {
                                System.arraycopy(value, (i - 1) * 15, tx, 3, 15);
                                tx_length = 18;
                            } else {
                                System.arraycopy(value, (i - 1) * 15, tx, 3, (value_length - (i - 1) * 15));
                                tx_length = 3 + (value_length - (i - 1) * 15);
                            }

                            //mService.writeRXCharacteristic(tx);
                            byte tx_active[] = new byte[tx_length];
                            System.arraycopy(tx, 0, tx_active, 0, tx_length);
                            mService.writeRXCharacteristic(tx_active);
                            Thread.sleep(120);// 40ms x 2
                        }
                        tx_tick++;
                        Thread.sleep(100);// 40ms x 2
                        //Update the log with time stamp
                        //String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                        //listAdapter.add("["+currentDateTimeString+"] TX: "+ message);
                    }

                    //edtMessage.setText("");
                    //} catch (UnsupportedEncodingException e) {
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            }


        });
        // Set initial UI state

    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (isShouldHideInput(v, ev)) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
            }
            return super.dispatchTouchEvent(ev);
        }
        if (getWindow().superDispatchTouchEvent(ev)) {
            return true;
        }
        return onTouchEvent(ev);
    }
    public  boolean isShouldHideInput(View v, MotionEvent event) {
        if (v != null && (v instanceof EditText)) {
            int[] leftTop = { 0, 0 };
            v.getLocationInWindow(leftTop);
            int left = leftTop[0];
            int top = leftTop[1];
            int bottom = top + v.getHeight();
            int right = left + v.getWidth();
            if (event.getX() > left && event.getX() < right
                    && event.getY() > top && event.getY() < bottom) {
                return false;
            } else {
                return true;
            }
        }
        return false;
    }
    private void saveSendHistoryRecord() {
        int i;
        SharedPreferences mSharePreferences = getSharedPreferences(
                "send_history_record", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = mSharePreferences.edit();

        for (i = 0; i < adapterSendRecord.getCount(); i++) {
            editor.putString("item" + i, adapterSendRecord.getItem(i).toString());
        }
        editor.putInt("count", i);
        editor.commit();
    }

    private void recoverSendHistoryRecord() {
        int count;
        SharedPreferences mySharePerferences = getSharedPreferences(
                "send_history_record", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = mySharePerferences.edit();
        count = mySharePerferences.getInt("count", 0);
        for (int i = 0; i < count; i++) {
            String value = mySharePerferences.getString("item" + i, "");
            if (!value.equals("")) {
                adapterSendRecord.add(value);
            }
        }
    }

    private void SendRecordUpdate(String newItem) {
        for (int i = 0; i < adapterSendRecord.getCount(); i++) {
            if (newItem.equals(adapterSendRecord.getItem(i))) {
                return;
            }
        }
        if (!newItem.equals("")) {
            adapterSendRecord.add(newItem);
        }
        if (adapterSendRecord.getCount() > 5) {
            spSendRecord.setSelection(0);
            adapterSendRecord.remove(spSendRecord.getSelectedItem().toString());
        }

    }

    private String strAddPostfix(String str) {
        String postfix;
        postfix = str.substring(str.length());
        if (!("\n".equals(postfix))) {
            return str + "\r\n";
        }
        return str;
    }

    public class edtFocusChangeListener implements View.OnFocusChangeListener {

        public void onFocusChange(View var1, boolean hasFocus) {
            Log.e(TAG, "onFocusChange");
            switch (var1.getId()) {
                case R.id.editTextSend:
                    break;
            }

        }
    }

    public class buttonListener implements View.OnClickListener {

        @Override
        public void onClick(View view) {
            // TODO Auto-generated method stub
            switch (view.getId()) {
                case R.id.buttonRxClear:
                    textViewReciver.setText("");
                    break;

                case R.id.buttonStop:
                    if (mRxStopFlag == 1) {
                        mRxStopFlag = 0;
                        btnStop.setText("Stop");
                        btnStop.setTextColor(0xFF00FF00);
                    } else {
                        mRxStopFlag = 1;
                        btnStop.setText("Start");
                        btnStop.setTextColor(0xFFFF00FF);
                    }
                    break;
                case R.id.buttonSave:
                    if (textViewReciver.getText().toString().equals("")) break;

                    String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                    //String fileName= currentDateTimeString+"_log";
                    // Log.e(TAG, fileName);
                    String fileName = "log.txt";
                    String filePath = Environment.getExternalStorageDirectory() + File.separator + fileName;
                    String content = textViewReciver.getText().toString();
                    save2File(filePath, content);


                    Log.e(TAG, filePath);
                    Uri fileUri = Uri.fromFile(new File(filePath));

                    //Intent intent=new Intent(Intent.ACTION_SEND);
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    //intent.setType("text/plain");
                    intent.setType("*/*");
                    intent.putExtra(Intent.EXTRA_SUBJECT, currentDateTimeString + "_log");
                    intent.putExtra(Intent.EXTRA_TEXT, content);
                    intent.putExtra(Intent.EXTRA_STREAM, fileUri);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(Intent.createChooser(intent, "Share Log"));

                    break;
                case R.id.buttonSendClear:
                    edtMessage.setText("");
                    break;
                default:
                    break;
            }
        }

    }

    private void save2File(String filePath, String content) {

        try {
            File logFile = new File(filePath);
            if (!logFile.exists()) {
                logFile.createNewFile();
            }
            FileWriter fWriter = new FileWriter(logFile);
            fWriter.write(content);
            fWriter.close();
            Log.e(TAG, "save success");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    //UART service connected/disconnected
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mService = ((UartService.LocalBinder) rawBinder).getService();
            Log.d(TAG, "onServiceConnected mService= " + mService);
            if (!mService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }

        }

        public void onServiceDisconnected(ComponentName classname) {
            ////     mService.disconnect(mDevice);
            mService = null;
        }
    };

    private Handler mHandler = new Handler() {
        @Override

        //Handler events that received from UART service
        public void handleMessage(Message msg) {

        }
    };

    private final BroadcastReceiver UARTStatusChangeReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            final Intent mIntent = intent;
            //*********************//
            if (action.equals(UartService.ACTION_GATT_CONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                        Log.d(TAG, "UART_CONNECT_MSG");
                        mMenuTtemConnect.setTitle("Disconnect");
                        edtMessage.setEnabled(true);
                        btnSend.setEnabled(true);
                        setTitle(mDevice.getName() + " - Ready");

                        //showMessage(mDevice.getName()+ " - ready");
                        mState = UART_PROFILE_CONNECTED;
                    }
                });
            }

            //*********************//
            if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                        Log.d(TAG, "UART_DISCONNECT_MSG");
                        mMenuTtemConnect.setTitle("Connect");
                        edtMessage.setEnabled(false);
                        btnSend.setEnabled(false);
                        setTitle(R.string.app_name);
                        //showMessage("Disconnected to: "+ mDevice.getName());
                        mState = UART_PROFILE_DISCONNECTED;
                        mService.close();
                        //setUiState();

                    }
                });
            }


            //*********************//
            if (action.equals(UartService.ACTION_GATT_SERVICES_DISCOVERED)) {
                try {
                    Thread.sleep(500);// 40ms x 2
                    byte imu_send_suspend_cmd[] = {0x41, 0x30};
                    mService.writeRXCharacteristic(imu_send_suspend_cmd);
                    Thread.sleep(500);// 40ms x 2
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
                mService.enableTXNotification();
            }
            //*********************//
            if (action.equals(UartService.ACTION_DATA_AVAILABLE)) {
                final byte[] value = intent.getByteArrayExtra(UartService.EXTRA_DATA);
                int last_rx_tick = rx_tick & 0xff;
                if (value.length > 5 && value[1] == 0x04) { //&& value[2]  <= 17 && value[2] + 3 == value.length && (value[4] & 0xf) > 0
                    int cur_rx_tick = value[3] & 0xff;
                    int max_seq = (value[4] >> 4) & 0x0f;
                    int cur_seq = (value[4] & 0x0f);
                    if (((cur_seq < max_seq) && value.length == 20) || (cur_seq == max_seq && value.length <= 20)) {
                        if (cur_rx_tick >= last_rx_tick || cur_rx_tick == 0) {
                            if (cur_rx_tick > last_rx_tick || ((0 == cur_rx_tick) && (0 != last_rx_tick))) {
                                Arrays.fill(rx, (byte) 0);
                                rx_pkt_num = 0;
                            }
                            System.arraycopy(value, 5, rx, (cur_seq - 1) * 15, value.length - 5);
                            rx_tick = value[3];
                            rx_pkt_num++;
                            if (rx_pkt_num == max_seq)
                                rx_received = true;
                        } else {
                            Log.i(TAG, "rx - Overdue pkt: cur_rx_tick(" + cur_rx_tick + ") < last_rx_tick(" + last_rx_tick + ")!");
                        }

                    } else {
                        Log.i(TAG, "rx - Packet format error: cur_seq > max_seq or length check fail!");
                    }
                }

                runOnUiThread(new Runnable() {
                    public void run() {

                        try {
                            if (rx_received) {
                                String text = new String(rx, "UTF-8");
                                //String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                                //listAdapter.add("["+currentDateTimeString+"] RX: "+text);
                                data2Display(text);
                                rx_received = false;
                            }
                        } catch (Exception e) {
                            Log.e(TAG, e.toString());
                        }
                    }
                });
            }
            //*********************//
            if (action.equals(UartService.DEVICE_DOES_NOT_SUPPORT_UART)) {
                showMessage("Device doesn't support UART. Disconnecting");
                mService.disconnect();
            }


        }
    };

    private void data2Display(String str) {
        if (mRxStopFlag == 0) {
            StringBuilder sMsg = new StringBuilder();
            sMsg.append(str);
            textViewReciver.append(sMsg);
            scrollViewRecviver.fullScroll(ScrollView.FOCUS_DOWN);
        }
    }

    private void service_init() {
        Intent bindIntent = new Intent(this, UartService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
        return intentFilter;
    }

    @Override
    public void onStart() {
        super.onStart();// ATTENTION: This was auto-generated to implement the App Indexing API.
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");

        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(UARTStatusChangeReceiver);
        } catch (Exception ignore) {
            Log.e(TAG, ignore.toString());
        }
        unbindService(mServiceConnection);
        mService.stopSelf();
        mService = null;

    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();// ATTENTION: This was auto-generated to implement the App Indexing API.

    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (!mBtAdapter.isEnabled()) {
            Log.i(TAG, "onResume - BT not enabled yet");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {

            case REQUEST_SELECT_DEVICE:
                //When the DeviceListActivity return, with the selected device address
                if (resultCode == Activity.RESULT_OK && data != null) {
                    String deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
                    mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);

                    Log.d(TAG, "... onActivityResultdevice.address==" + mDevice + "mserviceValue" + mService);
                    showMessage(mDevice.getName() + " - connecting");
                    mService.connect(deviceAddress);


                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();

                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
                Log.e(TAG, "wrong request code");
                break;
        }
    }

    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onBackPressed() {
        if (mState == UART_PROFILE_CONNECTED) {
            Intent startMain = new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startMain);
            showMessage("nRFUART's running in background.\n             Disconnect to exit");
        } else {
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.popup_title)
                    .setMessage(R.string.popup_message)
                    .setPositiveButton(R.string.popup_yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            saveSendHistoryRecord();
                            finish();
                        }
                    })
                    .setNegativeButton(R.string.popup_no, null)
                    .show();
        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.connect:
                if (!mBtAdapter.isEnabled()) {
                    Log.i(TAG, "onClick - BT not enabled yet");
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                } else {
                    if (item.getTitle().toString().equals("Connect")) {

                        //Connect button pressed, open DeviceListActivity class, with popup windows that scan for devices

                        Intent newIntent = new Intent(MainActivity.this, DeviceListActivity.class);
                        startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
                    } else {
                        //Disconnect button pressed
                        if (mDevice != null) {
                            mService.disconnect();

                        }
                    }
                }
                break;
            case R.id.about:
                break;
            default:
        }
        return true;
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        mMenuTtemConnect = menu.findItem(R.id.connect);
        return true;
    }
}
