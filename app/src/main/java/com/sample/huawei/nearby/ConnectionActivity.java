package com.sample.huawei.nearby;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.huawei.hmf.tasks.OnFailureListener;
import com.huawei.hmf.tasks.OnSuccessListener;
import com.huawei.hmf.tasks.Task;
import com.huawei.hms.common.ApiException;
import com.huawei.hms.nearby.Nearby;
import com.huawei.hms.nearby.StatusCode;
import com.huawei.hms.nearby.discovery.BroadcastOption;
import com.huawei.hms.nearby.discovery.ConnectCallback;
import com.huawei.hms.nearby.discovery.ConnectInfo;
import com.huawei.hms.nearby.discovery.ConnectResult;
import com.huawei.hms.nearby.discovery.Policy;
import com.huawei.hms.nearby.discovery.ScanEndpointCallback;
import com.huawei.hms.nearby.discovery.ScanEndpointInfo;
import com.huawei.hms.nearby.discovery.ScanOption;
import com.huawei.hms.nearby.transfer.Data;
import com.huawei.hms.nearby.transfer.DataCallback;
import com.huawei.hms.nearby.transfer.TransferStateUpdate;
import com.sample.huawei.nearby.utils.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.sample.huawei.nearby.SearchDialogFragment.*;
import static java.nio.charset.StandardCharsets.UTF_8;

public class ConnectionActivity extends AppCompatActivity {

    private final Policy policy = Policy.POLICY_STAR;
    private final String SERVICE_ID = "com.sample.huawei.nearby";

    private static final int REQUEST_PICKFILE = 1001;
    private static final String FILEDATA_SUFFIX = ":FILEDATA";

    private Boolean isBroadcasting = false;
    private Boolean isConnected = false;
    private String endpointName;
    private String remoteEndpoint;
    private SearchDialogFragment<ScanEndpointInfo> searchDialogFragment;

    private TextView broadCastingItemTextView;
    private TextView status;
    private AlertDialog confirmConnectionDialog;

    private LinearLayout transferInfoContainer;
    private ProgressBar transferProgressBar;
    private TextView transferBlockTitle;
    private TextView transferBlockDetails;
    private long transferFileSize;
    private String transferFileName;
    private Data transferFilePayload;
    private Boolean isSendingFile;

