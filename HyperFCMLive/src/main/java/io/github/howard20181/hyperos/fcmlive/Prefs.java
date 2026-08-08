package io.github.howard20181.hyperos.fcmlive;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Configuration storage shared between the settings UI (runs in the module's own
 * process) and the Xposed hooks (run in system_server). Both read/write the same
 * SharedPreferences file; {@link #ensureReadable(Context)} makes it readable by
 * system_server which runs under a different uid.
 */
public final class Prefs {
    public static final String PREF_NAME = "config";
    public static final String KEY_ALLOWLIST = "allowlist";
    public static final String MODULE_PKG = "io.github.howard20181.hyperos.fcmlive";

    private Prefs() {
    }

    public static SharedPreferences prefs(Context context) {
        // MODE_MULTI_PROCESS: system_server re-reads the file when it changes
        // (mtime) so the hooks pick up UI changes without restart.
        return context.getSharedPreferences(PREF_NAME, Context.MODE_MULTI_PROCESS);
    }

    /** Package names the user allows FCM to wake / auto-launch. */
    public static Set<String> readAllowlist(Context context) {
        Set<String> set = prefs(context).getStringSet(KEY_ALLOWLIST, Collections.emptySet());
        return set != null ? new HashSet<>(set) : new HashSet<>();
    }

    public static void writeAllowlist(Context context, Set<String> allowlist) {
        // commit() (synchronous) so the file exists before ensureReadable() chmods it.
        prefs(context).edit().putStringSet(KEY_ALLOWLIST, new HashSet<>(allowlist)).commit();
        ensureReadable(context);
    }

    /**
     * Best-effort: make the prefs file (and its parent directory) world-readable so
     * the system_server hook (a different uid) can read the same config file.
     */
    public static void ensureReadable(Context context) {
        try {
            File sharedPrefs = new File(context.getDataDir(), "shared_prefs");
            Runtime.getRuntime().exec(new String[]{"chmod", "711", sharedPrefs.getAbsolutePath()});
            Runtime.getRuntime().exec(new String[]{"chmod", "644",
                    new File(sharedPrefs, PREF_NAME + ".xml").getAbsolutePath()});
        } catch (Exception ignored) {
        }
    }
}
