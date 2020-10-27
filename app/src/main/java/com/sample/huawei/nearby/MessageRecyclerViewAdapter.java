package com.sample.huawei.nearby;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;


public class MessageRecyclerViewAdapter extends RecyclerView.Adapter<MessageRecyclerViewAdapter.ItemListRecyclerViewHolder>
        implements View.OnClickListener {
    private List<String> itemList;
    private OnItemClickListener clickListener;

    public MessageRecyclerViewAdapter(OnItemClickListener listener) {
        itemList = new ArrayList<>();
        clickListener = listener;
    }

    @NonNull
    @Override
    public ItemListRecyclerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_item, parent, false);
        return new ItemListRecyclerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemListRecyclerViewHolder holder, int position) {
        if (itemList == null || itemList.isEmpty() || itemList.size() <= position) {
            return;
        }
        String name = itemList.get(position);
        if (name == null) {
            return;
        }
        holder.nameTextView.setText(name);
        holder.nameTextView.setOnClickListener(this);
    }

    @Override
    public int getItemCount() {
        return itemList.size();
    }

    @Override
    public void onClick(View view) {
        clickListener.onItemClick(((TextView)view).getText().toString());
    }

    public void addName(String name) {
        itemList.add(name);
        notifyDataSetChanged();
    }

    public void removeName(String name) {
        itemList.remove(name);
        notifyDataSetChanged();
    }

    static class ItemListRecyclerViewHolder extends RecyclerView.ViewHolder {

        private TextView nameTextView;

        public ItemListRecyclerViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.name);
        }
    }

    public interface OnItemClickListener {
        void onItemClick(String name);
    }

    public void clearItems() {
        itemList.clear();
        notifyDataSetChanged();
    }

}
