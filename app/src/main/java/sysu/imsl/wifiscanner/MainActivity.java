package sysu.imsl.wifiscanner;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.net.wifi.WifiManager;
import android.net.wifi.ScanResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private WifiManager wifiManager;
    private FileUtil fileUtil;
    private Handler handler;

    private EditText edt_x;
    private EditText edt_y;
    private EditText edt_scan_times;
    private EditText edt_scan_interval;
    private TextView tv_hint;
    private Button btn_scan;
    private Switch switch_filter;

    private ProgressDialog dialog;
    private WifiScanReceiver wifiScanReceiver = new WifiScanReceiver() ;
    private IntentFilter intentFilter = new IntentFilter();

    private List<String> LOC_AP_MACS;
    private final String AP_MAC_FILE = "AP_MAC_FOR_LOC.txt";
    private static int SCAN_TIMES = 20;
    private static int SCAN_INTERVAL = 5;
    private boolean MAC_FILTER = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();

        handler = new Handler(){
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                tv_hint.setText("Scan Result: " + msg.obj.toString());
            }
        };

        switch_filter.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(buttonView.isChecked()){
                    MAC_FILTER = true;
                }
                else{
                    MAC_FILTER = false;
                }
            }
        });

        btn_scan.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                SCAN_TIMES = Integer.valueOf(edt_scan_times.getText().toString());
                SCAN_INTERVAL = Integer.valueOf(edt_scan_interval.getText().toString());
                if (SCAN_TIMES < 1 || SCAN_TIMES > 100) {
                    Toast.makeText(MainActivity.this, "扫描次数范围为（0~100）,默认为6", Toast.LENGTH_SHORT);
                    SCAN_TIMES = 6;
                }

                if (MAC_FILTER) {
                    if (LOC_AP_MACS.size() == 0 || LOC_AP_MACS == null) {
                        Toast.makeText(MainActivity.this, "MAC配置文件为空！", Toast.LENGTH_SHORT);
                        tv_hint.setText("Scan MAC File is EMPTY!");
                        return;
                    }

                    tv_hint.setText("Wi-Fi scan is processing...");
                    int[][] RSSIs = new int[SCAN_TIMES][LOC_AP_MACS.size()];
                    boolean[] scanState = new boolean[SCAN_TIMES];

                    dialog = ProgressDialog.show(MainActivity.this, "请稍后", "Wi-Fi信号扫描中。。。");
                    new Thread() {
                        @Override
                        public void run() {
                            super.run();
                            Looper.prepare();
                            for (int count = 0; count < SCAN_TIMES; count++) {
                                registerReceiver(wifiScanReceiver, intentFilter);
                                boolean success = wifiManager.startScan();

    //                            if(count == -1){
    //                                SystemClock.sleep(3000);
    //                                unregisterReceiver(wifiScanReceiver);
    //                                continue;
    //                            }

                                SystemClock.sleep(SCAN_INTERVAL * 1000);
                                RSSIs[count] = scanSuccess();
                                if (success) {
                                    Log.d("scan res", "scan success: " + intArrToString(scanSuccess()));
                                    scanState[count] = true;
                                } else {
                                    Log.d("scan res", "scan failed: " + intArrToString(scanSuccess()));
                                    scanState[count] = false;
                                }
                                unregisterReceiver(wifiScanReceiver);
                            }
                            dialog.dismiss();
                            Message message = handler.obtainMessage();
                            message.what = 0;
                            message.obj = saveScanResults(RSSIs, scanState);
                            handler.sendMessage(message);
                            Looper.loop();
                        }
                    }.start();
                }
                else{
                    dialog = ProgressDialog.show(MainActivity.this, "请稍后", "Wi-Fi信号扫描中。。。");
                    new Thread() {
                        @Override
                        public void run() {
                            super.run();
                            Looper.prepare();
                            for (int count = 0; count < SCAN_TIMES; count++) {
                                registerReceiver(wifiScanReceiver, intentFilter);
                                boolean success = wifiManager.startScan();

                                SystemClock.sleep(SCAN_INTERVAL * 1000);
                                if (success) {
                                    saveAllScanRes();
                                    Log.d("scan res", "scan success");
                                }
                                else{
                                    Log.d("scan res", "scan failed");
                                }
                                unregisterReceiver(wifiScanReceiver);
                            }
                            dialog.dismiss();
                            Looper.loop();
                        }
                    }.start();
                }
             }
        });
    }

    private void init(){
        btn_scan = findViewById(R.id.bt_scan);
        edt_x = findViewById(R.id.edt_coordinateX);
        edt_y = findViewById(R.id.edt_coordinateY);
        edt_scan_times = findViewById(R.id.edt_scan_time);
        edt_scan_interval = findViewById(R.id.edt_scan_interval);
        tv_hint = findViewById(R.id.tv_hint);
        switch_filter = findViewById(R.id.switch_filter);

        getPermission();

        fileUtil = new FileUtil();
        wifiManager = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
//        registerReceiver(wifiScanReceiver, intentFilter);

        // read MAC info from file
        LOC_AP_MACS = fileUtil.readTxt(AP_MAC_FILE);
        // read MAC info from internal java class
        //LOC_AP_MACS = LOC_MACS.MACs;
    }

    private void getPermission(){
        String[] permissions = new String[]{
                Manifest.permission.CALL_PHONE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CHANGE_WIFI_STATE
        };
        List<String> mPermissionList = new ArrayList<>();

        for (int idx = 0; idx < permissions.length; idx++) {
            if (ContextCompat.checkSelfPermission(getApplicationContext(), permissions[idx]) != PackageManager.PERMISSION_GRANTED) {
                mPermissionList.add(permissions[idx]);
            }
        }

        if (mPermissionList.isEmpty()) {//未授予的权限为空，表示都授予了
            Log.d("Permissions","All Granted");
        } else {//请求权限方法
            //将List转为数组
            String[] permissionsNeed = mPermissionList.toArray(new String[mPermissionList.size()]);
            ActivityCompat.requestPermissions(MainActivity.this, permissionsNeed, 1);
        }
    }

    private class WifiScanReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean success = intent.getBooleanExtra(
                    WifiManager.EXTRA_RESULTS_UPDATED, false);
            if (success) {
                //save scan info
//                Log.d("scan res", "scan success: " + intArrToString(scanSuccess()));
//                tv_hint.setText("Wi-Fi Scan <COMPLETE>, Press button to start another scanning");
                //scanSuccess();
            }
            else {
                // scan failure handling
//                Log.d("scan res", "scan failed: " + intArrToString(scanSuccess()));
//                tv_hint.setText("Wi-Fi Scan <FAILED>, Press button to start another scanning");
//                scanFailure();
            }
        }
    }

    public static String intArrToString(int[] arr){
        String str = "";
        for(int i = 0 ; i < arr.length ; i++){
            str += String.valueOf(arr[i]);
        }
            return str;
    }

    private int[] scanSuccess(){
        List<ScanResult> scanResultList = wifiManager.getScanResults();

        // Initial RSSI array
        int[] RSSI = new int[LOC_AP_MACS.size()];
        for(int i= 0 ; i < RSSI.length; i++) {
            RSSI[i] = -100;
        }

        for(ScanResult sc : scanResultList){
            int idx = LOC_AP_MACS.indexOf(sc.BSSID);
            if(idx == -1)
                continue;
            RSSI[idx] = sc.level;
            //scan_data = scan_data + "" + pos + "," + sc.SSID + "," + sc.BSSID + "," + sc.level
            //        + "," + sc.frequency + "," + sc.timestamp + "\n";
        }
        return RSSI;
    }

    private void scanFailure(){
        //unregisterReceiver(wifiScanReceiver);
//        Toast.makeText(MainActivity.this, "Wi-Fi扫描失败", Toast.LENGTH_SHORT).show();
    }

    private String saveScanResults(int[][] RSSIs, boolean[] state){
        int stateSum = 0;

        for(int i = 0 ; i < state.length ; i++){
            if(!state[i])
                continue;

            int rssi_sum = 0;
            for(int j = 0 ; j < LOC_AP_MACS.size() ; j++){
                rssi_sum += RSSIs[i][j];
            }
            if(rssi_sum == -100 * LOC_AP_MACS.size()){
                state[i] = false;
                continue;
            }

            stateSum++;
        }

//        for(boolean s:state){
//            if(s)
//                stateSum++;
//        }

        String scan_res_str = "";
        if(stateSum == 0){
            scan_res_str += "Wi-Fi扫描失败";
            Toast.makeText(MainActivity.this, "Wi-Fi扫描失败", Toast.LENGTH_SHORT).show();
            return " Scan Failed!";
        }
        else{
            scan_res_str += "Wi-Fi有效扫描" + stateSum + "次";
            Toast.makeText(MainActivity.this, "Wi-Fi有效扫描" + stateSum + "次", Toast.LENGTH_SHORT).show();
        }

        String pos = edt_x.getText().toString() + "," + edt_y.getText().toString();

        for(int i = 0 ; i < RSSIs.length ; i++){
            if(!state[i])
                continue;
            String RSSI_str = pos + ",";
            for(int j = 0 ; j < LOC_AP_MACS.size(); j++){
                RSSI_str += "," + String.valueOf(RSSIs[i][j]);
            }
            fileUtil.saveSensorData("ScanData.csv", RSSI_str + "\n");
        }

        String RSSI_str = pos + ",avg";
        for(int i = 0 ; i < LOC_AP_MACS.size() ; i++){
            int RSSI_sum_tmp = 0;
            for(int j = 0 ; j < RSSIs.length ; j++){
                if(state[j])
                    RSSI_sum_tmp += RSSIs[j][i];
            }

            int RSSI_avg = RSSI_sum_tmp / stateSum;
            RSSI_str += "," + String.valueOf(RSSI_avg);
        }
        Log.d("RSSI vector", RSSI_str);
        boolean success = fileUtil.saveSensorData("ScanData.csv", RSSI_str + "\n\n");
        if(!success){
            Toast.makeText(MainActivity.this, "数据存储失败", Toast.LENGTH_SHORT).show();
        }
        else{
            Toast.makeText(MainActivity.this, "数据存储成功", Toast.LENGTH_SHORT).show();
        }
        return scan_res_str;
    }

    private void saveAllScanRes(){
        List<ScanResult> scanResultList = wifiManager.getScanResults();

        String pos = edt_x.getText().toString() + "," + edt_y.getText().toString();
        String scan_data = "";
        for(ScanResult sc : scanResultList){
            scan_data = scan_data + "" + pos + "," + sc.SSID + "," + sc.BSSID + "," + sc.level
                    + "," + sc.frequency + "," + sc.timestamp + "\n";
        }
        boolean success = fileUtil.saveSensorData("ScanAllData.csv", scan_data + "\n");
        if(!success){
            Toast.makeText(MainActivity.this, "数据存储失败", Toast.LENGTH_SHORT).show();
        }
        else{
            Toast.makeText(MainActivity.this, "数据存储成功", Toast.LENGTH_SHORT).show();
        }
    }
    public void Xsub(View view){
        EditText ev = (EditText) findViewById(R.id.edt_coordinateX);
        String s = ev.getText().toString();
        if(s.length()>0){
            int num = Integer.parseInt(s);
            num--;
            String temp = num+"";
            ev.setText(temp);
        }
    }
    public void Xadd(View view){
        EditText ev = (EditText) findViewById(R.id.edt_coordinateX);
        String s = ev.getText().toString();
        if(s.length()>0){
            int num = Integer.parseInt(s);
            num++;
            String temp = num+"";
            ev.setText(temp);
        }
    }
    public void Ysub(View view){
        EditText ev = (EditText) findViewById(R.id.edt_coordinateY);
        String s = ev.getText().toString();
        if(s.length()>0){
            int num = Integer.parseInt(s);
            num--;
            String temp = num+"";
            ev.setText(temp);
        }
    }
    public void Yadd(View view){
        EditText ev = (EditText) findViewById(R.id.edt_coordinateY);
        String s = ev.getText().toString();
        if(s.length()>0){
            int num = Integer.parseInt(s);
            num++;
            String temp = num+"";
            ev.setText(temp);
        }
    }
}