/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.keyguard;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.AlarmManager;
import android.app.IUserSwitchObserver;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.app.trust.TrustManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Bitmap;

import static android.os.BatteryManager.BATTERY_STATUS_FULL;
import static android.os.BatteryManager.BATTERY_STATUS_UNKNOWN;
import static android.os.BatteryManager.BATTERY_HEALTH_UNKNOWN;
import static android.os.BatteryManager.EXTRA_STATUS;
import static android.os.BatteryManager.EXTRA_PLUGGED;
import static android.os.BatteryManager.EXTRA_LEVEL;
import static android.os.BatteryManager.EXTRA_HEALTH;

import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.IRemoteCallback;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.IccCardConstants.State;
//HB. Comments : whether the lock screen is secured , Engerineer : wxue , Date : 2017年6月23日 ,begin
import com.android.internal.widget.LockPatternUtils;
//HB. end
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import android.hardware.fingerprint.FingerprintManager;
import android.hardware.fingerprint.FingerprintManager.AuthenticationCallback;
import android.hardware.fingerprint.FingerprintManager.AuthenticationResult;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import com.google.android.collect.Lists;
import com.mediatek.internal.telephony.ITelephonyEx;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
// byd added for byd_fps
import java.io.File;

/**
 * Watches for updates that may be interesting to the keyguard, and provides
 * the up to date information as well as a registration for callbacks that care
 * to be updated.
 *
 * Note: under time crunch, this has been extended to include some stuff that
 * doesn't really belong here.  see {@link #handleBatteryUpdate} where it shutdowns
 * the device, and {@link #getFailedUnlockAttempts()}, {@link #reportFailedAttempt()}
 * and {@link #clearFailedUnlockAttempts()}.  Maybe we should rename this 'KeyguardContext'...
 */
public class KeyguardUpdateMonitor implements TrustManager.TrustListener {

    private static final String TAG = "KeyguardUpdateMonitor";
    private static final boolean DEBUG = KeyguardConstants.DEBUG;
    private static final boolean DEBUG_SIM_STATES = KeyguardConstants.DEBUG_SIM_STATES;
    /// M: Change the threshold to 16 for mediatek device
    private static final int LOW_BATTERY_THRESHOLD = 16;

    private static final boolean DEBUG_FP_WAKELOCK = KeyguardConstants.DEBUG_FP_WAKELOCK;
    private static final long FINGERPRINT_WAKELOCK_TIMEOUT_MS = 15 * 1000;

    private static final String ACTION_FACE_UNLOCK_STARTED
            = "com.android.facelock.FACE_UNLOCK_STARTED";
    private static final String ACTION_FACE_UNLOCK_STOPPED
            = "com.android.facelock.FACE_UNLOCK_STOPPED";
    private static final String FINGERPRINT_WAKE_LOCK_NAME = "wake-and-unlock wakelock";

    /**
     * Mode in which we don't need to wake up the device when we get a fingerprint.
     */
    private static final int FP_WAKE_NONE = 0;

    /**
     * Mode in which we wake up the device, and directly dismiss Keyguard. Active when we acquire
     * a fingerprint while the screen is off and the device was sleeping.
     */
    private static final int FP_WAKE_DIRECT_UNLOCK = 1;

    /**
     * Mode in which we wake up the device, but play the normal dismiss animation. Active when we
     * acquire a fingerprint pulsing in doze mode.
     * */
    private static final int FP_WAKE_WAKE_TO_BOUNCER = 2;

    // Callback messages
    private static final int MSG_TIME_UPDATE = 301;
    private static final int MSG_BATTERY_UPDATE = 302;
    // M: add for carrier
    private static final int MSG_CARRIER_INFO_UPDATE = 303;
    private static final int MSG_SIM_STATE_CHANGE = 304;
    private static final int MSG_RINGER_MODE_CHANGED = 305;
    private static final int MSG_PHONE_STATE_CHANGED = 306;
    private static final int MSG_DEVICE_PROVISIONED = 308;
    private static final int MSG_DPM_STATE_CHANGED = 309;
    private static final int MSG_USER_SWITCHING = 310;
    private static final int MSG_KEYGUARD_VISIBILITY_CHANGED = 311;
    private static final int MSG_KEYGUARD_RESET = 312;
    private static final int MSG_BOOT_COMPLETED = 313;
    private static final int MSG_USER_SWITCH_COMPLETE = 314;
    private static final int MSG_USER_INFO_CHANGED = 317;
    private static final int MSG_REPORT_EMERGENCY_CALL_ACTION = 318;
    private static final int MSG_STARTED_WAKING_UP = 319;
    private static final int MSG_FINISHED_GOING_TO_SLEEP = 320;
    private static final int MSG_KEYGUARD_BOUNCER_CHANGED = 322;
    private static final int MSG_FACE_UNLOCK_STATE_CHANGED = 327;
    private static final int MSG_SIM_SUBSCRIPTION_INFO_CHANGED = 328;
    private static final int MSG_AIRPLANE_MODE_CHANGED = 329;
    private static final int MSG_SERVICE_STATE_CHANGE = 330;
    // add by wxue 20140427 start
    private static final int MSG_FINGERPRINT_LOCK_TIMEOUT = 331;
    private static final int MSG_KEYGUARD_OCCLUDED = 332;
    private static final int FINGERPRINT_LOCKOUT_TIME = 5*1000;
    private boolean mFingerprintLockout;
    private boolean mFingerprintLockoutTimeout = true;
    private boolean mOccluded;
    private LockPatternUtils mLockPatternUtils;
    // add by wxue 20140427 end
    
    private static KeyguardUpdateMonitor sInstance;
    
    private final Context mContext;
    HashMap<Integer, SimData> mSimDatas = new HashMap<Integer, SimData>();
    HashMap<Integer, ServiceState> mServiceStates = new HashMap<Integer, ServiceState>();

    // M: Telephony state
    private HashMap<Integer, IccCardConstants.State> mSimStateOfPhoneId =
            new HashMap<Integer, IccCardConstants.State>();
    private HashMap<Integer, CharSequence> mTelephonyPlmn = new HashMap<Integer, CharSequence>();
    private HashMap<Integer, CharSequence> mTelephonySpn = new HashMap<Integer, CharSequence>();
    private int mRingMode;

    // Phone state is set as OFFHOOK if one subscription is in OFFHOOK state.
    private int mPhoneState;
    private boolean mKeyguardIsVisible;
    private boolean mBouncer;
    private boolean mBootCompleted;
    private boolean mUserHasAuthenticatedSinceBoot;
	// byd added for byd_fps
    private boolean mFingerprintHasAuthenticated;
	private boolean isBydFps = false;
	private final static String FP_BYDFPS = "/sys/bus/spi/drivers/byd_fps/spi1.1/fp_qsee";
    // Device provisioning state
    private boolean mDeviceProvisioned;

    // Battery status
    private BatteryStatus mBatteryStatus;

    // Password attempts
    private SparseIntArray mFailedAttempts = new SparseIntArray();

    private boolean mClockVisible;

    private final ArrayList<WeakReference<KeyguardUpdateMonitorCallback>>
            mCallbacks = Lists.newArrayList();
    private ContentObserver mDeviceProvisionedObserver;

    private boolean mSwitchingUser;

    private boolean mDeviceInteractive;
    private boolean mScreenOn;
    private SubscriptionManager mSubscriptionManager;
    private List<SubscriptionInfo> mSubscriptionInfo;
    private boolean mFingerprintDetectionRunning;
    private TrustManager mTrustManager;
    private PowerManager mPowerManager;

