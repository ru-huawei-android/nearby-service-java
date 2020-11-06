package com.sample.huawei.nearby;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

public class NewMessageDialogFragment extends DialogFragment {

    private EditText messageTitle;
    private EditText messageContent;
    private CheckBox unpublishCheckbox;

    private final OnConfirmCallback mCallback;

    public NewMessageDialogFragment(@NonNull OnConfirmCallback callback) {
        mCallback = callback;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.new_message_dialog, null);
        messageTitle = view.findViewById(R.id.message_title);
        messageContent = view.findViewById(R.id.message_content);
        unpublishCheckbox = view.findViewById(R.id.unpublishOld);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setView(view)
                .setCancelable(false)
                .setPositiveButton(R.string.dialog_ok, (dialog, which) -> {
                    String title = messageTitle.getText().toString();
                    if (title.length() == 0) {
                        Toast.makeText(getActivity(), R.string.empty_title_error_message, Toast.LENGTH_SHORT)
                                .show();
                        return;
                    }
                    mCallback.onConfirm(title, messageContent.getText().toString(), unpublishCheckbox.isChecked());
                })
                .setNegativeButton(R.string.dialog_cancel, (dialog, which) -> {
                });
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    public interface OnConfirmCallback {
        void onConfirm(String title, String content, boolean unpublishOld);
    }
}
