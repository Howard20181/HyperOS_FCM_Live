package io.github.howard20181.hyperos.fcmlive;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;

import io.github.libxposed.api.XposedModule;

public class Hooker extends XposedModule {
    private static final String TAG = "HyperGreeze";
    private static final List<String> CN_DEFER_BROADCAST = Arrays.asList("com.google.android.intent.action.GCM_RECONNECT", "com.google.android.gcm.DISCONNECTED", "com.google.android.gcm.CONNECTED", "com.google.android.gms.gcm.HEARTBEAT_ALARM");

    @Override
    public void onSystemServerStarting(@NonNull SystemServerStartingParam param) {
        var classLoader = param.getClassLoader();
        try {
            try {
                hookGreezeManagerService(classLoader);
            } catch (Exception t) {
                log(Log.ERROR, TAG, "Failed to hook GreezeManagerService", t);
            }
            try {
                hookDomesticPolicyManager(classLoader);
            } catch (Exception t) {
                log(Log.ERROR, TAG, "Failed to hook DomesticPolicyManager", t);
            }
        } catch (Throwable tr) {
            log(Log.ERROR, TAG, "Failed to hook SystemServer", tr);
        }
    }

    private void hookGreezeManagerService(ClassLoader classLoader) throws ClassNotFoundException, NoSuchMethodException, NoSuchFieldException {
        var GreezeManagerServiceClass = classLoader.loadClass("com.miui.server.greeze.GreezeManagerService");
        // boolean isAllowBroadcast(int callerUid, String callerPkgName, int calleeUid, String calleePkgName, String action)
        var isAllowBroadcastMethod = GreezeManagerServiceClass.getDeclaredMethod("isAllowBroadcast", int.class, String.class, int.class, String.class, String.class);
        // boolean deferBroadcastForMiui(String action)
        var deferBroadcastForMiuiMethod = GreezeManagerServiceClass.getDeclaredMethod("deferBroadcastForMiui", String.class);
        var mScreenOnOffField = GreezeManagerServiceClass.getDeclaredField("mScreenOnOff");
        mScreenOnOffField.setAccessible(true);
        hook(isAllowBroadcastMethod).intercept(chain -> {
            if (chain.getArg(3) instanceof String calleePkgName && calleePkgName.contains("com.google.android.gms")) {
                var mScreenOnOff = mScreenOnOffField.getBoolean(chain.getThisObject());
                if (mScreenOnOff && chain.getArg(4) instanceof String action && CN_DEFER_BROADCAST.contains(action))
                    return true;
            }
            return chain.proceed();
        });
        deoptimize(isAllowBroadcastMethod);
        hook(deferBroadcastForMiuiMethod).intercept(chain -> {
            if (chain.getArg(0) instanceof String action && CN_DEFER_BROADCAST.contains(action)) {
                return false;
            }
            return chain.proceed();
        });
        deoptimize(deferBroadcastForMiuiMethod);
    }

    private void hookDomesticPolicyManager(ClassLoader classLoader) throws ClassNotFoundException, NoSuchMethodException {
        var DomesticPolicyManagerClass = classLoader.loadClass("com.miui.server.greeze.DomesticPolicyManager");
        // boolean deferBroadcast(String action)
        var deferBroadcastMethod = DomesticPolicyManagerClass.getDeclaredMethod("deferBroadcast", String.class);
        hook(deferBroadcastMethod).intercept(chain -> false);
        deoptimize(deferBroadcastMethod);
    }
}
