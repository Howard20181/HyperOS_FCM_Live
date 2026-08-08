package io.github.howard20181.hyperos.fcmlive;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class AppListAdapter extends BaseAdapter {

    public interface OnCheckedChangeListener {
        void onCheckedChanged(String packageName, boolean checked);
    }

    public static class AppEntry {
        public final String packageName;
        public final String label;
        public final Drawable icon;
        public boolean checked;

        public AppEntry(String packageName, String label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    private final LayoutInflater inflater;
    private final List<AppEntry> apps;
    private final OnCheckedChangeListener listener;

    public AppListAdapter(Context context, List<AppEntry> apps, OnCheckedChangeListener listener) {
        this.inflater = LayoutInflater.from(context);
        this.apps = apps;
        this.listener = listener;
    }

    @Override
    public int getCount() {
        return apps.size();
    }

    @Override
    public AppEntry getItem(int position) {
        return apps.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_app, parent, false);
            holder = new ViewHolder();
            holder.icon = convertView.findViewById(R.id.app_icon);
            holder.label = convertView.findViewById(R.id.app_label);
            holder.pkg = convertView.findViewById(R.id.app_pkg);
            holder.check = convertView.findViewById(R.id.app_check);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        AppEntry app = getItem(position);
        holder.icon.setImageDrawable(app.icon);
        holder.label.setText(app.label);
        holder.pkg.setText(app.packageName);
        holder.check.setOnCheckedChangeListener(null);
        holder.check.setChecked(app.checked);
        holder.check.setOnCheckedChangeListener((buttonView, isChecked) -> {
            app.checked = isChecked;
            if (listener != null) {
                listener.onCheckedChanged(app.packageName, isChecked);
            }
        });
        return convertView;
    }

    private static class ViewHolder {
        ImageView icon;
        TextView label;
        TextView pkg;
        CheckBox check;
    }
}
