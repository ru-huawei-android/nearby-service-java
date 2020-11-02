package com.sample.huawei.nearby;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.huawei.hmf.tasks.OnFailureListener;
import com.huawei.hmf.tasks.OnSuccessListener;
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

public class ConnectionActivity extends AppCompatActivity implements View.OnClickListener {

    private final Policy policy = Policy.POLICY_STAR;
    private final String SERVICE_ID = "com.sample.huawei.nearby";

    private static final int REQUEST_PICKFILE = 1001;
    private static final String FILEDATA_SUFFIX = ":FILEDATA";

    private boolean isBroadcasting;
    private boolean isConnected;
    private String endpointName;
    private String remoteEndpoint;
    private SearchDialogFragment<ScanEndpointInfo> searchDialogFragment;

    private TextView broadCastingItemTextView;
    private TextView status;
    private TextView sendingBytesTextView;
    private AlertDialog confirmConnectionDialog;

    private LinearLayout transferInfoContainer;
    private ProgressBar transferProgressBar;
    private TextView transferBlockTitle;
    private TextView transferBlockDetails;
    private long transferFileSize;
    private String transferFileName;
    private Data transferFilePayload;
    private boolean isSendingFile;

    private LinearLayout bytesInfoContainer;
    private SeekBar bytesSeekBar;
    private boolean isSendingBytes;