    // M: modify for mock
    @VisibleForTesting
    final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_TIME_UPDATE:
                    handleTimeUpdate();
                    break;
                case MSG_BATTERY_UPDATE:
                    handleBatteryUpdate((BatteryStatus) msg.obj);
                    break;
                // M: add for carrier
                case MSG_CARRIER_INFO_UPDATE:
                    handleCarrierInfoUpdate((Integer) msg.obj);
                    break;
                case MSG_SIM_STATE_CHANGE:
                    // handleSimStateChange(msg.arg1, msg.arg2, (State) msg.obj);
                    handleSimStateChange((SimData) msg.obj);
                    break;
                case MSG_RINGER_MODE_CHANGED:
                    handleRingerModeChange(msg.arg1);
                    break;
                case MSG_PHONE_STATE_CHANGED:
                    // handlePhoneStateChanged((String) msg.obj);
                    handlePhoneStateChanged();
                    break;
                case MSG_DEVICE_PROVISIONED:
                    handleDeviceProvisioned();
                    break;
                case MSG_DPM_STATE_CHANGED:
                    handleDevicePolicyManagerStateChanged();
                    break;
                case MSG_USER_SWITCHING:
                    handleUserSwitching(msg.arg1, (IRemoteCallback) msg.obj);
                    break;
                case MSG_USER_SWITCH_COMPLETE:
                    handleUserSwitchComplete(msg.arg1);
                    break;
                case MSG_KEYGUARD_VISIBILITY_CHANGED:
                    handleKeyguardVisibilityChanged(msg.arg1);
                    break;
                case MSG_KEYGUARD_RESET:
                    handleKeyguardReset();
                    break;
                case MSG_KEYGUARD_BOUNCER_CHANGED:
                    handleKeyguardBouncerChanged(msg.arg1);
                    break;
                case MSG_BOOT_COMPLETED:
                    handleBootCompleted();
                    break;
                case MSG_USER_INFO_CHANGED:
                    handleUserInfoChanged(msg.arg1);
                    break;
                case MSG_REPORT_EMERGENCY_CALL_ACTION:
                    handleReportEmergencyCallAction();
                    break;
                case MSG_FINISHED_GOING_TO_SLEEP:
                    handleFinishedGoingToSleep(msg.arg1);
                    break;
                case MSG_STARTED_WAKING_UP:
                    handleStartedWakingUp();
                    break;
                case MSG_FACE_UNLOCK_STATE_CHANGED:
                    handleFaceUnlockStateChanged(msg.arg1 != 0, msg.arg2);
                    break;
                case MSG_SIM_SUBSCRIPTION_INFO_CHANGED:
                    handleSimSubscriptionInfoChanged();
                    break;
                case MSG_AIRPLANE_MODE_CHANGED:
                    handleAirplaneModeChanged();
                    break;
                case MSG_SERVICE_STATE_CHANGE:
                    handleServiceStateChange(msg.arg1, (ServiceState) msg.obj);
                    break;
                ///M: support airplan mode
                case MSG_AIRPLANE_MODE_UPDATE:
                    if (DEBUG) {
                        Log.d(TAG, "MSG_AIRPLANE_MODE_UPDATE, msg.obj=" + (Boolean) msg.obj);
                    }
                    handleAirPlaneModeUpdate((Boolean) msg.obj);
                    break;
                // add by wxue 20170427 start 
                case MSG_FINGERPRINT_LOCK_TIMEOUT:
                	handleFingerprintLockedTimeout();
                	break;
                case MSG_KEYGUARD_OCCLUDED:
                	handleKeyguardOccluded(msg.arg1);
                // add by wxue 20170427 end	
            }
        }
    };

    private OnSubscriptionsChangedListener mSubscriptionListener =
            new OnSubscriptionsChangedListener() {
        @Override
        public void onSubscriptionsChanged() {
            Log.d(TAG, "onSubscriptionsChanged() is called.") ;
            ///M: fix ALPS01966184, we add a debounce mechanism here to handle overflowed
            ///   MSG_SIM_SUBSCRIPTION_INFO_CHANGED messages.
            mHandler.removeMessages(MSG_SIM_SUBSCRIPTION_INFO_CHANGED);
            mHandler.sendEmptyMessage(MSG_SIM_SUBSCRIPTION_INFO_CHANGED);
        }
    };

    private SparseBooleanArray mUserHasTrust = new SparseBooleanArray();
    private SparseBooleanArray mUserTrustIsManaged = new SparseBooleanArray();
    private SparseBooleanArray mUserFingerprintAuthenticated = new SparseBooleanArray();
    private SparseBooleanArray mUserFaceUnlockRunning = new SparseBooleanArray();

    private static int sCurrentUser;

    private int mFpWakeMode;

    public synchronized static void setCurrentUser(int currentUser) {
        sCurrentUser = currentUser;
    }

    public synchronized static int getCurrentUser() {
        return sCurrentUser;
    }

    @Override
    public void onTrustChanged(boolean enabled, int userId, int flags) {
        mUserHasTrust.put(userId, enabled);
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onTrustChanged(userId);
                if (enabled && flags != 0) {
                    cb.onTrustGrantedWithFlags(flags, userId);
                }
            }
        }
    }

    protected void handleSimSubscriptionInfoChanged() {
        if (DEBUG_SIM_STATES) {
            Log.v(TAG, "onSubscriptionInfoChanged()");
            List<SubscriptionInfo> sil = mSubscriptionManager.getActiveSubscriptionInfoList();
            if (sil != null) {
                for (SubscriptionInfo subInfo : sil) {
                    Log.v(TAG, "SubInfo:" + subInfo);
                }
            } else {
                Log.v(TAG, "onSubscriptionInfoChanged: list is null");
            }
        }
        List<SubscriptionInfo> subscriptionInfos = getSubscriptionInfo(true /* forceReload */);

        // Hack level over 9000: Because the subscription id is not yet valid when we see the
        // first update in handleSimStateChange, we need to force refresh all all SIM states
        // so the subscription id for them is consistent.
        ArrayList<SubscriptionInfo> changedSubscriptions = new ArrayList<>();
        Log.d(TAG, "handleSimSubscriptionInfoChanged() - call refreshSimState()") ;

        for (int i = 0; i < subscriptionInfos.size(); i++) {
            SubscriptionInfo info = subscriptionInfos.get(i);
            boolean changed = refreshSimState(info.getSubscriptionId(), info.getSimSlotIndex());
            if (changed) {
                changedSubscriptions.add(info);
            }
        }

        Log.d(TAG, "handleSimSubscriptionInfoChanged() - call onSimStateChangedUsingPhoneId() & "
            + "onRefreshCarrierInfo().") ;
        for (int i = 0; i < changedSubscriptions.size(); i++) {
            // SimData data = mSimDatas.get(changedSubscriptions.get(i).getSubscriptionId());
            int subId = changedSubscriptions.get(i).getSubscriptionId();
            int phoneId = changedSubscriptions.get(i).getSimSlotIndex();
            Log.d(TAG, "handleSimSubscriptionInfoChanged() - call callbacks for subId = " + subId +
                " & phoneId = " + phoneId) ;

            for (int j = 0; j < mCallbacks.size(); j++) {
                KeyguardUpdateMonitorCallback cb = mCallbacks.get(j).get();
                if (cb != null) {
                    // cb.onSimStateChanged(data.subId, data.slotId, data.simState);
                    cb.onSimStateChangedUsingPhoneId(phoneId, mSimStateOfPhoneId.get(phoneId));
                }
            }
        }

        for (int j = 0; j < mCallbacks.size(); j++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(j).get();
            if (cb != null) {
                cb.onRefreshCarrierInfo();
            }
        }
    }

    private void handleAirplaneModeChanged() {
        for (int j = 0; j < mCallbacks.size(); j++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(j).get();
            if (cb != null) {
                cb.onRefreshCarrierInfo();
            }
        }
    }

    /** @return List of SubscriptionInfo records, maybe empty but never null */
    List<SubscriptionInfo> getSubscriptionInfo(boolean forceReload) {
        List<SubscriptionInfo> sil = mSubscriptionInfo;
        ///M: fix ALPS01963966, we should force reload sub list for hot-plug sim device.
        ///   since we may insert the sim card later and the sub list is not null and cannot
        ///   fetch the latest/updated active sub list.
        if (sil == null || forceReload ||
            ((sil != null) && (sil.size() == 0))
        ) {
            Log.d(TAG, "getSubscriptionInfo() - call "
                + "SubscriptionManager.getActiveSubscriptionInfoList()") ;
            sil = mSubscriptionManager.getActiveSubscriptionInfoList();
        }

        if (sil == null) {
            Log.d(TAG, "getSubscriptionInfo() - SubMgr returns empty list.") ;
            // getActiveSubscriptionInfoList was null callers expect an empty list.
            mSubscriptionInfo = new ArrayList<SubscriptionInfo>();
        } else {
            mSubscriptionInfo = sil;
        }

        Log.d(TAG, "getSubscriptionInfo() - mSubscriptionInfo.size = " + mSubscriptionInfo.size());
        return mSubscriptionInfo;
    }

    @Override
    public void onTrustManagedChanged(boolean managed, int userId) {
        mUserTrustIsManaged.put(userId, managed);

        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onTrustManagedChanged(userId);
            }
        }
    }

    private void onFingerprintAuthenticated(int userId, boolean wakeAndUnlocking) {
        mUserFingerprintAuthenticated.put(userId, true);
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onFingerprintAuthenticated(userId, wakeAndUnlocking);
            }
        }
    }

    private void handleFingerprintAuthFailed() {
        releaseFingerprintWakeLock();
        // modify by wxue 20170425 start
        //handleFingerprintHelp(-1, mContext.getString(R.string.fingerprint_not_recognized));
        handleFingerprintHelp(-1, mContext.getString(R.string.fingerprint_try_again));
        // modify by wxue 20170425 end
    }

    private void handleFingerprintAcquired(int acquireInfo) {
    	if (acquireInfo != FingerprintManager.FINGERPRINT_ACQUIRED_GOOD) {
            return;
        }
        if (!mDeviceInteractive && !mScreenOn) {
            releaseFingerprintWakeLock();
            mWakeLock = mPowerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, FINGERPRINT_WAKE_LOCK_NAME);
            mWakeLock.acquire();
            mFpWakeMode = FP_WAKE_DIRECT_UNLOCK;
            if (DEBUG_FP_WAKELOCK) {
                Log.i(TAG, "fingerprint acquired, grabbing fp wakelock");
            }
            mHandler.postDelayed(mReleaseFingerprintWakeLockRunnable,
                    FINGERPRINT_WAKELOCK_TIMEOUT_MS);
        } else if (!mDeviceInteractive) {
            mFpWakeMode = FP_WAKE_WAKE_TO_BOUNCER;
        } else {
            mFpWakeMode = FP_WAKE_NONE;
        }
    }

    private final Runnable mReleaseFingerprintWakeLockRunnable = new Runnable() {
        @Override
        public void run() {
            if (DEBUG_FP_WAKELOCK) {
                Log.i(TAG, "fp wakelock: TIMEOUT!!");
            }
            releaseFingerprintWakeLock();
        }
    };

    private void releaseFingerprintWakeLock() {
        if (mWakeLock != null) {
            mHandler.removeCallbacks(mReleaseFingerprintWakeLockRunnable);
            if (DEBUG_FP_WAKELOCK) {
                Log.i(TAG, "releasing fp wakelock");
            }
            mWakeLock.release();
            mWakeLock = null;
        }
    }

    private void handleFingerprintAuthenticated() {
		Log.d(TAG, "handleFingerprintAuthenticated enter mFpWakeMode = " + mFpWakeMode);
        // byd added
		if(isBydFps) {
			mFingerprintHasAuthenticated = true;
		}
        if (mFpWakeMode == FP_WAKE_WAKE_TO_BOUNCER || mFpWakeMode == FP_WAKE_DIRECT_UNLOCK) {
            if (DEBUG_FP_WAKELOCK) {
                Log.i(TAG, "fp wakelock: Authenticated, waking up...");
            }
            mPowerManager.wakeUp(SystemClock.uptimeMillis());
        }
        releaseFingerprintWakeLock();
        try {
            final int userId;
            try {
                userId = ActivityManagerNative.getDefault().getCurrentUser().id;
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to get current user id: ", e);
                return;
            }
            if (isFingerprintDisabled(userId)) {
                Log.d(TAG, "Fingerprint disabled by DPM for userId: " + userId);
                return;
            }
            onFingerprintAuthenticated(userId, mFpWakeMode == FP_WAKE_DIRECT_UNLOCK);
        } finally {
            setFingerprintRunningDetectionRunning(false);
        }
    }

    private void handleFingerprintHelp(int msgId, String helpString) {
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onFingerprintHelp(msgId, helpString);
            }
        }
    }

    private void handleFingerprintError(int msgId, String errString) {
        setFingerprintRunningDetectionRunning(false);
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onFingerprintError(msgId, errString);
            }
        }
    }

    private void setFingerprintRunningDetectionRunning(boolean running) {
        if (running != mFingerprintDetectionRunning) {
            mFingerprintDetectionRunning = running;
            notifyFingerprintRunningStateChanged();
        }
    }

    private void notifyFingerprintRunningStateChanged() {
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onFingerprintRunningStateChanged(mFingerprintDetectionRunning);
            }
        }
    }
    private void handleFaceUnlockStateChanged(boolean running, int userId) {
        Log.d(TAG, "handleFaceUnlockStateChanged(running = " + running + " , userId = " + userId) ;
        mUserFaceUnlockRunning.put(userId, running);
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onFaceUnlockStateChanged(running, userId);
            }
        }
    }

    public boolean isFaceUnlockRunning(int userId) {
        return mUserFaceUnlockRunning.get(userId);
    }

    public boolean isFingerprintDetectionRunning() {
        return mFingerprintDetectionRunning;
    }

    private boolean isTrustDisabled(int userId) {
        // Don't allow trust agent if device is secured with a SIM PIN. This is here
        // mainly because there's no other way to prompt the user to enter their SIM PIN
        // once they get past the keyguard screen.
        final boolean disabledBySimPin = isSimPinSecure();
        return disabledBySimPin;
    }

    private boolean isFingerprintDisabled(int userId) {
        final DevicePolicyManager dpm =
                (DevicePolicyManager) mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && (dpm.getKeyguardDisabledFeatures(null, userId)
                    & DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT) != 0;
    }

    public boolean getUserCanSkipBouncer(int userId) {
        return getUserHasTrust(userId) || (mUserFingerprintAuthenticated.get(userId)
                && isUnlockingWithFingerprintAllowed());
    }

    public boolean getUserHasTrust(int userId) {
        return !isTrustDisabled(userId) && mUserHasTrust.get(userId);
    }

    public boolean getUserTrustIsManaged(int userId) {
        return mUserTrustIsManaged.get(userId) && !isTrustDisabled(userId);
    }

    public boolean isUnlockingWithFingerprintAllowed() {
        return mUserHasAuthenticatedSinceBoot;
    }

    static class DisplayClientState {
        public int clientGeneration;
        public boolean clearing;
        public PendingIntent intent;
        public int playbackState;
        public long playbackEventTime;
    }

    private DisplayClientState mDisplayClientState = new DisplayClientState();

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (DEBUG) Log.d(TAG, "received broadcast " + action);

            if (Intent.ACTION_TIME_TICK.equals(action)
                    || Intent.ACTION_TIME_CHANGED.equals(action)
                    || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
                mHandler.sendEmptyMessage(MSG_TIME_UPDATE);
            } else if (TelephonyIntents.SPN_STRINGS_UPDATED_ACTION.equals(action)) {
                int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID);
                Log.d(TAG, "SPN_STRINGS_UPDATED_ACTION, sub Id = " + subId) ;
                int phoneId = KeyguardUtils.getPhoneIdUsingSubId(subId) ;

                if (KeyguardUtils.isValidPhoneId(phoneId)) {
                    mTelephonyPlmn.put(phoneId, getTelephonyPlmnFrom(intent));
                    mTelephonySpn.put(phoneId, getTelephonySpnFrom(intent));
                    mTelephonyCsgId.put(phoneId, getTelephonyCsgIdFrom(intent)) ;
                    mTelephonyHnbName.put(phoneId, getTelephonyHnbNameFrom(intent));
                    if (DEBUG) {
                        Log.d(TAG, "SPN_STRINGS_UPDATED_ACTION, update phoneId=" + phoneId
                            + ", plmn=" + mTelephonyPlmn.get(phoneId)
                            + ", spn=" + mTelephonySpn.get(phoneId)
                            + ", csgId=" + mTelephonyCsgId.get(phoneId)
                            + ", hnbName=" + mTelephonyHnbName.get(phoneId));
                    }
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_CARRIER_INFO_UPDATE, phoneId));
                } else {
                    Log.d(TAG, "SPN_STRINGS_UPDATED_ACTION, invalid phoneId = " + phoneId) ;
                }
            } else if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                final int status = intent.getIntExtra(EXTRA_STATUS, BATTERY_STATUS_UNKNOWN);
                final int plugged = intent.getIntExtra(EXTRA_PLUGGED, 0);
                final int level = intent.getIntExtra(EXTRA_LEVEL, 0);
                final int health = intent.getIntExtra(EXTRA_HEALTH, BATTERY_HEALTH_UNKNOWN);
                final Message msg = mHandler.obtainMessage(
                        MSG_BATTERY_UPDATE, new BatteryStatus(status, level, plugged, health));
                mHandler.sendMessage(msg);
            } /* else if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)) {
                SimData args = SimData.fromIntent(intent);
                if (DEBUG_SIM_STATES) {
                    Log.v(TAG, "action " + action
                        + " state: " + intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE)
                        + " slotId: " + args.slotId + " subid: " + args.subId);
                }
                mHandler.obtainMessage(MSG_SIM_STATE_CHANGE, args.subId, args.slotId, args.simState)
                        .sendToTarget(); **/

            else if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)
                    || TelephonyIntents.ACTION_UNLOCK_SIM_LOCK.equals(action)) {
                String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                SimData simArgs = SimData.fromIntent(intent);
                if (DEBUG_SIM_STATES) {
                    Log.v(TAG, "action=" + action + ", state=" + stateExtra
                        + ", slotId=" + simArgs.phoneId + ", subId=" + simArgs.subId
                        + ", simArgs.simState = " + simArgs.simState);
                }

                if (TelephonyIntents.ACTION_UNLOCK_SIM_LOCK.equals(action)) {
                    /// M: set sim state as UNKNOWN state to trigger SIM lock view again.
                    Log.d(TAG, "ACTION_UNLOCK_SIM_LOCK, set sim state as UNKNOWN") ;
                    mSimStateOfPhoneId.put(simArgs.phoneId, IccCardConstants.State.UNKNOWN);
                }

                proceedToHandleSimStateChanged(simArgs) ;
            } else if (AudioManager.RINGER_MODE_CHANGED_ACTION.equals(action)) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_RINGER_MODE_CHANGED,
                        intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1), 0));
            } else if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) {
                String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                mHandler.sendMessage(mHandler.obtainMessage(MSG_PHONE_STATE_CHANGED, state));
            } /*else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                mHandler.sendEmptyMessage(MSG_AIRPLANE_MODE_CHANGED);
            } */else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                dispatchBootCompleted();
            } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                ///M: fix ALPS01821063, we should assume that extra value may not exist.
                ///   Although the extra value of AIRPLANE_MODE_CHANGED intent should exist in fact.
                boolean state = intent.getBooleanExtra("state", false);
                Log.d(TAG, "Receive ACTION_AIRPLANE_MODE_CHANGED, state = " + state);
                Message msg = new Message() ;
                msg.what = MSG_AIRPLANE_MODE_UPDATE ;
                msg.obj = new Boolean(state) ;
                mHandler.sendMessage(msg);
            } else if (TelephonyIntents.ACTION_SERVICE_STATE_CHANGED.equals(action)) {
                ServiceState serviceState = ServiceState.newFromBundle(intent.getExtras());
                int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID);
                if (DEBUG) {
                    Log.v(TAG, "action " + action + " serviceState=" + serviceState + " subId="
                            + subId);
                }
                mHandler.sendMessage(
                        mHandler.obtainMessage(MSG_SERVICE_STATE_CHANGE, subId, 0, serviceState));
            }
        }
    };

    private void proceedToHandleSimStateChanged(SimData simArgs) {
        if ((IccCardConstants.State.NETWORK_LOCKED == simArgs.simState) &&
            KeyguardUtils.isMediatekSimMeLockSupport()) {
            //if (KeyguardUtils.isMediatekSimMeLockSupport()) {
            /// M: to create new thread to query SIM ME lock status
            /// after finish query, send MSG_SIM_STATE_CHANGE message
            new simMeStatusQueryThread(simArgs).start();
        } else {
            mHandler.sendMessage(mHandler.obtainMessage(MSG_SIM_STATE_CHANGE, simArgs));
        }
    }

    private final BroadcastReceiver mBroadcastAllReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED.equals(action)) {
                mHandler.sendEmptyMessage(MSG_TIME_UPDATE);
            } else if (Intent.ACTION_USER_INFO_CHANGED.equals(action)) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_USER_INFO_CHANGED,
                        intent.getIntExtra(Intent.EXTRA_USER_HANDLE, getSendingUserId()), 0));
            } else if (ACTION_FACE_UNLOCK_STARTED.equals(action)) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_FACE_UNLOCK_STATE_CHANGED, 1,
                        getSendingUserId()));
            } else if (ACTION_FACE_UNLOCK_STOPPED.equals(action)) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_FACE_UNLOCK_STATE_CHANGED, 0,
                        getSendingUserId()));
            } else if (DevicePolicyManager.ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED
                    .equals(action)) {
                mHandler.sendEmptyMessage(MSG_DPM_STATE_CHANGED);
            }
        }
    };

    private FingerprintManager.AuthenticationCallback mAuthenticationCallback
            = new AuthenticationCallback() {

        @Override
        public void onAuthenticationFailed() {
            handleFingerprintAuthFailed();
        };

        @Override
        public void onAuthenticationSucceeded(AuthenticationResult result) {
            handleFingerprintAuthenticated();
        }

        @Override
        public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
            handleFingerprintHelp(helpMsgId, helpString.toString());
        }

        @Override
        public void onAuthenticationError(int errMsgId, CharSequence errString) {
        	// add by wxue 20170427 start
        	//if(DEBUG) Log.i(TAG,"---onAuthenticationError()--errMsgId = " + errMsgId + " errString = " + errString);
        	if(!mScreenOn && errMsgId == FingerprintManager.FINGERPRINT_ERROR_LOCKOUT){
        		mPowerManager.wakeUp(SystemClock.uptimeMillis());
        	}
        	if(errMsgId == FingerprintManager.FINGERPRINT_ERROR_LOCKOUT  && mUserHasAuthenticatedSinceBoot && mFingerprintLockoutTimeout){
        		mFingerprintLockout = true;
        		mFingerprintLockoutTimeout = false;
        		mHandler.sendEmptyMessageDelayed(MSG_FINGERPRINT_LOCK_TIMEOUT, FINGERPRINT_LOCKOUT_TIME);
        	}
        	// add by wxue 20170427 end
        	handleFingerprintError(errMsgId, errString.toString());
        }

        @Override
        public void onAuthenticationAcquired(int acquireInfo) {
            handleFingerprintAcquired(acquireInfo);
        }
    };
    private CancellationSignal mFingerprintCancelSignal;
    private FingerprintManager mFpm;
    private PowerManager.WakeLock mWakeLock;

    /**
     * When we receive a
     * {@link com.android.internal.telephony.TelephonyIntents#ACTION_SIM_STATE_CHANGED} broadcast,
     * and then pass a result via our handler to {@link KeyguardUpdateMonitor#handleSimStateChange},
     * we need a single object to pass to the handler.  This class helps decode
     * the intent and provide a {@link SimCard.State} result.
     */
    // modify for mock
    @VisibleForTesting
    static class SimData {
        // public State simState;
        public final IccCardConstants.State simState;
        // public int slotId;
        public int phoneId = 0;
        public int subId;
        public int simMECategory = 0;

        /***SimData(State state, int slot, int id) {
            simState = state;
            slotId = slot;
            subId = id;
        } ** Google defaut */

        SimData(IccCardConstants.State state, int phoneId, int subId) {
            this.simState = state;
            this.phoneId = phoneId;
            this.subId = subId;
        }
        // modify for mock
        @VisibleForTesting
        SimData(IccCardConstants.State state, int phoneId, int subId, int meCategory) {
            this.simState = state;
            this.phoneId = phoneId;
            this.subId = subId ;
            this.simMECategory = meCategory;
        }

        static SimData fromIntent(Intent intent) {
            // State state;
            /// M:Add for ALPS02296548
            IccCardConstants.State state;
            if (!TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(intent.getAction())
                   && !TelephonyIntents.ACTION_UNLOCK_SIM_LOCK.equals(intent.getAction())) {
                throw new IllegalArgumentException("only handles intent ACTION_SIM_STATE_CHANGED");
            }
            /// M:
            int meCategory = 0;
            String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
            // int slotId = intent.getIntExtra(PhoneConstants.SLOT_KEY, 0);
            int phoneId = intent.getIntExtra(PhoneConstants.SLOT_KEY, 0);
            int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
                final String absentReason = intent
                    .getStringExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON);

                if (IccCardConstants.INTENT_VALUE_ABSENT_ON_PERM_DISABLED.equals(
                        absentReason)) {
                    state = IccCardConstants.State.PERM_DISABLED;
                } else {
                    state = IccCardConstants.State.ABSENT;
                }
            } else if (IccCardConstants.INTENT_VALUE_ICC_READY.equals(stateExtra)) {
                state = IccCardConstants.State.READY;
            } else if (IccCardConstants.INTENT_VALUE_ICC_LOCKED.equals(stateExtra)) {
                final String lockedReason = intent
                        .getStringExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON);
                Log.d(TAG, "INTENT_VALUE_ICC_LOCKED, lockedReason=" + lockedReason);

                if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PIN.equals(lockedReason)) {
                    state = IccCardConstants.State.PIN_REQUIRED;
                } else if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PUK.equals(lockedReason)) {
                    state = IccCardConstants.State.PUK_REQUIRED;
                } else if (IccCardConstants.INTENT_VALUE_LOCKED_NETWORK.equals(lockedReason)) {
                    meCategory = 0;
                    state = IccCardConstants.State.NETWORK_LOCKED;
                } else if (IccCardConstants.INTENT_VALUE_LOCKED_NETWORK_SUBSET
                        .equals(lockedReason)) {
                    meCategory = 1;
                    state = IccCardConstants.State.NETWORK_LOCKED;
                } else if (IccCardConstants.INTENT_VALUE_LOCKED_SERVICE_PROVIDER
                        .equals(lockedReason)) {
                    meCategory = 2;
                    state = IccCardConstants.State.NETWORK_LOCKED;
                } else if (IccCardConstants.INTENT_VALUE_LOCKED_CORPORATE.equals(lockedReason)) {
                    meCategory = 3;
                    state = IccCardConstants.State.NETWORK_LOCKED;
                } else if (IccCardConstants.INTENT_VALUE_LOCKED_SIM.equals(lockedReason)) {
                    meCategory = 4;
                    state = IccCardConstants.State.NETWORK_LOCKED;
                } else {
                    state = IccCardConstants.State.UNKNOWN;
                }
            } else if (IccCardConstants.INTENT_VALUE_LOCKED_NETWORK.equals(stateExtra)) {
                state = IccCardConstants.State.NETWORK_LOCKED;
            } else if (IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(stateExtra)
                        || IccCardConstants.INTENT_VALUE_ICC_IMSI.equals(stateExtra)) {
                // This is required because telephony doesn't return to "READY" after
                // these state transitions. See bug 7197471.
                state = IccCardConstants.State.READY;
            } else if (IccCardConstants.INTENT_VALUE_ICC_NOT_READY.equals(stateExtra)) {///M:
                state = IccCardConstants.State.NOT_READY;
            } else {
                state = IccCardConstants.State.UNKNOWN;
            }
            // return new SimData(state, slotId, subId);
            return new SimData(state, phoneId, subId, meCategory);
        }

        public String toString() {
            // return "SimData{state=" + simState + ",slotId=" + slotId + ",subId=" + subId + "}";
            return simState.toString();
        }
    }

    public static class BatteryStatus {
        public final int status;
        public final int level;
        public final int plugged;
        public final int health;
        public BatteryStatus(int status, int level, int plugged, int health) {
            this.status = status;
            this.level = level;
            this.plugged = plugged;
            this.health = health;
        }

        /**
         * Determine whether the device is plugged in (USB, power, or wireless).
         * @return true if the device is plugged in.
         */
        public boolean isPluggedIn() {
            return plugged == BatteryManager.BATTERY_PLUGGED_AC
                    || plugged == BatteryManager.BATTERY_PLUGGED_USB
                    || plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
        }

        /**
         * Whether or not the device is charged. Note that some devices never return 100% for
         * battery level, so this allows either battery level or status to determine if the
         * battery is charged.
         * @return true if the device is charged
         */
        public boolean isCharged() {
            return status == BATTERY_STATUS_FULL || level >= 100;
        }

        /**
         * Whether battery is low and needs to be charged.
         * @return true if battery is low
         */
        public boolean isBatteryLow() {
            return level < LOW_BATTERY_THRESHOLD;
        }

    }

    /* package */ static class SubInfoContent {
        public final int subInfoId;
        public final String column;
        public final String sValue;
        public final int iValue;
        public SubInfoContent(int subInfoId, String column, String sValue, int iValue) {
            this.subInfoId = subInfoId;
            this.column = column;
            this.sValue = sValue;
            this.iValue = iValue;
        }
    }

    public static KeyguardUpdateMonitor getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new KeyguardUpdateMonitor(context);
        }
        return sInstance;
    }

    protected void handleStartedWakingUp() {
        Log.d(TAG, "handleStartedWakingUp");
        updateFingerprintListeningState();
        final int count = mCallbacks.size();
        for (int i = 0; i < count; i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onStartedWakingUp();
            }
        }
    }

    protected void handleFinishedGoingToSleep(int arg1) {
        if (DEBUG) Log.d(TAG, "handleFinishedGoingToSleep");
        // byd added for byd_fps
        if(isBydFps) {
		    mFpWakeMode = FP_WAKE_DIRECT_UNLOCK;
        }
        clearFingerprintRecognized();
        final int count = mCallbacks.size();
        for (int i = 0; i < count; i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onFinishedGoingToSleep(arg1);
            }
        }
		// byd added
		if(isBydFps) {
			mFingerprintHasAuthenticated = false;
		}
        updateFingerprintListeningState();
    }

    /**
     * IMPORTANT: Must be called from UI thread.
     */
    public void dispatchSetBackground(Bitmap bmp) {
        if (DEBUG) Log.d(TAG, "dispatchSetBackground");
        final int count = mCallbacks.size();
        for (int i = 0; i < count; i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onSetBackground(bmp);
            }
        }
    }

    private void handleUserInfoChanged(int userId) {
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onUserInfoChanged(userId);
            }
        }
    }

    private KeyguardUpdateMonitor(Context context) {
        mContext = context;
        mSubscriptionManager = SubscriptionManager.from(context);
        mPowerManager = context.getSystemService(PowerManager.class);
        mDeviceProvisioned = isDeviceProvisionedInSettingsDb();
        //HB. Comments : whether the lock screen is secured , Engerineer : wxue , Date : 2017年6月23日 ,begin
        mLockPatternUtils = new LockPatternUtils(mContext);
        //HB. end

        if (DEBUG) Log.d(TAG, "mDeviceProvisioned is:" + mDeviceProvisioned);

        // Since device can't be un-provisioned, we only need to register a content observer
        // to update mDeviceProvisioned when we are...
        if (!mDeviceProvisioned) {
            watchForDeviceProvisioning();
        }

        // Take a guess at initial SIM state, battery status and PLMN until we get an update
        mBatteryStatus = new BatteryStatus(BATTERY_STATUS_UNKNOWN, 100, 0, 0);

        initMembers();

        // Watch for interesting updates
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED);
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        //filter.addAction(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION);
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);

        /// M: SIM lock unlock request after dismiss
        filter.addAction(TelephonyIntents.ACTION_UNLOCK_SIM_LOCK);

        /// M: ALPS02139605 It always prompt "incorrect SIM PIN code, you have 3 remaining attempt"
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);

        context.registerReceiver(mBroadcastReceiver, filter);

        final IntentFilter bootCompleteFilter = new IntentFilter();
        bootCompleteFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        bootCompleteFilter.addAction(Intent.ACTION_BOOT_COMPLETED);
        context.registerReceiver(mBroadcastReceiver, bootCompleteFilter);

        final IntentFilter allUserFilter = new IntentFilter();
        allUserFilter.addAction(Intent.ACTION_USER_INFO_CHANGED);
        allUserFilter.addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED);
        allUserFilter.addAction(ACTION_FACE_UNLOCK_STARTED);
        allUserFilter.addAction(ACTION_FACE_UNLOCK_STOPPED);
        allUserFilter.addAction(DevicePolicyManager.ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED);
        context.registerReceiverAsUser(mBroadcastAllReceiver, UserHandle.ALL, allUserFilter,
                null, null);

        mSubscriptionManager.addOnSubscriptionsChangedListener(mSubscriptionListener);
        try {
            ActivityManagerNative.getDefault().registerUserSwitchObserver(
                    new IUserSwitchObserver.Stub() {
                        @Override
                        public void onUserSwitching(int newUserId, IRemoteCallback reply) {
                            mHandler.sendMessage(mHandler.obtainMessage(MSG_USER_SWITCHING,
                                    newUserId, 0, reply));
                        }
                        @Override
                        public void onUserSwitchComplete(int newUserId) throws RemoteException {
                            mHandler.sendMessage(mHandler.obtainMessage(MSG_USER_SWITCH_COMPLETE,
                                    newUserId, 0));
                        }
                        @Override
                        public void onForegroundProfileSwitch(int newProfileId) {
                            // Ignore.
                        }
                    });
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        mTrustManager = (TrustManager) context.getSystemService(Context.TRUST_SERVICE);
        mTrustManager.registerTrustListener(this);

        mFpm = (FingerprintManager) context.getSystemService(Context.FINGERPRINT_SERVICE);
        // byd added
		isBydFps = isExist(FP_BYDFPS);
        updateFingerprintListeningState();
    }

    private void updateFingerprintListeningState() {
        boolean shouldListenForFingerprint = shouldListenForFingerprint();
        //Log.i(TAG,"---updateFingerprintListeningState()--shouldListenForFingerprint = " +shouldListenForFingerprint + " mFingerprintDetectionRunning = " + mFingerprintDetectionRunning);
        if (mFingerprintDetectionRunning && !shouldListenForFingerprint) {
            stopListeningForFingerprint();
        } else if (!mFingerprintDetectionRunning && shouldListenForFingerprint) {
        	// modify by wxue 20170427 start
            //startListeningForFingerprint();
        	//Log.i(TAG,"---updateFingerprintListeningState()--mFingerprintLockoutTimeout = " + mFingerprintLockoutTimeout + " mFingerprintLockout = " + mFingerprintLockout );
        	if(mFingerprintLockoutTimeout && !mFingerprintLockout){
        		startListeningForFingerprint();
        	}else{
        		//Log.i(TAG,"---updateFingerprintListeningState()--fingerprint locked " );
        	}
            // modify by wxue 20170427 end
        }
    }

    private boolean shouldListenForFingerprint() {
    	//HB. Comments : can use fingerprint when other window occluded keyguard window, Engerineer : wxue , Date : 2017年6月20日 ,begin
    	boolean isSecure = mLockPatternUtils.isSecure(getCurrentUser());
    	boolean isLocked = (mLockPatternUtils.getLockoutAttemptDeadline(getCurrentUser()) != 0);
    	if(DEBUG) Log.i(TAG,"--shouldListenForFingerprint()--mKeyguardIsVisible = " + mKeyguardIsVisible + " mOccluded = " + mOccluded + " mSwitchingUser = " + mSwitchingUser
    			+  " isSecure = " + isSecure + " isLocked = " + isLocked);
        // byd added
		if(isBydFps) {
            if(!mKeyguardIsVisible && mOccluded && isSecure && !isLocked && !mFingerprintHasAuthenticated){
        		return true;
        	}
        	//HB. end
            return mKeyguardIsVisible && !mSwitchingUser && isSecure && !isLocked && !mFingerprintHasAuthenticated;
		} else {
        	if(!mKeyguardIsVisible && mOccluded && isSecure && !isLocked){
        		return true;
        	}
        	//HB. end
            return mKeyguardIsVisible && !mSwitchingUser && isSecure && !isLocked;
		}
    }

    private void startListeningForFingerprint() {
        if (DEBUG) Log.v(TAG, "startListeningForFingerprint()");
        int userId = ActivityManager.getCurrentUser();
        if (isUnlockWithFingerPrintPossible(userId)) {
        	//if (DEBUG) Log.i(TAG,"---startListeningForFingerprint)--success");
            mUserHasAuthenticatedSinceBoot = mTrustManager.hasUserAuthenticatedSinceBoot(
                    ActivityManager.getCurrentUser());
            if (mFingerprintCancelSignal != null) {
                mFingerprintCancelSignal.cancel();
            }
            mFingerprintCancelSignal = new CancellationSignal();
			//Log.d(TAG, "startListeningForFingerprint() mFingerprintCancelSignal  = " + mFingerprintCancelSignal + ", mAuthenticationCallback = " + mAuthenticationCallback);
			// byd modified for byd_fps
			if(isBydFps) {
				mFingerprintHasAuthenticated = false;
			}
            mFpm.authenticate(null, mFingerprintCancelSignal, 0, mAuthenticationCallback, null, userId);
            setFingerprintRunningDetectionRunning(true);
        }
    }

    public boolean isUnlockWithFingerPrintPossible(int userId) {
        return mFpm != null && mFpm.isHardwareDetected() && !isFingerprintDisabled(userId)
                && mFpm.getEnrolledFingerprints(userId).size() > 0;
    }

    private void stopListeningForFingerprint() {
        if (DEBUG) Log.v(TAG, "stopListeningForFingerprint()");
        if (isFingerprintDetectionRunning()) {
            mFingerprintCancelSignal.cancel();
            mFingerprintCancelSignal = null;
        }
        setFingerprintRunningDetectionRunning(false);
    }

    private boolean isDeviceProvisionedInSettingsDb() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.DEVICE_PROVISIONED, 0) != 0;
    }

    private void watchForDeviceProvisioning() {
        mDeviceProvisionedObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                mDeviceProvisioned = isDeviceProvisionedInSettingsDb();
                if (mDeviceProvisioned) {
                    mHandler.sendEmptyMessage(MSG_DEVICE_PROVISIONED);
                }
                if (DEBUG) Log.d(TAG, "DEVICE_PROVISIONED state = " + mDeviceProvisioned);
            }
        };

        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.DEVICE_PROVISIONED),
                false, mDeviceProvisionedObserver);

        // prevent a race condition between where we check the flag and where we register the
        // observer by grabbing the value once again...
        boolean provisioned = isDeviceProvisionedInSettingsDb();
        if (provisioned != mDeviceProvisioned) {
            mDeviceProvisioned = provisioned;
            if (mDeviceProvisioned) {
                mHandler.sendEmptyMessage(MSG_DEVICE_PROVISIONED);
            }
        }
    }

    /**
     * Handle {@link #MSG_DPM_STATE_CHANGED}
     */
    protected void handleDevicePolicyManagerStateChanged() {
        for (int i = mCallbacks.size() - 1; i >= 0; i--) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onDevicePolicyManagerStateChanged();
            }
        }
    }

    /**
     * Handle {@link #MSG_USER_SWITCHING}
     */
    protected void handleUserSwitching(int userId, IRemoteCallback reply) {
        mSwitchingUser = true;
        updateFingerprintListeningState();

        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onUserSwitching(userId);
            }
        }
        try {
            reply.sendResult(null);
        } catch (RemoteException e) {
        }
    }

    /**
     * Handle {@link #MSG_USER_SWITCH_COMPLETE}
     */
    protected void handleUserSwitchComplete(int userId) {
        mSwitchingUser = false;
        updateFingerprintListeningState();

        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onUserSwitchComplete(userId);
            }
        }
    }

    /**
     * This is exposed since {@link Intent#ACTION_BOOT_COMPLETED} is not sticky. If
     * keyguard crashes sometime after boot, then it will never receive this
     * broadcast and hence not handle the event. This method is ultimately called by
     * PhoneWindowManager in this case.
     */
    public void dispatchBootCompleted() {
        mHandler.sendEmptyMessage(MSG_BOOT_COMPLETED);
    }

    /**
     * Handle {@link #MSG_BOOT_COMPLETED}
     */
    protected void handleBootCompleted() {
        if (mBootCompleted) return;
        mBootCompleted = true;
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onBootCompleted();
            }
        }
    }

    /**
     * We need to store this state in the KeyguardUpdateMonitor since this class will not be
     * destroyed.
     */
    public boolean hasBootCompleted() {
        return mBootCompleted;
    }

    /**
     * Handle {@link #MSG_DEVICE_PROVISIONED}
     */
    protected void handleDeviceProvisioned() {
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onDeviceProvisioned();
            }
        }
        if (mDeviceProvisionedObserver != null) {
            // We don't need the observer anymore...
            mContext.getContentResolver().unregisterContentObserver(mDeviceProvisionedObserver);
            mDeviceProvisionedObserver = null;
        }
    }

    /**
     * Handle {@link #MSG_PHONE_STATE_CHANGED}
     */
        /**protected void handlePhoneStateChanged(String newState) {
        if (DEBUG) Log.d(TAG, "handlePhoneStateChanged(" + newState + ")");
        if (TelephonyManager.EXTRA_STATE_IDLE.equals(newState)) {
            mPhoneState = TelephonyManager.CALL_STATE_IDLE;
        } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(newState)) {
            mPhoneState = TelephonyManager.CALL_STATE_OFFHOOK;
        } else if (TelephonyManager.EXTRA_STATE_RINGING.equals(newState)) {
            mPhoneState = TelephonyManager.CALL_STATE_RINGING;
        }
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onPhoneStateChanged(mPhoneState);
            }
        }
    } ***/


    /**
     * Handle {@link #MSG_PHONE_STATE_CHANGED}
     *  Set mPhoneState as OFFHOOK if one subscription is in OFFHOOK state.
     *  Otherwise, set as RINGING state if one subscription is in RINGING state.
     *  Set as IDLE if all subscriptions are in IDLE state.
     */
    protected void handlePhoneStateChanged() {
        if (DEBUG) {
            Log.d(TAG, "handlePhoneStateChanged");
        }
        mPhoneState = TelephonyManager.CALL_STATE_IDLE;
        for (int i = 0; i < KeyguardUtils.getNumOfPhone(); i++) {
            int subId = KeyguardUtils.getSubIdUsingPhoneId(i);
            int callState = TelephonyManager.getDefault().getCallState(subId);
            if (callState == TelephonyManager.CALL_STATE_OFFHOOK) {
                mPhoneState = callState;
            } else if (callState == TelephonyManager.CALL_STATE_RINGING
                    && mPhoneState == TelephonyManager.CALL_STATE_IDLE) {
                mPhoneState = callState;
            }
        }

        Log.d(TAG, "handlePhoneStateChanged() - mPhoneState = " + mPhoneState);

        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onPhoneStateChanged(mPhoneState);
            }
        }
    }

    /**
     * Handle {@link #MSG_RINGER_MODE_CHANGED}
     */
    protected void handleRingerModeChange(int mode) {
        if (DEBUG) Log.d(TAG, "handleRingerModeChange(" + mode + ")");
        mRingMode = mode;
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onRingerModeChanged(mode);
            }
        }
    }

    /**
     * Handle {@link #MSG_TIME_UPDATE}
     */
    private void handleTimeUpdate() {
        if (DEBUG) Log.d(TAG, "handleTimeUpdate");
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onTimeChanged();
            }
        }
    }

    /**
     * Handle {@link #MSG_BATTERY_UPDATE}
     */
    private void handleBatteryUpdate(BatteryStatus status) {
        if (DEBUG) Log.d(TAG, "handleBatteryUpdate");
        final boolean batteryUpdateInteresting = isBatteryUpdateInteresting(mBatteryStatus, status);
        mBatteryStatus = status;
        if (batteryUpdateInteresting) {
            for (int i = 0; i < mCallbacks.size(); i++) {
                KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
                if (cb != null) {
                    cb.onRefreshBatteryInfo(status);
                }
            }
        }
    }

    /**
     * Handle {@link #MSG_CARRIER_INFO_UPDATE}
     */
    private void handleCarrierInfoUpdate(int phoneId) {
        /*if (DEBUG) {
            Log.d(TAG, "handleCarrierInfoUpdate: plmn = " + mTelephonyPlmn.get(phoneId)
                       + ", spn = " + mTelephonySpn.get(phoneId) + ", phoneId = " + phoneId);
        }

        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onRefreshCarrierInfo();
            }
        }*/
    }


    /**
     * Handle {@link #MSG_SIM_STATE_CHANGE}
     */
    private void printState() {
        for (int i = 0 ; i < KeyguardUtils.getNumOfPhone() ; i++) {
            Log.d(TAG, "Phone# " + i + ", state = " + mSimStateOfPhoneId.get(i)) ;
        }
    }

    /**
     * Handle {@link #MSG_SIM_STATE_CHANGE}
     */
