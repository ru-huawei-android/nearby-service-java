package com.sample.huawei.nearby;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;


import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sample.huawei.nearby.utils.BluetoothCheckUtil;
import com.sample.huawei.nearby.utils.LocationCheckUtil;
import com.sample.huawei.nearby.utils.NetCheckUtil;
import com.sample.huawei.nearby.utils.PermissionUtil;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ArrayList<MainMenuItem> dataSet = new ArrayList<>();
        dataSet.add(new MainMenuItem(getString(R.string.mesaage_engine_item), MessageEngineActivity.class));
        dataSet.add(new MainMenuItem(getString(R.string.nearby_connection_item), ConnectionActivity.class));

        RecyclerView recyclerView = findViewById(R.id.rv);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        recyclerView.setAdapter(new RvAdapter(dataSet, this));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        checkPermission();
    }

    private void checkPermission() {
        if (!BluetoothCheckUtil.isBlueEnabled()) {
            showWarnDialog(getString(R.string.bt_unavailable_message));
            return;
        }

        if (!LocationCheckUtil.isLocationEnabled(this)) {
            showWarnDialog(getString(R.string.location_unavailable_message));
            return;
        }

        if (!NetCheckUtil.isNetworkAvailable(this)) {
            showWarnDialog(getString(R.string.internet_unavailable_message));
            return;
        }

        String[] deniedPermission = PermissionUtil.getDeniedPermissions(this, new String[] {
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
        });
        if (deniedPermission.length > 0) {
            PermissionUtil.requestPermissions(this, deniedPermission, 10);
        }
    }

    private void showWarnDialog(String content) {
        DialogInterface.OnClickListener onClickListener = (dialog, which) -> android.os.Process.killProcess(android.os.Process.myPid());
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.warn);
        builder.setIcon(R.drawable.ic_warn);
        builder.setMessage(content);
        builder.setNegativeButton(R.string.confirm, onClickListener);
        builder.show();
    }

}