    private AlertDialog waitingForConnectionDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection);

        status = findViewById(R.id.status);
        broadCastingItemTextView = findViewById(R.id.start_broadcast_item);
        sendingBytesTextView = findViewById(R.id.send_bytes_item);
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

        bytesInfoContainer = findViewById(R.id.bytes_container);
        bytesSeekBar = findViewById(R.id.bytes_seek_bar);
        bytesSeekBar.setOnSeekBarChangeListener(seekBarChangeListener);
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
                                        }));
                                showTransferInfo();
                            }
                    );

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private final SeekBar.OnSeekBarChangeListener seekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (isConnected && fromUser && isSendingBytes) {
                Data bytesData = Data.fromBytes(new byte[] {(byte)progress});
                Nearby.getTransferEngine(getApplicationContext()).sendData(remoteEndpoint, bytesData);
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    };

    private final ItemHandler itemHandler = new ItemHandler() {
        @Override
        public String getItemTitle(Object item) {
            return ((ScanEndpointInfo) item).getName();
        }

        @Override
        public String getItemStringContent(Object item) {
            ScanEndpointInfo info = (ScanEndpointInfo) item;
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
            Map.Entry<String, ScanEndpointInfo> mapEntry = (Map.Entry<String, ScanEndpointInfo>) item;
            ScanEndpointInfo info = mapEntry.getValue();
            if (info != null) {
                doStartConnection(mapEntry.getKey(), info.getName());

                AlertDialog.Builder builder = new AlertDialog.Builder(ConnectionActivity.this);
                builder
                        .setTitle(R.string.waiting_for_connection)
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


    private final ConnectCallback connectionCallback = new ConnectCallback() {

        @Override
        public void onEstablish(String endpointId, ConnectInfo connectInfo) {
            if (waitingForConnectionDialog != null) waitingForConnectionDialog.dismiss();
            AlertDialog.Builder builder = new AlertDialog.Builder(ConnectionActivity.this);
            builder
                    .setTitle(connectInfo.getEndpointName() + getString(R.string.someone_requested_connection))
                    .setMessage(getString(R.string.confirm_pin_code) + connectInfo.getAuthCode())
                    .setPositiveButton(
                            R.string.connection_accept,
                            (dialog, which) ->
                            {
                                Nearby.getDiscoveryEngine(ConnectionActivity.this).acceptConnect(endpointId, new ReceiveDataListener());
                            })

                    .setNegativeButton(
                            R.string.connection_reject,
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

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.start_broadcast_item:
                toggleBroadcasting();
                break;
            case R.id.start_scan_item:
                startDiscovery();
                break;
            case R.id.disconnect_item:
                disconnectAll();
                break;
            case R.id.send_file_item:
                openFileDialog();
                break;
            case R.id.send_bytes_item:
                toggleSendingBytes();
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + v.getId());
        }
    }

    private void toggleBroadcasting() {
        if (!isBroadcasting) {
            startAdvertising();
        } else {
            stopAdvertising();
        }
    }

    private void toggleSendingBytes() {
        if (isSendingBytes) {
            stopSendingBytes();
        } else {
            startSendingBytes();
        }
    }

    private void disconnectAll() {
        Nearby.getDiscoveryEngine(this).disconnectAll();
        setConnectedStatus(false, null);
    }

    class ReceiveDataListener extends DataCallback {
        @Override
        public void onReceived(String endpointId, Data data) {
            /* BYTES data is sent as a single block, so we can get complete data. */
            if (data.getType() == Data.Type.BYTES) {

                byte[] receivedBytes = data.asBytes();

                if (receivedBytes.length == 1) {
                    if (receivedBytes[0] == -1) {
                        stopSendingBytes();
                        return;
                    }
                    if (!isSendingBytes) {
                        startSendingBytes();
                    }

                    updateBytesInfo(receivedBytes[0]);
                } else {
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
                            Toast.makeText(ConnectionActivity.this, (getString(R.string.transfer_complete)), Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(ConnectionActivity.this, (getString(R.string.transfer_failed)), Toast.LENGTH_LONG).show();
                        }
                    }
                } else { /* cancelled or failed */
                    Toast.makeText(ConnectionActivity.this, (getString(R.string.transfer_interrupted)), Toast.LENGTH_LONG).show();
                }
                hideTransferInfo();
                isSendingFile = false;
            }
        }
    }

    private final ScanEndpointCallback scanEndpointCallback = new ScanEndpointCallback() {
        @Override
        public void onFound(String endpointId, ScanEndpointInfo discoveryEndpointInfo) {
            //Toast.makeText(ConnectionActivity.this, "Found: " + discoveryEndpointInfo.getName(), Toast.LENGTH_LONG).show();
            searchDialogFragment.addItem(endpointId, discoveryEndpointInfo);
        }

        @Override
        public void onLost(String endpointId) {
            //Toast.makeText(ConnectionActivity.this, "onLost", Toast.LENGTH_LONG).show();
            searchDialogFragment.removeItem(endpointId);
        }
    };

    private void startAdvertising() {
        stopDiscovery();
        BroadcastOption broadcastOption = new BroadcastOption.Builder().setPolicy(policy).build();
        Nearby.getDiscoveryEngine(ConnectionActivity.this)
                .startBroadcasting(endpointName, SERVICE_ID, connectionCallback, broadcastOption)
                .addOnSuccessListener(aVoid -> {
                    setStatus(getString(R.string.status_advertising));
                    isBroadcasting = true;
                    broadCastingItemTextView.setText(getString(R.string.start_broadcasting_item_text_stop));
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(ConnectionActivity.this, R.string.advertising_failure, Toast.LENGTH_LONG).show();
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
                    setStatus(getString(R.string.status_discovering));
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
                        setStatus(String.format(getString(R.string.status_connecting_to), endpointId, remoteEndpointName));
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Toast.makeText(ConnectionActivity.this, "requestConnect onFailure ", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setStatus(String status) {
        this.status.setText(String.format(getString(R.string.status_general), !status.isEmpty() ? status : getString(R.string.status_idle)));
    }

    private void setConnectedStatus(boolean isConnected, String remoteEndpoint) {
        this.isConnected = isConnected;

        int visibility = isConnected ? View.VISIBLE : View.GONE;

        findViewById(R.id.disconnect_item).setVisibility(visibility);
        findViewById(R.id.divider3).setVisibility(visibility);
        findViewById(R.id.send_file_item).setVisibility(visibility);
        findViewById(R.id.divider4).setVisibility(visibility);
        findViewById(R.id.send_bytes_item).setVisibility(visibility);
        findViewById(R.id.divider5).setVisibility(visibility);

        if (isConnected) {
            setStatus(String.format(getString(R.string.status_connected), remoteEndpoint));
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
        transferBlockTitle.setText(String.format(getString(R.string.transferring_bytes_string), transferFileName));
        transferProgressBar.setProgress(0);
        transferProgressBar.setMax(100);
        transferInfoContainer.setVisibility(View.VISIBLE);
    }

    private void updateTransferInfo(long current) {
        int percent = transferFileSize != 0
                ? (int) (((float) current / (float) transferFileSize) * 100)
                : 0;
        transferProgressBar.setProgress(percent);
        transferBlockDetails.setText(String.format(getString(R.string.transfer_details_text), current, transferFileSize));
    }

    private void hideTransferInfo() {
        transferInfoContainer.setVisibility(View.GONE);
    }

    private void startSendingBytes() {
        sendingBytesTextView.setText(R.string.stop_send_bytes_item_text);
        isSendingBytes = true;
        bytesInfoContainer.setVisibility(View.VISIBLE);
        bytesSeekBar.setMax(128);
        bytesSeekBar.setProgress(64);
    }

    private void stopSendingBytes() {
        if (isSendingBytes) sendByteTransferEnd();
        sendingBytesTextView.setText(R.string.start_send_bytes_item_text);
        isSendingBytes = false;
        bytesInfoContainer.setVisibility(View.GONE);
    }

    private void updateBytesInfo(int progress) {
        bytesSeekBar.setProgress(progress);
    }

    private void sendByteTransferEnd() {
        Nearby.getTransferEngine(getApplicationContext()).sendData(remoteEndpoint, Data.fromBytes(new byte[] {-1}));
    }

    private void openFileDialog() {
        Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
        chooseFile.setType("*/*");
        chooseFile = Intent.createChooser(chooseFile, "Choose a file");
        startActivityForResult(chooseFile, REQUEST_PICKFILE);
    }

}