/**    private void handleSimStateChange(int subId, int slotId, State state) {

        if (DEBUG_SIM_STATES) {
            Log.d(TAG, "handleSimStateChange(subId=" + subId + ", slotId="
                    + slotId + ", state=" + state +")");
        }

        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            Log.w(TAG, "invalid subId in handleSimStateChange()");
            return;
        }

        SimData data = mSimDatas.get(subId);
        final boolean changed;
        if (data == null) {
            data = new SimData(state, slotId, subId);
            mSimDatas.put(subId, data);
            changed = true; // no data yet; force update
        } else {
            changed = (data.simState != state || data.subId != subId || data.slotId != slotId);
            data.simState = state;
            data.subId = subId;
            data.slotId = slotId;
        }
        if (changed && state != State.UNKNOWN) {
            for (int i = 0; i < mCallbacks.size(); i++) {
                KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
                if (cb != null) {
                    cb.onSimStateChanged(subId, slotId, state);
                }
            }
        }
    } ***/

    /**
     * Handle {@link #MSG_SIM_STATE_CHANGE}
     */
    private void handleSimStateChange(SimData simArgs) {
        final IccCardConstants.State state = simArgs.simState;

        if (DEBUG) {
            Log.d(TAG, "handleSimStateChange: intentValue = " + simArgs + " "
                    + "state resolved to " + state.toString() + " phoneId=" + simArgs.phoneId);
        }
        if (state != IccCardConstants.State.UNKNOWN &&
            (state == IccCardConstants.State.NETWORK_LOCKED ||
             state != mSimStateOfPhoneId.get(simArgs.phoneId))) {

            mSimStateOfPhoneId.put(simArgs.phoneId, state);

            int phoneId = simArgs.phoneId ;
            if (DEBUG) Log.d(TAG, "handleSimStateChange phoneId = " + phoneId) ;

            printState() ;

            for (int i = 0; i < mCallbacks.size(); i++) {
                KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
                if (cb != null) {
                    cb.onSimStateChangedUsingPhoneId(phoneId, state);
                }
            }
        }
    }

    /**
     * Handle {@link #MSG_SERVICE_STATE_CHANGE}
     */
    private void handleServiceStateChange(int subId, ServiceState serviceState) {
        if (DEBUG) {
            Log.d(TAG,
                    "handleServiceStateChange(subId=" + subId + ", serviceState=" + serviceState);
        }

        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            Log.w(TAG, "invalid subId in handleServiceStateChange()");
            return;
        }

        mServiceStates.put(subId, serviceState);

        for (int j = 0; j < mCallbacks.size(); j++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(j).get();
            if (cb != null) {
                cb.onRefreshCarrierInfo();
            }
        }
    }

    /**
     * Handle {@link #MSG_KEYGUARD_VISIBILITY_CHANGED}
     */
    private void handleKeyguardVisibilityChanged(int showing) {
        if (DEBUG) Log.d(TAG, "handleKeyguardVisibilityChanged(" + showing + ")");
        boolean isShowing = (showing == 1);
        mKeyguardIsVisible = isShowing;
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onKeyguardVisibilityChangedRaw(isShowing);
            }
        }
        updateFingerprintListeningState();
		// byd added for byd_fps when keyguarddone
		if(showing != 1 && isUnlockWithFingerPrintPossible(ActivityManager.getCurrentUser()) && isBydFps) {
            mFingerprintHasAuthenticated = false;
		}
    }

    /**
     * Handle {@link #MSG_KEYGUARD_RESET}
     */
    private void handleKeyguardReset() {
        if (DEBUG) Log.d(TAG, "handleKeyguardReset");
        if (!isUnlockingWithFingerprintAllowed()) {
            updateFingerprintListeningState();
        }
    }

    /**
     * Handle {@link #MSG_KEYGUARD_BOUNCER_CHANGED}
     * @see #sendKeyguardBouncerChanged(boolean)
     */
    private void handleKeyguardBouncerChanged(int bouncer) {
        if (DEBUG) Log.d(TAG, "handleKeyguardBouncerChanged(" + bouncer + ")");
        boolean isBouncer = (bouncer == 1);
        mBouncer = isBouncer;
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onKeyguardBouncerChanged(isBouncer);
            }
        }
    }

    /**
     * Handle {@link #MSG_REPORT_EMERGENCY_CALL_ACTION}
     */
    private void handleReportEmergencyCallAction() {
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onEmergencyCallAction();
            }
        }
    }

    private static boolean isBatteryUpdateInteresting(BatteryStatus old, BatteryStatus current) {
        final boolean nowPluggedIn = current.isPluggedIn();
        final boolean wasPluggedIn = old.isPluggedIn();
        final boolean stateChangedWhilePluggedIn =
            wasPluggedIn == true && nowPluggedIn == true
            && (old.status != current.status);

        // change in plug state is always interesting
        if (wasPluggedIn != nowPluggedIn || stateChangedWhilePluggedIn) {
            return true;
        }

        // change in battery level while plugged in
        // if (nowPluggedIn && old.level != current.level) {
        /// M: We remove "nowPluggedIn" condition here.
        /// To fix the issue that if HW give up a low battery level(below threshold)
        /// and then a high battery level(above threshold) while device is not pluggin,
        /// then Keyguard may never be able be show
        /// charging text on screen when pluggin
        if (old.level != current.level) {
            return true;
        }

        // change where battery needs charging
        if (!nowPluggedIn && current.isBatteryLow() && current.level != old.level) {
            return true;
        }
        return false;
    }

    /**
     * @param intent The intent with action {@link TelephonyIntents#SPN_STRINGS_UPDATED_ACTION}
     * @return The string to use for the plmn, or null if it should not be shown.
     */
    private CharSequence getTelephonyPlmnFrom(Intent intent) {
        if (intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_PLMN, false)) {
            final String plmn = intent.getStringExtra(TelephonyIntents.EXTRA_PLMN);
            return (plmn != null) ? plmn : getDefaultPlmn();
        }
        return null;
    }

    /**
     * @return The default plmn (no service)
     */
    public CharSequence getDefaultPlmn() {
        return mContext.getResources().getText(R.string.keyguard_carrier_default);
    }

    /**
     * @param intent The intent with action {@link Telephony.Intents#SPN_STRINGS_UPDATED_ACTION}
     * @return The string to use for the plmn, or null if it should not be shown.
     */
    private CharSequence getTelephonySpnFrom(Intent intent) {
        if (intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_SPN, false)) {
            final String spn = intent.getStringExtra(TelephonyIntents.EXTRA_SPN);
            if (spn != null) {
                return spn;
            }
        }
        return null;
    }

    /**
     * Remove the given observer's callback.
     *
     * @param callback The callback to remove
     */
    public void removeCallback(KeyguardUpdateMonitorCallback callback) {
        if (DEBUG) Log.v(TAG, "*** unregister callback for " + callback);
        for (int i = mCallbacks.size() - 1; i >= 0; i--) {
            if (mCallbacks.get(i).get() == callback) {
                mCallbacks.remove(i);
            }
        }
    }

    /**
     * Register to receive notifications about general keyguard information
     * (see {@link InfoCallback}.
     * @param callback The callback to register
     */
    public void registerCallback(KeyguardUpdateMonitorCallback callback) {
        if (DEBUG) Log.v(TAG, "*** register callback for " + callback);
        // Prevent adding duplicate callbacks
        for (int i = 0; i < mCallbacks.size(); i++) {
            if (mCallbacks.get(i).get() == callback) {
                if (DEBUG) Log.e(TAG, "Object tried to add another callback",
                        new Exception("Called by"));
                return;
            }
        }
        mCallbacks.add(new WeakReference<KeyguardUpdateMonitorCallback>(callback));
        removeCallback(null); // remove unused references
        sendUpdates(callback);

        ///M: in order to improve performance, add a flag
        // to fliter redundant visibility change callbacks
        mNewClientRegUpdateMonitor = true;
    }

    private void sendUpdates(KeyguardUpdateMonitorCallback callback) {
        // Notify listener of the current state
        callback.onRefreshBatteryInfo(mBatteryStatus);
        callback.onTimeChanged();
        callback.onRingerModeChanged(mRingMode);
        callback.onPhoneStateChanged(mPhoneState);
        callback.onRefreshCarrierInfo();
        callback.onClockVisibilityChanged();
        /**for (Entry<Integer, SimData> data : mSimDatas.entrySet()) {
            final SimData state = data.getValue();
            callback.onSimStateChanged(state.subId, state.slotId, state.simState);
        } **/
        for (int phoneId = 0 ; phoneId < KeyguardUtils.getNumOfPhone() ; phoneId++) {
            //callback.onRefreshCarrierInfo(phoneId, mTelephonyPlmn.get(phoneId),
            //                              mTelephonySpn.get(phoneId));
            callback.onSimStateChangedUsingPhoneId(phoneId, mSimStateOfPhoneId.get(phoneId));
        }
    }

    public void sendKeyguardVisibilityChanged(boolean showing) {
        ///M: in order to improve performance we skip callbacks if no new client registered
        if (mNewClientRegUpdateMonitor || showing != mShowing) {
            if (DEBUG) Log.d(TAG, "sendKeyguardVisibilityChanged(" + showing + ")");
            Message message = mHandler.obtainMessage(MSG_KEYGUARD_VISIBILITY_CHANGED);
            message.arg1 = showing ? 1 : 0;
            message.sendToTarget();
            mNewClientRegUpdateMonitor = false;
            mShowing = showing;
        }
    }

    public void sendKeyguardReset() {
        mHandler.obtainMessage(MSG_KEYGUARD_RESET).sendToTarget();
    }

    /**
     * @see #handleKeyguardBouncerChanged(int)
     */
    public void sendKeyguardBouncerChanged(boolean showingBouncer) {
        if (DEBUG) Log.d(TAG, "sendKeyguardBouncerChanged(" + showingBouncer + ")");
        Message message = mHandler.obtainMessage(MSG_KEYGUARD_BOUNCER_CHANGED);
        message.arg1 = showingBouncer ? 1 : 0;
        message.sendToTarget();
    }

    //HB. Comments : can use fingerprint when other window occluded keyguard window , Engerineer : wxue , Date : 2017年6月20日 ,begin
    public void sendKeyguardOccluded(boolean occluded){
    	if (DEBUG) Log.d(TAG, "sendKeyguardOccluded(" + occluded + ")");
    	Message message = mHandler.obtainMessage(MSG_KEYGUARD_OCCLUDED);
        message.arg1 = occluded ? 1 : 0;
        message.sendToTarget();
    }
    
    private void handleKeyguardOccluded(int occluded) {
        if (DEBUG) Log.d(TAG, "handleKeyguardOccluded(" + occluded + ")");
        boolean isOccluded = (occluded == 1);
        mOccluded = isOccluded;
    }
    //HB. end
    
    /** M:
     * get SIM state of phoneId.
     * @param phoneId phoneId.
     * @return sim state.
     */
    public IccCardConstants.State getSimStateOfPhoneId(int phoneId) {
        return mSimStateOfPhoneId.get(phoneId);
    }

    /**
     * Report that the user successfully entered the SIM PIN or PUK/SIM PIN so we
     * have the information earlier than waiting for the intent
     * broadcast from the telephony code.
     *
     * NOTE: Because handleSimStateChange() invokes callbacks immediately without going
     * through mHandler, this *must* be called from the UI thread.
     * @param phoneId phoneId.
     */
    /// M: modify for sim lock
    public void reportSimUnlocked(int phoneId) {
        // if (DEBUG_SIM_STATES) Log.v(TAG, "reportSimUnlocked(subId=" + subId + ")");
        // int slotId = SubscriptionManager.getSlotId(subId);
        // handleSimStateChange(subId, slotId, State.READY);
        int subId = KeyguardUtils.getSubIdUsingPhoneId(phoneId) ;
        handleSimStateChange(new SimData(IccCardConstants.State.READY, phoneId, subId));
    }

    /**
     * Report that the emergency call button has been pressed and the emergency dialer is
     * about to be displayed.
     *
     * @param bypassHandler runs immediately.
     *
     * NOTE: Must be called from UI thread if bypassHandler == true.
     */
    public void reportEmergencyCallAction(boolean bypassHandler) {
        if (!bypassHandler) {
            mHandler.obtainMessage(MSG_REPORT_EMERGENCY_CALL_ACTION).sendToTarget();
        } else {
            handleReportEmergencyCallAction();
        }
    }

    /**
     * Get PLMN of phone id.
     * @param phoneId phoneId.
     * @return PLMN.
     */
    public CharSequence getTelephonyPlmn(int phoneId) {
        return mTelephonyPlmn.get(phoneId);
    }

    /**
     * Get SPN of phone id.
     * @param phoneId phoneId.
     * @return SPN.
     */
    public CharSequence getTelephonySpn(int phoneId) {
        return mTelephonySpn.get(phoneId);
    }

    /**
     * @return Whether the device is provisioned (whether they have gone through
     *   the setup wizard)
     */
    public boolean isDeviceProvisioned() {
        if (mDeviceProvisioned == false) {
            Log.d(TAG, "isDeviceProvisioned get DEVICE_PROVISIONED from db again !!");
            return (0 != Settings.Global.getInt(
                    mContext.getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 0));
        } else {
            Log.d(TAG, "mDeviceProvisioned == true");
            return mDeviceProvisioned;
        }
    }

    public void clearFailedUnlockAttempts() {
        mFailedAttempts.delete(sCurrentUser);
        mFailedBiometricUnlockAttempts = 0;
    }

    public int getFailedUnlockAttempts() {
        return mFailedAttempts.get(sCurrentUser, 0);
    }

    public void reportFailedUnlockAttempt() {
        mFailedAttempts.put(sCurrentUser, getFailedUnlockAttempts() + 1);
    }

    public void clearFingerprintRecognized() {
        mUserFingerprintAuthenticated.clear();
    }

    public boolean isSimPinVoiceSecure() {
        // TODO: only count SIMs that handle voice
        return isSimPinSecure();
    }

    // public boolean isSimPinSecure() {
        // True if any SIM is pin secure
        // for (SubscriptionInfo info : getSubscriptionInfo(false /* forceReload */)) {
        //    if (isSimPinSecure(getSimState(info.getSubscriptionId()))) return true;
     //   }
     //   return false;
    // }

    public boolean isSimLocked() {
        boolean bSimLocked = false;

        for (int phoneId = 0; phoneId < KeyguardUtils.getNumOfPhone() ; phoneId++) {
            if (isSimLocked(mSimStateOfPhoneId.get(phoneId))) {
                bSimLocked = true;
                break;
            }
        }
        return bSimLocked;
    }

    public static boolean isSimLocked(IccCardConstants.State state) {
        return state == IccCardConstants.State.PIN_REQUIRED
        || state == IccCardConstants.State.PUK_REQUIRED
        || (state == IccCardConstants.State.NETWORK_LOCKED &&
            KeyguardUtils.isMediatekSimMeLockSupport())
        || state == IccCardConstants.State.PERM_DISABLED;
    }


    /*public State getSimState(int subId) {
        if (mSimDatas.containsKey(subId)) {
            return mSimDatas.get(subId).simState;
        } else {
            return State.UNKNOWN;
        }
    }*/

    /**
     * @return true if and only if the state has changed for the specified {@code slotId}
     */
    private boolean refreshSimState(int subId, int slotId) {
        Log.d(TAG, "refreshSimState() - sub = " + subId + " phone = " + slotId) ;

        // This is awful. It exists because there are two APIs for getting the SIM status
        // that don't return the complete set of values and have different types. In Keyguard we
        // need IccCardConstants, but TelephonyManager would only give us
        // TelephonyManager.SIM_STATE*, so we retrieve it manually.
        final TelephonyManager tele = TelephonyManager.from(mContext);
        int simState =  tele.getSimState(slotId);
        State state;
        try {
            state = State.intToState(simState);
        } catch(IllegalArgumentException ex) {
            Log.w(TAG, "Unknown sim state: " + simState);
            state = State.UNKNOWN;
        }
        /**SimData data = mSimDatas.get(subId);
        final boolean changed;
        if (data == null) {
            data = new SimData(state, slotId, subId);
            mSimDatas.put(subId, data);
            changed = true; // no data yet; force update
        } else {
            changed = data.simState != state;
            data.simState = state;
        }
        return changed; **/

        State oriState = mSimStateOfPhoneId.get(slotId) ;
        final boolean changed;
        changed = oriState != state;
        if (changed) {
            mSimStateOfPhoneId.put(slotId, state);
        }

        Log.d(TAG, "refreshSimState() - phoneId = " + slotId + ", ori-state = " + oriState
            + ", new-state = " + state + ", changed = " + changed) ;
        return changed;
    }

    /**public static boolean isSimPinSecure(IccCardConstants.State state) {
        final IccCardConstants.State simState = state;
        return (simState == IccCardConstants.State.PIN_REQUIRED
                || simState == IccCardConstants.State.PUK_REQUIRED
                || simState == IccCardConstants.State.PERM_DISABLED);
    } ***/

    public boolean isSimPinSecure() {
        boolean isSecure = false;
        for (int phoneId = 0 ; phoneId < KeyguardUtils.getNumOfPhone() ; phoneId++) {
            if (isSimPinSecure(phoneId)) {
                isSecure = true;
                break;
            }
        }
        return isSecure;
    }

    /** M:
       * Check if the subscription is in SIM pin lock state and wait user to unlock.
       * @param phoneId phoneId.
       * @return Returns true if the subscription is in SIM pin lock state and not yet dismissed.
       **/
    public boolean isSimPinSecure(int phoneId) {
        IccCardConstants.State state = mSimStateOfPhoneId.get(phoneId);
        final IccCardConstants.State simState = state;
        return ((simState == IccCardConstants.State.PIN_REQUIRED
                || simState == IccCardConstants.State.PUK_REQUIRED
                || (simState == IccCardConstants.State.NETWORK_LOCKED) &&
                    KeyguardUtils.isMediatekSimMeLockSupport())
                && !getPinPukMeDismissFlagOfPhoneId(phoneId));
    }

    public DisplayClientState getCachedDisplayClientState() {
        return mDisplayClientState;
    }

    // TODO: use these callbacks elsewhere in place of the existing notifyScreen*()
    // (KeyguardViewMediator, KeyguardHostView)
    public void dispatchStartedWakingUp() {
        synchronized (this) {
            mDeviceInteractive = true;
        }
        mHandler.sendEmptyMessage(MSG_STARTED_WAKING_UP);
    }

    public void dispatchFinishedGoingToSleep(int why) {
        synchronized(this) {
            mDeviceInteractive = false;
        }
        mHandler.sendMessage(mHandler.obtainMessage(MSG_FINISHED_GOING_TO_SLEEP, why, 0));
    }

    public void dispatchScreenTurnedOn() {
        synchronized (this) {
            mScreenOn = true;
        }
    }

    public void dispatchScreenTurnedOff() {
        synchronized(this) {
            mScreenOn = false;
        }
    }

    public boolean isDeviceInteractive() {
        return mDeviceInteractive;
    }

    /**
     * Find the next SubscriptionId for a SIM in the given state, favoring lower slot numbers first.
     * @param state
     * @return subid or {@link SubscriptionManager#INVALID_SUBSCRIPTION_ID} if none found
     */
