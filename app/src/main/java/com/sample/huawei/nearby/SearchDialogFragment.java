package com.sample.huawei.nearby;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class SearchDialogFragment<T> extends DialogFragment implements MessageRecyclerViewAdapter.OnItemClickListener {

    private final MessageRecyclerViewAdapter adapter = new MessageRecyclerViewAdapter(this);;
    private final HashMap<String, T> items = new HashMap<>();
    private final OnCloseListener onCloseListener;
    private final ItemHandler itemHandler;
    private final OnSelectListener onSelectListener;
    private String dialogTitle;

    public SearchDialogFragment(@NonNull ItemHandler itemHandler, OnCloseListener onCloseListener, OnSelectListener onSelectListener) {
        this.itemHandler = itemHandler;
        this.onCloseListener = onCloseListener;
        this.onSelectListener = onSelectListener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.discover_dialog, null);
        TextView titleTextView = view.findViewById(R.id.dialog_title);
        titleTextView.setText(dialogTitle);

        RecyclerView itemsView = view.findViewById(R.id.recycler_view);
        itemsView.setLayoutManager(new LinearLayoutManager(getActivity()));
        itemsView.setAdapter(adapter);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setView(view)
                .setCancelable(false)
                .setNegativeButton("Cancel", (dialog, which) -> {
                    if (onCloseListener != null) {
                        onCloseListener.onClose();
                    }
                });
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    @Override
    public void onItemClick(String name) {
        Optional<Map.Entry<String, T>> result = items
                .entrySet()
                .stream()
                .filter(x -> itemHandler.getItemTitle(x.getValue()).equals(name)).findFirst();

        if (result.isPresent()) {
            Map.Entry<String, T> item = result.get();
            onSelectListener.OnItemSelected(item);
            onCloseListener.onClose();
            this.dismiss();
        }
    }

    public void addItem(String id, T item) {
        String itemTitle = itemHandler.getItemTitle(item);
        if (items.containsKey(itemTitle)) {
            return;
        }
        adapter.addName(itemTitle);
        items.put(id, item);
    }

    public void removeItem(String id) {
        T item = items.get(id);
        if (item != null) {
            String itemTitle = itemHandler.getItemTitle(item);
            adapter.removeName(itemTitle);
        }
        items.remove(id);
    }

    public void clearItems() {
        items.clear();
        adapter.clearItems();
    }

    public void setDialogTitle(String title) {
        dialogTitle = title;
    }

    public interface OnCloseListener {
        void onClose();
    }

    public interface OnSelectListener {
        void OnItemSelected(Object item);
    }

    public interface ItemHandler {
        String getItemTitle(Object item);
        String getItemStringContent(Object item);
    }
}
