package io.github.howard20181.hyperos.fcmlive;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * FCM wake allowlist, shared between the module's settings UI (app process) and
 * the Xposed hooks (system_server) via libxposed's cross-process remote
 * preferences ({@code XposedInterface.getRemotePreferences}).
 *
 * system_server cannot read the module's private files (SELinux MLS categories)
 * and querying an on-demand provider is unreliable, so we use the framework's
 * own cross-process prefs as the single source of truth. After writing, the app
 * broadcasts {@link #ACTION_ALLOWLIST_CHANGED} so the system_server hook re-reads
 * its in-memory copy.
 */
public final class Prefs {
    public static final String MODULE_PKG = "io.github.howard20181.hyperos.fcmlive";
    /** Remote prefs group shared by the app process and system_server. */
    public static final String GROUP_CONFIG = "config";
    public static final String KEY_ALLOWLIST = "allowlist";
    /** Action the app broadcasts after writing, to refresh system_server. */
    public static final String ACTION_ALLOWLIST_CHANGED = MODULE_PKG + ".ALLOWLIST_CHANGED";

    private Prefs() {
    }

    /** Package names the user allows FCM to wake / auto-launch. */
    public static Set<String> readAllowlist(SharedPreferences remotePrefs) {
        Set<String> set = remotePrefs.getStringSet(KEY_ALLOWLIST, Collections.emptySet());
        return set != null ? new HashSet<>(set) : new HashSet<>();
    }

    public static void writeAllowlist(Context context, SharedPreferences remotePrefs,
                                      Set<String> allowlist) {
        remotePrefs.edit().putStringSet(KEY_ALLOWLIST, new HashSet<>(allowlist)).commit();
        context.sendBroadcast(new Intent(ACTION_ALLOWLIST_CHANGED));
    }
}
