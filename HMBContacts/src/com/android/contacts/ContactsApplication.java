/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.contacts;
import com.android.internal.telephony.ITelephony;
import com.android.contacts.common.preference.ContactsPreferences;
import android.os.ServiceManager;

import android.app.Activity;
import android.app.Application;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.telephony.SubscriptionManager;
import android.util.Log;
import hb.provider.ContactsContract.Contacts;

import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.list.ContactListFilterController;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.contacts.common.testing.InjectedServices;
import com.android.contacts.common.util.Constants;
import com.android.contacts.commonbind.analytics.AnalyticsUtil;
import com.google.common.annotations.VisibleForTesting;

import com.mediatek.contacts.ContactsApplicationEx;
import com.mediatek.contacts.simcontact.SlotUtils;
//import com.mediatek.contacts.util.Log;
import com.hb.privacy.PrivacyUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ContactsApplication extends Application {
    private static final boolean ENABLE_LOADER_LOG = false; // Don't submit with true
    private static final boolean ENABLE_FRAGMENT_LOG = false; // Don't submit with true
    public static List<Activity> mPrivacyActivityList = new ArrayList<Activity>();
    private static InjectedServices sInjectedServices;
    /**
     * Log tag for enabling/disabling StrictMode violation log.
     * To enable: adb shell setprop log.tag.ContactsStrictMode DEBUG
     */
    public static final String STRICT_MODE_TAG = "ContactsStrictMode";
	private static final String TAG = "ContactsApplication";
    private ContactPhotoManager mContactPhotoManager;
    private ContactListFilterController mContactListFilterController;
    /// M: Single thread, don't simultaneously handle contacts copy-delete-import-export request.
    private final ExecutorService mSingleTaskService = Executors.newSingleThreadExecutor();

    /**
     * Overrides the system services with mocks for testing.
     */
    @VisibleForTesting
    public static void injectServices(InjectedServices services) {
        sInjectedServices = services;
    }

    public static InjectedServices getInjectedServices() {
        return sInjectedServices;
    }

    @Override
    public ContentResolver getContentResolver() {
        if (sInjectedServices != null) {
            ContentResolver resolver = sInjectedServices.getContentResolver();
            if (resolver != null) {
                return resolver;
            }
        }
        return super.getContentResolver();
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        if (sInjectedServices != null) {
            SharedPreferences prefs = sInjectedServices.getSharedPreferences();
            if (prefs != null) {
                return prefs;
            }
        }

        return super.getSharedPreferences(name, mode);
    }

    @Override
    public Object getSystemService(String name) {
        if (sInjectedServices != null) {
            Object service = sInjectedServices.getSystemService(name);
            if (service != null) {
                return service;
            }
        }

        return super.getSystemService(name);
    }

    private ContactsPreferences mPreferences;
    @Override
    public void onCreate() {
        super.onCreate();

//        if (Log.isLoggable(Constants.PERFORMANCE_TAG, Log.DEBUG)) {
//            Log.d(Constants.PERFORMANCE_TAG, "ContactsApplication.onCreate start");
//        }

//        if (ENABLE_FRAGMENT_LOG) FragmentManager.enableDebugLogging(true);
//        if (ENABLE_LOADER_LOG) LoaderManager.enableDebugLogging(true);

//        if (Log.isLoggable(STRICT_MODE_TAG, Log.DEBUG)) {
//            StrictMode.setThreadPolicy(
//                    new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build());
//        }

        /// M: Set the application context to some class and clear notification.
        ContactsApplicationEx.onCreateEx(this);

        // Perform the initialization that doesn't have to finish immediately.
        // We use an async task here just to avoid creating a new thread.
//        (new DelayedInitializer()).execute();

//        if (Log.isLoggable(Constants.PERFORMANCE_TAG, Log.DEBUG)) {
//            Log.d(Constants.PERFORMANCE_TAG, "ContactsApplication.onCreate finish");
//        }
//        mPreferences = new ContactsPreferences(ContactsApplication.this);
//        mPreferences.setDisplayOrder(ContactsPreferences.DISPLAY_ORDER_PRIMARY);       
//        PrivacyUtils.bindService(this);
//        AnalyticsUtil.initialize(this);
    }

    private class DelayedInitializer extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            final Context context = ContactsApplication.this;

            // Warm up the preferences and the contacts provider.  We delay initialization
            // of the account type manager because we may not have the contacts group permission
            // (and thus not have the get accounts permission).
            PreferenceManager.getDefaultSharedPreferences(context);
            getContentResolver().getType(ContentUris.withAppendedId(Contacts.CONTENT_URI, 1));
            return null;
        }

        public void execute() {
            executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                    (Void[]) null);
        }
    }

    /**
     * M: Get the ContactsApplication Instance.
     */
    public static ContactsApplication getInstance() {
        return ContactsApplicationEx.getContactsApplication();
    }

    /**
     * M: Get Application Task Sevice.
     */
    public ExecutorService getApplicationTaskService() {
        return mSingleTaskService;
    }
    public static boolean isMultiSimEnabled;//是否启用双卡
    public static boolean isMultiSimEnabled(Context mContext){
		if(mContext==null) return false;
		int activeSubscriptionInfoCount=SubscriptionManager.from(mContext).getActiveSubscriptionInfoCount();
		Log.d(TAG,"activeSubscriptionInfoCount:"+activeSubscriptionInfoCount);
		
		if(activeSubscriptionInfoCount<2) {
			isMultiSimEnabled=false;
			return isMultiSimEnabled;
		}
		
		int[] subIds0=SubscriptionManager.getSubId(0);
		int[] subIds1=SubscriptionManager.getSubId(1);
		int subId0=-1,subId1=-1;
		if(subIds0!=null&&subIds0.length>0) subId0=subIds0[0];
		if(subIds1!=null&&subIds1.length>0) subId1=subIds1[0];
		
		Log.d(TAG, "subId0:"+subId0+" subId1:"+subId1);
		
		boolean isRadioOn0=isRadioOn(subId0, mContext);
		boolean isRadioOn1=isRadioOn(subId1,mContext);
		
		Log.d(TAG, "isRadioOn0:"+isRadioOn0+" isRadioOn1"+isRadioOn1);
		if(isRadioOn0 && isRadioOn1) isMultiSimEnabled=true;
		else isMultiSimEnabled=false;
		return isMultiSimEnabled;
	}
	
	public static boolean isAnySimEnabled(Context mContext){
		int activeSubscriptionInfoCount=SubscriptionManager.from(mContext).getActiveSubscriptionInfoCount();
		Log.d(TAG,"activeSubscriptionInfoCount:"+activeSubscriptionInfoCount);
		
		if(activeSubscriptionInfoCount<1) return false;
		
		int[] subIds0=SubscriptionManager.getSubId(0);
		int[] subIds1=SubscriptionManager.getSubId(1);
		int subId0=-1,subId1=-1;
		if(subIds0!=null&&subIds0.length>0) subId0=subIds0[0];
		if(subIds1!=null&&subIds1.length>0) subId1=subIds1[0];
		
		boolean isRadioOn0=isRadioOn(subId0, mContext);
		boolean isRadioOn1=isRadioOn(subId1,mContext);
		
		Log.d(TAG, "subId0:"+subId0+" subId1:"+subId1+" isRadioOn0:"+isRadioOn0+" isRadioOn1:"+isRadioOn1);
		return isRadioOn0 || isRadioOn1;
	}
	
	public static int getSubId(int slotId){
		int[] subIds=SubscriptionManager.getSubId(slotId);
		int subId=-1;
		if(subIds!=null&&subIds.length>0) {
			subId=subIds[0];
		}
		Log.d(TAG, "subId:"+subId);
		return subId;
	}
	
	
	public static boolean isRadioOn(int subId, Context context) {
        ITelephony phone = ITelephony.Stub.asInterface(ServiceManager
                .getService(Context.TELEPHONY_SERVICE));
        boolean isOn = false;
        try {
            // for ALPS02460942, during SIM switch, radio is unavailable, consider it as OFF
            if (phone != null) {
                isOn = subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID ? false :
                    phone.isRadioOnForSubscriber(subId, context.getPackageName());
            } else {
                Log.d(TAG, "capability switching, or phone is null ? " + (phone == null));
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "isOn = " + isOn + ", subId: " + subId);
        return isOn;
    }
}
