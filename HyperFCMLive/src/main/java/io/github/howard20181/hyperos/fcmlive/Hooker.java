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
            try {
                hookListAppsManager(classLoader);
            } catch (Exception t) {
                log(Log.ERROR, TAG, "Failed to hook ListAppsManager", t);
            }
        } catch (Throwable tr) {
            log(Log.ERROR, TAG, "Failed to hook SystemServer", tr);
        }
    }

    private void hookGreezeManagerService(ClassLoader classLoader) throws ClassNotFoundException, NoSuchMethodException {
        var GreezeManagerServiceClass = classLoader.loadClass("com.miui.server.greeze.GreezeManagerService");
        try {
            // boolean isAllowBroadcast(int callerUid, String callerPkgName, int calleeUid, String calleePkgName, String action)
            var isAllowBroadcastMethod = GreezeManagerServiceClass.getDeclaredMethod("isAllowBroadcast", int.class, String.class, int.class, String.class, String.class);
            var mScreenOnOffField = GreezeManagerServiceClass.getDeclaredField("mScreenOnOff");
            mScreenOnOffField.setAccessible(true);
            hook(isAllowBroadcastMethod).intercept(chain -> {
                if (chain.getArg(3) instanceof String calleePkgName && calleePkgName.contains("com.google.android.gms")) {
                    try {
                        if (chain.getArg(4) instanceof String action && CN_DEFER_BROADCAST.contains(action) || mScreenOnOffField.getBoolean(chain.getThisObject())) {
                            return true;
                        }
                    } catch (Exception e) {
                        log(Log.ERROR, TAG, "Failed to modify GreezeManagerService#isAllowBroadcast", e);
                    }
                }
                return chain.proceed();
            });
            deoptimize(isAllowBroadcastMethod);
        } catch (Exception e) {
            log(Log.ERROR, TAG, "Failed to hook GreezeManagerService#isAllowBroadcast, trying isAllowBroadcastV2", e);
        }
        // boolean deferBroadcastForMiui(String action)
        var deferBroadcastForMiuiMethod = GreezeManagerServiceClass.getDeclaredMethod("deferBroadcastForMiui", String.class);
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

    private void hookListAppsManager(ClassLoader classLoader) throws ClassNotFoundException, NoSuchFieldException {
        var ListAppsManagerClass = classLoader.loadClass("com.miui.server.greeze.power.ListAppsManager");
        var mSystemBlackListField = ListAppsManagerClass.getDeclaredField("mSystemBlackList");
        mSystemBlackListField.setAccessible(true);
        var PowerStrategyModeConstructors = ListAppsManagerClass.getDeclaredConstructors();
        for (var constructor : PowerStrategyModeConstructors) {
            hook(constructor).intercept(chain -> {
                try {
                    return chain.proceed();
                } finally {
                    try {
                        List<String> mSystemBlackList = (List<String>) mSystemBlackListField.get(chain.getThisObject());
                        if (mSystemBlackList != null) {
                            mSystemBlackList.remove("com.google.android.gms");
                        }
                    } catch (Exception e) {
                        log(Log.ERROR, TAG, "Failed to modify ListAppsManager$PowerStrategyMode constructor", e);
                    }
                }
            });
            deoptimize(constructor);
        }
    }
}