///TODO: temp disabled
/*  M:
    public int getNextSubIdForState(State state) {
*/
//        List<SubscriptionInfo> list = getSubscriptionInfo(false /* forceReload */);
/*        int resultId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        int bestSlotId = Integer.MAX_VALUE; // Favor lowest slot first
        for (int i = 0; i < list.size(); i++) {
            final SubscriptionInfo info = list.get(i);
            final int id = info.getSubscriptionId();
            int slotId = SubscriptionManager.getSlotId(id);
            if (state == getSimState(id) && bestSlotId > slotId ) {
                resultId = id;
                bestSlotId = slotId;
            }
        }
        return resultId;
    }
    */
    public SubscriptionInfo getSubscriptionInfoForSubId(int subId) {
        return getSubscriptionInfoForSubId(subId, false) ;
    }

    /**
     * get subscription info for related sub id.
     * @param subId subId
     * @param forceReload force to reload or not.
     * @return SubscriptionInfo
     */
    public SubscriptionInfo getSubscriptionInfoForSubId(int subId, boolean forceReload) {
        List<SubscriptionInfo> list = getSubscriptionInfo(forceReload /* forceReload */);
        for (int i = 0; i < list.size(); i++) {
            SubscriptionInfo info = list.get(i);
            if (subId == info.getSubscriptionId()) return info;
        }
        return null; // not found
    }

    /**
     * get sim lock phone id.
     * @return phone id.
     **/
    public int getSimPinLockPhoneId() {
        int currentSimPinPhoneId = KeyguardUtils.INVALID_PHONE_ID;
        for (int phoneId = 0 ; phoneId < KeyguardUtils.getNumOfPhone() ; phoneId++) {
            if (DEBUG) {
                Log.d(TAG, "getSimPinLockSubId, phoneId=" + phoneId
                    + " mSimStateOfPhoneId.get(phoneId)=" + mSimStateOfPhoneId.get(phoneId));
            }
            if (mSimStateOfPhoneId.get(phoneId) == IccCardConstants.State.PIN_REQUIRED
                && !getPinPukMeDismissFlagOfPhoneId(phoneId)) {
                currentSimPinPhoneId = phoneId;
                break;
            }
        }
        return currentSimPinPhoneId;
    }

    /**
     * get sim puk lock phone id.
     * @return phone id.
     **/
    public int getSimPukLockPhoneId() {
        int currentSimPukPhoneId = KeyguardUtils.INVALID_PHONE_ID;
        for (int phoneId = 0 ; phoneId < KeyguardUtils.getNumOfPhone() ; phoneId++) {
            if (DEBUG) {
                Log.d(TAG, "getSimPukLockSubId, phoneId=" + phoneId
                    + " mSimStateOfSub.get(phoneId)=" + mSimStateOfPhoneId.get(phoneId));
            }
            if (mSimStateOfPhoneId.get(phoneId) == IccCardConstants.State.PUK_REQUIRED
                && !getPinPukMeDismissFlagOfPhoneId(phoneId)
                && getRetryPukCountOfPhoneId(phoneId) != 0) {
                currentSimPukPhoneId = phoneId;
                break;
            }
        }
        return currentSimPukPhoneId;
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("KeyguardUpdateMonitor state:");
        pw.println("  SIM States:");
        for (SimData data : mSimDatas.values()) {
            pw.println("    " + data.toString());
        }
        pw.println("  Subs:");
        if (mSubscriptionInfo != null) {
            for (int i = 0; i < mSubscriptionInfo.size(); i++) {
                pw.println("    " + mSubscriptionInfo.get(i));
            }
        }
        pw.println("  Service states:");
        for (int subId : mServiceStates.keySet()) {
            pw.println("    " + subId + "=" + mServiceStates.get(subId));
        }
    }

    /********************************************************
     ** Mediatek add begin
     ********************************************************/

    /// M: init members
    private void initMembers() {

        if (DEBUG) {
            Log.d(TAG, "initMembers() - NumOfPhone=" + KeyguardUtils.getNumOfPhone());
        }

        // Take a guess at initial SIM state, battery status and PLMN until we get an update
        for (int i = 0; i < KeyguardUtils.getNumOfPhone(); i++) {
            mSimStateOfPhoneId.put(i, IccCardConstants.State.UNKNOWN);
            mTelephonyPlmn.put(i, getDefaultPlmn());
            mTelephonyCsgId.put(i, "") ;
            mTelephonyHnbName.put(i, "");

            //ME lock Related
            mSimMeCategory.put(i, DEFAULT_ME_CATEGORY) ;
            mSimMeLeftRetryCount.put(i, DEFAULT_ME_RETRY_COUNT) ;
        }

        // Log.d(TAG, "initMembers() , mIsDisplayDevice=" + mIsDisplayDevice);
    }

    /// ---- Telephony Info begins ----
    /**
     ** M: Used to verify the lock type
     */
    public enum SimLockType {
        SIM_LOCK_PIN,
        SIM_LOCK_PUK,
        SIM_LOCK_ME
    }

    ///M: in order to improve performance, add a flag to
    // fliter redundant visibility change callbacks
    private boolean mNewClientRegUpdateMonitor = false;
    private boolean mShowing = true;

    /// M: SIM ME lock related info
    //current unlocking category of each SIM card.
    private static final int DEFAULT_ME_CATEGORY = 0 ;
    private HashMap<Integer, Integer> mSimMeCategory = new HashMap<Integer, Integer>();
    //current left retry count of current ME lock category.
    private static final int DEFAULT_ME_RETRY_COUNT = 5 ;
    private HashMap<Integer, Integer> mSimMeLeftRetryCount = new HashMap<Integer, Integer>();
    private static final String QUERY_SIMME_LOCK_RESULT =
            "com.mediatek.phone.QUERY_SIMME_LOCK_RESULT";
    private static final String SIMME_LOCK_LEFT_COUNT = "com.mediatek.phone.SIMME_LOCK_LEFT_COUNT";

    ///M: Dismiss flags
    private static final int PIN_PUK_ME_RESET = 0x0000;
    private static final int PIN_PUK_ME_DISMISSED = 0x0001;


    /// M: Flag used to indicate weather sim1 or sim2 card's pin/puk is dismissed by user.
    private int mPinPukMeDismissFlag = PIN_PUK_ME_RESET;

    private HashMap<Integer, CharSequence> mTelephonyHnbName = new HashMap<Integer, CharSequence>();
    private HashMap<Integer, CharSequence> mTelephonyCsgId = new HashMap<Integer, CharSequence>();

    /**
     ** M: Used to set specified sim card's pin or puk dismiss flag
     *
     * @param phoneId the id of the phone to set dismiss flag
     * @param dismiss true to dismiss this flag, false to clear
     */
    public void setPinPukMeDismissFlagOfPhoneId(int phoneId, boolean dismiss) {
        Log.d(TAG, "setPinPukMeDismissFlagOfPhoneId() - phoneId = " + phoneId) ;

        if (!KeyguardUtils.isValidPhoneId(phoneId)) {
            return;
        }

        int flag2Dismiss = PIN_PUK_ME_RESET;

        flag2Dismiss = PIN_PUK_ME_DISMISSED << phoneId;

        if (dismiss) {
            mPinPukMeDismissFlag |= flag2Dismiss;
        } else {
            mPinPukMeDismissFlag &= ~flag2Dismiss;
        }
    }

    /**
     ** M: Used to get specified sim card's pin or puk dismiss flag.
     * @param phoneId the id of the phone to get dismiss flag
     * @return Returns false if dismiss flag is set.
     */
    public boolean getPinPukMeDismissFlagOfPhoneId(int phoneId) {
        int flag2Check = PIN_PUK_ME_RESET;
        boolean result = false;

        flag2Check = PIN_PUK_ME_DISMISSED << phoneId;
        result = (mPinPukMeDismissFlag & flag2Check) == flag2Check ? true : false;

        return result;
    }

    /**
     *  M:Get the remaining puk count of the sim card with the simId.
     * @param phoneId the phone ID
     * @return Return  the PUK retry count
     */
    public int getRetryPukCountOfPhoneId(final int phoneId) {
        int GET_SIM_RETRY_EMPTY = -1; ///M: The default value of the remaining puk count

        if (phoneId == 3) {
            return SystemProperties.getInt("gsm.sim.retry.puk1.4", GET_SIM_RETRY_EMPTY);
        } else if (phoneId == 2) {
            return SystemProperties.getInt("gsm.sim.retry.puk1.3", GET_SIM_RETRY_EMPTY);
        } else if (phoneId == 1) {
            return SystemProperties.getInt("gsm.sim.retry.puk1.2", GET_SIM_RETRY_EMPTY);
        } else {
            return SystemProperties.getInt("gsm.sim.retry.puk1", GET_SIM_RETRY_EMPTY);
        }
    }

    /**
     * M: Start a thread to query SIM ME status.
     */
    private class simMeStatusQueryThread extends Thread {
        SimData simArgs;

        simMeStatusQueryThread(SimData simArgs) {
            this.simArgs = simArgs;
        }

        @Override
        public void run() {
            try {
                mSimMeCategory.put(simArgs.phoneId, simArgs.simMECategory);
                Log.d(TAG, "queryNetworkLock, phoneId =" + simArgs.phoneId + ", simMECategory ="
                        + simArgs.simMECategory);

                if (simArgs.simMECategory < 0 || simArgs.simMECategory > 5) {
                    return;
                }

                int subId = KeyguardUtils.getSubIdUsingPhoneId(simArgs.phoneId) ;
                Bundle bundle = ITelephonyEx.Stub.asInterface(ServiceManager.getService("phoneEx"))
                        .queryNetworkLock(subId, simArgs.simMECategory);
                boolean query_result = bundle.getBoolean(QUERY_SIMME_LOCK_RESULT, false);

                Log.d(TAG, "queryNetworkLock, " + "query_result =" + query_result);

                if (query_result) {
                    mSimMeLeftRetryCount.put(simArgs.phoneId,
                                             bundle.getInt(SIMME_LOCK_LEFT_COUNT, 5));
                } else {
                    Log.e(TAG, "queryIccNetworkLock result fail");
                }
                mHandler.sendMessage(mHandler.obtainMessage(MSG_SIM_STATE_CHANGE, simArgs));
            } catch (Exception e) {
                Log.e(TAG, "queryIccNetworkLock got exception: " + e.getMessage());
            }
        }
    }

    /** get ME Category.
     * @param phoneId phoneId
     * @return MeCategory.
     */
    public int getSimMeCategoryOfPhoneId(int phoneId) {
        return mSimMeCategory.get(phoneId);
    }

    /** get ME Retrycount.
     * @param phoneId phoneId
     * @return me retry count.
     */
    public int getSimMeLeftRetryCountOfPhoneId(int phoneId) {
        return mSimMeLeftRetryCount.get(phoneId);
    }

    /** Minus retry count of ME.
     * @param phoneId phoneId
     */
    public void minusSimMeLeftRetryCountOfPhoneId(int phoneId) {
        int simMeRetryCount = mSimMeLeftRetryCount.get(phoneId) ;
        if (simMeRetryCount > 0) {
            mSimMeLeftRetryCount.put(phoneId, simMeRetryCount - 1);
        }
    }

    /** M: LTE CSG feature
     * @param intent The intent with action {@link Telephony.Intents#SPN_STRINGS_UPDATED_ACTION}
     * @return The string to use for the HNB name, or null if it should not be shown.
     */
    private CharSequence getTelephonyHnbNameFrom(Intent intent) {
        final String hnbName = intent.getStringExtra(TelephonyIntents.EXTRA_HNB_NAME);
        return hnbName;
    }

    /** M: LTE CSG feature
     * @param intent The intent with action {@link Telephony.Intents#SPN_STRINGS_UPDATED_ACTION}
     * @return The string to use for the CSG id, or null if it should not be shown.
     */
    private CharSequence getTelephonyCsgIdFrom(Intent intent) {
        final String csgId = intent.getStringExtra(TelephonyIntents.EXTRA_CSG_ID);
        return csgId;
    }

    /** Get HNB.
     * @param phoneId phoneId.
     * @return HNB name.
     */
    public CharSequence getTelephonyHnbNameOfPhoneId(int phoneId) {
        return mTelephonyHnbName.get(phoneId);
    }

    /** Get CSG ID.
     * @param phoneId phoneId.
     * @return CSG ID.
     */
    public CharSequence getTelephonyCsgIdOfPhoneId(int phoneId) {
        return mTelephonyCsgId.get(phoneId);
    }

    /**
     * Handle {@link #MSG_AIRPLANE_MODE_UPDATE}
     */
    private static final int MSG_AIRPLANE_MODE_UPDATE = 1015;
    private void handleAirPlaneModeUpdate(boolean airPlaneModeEnabled) {
        ///M: [ALPS01761127]
        ///   After AirPlane on, the sim state will keep as "PIN_REQUIRED".
        ///   After AirPlane off, if PowerOffModem is true,
        ///   Modem will send "NOT_READY" and "PIN_REQUIRED" after .
        ///   So we do not need to send PIN_REQUIRED here.
        if (airPlaneModeEnabled == false) {
            if (DEBUG) {
                Log.d(TAG, "Force to send sim pin/puk/me lock again if needed.");
            }

            for (int i = 0; i < KeyguardUtils.getNumOfPhone(); i++) {
                setPinPukMeDismissFlagOfPhoneId(i, false) ;
                Log.d(TAG, "setPinPukMeDismissFlagOfPhoneId false: " + i);
            }

            for (int phoneId = 0; phoneId < KeyguardUtils.getNumOfPhone(); phoneId++) {
                if (DEBUG) {
                    Log.d(TAG, "phoneId = " + phoneId +
                               " state=" + mSimStateOfPhoneId.get(phoneId));
                }
                switch (mSimStateOfPhoneId.get(phoneId)) {
                    case PIN_REQUIRED:
                    case PUK_REQUIRED:
                    case NETWORK_LOCKED:
                        /// 1. keep the original state
                        IccCardConstants.State oriState = mSimStateOfPhoneId.get(phoneId);
                        /// 2. reset state of subid
                        mSimStateOfPhoneId.put(phoneId, IccCardConstants.State.UNKNOWN);
                        /// 3. create the simData
                        int meCategory = 0 ;
                        if (mSimMeCategory.get(phoneId) != null) {
                            meCategory = mSimMeCategory.get(phoneId) ;
                        }
                        SimData simData = new SimData(oriState,
                                                phoneId,
                                                KeyguardUtils.getSubIdUsingPhoneId(phoneId),
                                                meCategory);
                        if (DEBUG) {
                            Log.v(TAG, "SimData state=" + simData.simState
                                + ", phoneId=" + simData.phoneId + ", subId=" + simData.subId
                                + ", SimData.simMECategory = " + simData.simMECategory);
                        }
                        proceedToHandleSimStateChanged(simData) ;

                        break ;
                    default:
                        break;
                } //end switch
            } //end for
        } else if (airPlaneModeEnabled == true && KeyguardUtils.isFlightModePowerOffMd()) {
            ///M: fix ALPS01831621
            ///   we supress all PIN/PUK/ME locks when receiving Flight-Mode turned on.
            Log.d(TAG, "Air mode is on, supress all SIM PIN/PUK/ME Lock views.") ;
            for (int i = 0; i < KeyguardUtils.getNumOfPhone(); i++) {
                setPinPukMeDismissFlagOfPhoneId(i, true) ;
                Log.d(TAG, "setPinPukMeDismissFlagOfPhoneId true: " + i);
            }
        }

        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onAirPlaneModeChanged(airPlaneModeEnabled);
                // M: add for ALPS02432499 update carrier when air plane mode change.
                cb.onRefreshCarrierInfo();
            }
        }
    }

    /// ---- Telephony Info ends ----

    /// --- Added for Voice unlock ---
    private static final int FAILED_BIOMETRIC_UNLOCK_ATTEMPTS_BEFORE_BACKUP = 3;
    private int mFailedBiometricUnlockAttempts = 0;
    private boolean mAlternateUnlockEnabled;

    public boolean isAlternateUnlockEnabled() {
        return mAlternateUnlockEnabled;
    }

    public boolean isSwitchingUser() {
        return mSwitchingUser;
    }

    /**
     * @return if the keyguard is currently in bouncer mode.
     */
    public boolean isKeyguardBouncer() {
        return mBouncer;
    }

    public boolean isKeyguardVisible() {
        return mKeyguardIsVisible;
    }

    public int getPhoneState() {
        return mPhoneState;
    }

    public void reportFailedBiometricUnlockAttempt() {
        mFailedBiometricUnlockAttempts++;
    }

    public boolean getMaxBiometricUnlockAttemptsReached() {
        return mFailedBiometricUnlockAttempts >= FAILED_BIOMETRIC_UNLOCK_ATTEMPTS_BEFORE_BACKUP;
    }

    public void setAlternateUnlockEnabled(boolean enabled) {
        Log.d(TAG, "setAlternateUnlockEnabled(enabled = " + enabled + ")") ;
        mAlternateUnlockEnabled = enabled;
    }

    public boolean isScreenOn() {
        return mScreenOn;
    }

    ///M: clear sim state when ipo shutdown
    public void clearSimStateMap() {
        mSimStateOfPhoneId.clear();
    }
	//byd added for byd_fps
	public boolean isExist(String path) {
        File file = new File(path);
        return file.exists();
    }
    
	// add by wxue 20170427 start
    public void setFingerprintLocked(boolean lock){
    	mFingerprintLockout = lock;
    }
    
    public boolean isFingerprintLocked(){
    	return mFingerprintLockout;
    }
    
    private void handleFingerprintLockedTimeout(){
    	mFingerprintLockoutTimeout = true;
    	updateFingerprintListeningState();
    }
    
    public void reportSecurityModeLocked(int timeoutMs){
    	if(DEBUG) Log.i(TAG,"---reportSecurityModeLocked()--timeoutMs = " + timeoutMs);
    	if(timeoutMs >= 0){
    		updateFingerprintListeningState();
    	}
    }
	// add by wxue 20170427 end
}