    private AlertDialog waitingForConnectionDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection);

        status = findViewById(R.id.status);
        broadCastingItemTextView = findViewById(R.id.start_broadcast_item);
        searchDialogFragment = new SearchDialogFragment<>(itemHandler, onCloseListener, onSelectListener);
        searchDialogFragment.setDialogTitle(getString(R.string.search_dialog_title));

        setStatus("");

        endpointName = Settings.Secure.getString(getContentResolver(), "bluetooth_name");
        if (endpointName == null || endpointName.isEmpty()) {
            endpointName = "UNKNOWN";
        }

        transferInfoContainer = findViewById(R.id.download_container);
        transferBlockTitle = findViewById(R.id.download_title);
        transferBlockDetails = findViewById(R.id.download_details);
        transferProgressBar = findViewById(R.id.transfer_progress);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        stopAdvertising();
        stopDiscovery();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (!isConnected) return;

        if (requestCode == REQUEST_PICKFILE) {
            if (resultCode == -1) {
                Uri fileUri = data.getData();
                assert fileUri != null;
                try {
                    ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(fileUri, "r");
                    Data fileData = Data.fromFile(pfd);

                    transferFileName = FileUtils.extractFilename(getContentResolver(), fileUri);
                    transferFileSize = pfd.getStatSize();

                    String fileInfo = transferFileName + ":" + transferFileSize + FILEDATA_SUFFIX;
                    Data fileInfoData = Data.fromBytes(fileInfo.getBytes(StandardCharsets.UTF_8));
                    isSendingFile = true;

                    Nearby.getTransferEngine(getApplicationContext())
                            .sendData(remoteEndpoint, fileInfoData).addOnSuccessListener(
                                    aVoid -> {
                                        Nearby.getTransferEngine(getApplicationContext())
                                                .sendData(remoteEndpoint, fileData)
                                                .addOnFailureListener((ex -> {
                                                    Toast.makeText(this, "sendData onFailure: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                                                }))
                                                .addOnCompleteListener(this, (endpointName) -> {
                                                    //Toast.makeText(this, "sendData onComplete - endpoint: " + endpointName, Toast.LENGTH_LONG).show();
                                                })
                                                .addOnSuccessListener(this, endpointName -> {
                                                    //Toast.makeText(this, "sendData onSuccess - endpoint: " + endpointName, Toast.LENGTH_LONG).show();
                                                });
                                        showTransferInfo();
                                    }
                    );

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    private final ItemHandler itemHandler = new ItemHandler() {
        @Override
        public String getItemTitle(Object item) {
            return ((ScanEndpointInfo)item).getName();
        }

        @Override
        public String getItemStringContent(Object item) {
            ScanEndpointInfo info = (ScanEndpointInfo)item;
            return info.getServiceId() + "//" + info.getName();
        }
    };

    private final OnCloseListener onCloseListener = new OnCloseListener() {
        @Override
        public void onClose() {
            searchDialogFragment.clearItems();
            stopDiscovery();
        }
    };

    private final OnSelectListener onSelectListener = new OnSelectListener() {
        @Override
        public void OnItemSelected(Object item) {
            Map.Entry<String, ScanEndpointInfo> mapEntry = (Map.Entry<String, ScanEndpointInfo>)item;
            ScanEndpointInfo info = mapEntry.getValue();
            if (info != null) {
                doStartConnection(mapEntry.getKey(), info.getName());

                AlertDialog.Builder builder = new AlertDialog.Builder(ConnectionActivity.this);
                builder
                        .setTitle("Waiting for connection")
                        .setNegativeButton(
                                "Cancel",
                                (dialog, which) -> {
                                    Nearby.getDiscoveryEngine(ConnectionActivity.this).disconnect(mapEntry.getKey());
                                    dialog.dismiss();
                                });
                waitingForConnectionDialog = builder.create();
                waitingForConnectionDialog.show();
            }
        }
    };


    public void startBroadcastingItemClick(View view) {
        if (!isBroadcasting) {
            startAdvertising();
        } else {
            stopAdvertising();
        }
    }

    public void startScanItemClick(View view) {
        startDiscovery();
    }

    private final ConnectCallback connectionCallback = new ConnectCallback() {

        @Override
        public void onEstablish(String endpointId, ConnectInfo connectInfo) {
            if (waitingForConnectionDialog != null) waitingForConnectionDialog.dismiss();
            AlertDialog.Builder builder = new AlertDialog.Builder(ConnectionActivity.this);
            builder
                    .setTitle(connectInfo.getEndpointName() + " request connection")
                    .setMessage("Please confirm the match code is: " + connectInfo.getAuthCode())
                    .setPositiveButton(
                            "Accept",
                            (dialog,  which) ->
                            {
                               Nearby.getDiscoveryEngine(ConnectionActivity.this).acceptConnect(endpointId, new ReceiveDataListener());
                            })

                    .setNegativeButton(
                            "Reject",
                            (dialog, which) -> {
                                Nearby.getDiscoveryEngine(ConnectionActivity.this).rejectConnect(endpointId);
                                dialog.dismiss();
                            })
                    .setIcon(android.R.drawable.ic_dialog_alert);
            confirmConnectionDialog = builder.create();
            confirmConnectionDialog.setCanceledOnTouchOutside(false);
            confirmConnectionDialog.show();
        }

        @Override
        public void onResult(String endpointId, ConnectResult connectResult) {
            stopAdvertising();
            switch (connectResult.getStatus().getStatusCode()) {
                case StatusCode.STATUS_SUCCESS:
                    setConnectedStatus(true, endpointId);
                    break;
                case StatusCode.STATUS_CONNECT_REJECTED:
                    setConnectedStatus(false, null);
                    hideConfirmationDialog();
                    break;
                default:
                    /* other unknown status code */
            }
        }

        @Override
        public void onDisconnected(String s) {
            if (waitingForConnectionDialog != null) waitingForConnectionDialog.dismiss();
            setConnectedStatus(false, null);
            Toast.makeText(ConnectionActivity.this, "Disconnected", Toast.LENGTH_LONG).show();
        }
    };

    public void disconnectItemClick(View view) {
        Nearby.getDiscoveryEngine(this).disconnectAll();
        setConnectedStatus(false, null);
    }

    public void sendFileItemClick(View view) {
        openFileDialog();
    }

    class ReceiveDataListener extends DataCallback {
        @Override
        public void onReceived(String endpointId, Data data) {
            /* BYTES data is sent as a single block, so we can get complete data. */
            if (data.getType() == Data.Type.BYTES) {
                String str = new String(data.asBytes(), UTF_8);
                if (str.endsWith(FILEDATA_SUFFIX)) {
                    String[] chunks = str.split(":");
                    if (chunks.length == 3) {
                        transferFileName = chunks[0];
                        transferFileSize = Long.parseLong(chunks[1]);
                        showTransferInfo();
                    }
                }
            }
            if (data.getType() == Data.Type.FILE) {
                transferFilePayload = data;
                showTransferInfo();
            }
        }

        @Override
        public void onTransferUpdate(String endpointId, TransferStateUpdate transferStateUpdate) {

            if (transferStateUpdate.getStatus() == TransferStateUpdate.Status.TRANSFER_STATE_IN_PROGRESS) {
                updateTransferInfo(transferStateUpdate.getBytesTransferred());
            } else {
                if (transferStateUpdate.getStatus() == TransferStateUpdate.Status.TRANSFER_STATE_SUCCESS) {
                    // Move and rename received file only if we receiving data
                    if (transferFilePayload != null && !isSendingFile) {
                        File payloadFile = transferFilePayload.asFile().asJavaFile();
                        File targetFileName = new File(Environment.getExternalStorageDirectory(), transferFileName);
                        boolean result = payloadFile.renameTo(targetFileName);
                        if (result) {
                            Toast.makeText(ConnectionActivity.this, ("File transfer complete, check external storage"), Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(ConnectionActivity.this, ("Move file failed"), Toast.LENGTH_LONG).show();
                        }
                    }
                } else { /* cancelled or failed */
                    // Inform user
                }
                // Dismiss transfer progress view
                hideTransferInfo();
                isSendingFile = false;
            }
        }
    }

    private final ScanEndpointCallback scanEndpointCallback = new ScanEndpointCallback() {
                @Override
                public void onFound(String endpointId, ScanEndpointInfo discoveryEndpointInfo) {
                    Toast.makeText(ConnectionActivity.this, "Found: " + discoveryEndpointInfo.getName(), Toast.LENGTH_LONG).show();
                    searchDialogFragment.addItem(endpointId, discoveryEndpointInfo);
                }

                @Override
                public void onLost(String endpointId) {
                    searchDialogFragment.removeItem(endpointId);
                    Toast.makeText(ConnectionActivity.this, "onLost", Toast.LENGTH_LONG).show();
                }
            };

    private void startAdvertising() {
        stopDiscovery();
        BroadcastOption broadcastOption = new BroadcastOption.Builder().setPolicy (policy).build();
        Nearby.getDiscoveryEngine(ConnectionActivity.this)
                .startBroadcasting(endpointName, SERVICE_ID, connectionCallback, broadcastOption)
                .addOnSuccessListener(aVoid -> {
                    setStatus("Advertising");
                    isBroadcasting = true;
                    broadCastingItemTextView.setText(getString(R.string.start_broadcasting_item_text_stop));
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(ConnectionActivity.this, "Advertising Failure", Toast.LENGTH_LONG).show();
                });
    }

    private void startDiscovery() {
        stopAdvertising();
        searchDialogFragment.clearItems();
        searchDialogFragment.show(getSupportFragmentManager(), "Search endpoints");

        ScanOption scanOption = new ScanOption.Builder().setPolicy(policy).build();
        Nearby.getDiscoveryEngine(ConnectionActivity.this)
                .startScan(SERVICE_ID, scanEndpointCallback, scanOption)
                .addOnSuccessListener(aVoid -> {
                    setStatus("Discovering");
                })
                .addOnFailureListener(e -> Toast.makeText(ConnectionActivity.this, "Start Scan Failure", Toast.LENGTH_SHORT).show());
    }

    private void stopAdvertising() {
        setStatus("");
        isBroadcasting = false;
        broadCastingItemTextView.setText(getString(R.string.start_broadcasting_item_text_start));
        Nearby.getDiscoveryEngine(ConnectionActivity.this).stopBroadcasting();
    }

    private void stopDiscovery() {
        setStatus("");
        Nearby.getDiscoveryEngine(ConnectionActivity.this).stopScan();
    }

    public void doStartConnection(String endpointId, String remoteEndpointName) {
        Nearby.getDiscoveryEngine(ConnectionActivity.this)
                .requestConnect(endpointName, endpointId, connectionCallback)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        setStatus(String.format("Connecting to: %s(%s)", endpointId, remoteEndpointName));
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Toast.makeText(ConnectionActivity.this, "requestConnect onFailure ", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setStatus (String status) {
        this.status.setText(String.format("Status: %s", !status.isEmpty() ? status : "Idle"));
    }

    private void setConnectedStatus(Boolean isConnected, String remoteEndpoint) {
        this.isConnected = isConnected;

        int visibility = isConnected ? View.VISIBLE : View.GONE;

        findViewById(R.id.disconnect_item).setVisibility(visibility);
        findViewById(R.id.divider3).setVisibility(visibility);
        findViewById(R.id.send_file_item).setVisibility(visibility);
        findViewById(R.id.divider4).setVisibility(visibility);

        if (isConnected) {
            setStatus(String.format("Connected to: %s", remoteEndpoint));
            this.remoteEndpoint = remoteEndpoint;
        } else {
            setStatus("");
            this.remoteEndpoint = "";
            stopAdvertising();
        }
    }

    private void hideConfirmationDialog() {
        if (confirmConnectionDialog != null) {
            confirmConnectionDialog.dismiss();
        }
    }

    private void showTransferInfo() {
        transferBlockTitle.setText(String.format("Transferring %s", transferFileName));
        transferProgressBar.setProgress(0);
        transferProgressBar.setMax(100);
        transferInfoContainer.setVisibility(View.VISIBLE);
    }

    private void updateTransferInfo(long current) {
        int percent = transferFileSize != 0
                ? (int)(((float)current / (float)transferFileSize) * 100)
                : 0;
        transferProgressBar.setProgress(percent);
        transferBlockDetails.setText(String.format(getString(R.string.transfer_details_text), current, transferFileSize));
    }

    private void hideTransferInfo() {
        transferInfoContainer.setVisibility(View.GONE);
    }

    private void openFileDialog() {
        Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
        chooseFile.setType("*/*");
        chooseFile = Intent.createChooser(chooseFile, "Choose a file");
        startActivityForResult(chooseFile, REQUEST_PICKFILE);
    }

}