package io.github.howard20181.hyperos.fcmlive;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Settings screen: lets the user pick which apps FCM is allowed to wake /
 * auto-launch (manual whitelist). Apps not checked are never woken.
 */
public class MainActivity extends Activity implements SearchView.OnQueryTextListener {

    private final List<AppListAdapter.AppEntry> allApps = new ArrayList<>();
    private final List<AppListAdapter.AppEntry> filteredApps = new ArrayList<>();
    private Set<String> allowlist = new HashSet<>();
    private AppListAdapter adapter;
    private SearchView searchView;
    // Don't show system apps by default; toggle to include them.
    private boolean showSystemApps = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle(R.string.settings_title);

        allowlist = Prefs.readAllowlist(this);

        adapter = new AppListAdapter(this, filteredApps, (pkg, checked) -> {
            if (checked) {
                allowlist.add(pkg);
            } else {
                allowlist.remove(pkg);
            }
            Prefs.writeAllowlist(this, allowlist);
            // Re-sort so the just-toggled app moves to/from the top.
            for (AppListAdapter.AppEntry app : allApps) {
                if (app.packageName.equals(pkg)) {
                    app.checked = checked;
                    break;
                }
            }
            sortApps();
            filterApps(searchView.getQuery().toString());
        });
        ((ListView) findViewById(R.id.app_list)).setAdapter(adapter);

        searchView = findViewById(R.id.search_view);
        searchView.setOnQueryTextListener(this);

        CheckBox cbSystemApps = findViewById(R.id.cb_system_apps);
        cbSystemApps.setChecked(showSystemApps);
        cbSystemApps.setOnCheckedChangeListener((buttonView, isChecked) -> {
            showSystemApps = isChecked;
            // loadApps runs off the main thread and refreshes the list on completion.
            loadApps();
        });

        Button selectAll = findViewById(R.id.btn_select_all);
        Button clearAll = findViewById(R.id.btn_clear_all);
        selectAll.setOnClickListener(v -> setAllChecked(true));
        clearAll.setOnClickListener(v -> setAllChecked(false));

        loadApps();
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        filterApps(newText);
        return true;
    }

    private void filterApps(String query) {
        filteredApps.clear();
        if (TextUtils.isEmpty(query)) {
            filteredApps.addAll(allApps);
        } else {
            String lower = query.toLowerCase();
            for (AppListAdapter.AppEntry app : allApps) {
                if (app.label.toLowerCase().contains(lower)
                        || app.packageName.toLowerCase().contains(lower)) {
                    filteredApps.add(app);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void setAllChecked(boolean checked) {
        allowlist = new HashSet<>();
        // Apply to all apps, not just filtered ones
        for (AppListAdapter.AppEntry app : allApps) {
            app.checked = checked;
            if (checked) {
                allowlist.add(app.packageName);
            }
        }
        Prefs.writeAllowlist(this, allowlist);
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void toggleSystemApps() {
        showSystemApps = !showSystemApps;
        loadApps();
        filterApps(searchView != null ? searchView.getQuery().toString() : "");
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void sortApps() {
        // Checked (allowlisted) apps first, then alphabetically by label,
        // with package name as tiebreak.
        allApps.sort((a, b) -> {
            if (a.checked != b.checked) {
                return a.checked ? -1 : 1;
            }
            int c = a.label.compareToIgnoreCase(b.label);
            return c != 0 ? c : a.packageName.compareTo(b.packageName);
        });
    }

    private boolean isSystemApp(ApplicationInfo ai) {
        // System app: either installed in /system or updated system app
        return (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
    }

    private void loadApps() {
        // Query the package manager and load labels off the main thread so the
        // first open of the screen stays responsive. Icons are loaded lazily by
        // the adapter, so only the lightweight query/label work happens here.
        final boolean showSys = showSystemApps;
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            java.util.List<android.content.pm.PackageInfo> installed =
                    pm.getInstalledPackages(0);
            java.util.List<AppListAdapter.AppEntry> result = new ArrayList<>();
            for (android.content.pm.PackageInfo pi : installed) {
                ApplicationInfo ai = pi.applicationInfo;
                // Skip the module's own package (it's never FCM-targeted by GMS).
                if (ai.packageName.equals(getPackageName())) {
                    continue;
                }
                // By default, only show user apps. Toggle to show system apps.
                if (!showSys && isSystemApp(ai)) {
                    continue;
                }
                result.add(new AppListAdapter.AppEntry(
                        ai.packageName, ai.loadLabel(pm).toString()));
            }
            for (AppListAdapter.AppEntry app : result) {
                app.checked = allowlist.contains(app.packageName);
            }
            // Checked (allowlisted) apps first, then label alphabetically.
            result.sort((a, b) -> {
                if (a.checked != b.checked) {
                    return a.checked ? -1 : 1;
                }
                int c = a.label.compareToIgnoreCase(b.label);
                return c != 0 ? c : a.packageName.compareTo(b.packageName);
            });
            runOnUiThread(() -> {
                allApps.clear();
                allApps.addAll(result);
                filterApps(searchView != null ? searchView.getQuery().toString() : "");
                adapter.notifyDataSetChanged();
            });
        }).start();
    }
}
