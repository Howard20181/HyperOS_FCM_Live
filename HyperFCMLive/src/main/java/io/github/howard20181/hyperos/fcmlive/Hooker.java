package io.github.howard20181.hyperos.fcmlive;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;

import io.github.libxposed.api.XposedModule;

@SuppressLint("PrivateApi")
public class Hooker extends XposedModule {
    private static final String TAG = "HyperGreeze";
    private static final List<String> CN_DEFER_BROADCAST = Arrays.asList("com.google.android.intent.action.GCM_RECONNECT", "com.google.android.gcm.DISCONNECTED", "com.google.android.gcm.CONNECTED", "com.google.android.gms.gcm.HEARTBEAT_ALARM");
    private static final String ACTION_REMOTE_INTENT = "com.google.android.c2dm.intent.RECEIVE";
    private static final String GMS_PACKAGE_NAME = "com.google.android.gms";
    private static final String GMS_PERSISTENT_PROCESS_NAME = "com.google.android.gms.persistent";

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
            try {
                hookBroadcastQueueModernStubImpl(classLoader);
            } catch (Exception e) {
                log(Log.ERROR, TAG, "Failed to hook BroadcastQueueModernStubImpl", e);
            }
            try {
                hookProcessPolicy(classLoader);
            } catch (Exception e) {
                log(Log.ERROR, TAG, "Failed to hook ProcessPolicy", e);
            }
            try {
                hookAwareResourceControl(classLoader);
            } catch (Exception e) {
                log(Log.ERROR, TAG, "Failed to hook AwareResourceControl", e);
            }
        } catch (Throwable tr) {
            log(Log.ERROR, TAG, "Failed to hook SystemServer", tr);
        }
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        if (!param.isFirstPackage()) return;
        var classLoader = param.getClassLoader();
        var packageName = param.getPackageName();
        if ("com.miui.powerkeeper".equals(packageName)) {
            try {
                hookGmsObserver(classLoader);
            } catch (Exception e) {
                log(Log.ERROR, TAG, "Failed to hook GmsObserver", e);
            }
        }
    }

    private void hookGreezeManagerService(ClassLoader classLoader) throws ClassNotFoundException, NoSuchMethodException {
        var GreezeManagerServiceClass = classLoader.loadClass("com.miui.server.greeze.GreezeManagerService");
        try {
            // boolean isAllowBroadcast(int callerUid, String callerPkgName, int calleeUid, String calleePkgName, String action)
            // BroadcastProcessQueue queue, am.ProcessRecord app = queue.app, app nullable but when app is null, this method will not call
            // calleePkgName = (app.info == null || app.info.packageName == null) ? app.processName : app.info.packageName
            var isAllowBroadcastMethod = GreezeManagerServiceClass.getDeclaredMethod("isAllowBroadcast", int.class, String.class, int.class, String.class, String.class);
            hook(isAllowBroadcastMethod).intercept(chain -> { // why contains? see above about where calleePkgName come from
                if (chain.getArg(3) instanceof String calleePkgName
                        && calleePkgName.contains(GMS_PACKAGE_NAME)
                        && chain.getArg(4) instanceof String action
                        && (ACTION_REMOTE_INTENT.equals(action)
                        || CN_DEFER_BROADCAST.contains(action))) {
                    return true;
                }
                return chain.proceed();
            });
            deoptimize(isAllowBroadcastMethod);
        } catch (Exception e) {
            log(Log.ERROR, TAG, "Failed to hook GreezeManagerService#isAllowBroadcast, trying isAllowBroadcastV2", e);
        }
        try {
            // boolean deferBroadcastForMiui(String action)
            var deferBroadcastForMiuiMethod = GreezeManagerServiceClass.getDeclaredMethod("deferBroadcastForMiui", String.class);
            hook(deferBroadcastForMiuiMethod).intercept(chain -> {
                if (chain.getArg(0) instanceof String action
                        && CN_DEFER_BROADCAST.contains(action)) {
                    return false;
                }
                return chain.proceed();
            });
            deoptimize(deferBroadcastForMiuiMethod);
        } catch (Exception e) {
            log(Log.ERROR, TAG, "Failed to hook GreezeManagerService#deferBroadcastForMiui", e);
        }
        var triggerGMSLimitActionMethod = GreezeManagerServiceClass.getDeclaredMethod("triggerGMSLimitAction", boolean.class);
        hook(triggerGMSLimitActionMethod).intercept(chain -> {
            var args = chain.getArgs().toArray();
            args[0] = false;
            return chain.proceed(args);
        });
        deoptimize(triggerGMSLimitActionMethod);
    }

    private void hookDomesticPolicyManager(ClassLoader classLoader) throws ClassNotFoundException,
            NoSuchMethodException {
        var DomesticPolicyManagerClass = classLoader.loadClass("com.miui.server.greeze.DomesticPolicyManager");
        // boolean deferBroadcast(String action)
        var deferBroadcastMethod = DomesticPolicyManagerClass.getDeclaredMethod("deferBroadcast", String.class);
        hook(deferBroadcastMethod).intercept(chain -> false);
        deoptimize(deferBroadcastMethod);
    }

    private void hookListAppsManager(ClassLoader classLoader) throws ClassNotFoundException,
            NoSuchFieldException {
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
                        var mSystemBlackList = (List<String>) mSystemBlackListField.get(chain.getThisObject());
                        if (mSystemBlackList != null) {
                            mSystemBlackList.remove(GMS_PACKAGE_NAME);
                        }
                    } catch (Exception e) {
                        log(Log.ERROR, TAG, "Failed to modify ListAppsManager$PowerStrategyMode constructor", e);
                    }
                }
            });
            deoptimize(constructor);
        }
    }

    private void hookBroadcastQueueModernStubImpl(ClassLoader classLoader) throws
            ClassNotFoundException, NoSuchMethodException, NoSuchFieldException {
        var BroadcastQueueModernStubImplClass = classLoader.loadClass("com.android.server.am.BroadcastQueueModernStubImpl");
        var BroadcastQueueClass = classLoader.loadClass("com.android.server.am.BroadcastQueue");
        var BroadcastRecordClass = classLoader.loadClass("com.android.server.am.BroadcastRecord");
        var callerPackageField = BroadcastRecordClass.getDeclaredField("callerPackage");
        callerPackageField.setAccessible(true);
        var intentField = BroadcastRecordClass.getDeclaredField("intent");
        intentField.setAccessible(true);
        var checkApplicationAutoStartMethod = BroadcastQueueModernStubImplClass.getDeclaredMethod("checkApplicationAutoStart", BroadcastQueueClass, BroadcastRecordClass, ResolveInfo.class);
        hook(checkApplicationAutoStartMethod).intercept(chain -> {
            try {
                var broadcastRecord = chain.getArg(1);
                if (callerPackageField.get(broadcastRecord) instanceof String callerPackage
                        && GMS_PACKAGE_NAME.equals(callerPackage) // BroadcastRecord.callerPackage nullable
                        && intentField.get(broadcastRecord) instanceof Intent intent
                        && ACTION_REMOTE_INTENT.equals(intent.getAction())) {
                    return true;
                }
            } catch (Exception e) {
                log(Log.ERROR, TAG, "Failed to modify BroadcastQueueModernStubImpl#checkApplicationAutoStart", e);
            }
            return chain.proceed();
        });
        deoptimize(checkApplicationAutoStartMethod);
    }

    private void hookProcessPolicy(ClassLoader classLoader) throws ClassNotFoundException,
            NoSuchMethodException {
        var ProcessPolicyClass = classLoader.loadClass("com.android.server.am.ProcessPolicy");
        var getWhiteListMethod = ProcessPolicyClass.getDeclaredMethod("getWhiteList", int.class);
        hook(getWhiteListMethod).intercept(chain -> {
            var result = chain.proceed();
            if (chain.getArg(0) instanceof Integer flags && (flags & 1) != 0) {
                if (result instanceof List<?>) {
                    var whiteList = (List<String>) result;
                    whiteList.add(GMS_PACKAGE_NAME);
                    whiteList.add(GMS_PERSISTENT_PROCESS_NAME);
                }
            }
            return result;
        });
    }

    private void hookAwareResourceControl(ClassLoader classLoader) throws ClassNotFoundException,
            NoSuchFieldException {
        var AwareResourceControlClass = classLoader.loadClass("com.miui.server.greeze.power.AwareResourceControl");
        var mNoNetworkBlackUidsField = AwareResourceControlClass.getDeclaredField("mNoNetworkBlackUids");
        mNoNetworkBlackUidsField.setAccessible(true);
        var AwareResourceControlConstructors = AwareResourceControlClass.getDeclaredConstructors();
        for (var constructor : AwareResourceControlConstructors) {
            hook(constructor).intercept(chain -> {
                try {
                    return chain.proceed();
                } finally {
                    try {
                        var mNoNetworkBlackUids = (List<String>) mNoNetworkBlackUidsField.get(chain.getThisObject());
                        if (mNoNetworkBlackUids != null) {
                            mNoNetworkBlackUids.remove(GMS_PACKAGE_NAME);
                        }
                    } catch (Exception e) {
                        log(Log.ERROR, TAG, "Failed to modify AwareResourceControl constructor", e);
                    }
                }
            });
            deoptimize(constructor);
        }
    }

    private void hookGmsObserver(ClassLoader classLoader) throws ClassNotFoundException,
            NoSuchMethodException {
        var GmsObserverClass = classLoader.loadClass("com.miui.powerkeeper.utils.GmsObserver");
        Hooker hooker = chain -> {
            var args = chain.getArgs().toArray();
            args[0] = false;
            return chain.proceed(args);
        };
        var updateGmsAlarmMethod = GmsObserverClass.getDeclaredMethod("updateGmsAlarm", boolean.class);
        hook(updateGmsAlarmMethod).intercept(hooker);
        deoptimize(updateGmsAlarmMethod);
        var updateGmsNetWorkMethod = GmsObserverClass.getDeclaredMethod("updateGmsNetWork", boolean.class);
        hook(updateGmsNetWorkMethod).intercept(hooker);
        deoptimize(updateGmsNetWorkMethod);
        var updateGoogleReletivesWakelockMethod = GmsObserverClass.getDeclaredMethod("updateGoogLeReletivesWakelock", boolean.class);
        hook(updateGoogleReletivesWakelockMethod).intercept(hooker);
        deoptimize(updateGoogleReletivesWakelockMethod);
    }
}
