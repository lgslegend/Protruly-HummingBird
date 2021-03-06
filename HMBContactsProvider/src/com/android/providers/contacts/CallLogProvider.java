/*
 * Copyright (C) 2014 MediaTek Inc.
 * Modification based on code covered by the mentioned copyright
 * and/or permission notice(s).
 */
/*
 * Copyright (C) 2009 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.providers.contacts;

import com.hb.csp.contactsprovider.HMBCallLogProvider;
import com.android.common.content.ProjectionMap;
import static com.android.providers.contacts.util.DbQueryUtils.checkForSupportedColumns;
import static com.android.providers.contacts.util.DbQueryUtils.getEqualityClause;
import static com.android.providers.contacts.util.DbQueryUtils.getInequalityClause;

import android.app.AppOpsManager;
import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import com.android.providers.hb.CallLog;
import com.android.providers.hb.CallLog.Calls;
import com.android.providers.hb.CallLog.ConferenceCalls;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.providers.contacts.ContactsDatabaseHelper.DbProperties;
import com.android.providers.contacts.ContactsDatabaseHelper.Tables;
import com.android.providers.contacts.util.SelectionBuilder;
import com.android.providers.contacts.util.UserUtils;
import com.android.providers.hb.SubPermissions;
import com.mediatek.providers.contacts.CallLogProviderEx;
import com.mediatek.providers.contacts.LogUtils;
import com.hb.dialersearch.DialerSearchHelperForHb.CallLogQuery;
import com.google.common.annotations.VisibleForTesting;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Call log content provider.
 */
public class CallLogProvider extends HMBCallLogProvider {
	private static final String TAG = CallLogProvider.class.getSimpleName();

	private static final int BACKGROUND_TASK_INITIALIZE = 0;
	private static final int BACKGROUND_TASK_ADJUST_PHONE_ACCOUNT = 1;

	/** Selection clause for selecting all calls that were made after a certain time */
	private static final String MORE_RECENT_THAN_SELECTION = Calls.DATE + "> ?";
	/** Selection clause to use to exclude voicemail records.  */
	private static final String EXCLUDE_VOICEMAIL_SELECTION = getInequalityClause(
			Calls.TYPE, Calls.VOICEMAIL_TYPE);
	/** Selection clause to exclude hidden records. */
	private static final String EXCLUDE_HIDDEN_SELECTION = getEqualityClause(
			Calls.PHONE_ACCOUNT_HIDDEN, 0);

	@VisibleForTesting
	static final String[] CALL_LOG_SYNC_PROJECTION = new String[] {
			Calls.NUMBER,
			Calls.NUMBER_PRESENTATION,
			Calls.TYPE,
			Calls.FEATURES,
			Calls.DATE,
			Calls.DURATION,
			Calls.DATA_USAGE,
			Calls.PHONE_ACCOUNT_COMPONENT_NAME,
			Calls.PHONE_ACCOUNT_ID
	};

	static final String[] MINIMAL_PROJECTION = new String[] { Calls._ID };

	private static final int CALLS = 1;

	private static final int CALLS_ID = 2;

	private static final int CALLS_FILTER = 3;



	private static final String UNHIDE_BY_PHONE_ACCOUNT_QUERY =
			"UPDATE " + Tables.CALLS + " SET " + Calls.PHONE_ACCOUNT_HIDDEN + "=0 WHERE " +
					Calls.PHONE_ACCOUNT_COMPONENT_NAME + "=? AND " + Calls.PHONE_ACCOUNT_ID + "=?;";

	private static final String UNHIDE_BY_ADDRESS_QUERY =
			"UPDATE " + Tables.CALLS + " SET " + Calls.PHONE_ACCOUNT_HIDDEN + "=0 WHERE " +
					Calls.PHONE_ACCOUNT_ADDRESS + "=?;";

	private static final int CALLS_SEARCH_FILTER = 4;
	private static final int CALLS_JION_DATA_VIEW = 5;
	private static final int CALLS_JION_DATA_VIEW_ID = 6;
	private static final int CONFERENCE_CALLS = 7;
	private static final int CONFERENCE_CALLS_ID = 8;
	private static final int SEARCH_SUGGESTIONS = 10001;
	private static final int SEARCH_SHORTCUT = 10002;
	private static final int CALL_DETAILS = 10003;
	private static final int CALL_DISTINCT_SEARCH = 10004;
	private static final int USEFUL_NUMBER = 10005;
	private static final int REJECT_CALLS_JION_DATA_VIEW = 10006;

	private static final int  RECOMMENDEDCALLS=10007;
	private static final int CALLS_HB = 10008;

	private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);
	static {
		sURIMatcher.addURI(CallLog.AUTHORITY, "calls", CALLS);
		sURIMatcher.addURI(CallLog.AUTHORITY, "calls/#", CALLS_ID);
		sURIMatcher.addURI(CallLog.AUTHORITY, "calls/filter/*", CALLS_FILTER);
		sURIMatcher.addURI(CallLog.AUTHORITY, "calls/search_filter/*", CALLS_SEARCH_FILTER);
		sURIMatcher.addURI(CallLog.AUTHORITY, "callsjoindataview", CALLS_JION_DATA_VIEW);
		sURIMatcher.addURI(CallLog.AUTHORITY, "callsjoindataview/#", CALLS_JION_DATA_VIEW_ID);
		sURIMatcher.addURI(CallLog.AUTHORITY,
				SearchManager.SUGGEST_URI_PATH_QUERY, SEARCH_SUGGESTIONS);
		sURIMatcher.addURI(CallLog.AUTHORITY,
				SearchManager.SUGGEST_URI_PATH_QUERY + "/*", SEARCH_SUGGESTIONS);
		sURIMatcher.addURI(CallLog.AUTHORITY,
				SearchManager.SUGGEST_URI_PATH_SHORTCUT + "/*", SEARCH_SHORTCUT);
		sURIMatcher.addURI(CallLog.AUTHORITY, "conference_calls", CONFERENCE_CALLS);
		sURIMatcher.addURI(CallLog.AUTHORITY, "conference_calls/#", CONFERENCE_CALLS_ID);

		sURIMatcher.addURI(CallLog.AUTHORITY, "calls/call_detail/*", CALL_DETAILS);
		sURIMatcher.addURI(CallLog.AUTHORITY, "calls/call_distinct_search/", CALL_DISTINCT_SEARCH);
		sURIMatcher.addURI(CallLog.AUTHORITY, "calls/useful_number/", USEFUL_NUMBER);
		sURIMatcher.addURI(CallLog.AUTHORITY, "hbcallsjoindataview", REJECT_CALLS_JION_DATA_VIEW);

		sURIMatcher.addURI(CallLog.AUTHORITY, "recommendedCalls", RECOMMENDEDCALLS);

		sURIMatcher.addURI(CallLog.AUTHORITY, "calls_hb", CALLS_HB);
	}

	private static final HashMap<String, String> sCallsProjectionMap;
	static {

		// Calls projection map
		sCallsProjectionMap = new HashMap<String, String>();
		/**
		 * M:
		 * Original Code:
        sCallsProjectionMap.put(Calls._ID, Calls._ID);
		 */
		sCallsProjectionMap.put(Calls._ID, Tables.CALLS + "._id as " + Calls._ID);
		sCallsProjectionMap.put(Calls.NUMBER, Calls.NUMBER);
		sCallsProjectionMap.put(Calls.NUMBER_PRESENTATION, Calls.NUMBER_PRESENTATION);
		sCallsProjectionMap.put(Calls.DATE, Calls.DATE);
		sCallsProjectionMap.put(Calls.DURATION, Calls.DURATION);
		sCallsProjectionMap.put(Calls.DATA_USAGE, Calls.DATA_USAGE);
		sCallsProjectionMap.put(Calls.TYPE, Calls.TYPE);
		sCallsProjectionMap.put(Calls.FEATURES, Calls.FEATURES);
		sCallsProjectionMap.put(Calls.PHONE_ACCOUNT_COMPONENT_NAME, Calls.PHONE_ACCOUNT_COMPONENT_NAME);
		sCallsProjectionMap.put(Calls.PHONE_ACCOUNT_ID, Calls.PHONE_ACCOUNT_ID);
		sCallsProjectionMap.put(Calls.PHONE_ACCOUNT_ADDRESS, Calls.PHONE_ACCOUNT_ADDRESS);
		sCallsProjectionMap.put(Calls.NEW, Calls.NEW);
		sCallsProjectionMap.put(Calls.VOICEMAIL_URI, Calls.VOICEMAIL_URI);
		sCallsProjectionMap.put(Calls.TRANSCRIPTION, Calls.TRANSCRIPTION);
		sCallsProjectionMap.put(Calls.IS_READ, Calls.IS_READ);
		sCallsProjectionMap.put(Calls.CACHED_NAME, Calls.CACHED_NAME);
		sCallsProjectionMap.put(Calls.CACHED_NUMBER_TYPE, Calls.CACHED_NUMBER_TYPE);
		sCallsProjectionMap.put(Calls.CACHED_NUMBER_LABEL, Calls.CACHED_NUMBER_LABEL);
		sCallsProjectionMap.put(Calls.COUNTRY_ISO, Calls.COUNTRY_ISO);
		sCallsProjectionMap.put(Calls.GEOCODED_LOCATION, Calls.GEOCODED_LOCATION);
		sCallsProjectionMap.put(Calls.CACHED_LOOKUP_URI, Calls.CACHED_LOOKUP_URI);
		sCallsProjectionMap.put(Calls.CACHED_MATCHED_NUMBER, Calls.CACHED_MATCHED_NUMBER);
		sCallsProjectionMap.put(Calls.CACHED_NORMALIZED_NUMBER, Calls.CACHED_NORMALIZED_NUMBER);
		sCallsProjectionMap.put(Calls.CACHED_PHOTO_ID, Calls.CACHED_PHOTO_ID);
		sCallsProjectionMap.put(Calls.CACHED_PHOTO_URI, "calls."+Calls.CACHED_PHOTO_URI);
		sCallsProjectionMap.put(Calls.CACHED_FORMATTED_NUMBER, Calls.CACHED_FORMATTED_NUMBER);
		/// M: @{
		sCallsProjectionMap.put(Calls.RAW_CONTACT_ID, Calls.RAW_CONTACT_ID);
		sCallsProjectionMap.put(Calls.DATA_ID, Calls.DATA_ID);
		sCallsProjectionMap.put(Calls.IP_PREFIX, Calls.IP_PREFIX);
		sCallsProjectionMap.put(Calls.CONFERENCE_CALL_ID, Calls.CONFERENCE_CALL_ID);
		//add by lgy
		sCallsProjectionMap.put("reject", "reject");
		sCallsProjectionMap.put("mark", "mark");
		sCallsProjectionMap.put("black_name", "black_name");
		sCallsProjectionMap.put("user_mark", "user_mark");
		//add by liyang
		sCallsProjectionMap.put("privacy_id", "privacy_id");
		sCallsProjectionMap.put("sub_id", "sub_id");

		sCallsProjectionMap.put("calls.number", "calls.number");
		/// @}
	}

	private static final HashMap<String, String> sCallsProjectionMapHB;
	static {

		// Calls projection map
		sCallsProjectionMapHB = new HashMap<String, String>();
		/**
		 * M:
		 * Original Code:
        sCallsProjectionMapHB.put(Calls._ID, Calls._ID);
		 */
		sCallsProjectionMapHB.put(Calls._ID, Tables.CALLS + "._id as " + Calls._ID);
		sCallsProjectionMapHB.put(Calls.NUMBER, Calls.NUMBER);
		sCallsProjectionMapHB.put(Calls.NUMBER_PRESENTATION, Calls.NUMBER_PRESENTATION);
		sCallsProjectionMapHB.put(Calls.DATE, Calls.DATE);
		sCallsProjectionMapHB.put(Calls.DURATION, Calls.DURATION);
		sCallsProjectionMapHB.put(Calls.DATA_USAGE, Calls.DATA_USAGE);
		sCallsProjectionMapHB.put(Calls.TYPE, Calls.TYPE);
		sCallsProjectionMapHB.put(Calls.FEATURES, Calls.FEATURES);
		sCallsProjectionMapHB.put(Calls.PHONE_ACCOUNT_COMPONENT_NAME, Calls.PHONE_ACCOUNT_COMPONENT_NAME);
		sCallsProjectionMapHB.put(Calls.PHONE_ACCOUNT_ID, Calls.PHONE_ACCOUNT_ID);
		sCallsProjectionMapHB.put(Calls.PHONE_ACCOUNT_ADDRESS, Calls.PHONE_ACCOUNT_ADDRESS);
		sCallsProjectionMapHB.put(Calls.NEW, Calls.NEW);
		sCallsProjectionMapHB.put(Calls.VOICEMAIL_URI, Calls.VOICEMAIL_URI);
		sCallsProjectionMapHB.put(Calls.TRANSCRIPTION, Calls.TRANSCRIPTION);
		sCallsProjectionMapHB.put(Calls.IS_READ, Calls.IS_READ);
		sCallsProjectionMapHB.put(Calls.CACHED_NAME, Calls.CACHED_NAME);
		sCallsProjectionMapHB.put(Calls.CACHED_NUMBER_TYPE, Calls.CACHED_NUMBER_TYPE);
		sCallsProjectionMapHB.put(Calls.CACHED_NUMBER_LABEL, Calls.CACHED_NUMBER_LABEL);
		sCallsProjectionMapHB.put(Calls.COUNTRY_ISO, Calls.COUNTRY_ISO);
		sCallsProjectionMapHB.put(Calls.GEOCODED_LOCATION, Calls.GEOCODED_LOCATION);
		sCallsProjectionMapHB.put(Calls.CACHED_LOOKUP_URI, Calls.CACHED_LOOKUP_URI);
		sCallsProjectionMapHB.put(Calls.CACHED_MATCHED_NUMBER, Calls.CACHED_MATCHED_NUMBER);
		sCallsProjectionMapHB.put(Calls.CACHED_NORMALIZED_NUMBER, Calls.CACHED_NORMALIZED_NUMBER);
		sCallsProjectionMapHB.put(Calls.CACHED_PHOTO_ID, Calls.CACHED_PHOTO_ID);
		sCallsProjectionMapHB.put(Calls.CACHED_PHOTO_URI, "calls."+Calls.CACHED_PHOTO_URI);
		sCallsProjectionMapHB.put(Calls.CACHED_FORMATTED_NUMBER, Calls.CACHED_FORMATTED_NUMBER);
		/// M: @{
		sCallsProjectionMapHB.put(Calls.RAW_CONTACT_ID, Calls.RAW_CONTACT_ID);
		sCallsProjectionMapHB.put(Calls.DATA_ID, Calls.DATA_ID);
		sCallsProjectionMapHB.put(Calls.IP_PREFIX, Calls.IP_PREFIX);
		sCallsProjectionMapHB.put(Calls.CONFERENCE_CALL_ID, Calls.CONFERENCE_CALL_ID);
		//add by lgy
		sCallsProjectionMapHB.put("reject", "reject");
		sCallsProjectionMapHB.put("mark", "mark");
		sCallsProjectionMapHB.put("black_name", "black_name");
		sCallsProjectionMapHB.put("user_mark", "user_mark");
		//add by liyang
		sCallsProjectionMapHB.put("privacy_id", "privacy_id");
		sCallsProjectionMapHB.put("sub_id", "sub_id");

		sCallsProjectionMapHB.put("calls.number", "calls.number");
		sCallsProjectionMapHB.put("mark.lable", "mark.lable");
		/// @}
	}
	
	
	public static final HashMap<String, String> sCallsProjectionMapForCallDetail;
	static {

		// Calls projection map
		sCallsProjectionMapForCallDetail = new HashMap<String, String>();
		/**
		 * M:
		 * Original Code:
        sCallsProjectionMapForCallDetail.put(Calls._ID, Calls._ID);
		 */
		sCallsProjectionMapForCallDetail.put(Calls._ID, Tables.CALLS + "._id as " + Calls._ID);
		sCallsProjectionMapForCallDetail.put(Calls.NUMBER, Calls.NUMBER);
		sCallsProjectionMapForCallDetail.put(Calls.NUMBER_PRESENTATION, Calls.NUMBER_PRESENTATION);
		sCallsProjectionMapForCallDetail.put(Calls.DATE, Calls.DATE);
		sCallsProjectionMapForCallDetail.put(Calls.DURATION, Calls.DURATION);
		sCallsProjectionMapForCallDetail.put(Calls.DATA_USAGE, Calls.DATA_USAGE);
		sCallsProjectionMapForCallDetail.put(Calls.TYPE, Calls.TYPE);
		sCallsProjectionMapForCallDetail.put(Calls.FEATURES, Calls.FEATURES);
		sCallsProjectionMapForCallDetail.put(Calls.PHONE_ACCOUNT_COMPONENT_NAME, Calls.PHONE_ACCOUNT_COMPONENT_NAME);
		sCallsProjectionMapForCallDetail.put(Calls.PHONE_ACCOUNT_ID, Calls.PHONE_ACCOUNT_ID);
		sCallsProjectionMapForCallDetail.put(Calls.PHONE_ACCOUNT_ADDRESS, Calls.PHONE_ACCOUNT_ADDRESS);
		sCallsProjectionMapForCallDetail.put(Calls.NEW, Calls.NEW);
		sCallsProjectionMapForCallDetail.put(Calls.VOICEMAIL_URI, Calls.VOICEMAIL_URI);
		sCallsProjectionMapForCallDetail.put(Calls.TRANSCRIPTION, Calls.TRANSCRIPTION);
		sCallsProjectionMapForCallDetail.put(Calls.IS_READ, Calls.IS_READ);
		sCallsProjectionMapForCallDetail.put(Calls.CACHED_NAME, Calls.CACHED_NAME);
		sCallsProjectionMapForCallDetail.put(Calls.CACHED_NUMBER_TYPE, Calls.CACHED_NUMBER_TYPE);
		sCallsProjectionMapForCallDetail.put(Calls.CACHED_NUMBER_LABEL, Calls.CACHED_NUMBER_LABEL);
		sCallsProjectionMapForCallDetail.put(Calls.COUNTRY_ISO, Calls.COUNTRY_ISO);
		sCallsProjectionMapForCallDetail.put(Calls.GEOCODED_LOCATION, Calls.GEOCODED_LOCATION);
		sCallsProjectionMapForCallDetail.put(Calls.CACHED_LOOKUP_URI, Calls.CACHED_LOOKUP_URI);
		sCallsProjectionMapForCallDetail.put(Calls.CACHED_MATCHED_NUMBER, Calls.CACHED_MATCHED_NUMBER);
		sCallsProjectionMapForCallDetail.put(Calls.CACHED_NORMALIZED_NUMBER, Calls.CACHED_NORMALIZED_NUMBER);
		sCallsProjectionMapForCallDetail.put(Calls.CACHED_PHOTO_ID, Calls.CACHED_PHOTO_ID);
		sCallsProjectionMapForCallDetail.put(Calls.CACHED_PHOTO_URI, "calls."+Calls.CACHED_PHOTO_URI);
		sCallsProjectionMapForCallDetail.put(Calls.CACHED_FORMATTED_NUMBER, Calls.CACHED_FORMATTED_NUMBER);
		/// M: @{
		sCallsProjectionMapForCallDetail.put(Calls.RAW_CONTACT_ID, Calls.RAW_CONTACT_ID);
		sCallsProjectionMapForCallDetail.put(Calls.DATA_ID, Calls.DATA_ID);
		sCallsProjectionMapForCallDetail.put(Calls.IP_PREFIX, Calls.IP_PREFIX);
		sCallsProjectionMapForCallDetail.put(Calls.CONFERENCE_CALL_ID, Calls.CONFERENCE_CALL_ID);
		//add by lgy
		sCallsProjectionMapForCallDetail.put("reject", "reject");
		sCallsProjectionMapForCallDetail.put("mark", "mark");
		sCallsProjectionMapForCallDetail.put("black_name", "black_name");
		sCallsProjectionMapForCallDetail.put("user_mark", "user_mark");
		sCallsProjectionMapForCallDetail.put("starred", "starred");
		sCallsProjectionMapForCallDetail.put("display_name", "display_name");
		sCallsProjectionMapForCallDetail.put("sub_id", "sub_id");
		/// @}
	}

	private HandlerThread mBackgroundThread;
	private Handler mBackgroundHandler;
	private volatile CountDownLatch mReadAccessLatch;

	private ContactsDatabaseHelper mDbHelper;
	private DatabaseUtils.InsertHelper mCallsInserter;
	private boolean mUseStrictPhoneNumberComparation;
	private VoicemailPermissions mVoicemailPermissions;
	private CallLogInsertionHelper mCallLogInsertionHelper;
	/// M: @{
	private CallLogProviderEx mCallLogProviderEx;
	/// @}
	@Override
	public boolean onCreate() {
		setAppOps(AppOpsManager.OP_READ_CALL_LOG, AppOpsManager.OP_WRITE_CALL_LOG);
		if (Log.isLoggable(Constants.PERFORMANCE_TAG, Log.DEBUG)) {
			Log.d(Constants.PERFORMANCE_TAG, "CallLogProvider.onCreate start");
		}

		// M: MoMS for controling database access ability
//		        setMoMSPermission(SubPermissions.QUERY_CALL_LOG, SubPermissions.MODIFY_CALL_LOG);

		final Context context = getContext();
		mDbHelper = getDatabaseHelper(context);
		mUseStrictPhoneNumberComparation =
				context.getResources().getBoolean(
						com.android.internal.R.bool.config_use_strict_phone_number_comparation);
		mVoicemailPermissions = new VoicemailPermissions(context);
		/// M: @{
		mCallLogProviderEx = CallLogProviderEx.getInstance(context);
		/// @}
		mCallLogInsertionHelper = createCallLogInsertionHelper(context);

		mBackgroundThread = new HandlerThread("CallLogProviderWorker",
				Process.THREAD_PRIORITY_BACKGROUND);
		mBackgroundThread.start();
		mBackgroundHandler = new Handler(mBackgroundThread.getLooper()) {
			@Override
			public void handleMessage(Message msg) {
				performBackgroundTask(msg.what, msg.obj);
			}
		};

		mReadAccessLatch = new CountDownLatch(1);

		scheduleBackgroundTask(BACKGROUND_TASK_INITIALIZE, null);

		if (Log.isLoggable(Constants.PERFORMANCE_TAG, Log.DEBUG)) {
			LogUtils.d(Constants.PERFORMANCE_TAG, "CallLogProvider.onCreate finish");
		}
		return true;
	}

	@VisibleForTesting
	protected CallLogInsertionHelper createCallLogInsertionHelper(final Context context) {
		return DefaultCallLogInsertionHelper.getInstance(context);
	}

	@VisibleForTesting
	protected ContactsDatabaseHelper getDatabaseHelper(final Context context) {
		return ContactsDatabaseHelper.getInstance(context);
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
			String sortOrder) {
		Log.d(TAG,"query,uri:"+uri+" projection:"+Arrays.toString(projection)+" selection:"+selection+" args:"+Arrays.toString(selectionArgs)+" sortOrder:"+sortOrder);
		waitForAccess(mReadAccessLatch);

		selection = parseCallLogSelection(selection, false);//add for privacy

		final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		qb.setTables(Tables.CALLS);
		qb.setProjectionMap(sCallsProjectionMap);
		qb.setStrict(true);
		//add by lgy

		final int match = sURIMatcher.match(uri);
		
		final SelectionBuilder selectionBuilder = new SelectionBuilder(processSelectionForBlack(match, selection));
		checkVoicemailPermissionAndAddRestriction(uri, selectionBuilder, true /*isQuery*/);
		selectionBuilder.addClause(EXCLUDE_HIDDEN_SELECTION);

		/// M: @{
		final SQLiteDatabase db = mDbHelper.getReadableDatabase();
		LogUtils.d(TAG, "match == " + match);

		String groupBy = null;
		/// @}
		switch (match) {
		case CALLS:
			break;
		case CALLS_HB:
			qb.setTables(Tables.CALLS+" left join mark on PHONE_NUMBERS_EQUAL(mark.number, calls.number, 0)");
			qb.setProjectionMap(sCallsProjectionMapHB);
			for(int i=0;i<projection.length;i++){
				if(TextUtils.equals(projection[i], "number")){
					projection[i]="calls.number";
					break;
				}
			}
			break;

		case CALLS_ID: {
			selectionBuilder.addClause(getEqualityClause(Calls._ID,
					parseCallIdFromUri(uri)));
			break;
		}

		case CALLS_FILTER: {
			List<String> pathSegments = uri.getPathSegments();
			String phoneNumber = pathSegments.size() >= 2 ? pathSegments.get(2) : null;
			if (!TextUtils.isEmpty(phoneNumber)) {
				qb.appendWhere("PHONE_NUMBERS_EQUAL(number, ");
				qb.appendWhereEscapeString(phoneNumber);
				qb.appendWhere(mUseStrictPhoneNumberComparation ? ", 1)" : ", 0)");
			} else {
				qb.appendWhere(Calls.NUMBER_PRESENTATION + "!="
						+ Calls.PRESENTATION_ALLOWED);
			}
			break;
		}
		/// M: @{
		case REJECT_CALLS_JION_DATA_VIEW:
			groupBy = Tables.CALLS + "." +Calls.NUMBER;
		case CALLS_SEARCH_FILTER:
		case CALLS_JION_DATA_VIEW:
		case CONFERENCE_CALLS_ID: {
			mCallLogProviderEx.queryCallLog(uri, projection, selection,
					selectionArgs, sortOrder, match, qb, selectionBuilder, null);
			break;
		}
		case CALLS_JION_DATA_VIEW_ID: {
			long parseCallId = parseCallIdFromUri(uri);
			mCallLogProviderEx.queryCallLog(uri, projection, selection,
					selectionArgs, sortOrder, match, qb, selectionBuilder, parseCallId);
			break;
		}
		case SEARCH_SUGGESTIONS:
		case SEARCH_SHORTCUT: {
			return mCallLogProviderEx.queryGlobalSearch(uri,
					projection, selection, selectionArgs, sortOrder, match);
		}
		case CALL_DETAILS:{
			mCallLogProviderEx.queryCallLog(uri, projection, selection,
					selectionArgs, sortOrder, match, qb, selectionBuilder, null);
			break;
		}
		case CALL_DISTINCT_SEARCH:{
			Cursor callLogCursor=db.query("calls", 
					CallLogQuery._PROJECTION, 
					"deleted = 0 AND NOT (type = 4)  AND raw_contact_id IS NULL", 
					null, 
					"number",
					null, 
					"_id desc");		
			Log.d(TAG,"calllogcursor:"+(callLogCursor==null?"null":callLogCursor.getCount()));
			return callLogCursor;
		}
		case USEFUL_NUMBER:{
			Cursor cursor=db.query("hb_useful_number", 
					null, 
					null, 
					null, 
					null,
					null, 
					null);		
			Log.d(TAG,"usefulCursor:"+(cursor==null?"null":cursor.getCount()));
			return cursor;
		}

		case RECOMMENDEDCALLS:{//add by liyang 2017-4-18
			long time0=System.currentTimeMillis();
			long datelimit = time0-1000L * 60 * 60 * 24 * 7;
			Cursor cursor=null;
			final int NAME_INDEX=0;
			final int NUMBER_INDEX=1;
			final int DURATION_INDEX=2;
			final int DATE_INDEX=3;
			String[] PROJECTIONS=new String[]{"name","number"};
			ArrayList<RecommenedCallEntry> result=null;
			MatrixCursor resultCursor = new MatrixCursor(PROJECTIONS);
			try{
				cursor=db.query("calls",
						new String[]{"name","number","duration","date"}, 
						"date>"+datelimit, 
						null, null, null,null);

				int count=cursor==null?0:cursor.getCount();
				Log.d(TAG,"count:"+count+" datelimit:"+datelimit);
				if(count==0) return resultCursor;

				HashMap<String,RecommenedCallEntry> map=new HashMap<>();
				cursor.moveToFirst();
				do{
					String number=cursor.getString(NUMBER_INDEX);
					if(!map.containsKey(number)){
						RecommenedCallEntry recommenedCallEntry=new RecommenedCallEntry(cursor.getString(NAME_INDEX),
								number,
								cursor.getLong(DURATION_INDEX),
								1,
								cursor.getLong(DATE_INDEX));
						map.put(number,recommenedCallEntry);
					}else{
						RecommenedCallEntry recommenedCallEntry=map.get(number);
						recommenedCallEntry.setDuration(cursor.getLong(DURATION_INDEX));
						recommenedCallEntry.setTimes(1);
						recommenedCallEntry.setLastDate(cursor.getLong(DATE_INDEX));
					}
				}while(cursor.moveToNext());

				ArrayList<RecommenedCallEntry> list=new ArrayList<>();
				Iterator iter = map.entrySet().iterator();
				while (iter.hasNext()) {
					Map.Entry entry = (Map.Entry) iter.next();
					RecommenedCallEntry val = (RecommenedCallEntry)entry.getValue();
					list.add(val);
				}

				Collections.sort(list,new SortRecommenedCallEntryByTimes());
				Log.d(TAG,"after SortRecommenedCallEntryByTimes:"+list);
				int count1=list==null?0:list.size();

				result=new ArrayList<>();
				ArrayList<RecommenedCallEntry> remainList=new ArrayList<>();
				ArrayList<RecommenedCallEntry> remainList2=new ArrayList<>();
				if(count1>4){
					for(int i=0;i<count1;i++){
						if(i<4) result.add(list.get(i));
						else remainList.add(list.get(i));	
					}
					Collections.sort(remainList,new SortRecommenedCallEntryByDuration());
					Log.d(TAG,"after SortRecommenedCallEntryByDuration:"+remainList);
					for(int j=0;j<remainList.size();j++){
						if(j<2) result.add(remainList.get(j));
						else remainList2.add(remainList.get(j));
					}

					Collections.sort(remainList2,new SortRecommenedCallEntryByDate());
					Log.d(TAG,"after SortRecommenedCallEntryByDate:"+remainList2);
					for(int k=0;k<remainList2.size();k++){
						result.add(remainList2.get(k));
						if(k==1) break;
					}
				}else{
					for(RecommenedCallEntry entry:list){
						result.add(entry);
					}
				}
				list=null;
				remainList=null;
				remainList2=null;
				map=null;
				Log.d(TAG,"result:"+result);

				if(result!=null && result.size()>0){
					Object[] objs = null;

					for(RecommenedCallEntry entry:result){
						objs = new Object[2];
						objs[0]=entry.getName();
						objs[1]=entry.getNumber();
						resultCursor.addRow(objs);
					}
					objs=null;
				}

				result=null;		

			}catch(Exception e){
				Log.d(TAG,"e:"+e);
			}finally{
				if(cursor!=null){
					cursor.close();
					cursor=null;			
				}
			}

			Log.d(TAG,"spend time:"+(System.currentTimeMillis()-time0));
			return resultCursor;
		}
		/// @}
		default:
			throw new IllegalArgumentException("Unknown URL " + uri);
		}



		LogUtils.d(TAG, "   In call log providers,  selectionBuilder=" + selectionBuilder.build());

		final int limit = getIntParam(uri, Calls.LIMIT_PARAM_KEY, 0);
		final int offset = getIntParam(uri, Calls.OFFSET_PARAM_KEY, 0);
		String limitClause = null;
		if (limit > 0) {
			limitClause = offset + "," + limit;
		}

		/**
		 * M: remove original code here
		 * Original Code:
        final SQLiteDatabase db = mDbHelper.getReadableDatabase();
		 */
		Log.d(TAG,"selectionBuilder:"+selectionBuilder+" selectionArgs:"+Arrays.toString(selectionArgs)+" limitClause:"+limitClause);
		final Cursor c = qb.query(db, projection, selectionBuilder.build(), selectionArgs, groupBy,
				null, sortOrder, limitClause);
		if (c != null) {
			c.setNotificationUri(getContext().getContentResolver(), CallLog.CONTENT_URI);
			LogUtils.d(TAG, "query count == " + c.getCount());
		}
		return c;
	}

	//add by liyang 2017-4-18 begin
	class SortRecommenedCallEntryByTimes implements Comparator<RecommenedCallEntry>{

		@Override
		public int compare(RecommenedCallEntry lhs, RecommenedCallEntry rhs) {
			// TODO Auto-generated method stub
			return rhs.getTimes()-lhs.getTimes();
		}		
	}

	class SortRecommenedCallEntryByDate implements Comparator<RecommenedCallEntry>{

		@Override
		public int compare(RecommenedCallEntry lhs, RecommenedCallEntry rhs) {
			// TODO Auto-generated method stub
			return rhs.getLastDate()-lhs.getLastDate()>0?1:-1;
		}		
	}

	class SortRecommenedCallEntryByDuration implements Comparator<RecommenedCallEntry>{

		@Override
		public int compare(RecommenedCallEntry lhs, RecommenedCallEntry rhs) {
			// TODO Auto-generated method stub
			return (int)(rhs.getDuration()-lhs.getDuration());
		}		
	}
	class RecommenedCallEntry{
		private String name;
		private String number;
		private long duration;
		private int times;
		private long lastDate;



		@Override
		public String toString() {
			// TODO Auto-generated method stub
			return "name:"+name+" number:"+number+" duration:"+duration+" times:"+times+" lastDate:"+lastDate;
		}
		public RecommenedCallEntry(String name,String number,long duration,int times,long lastDate){
			this.name=name;
			this.number=number;
			this.duration=duration;
			this.times=times;
			this.lastDate=lastDate;
		}
		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
		public String getNumber() {
			return number;
		}
		public void setNumber(String number) {
			this.number = number;
		}
		public long getDuration() {
			return duration;
		}
		public void setDuration(long duration) {
			this.duration +=duration;
		}
		public int getTimes() {
			return times;
		}
		public void setTimes(int times) {
			this.times +=times;
		}
		public long getLastDate() {
			return lastDate;
		}
		public void setLastDate(long lastDate) {
			this.lastDate = lastDate;
		}
	}	
	//add by liyang 2017-4-18 end






	/**
	 * Gets an integer query parameter from a given uri.
	 *
	 * @param uri The uri to extract the query parameter from.
	 * @param key The query parameter key.
	 * @param defaultValue A default value to return if the query parameter does not exist.
	 * @return The value from the query parameter in the Uri.  Or the default value if the parameter
	 * does not exist in the uri.
	 * @throws IllegalArgumentException when the value in the query parameter is not an integer.
	 */
	private int getIntParam(Uri uri, String key, int defaultValue) {
		String valueString = uri.getQueryParameter(key);
		if (valueString == null) {
			return defaultValue;
		}

		try {
			return Integer.parseInt(valueString);
		} catch (NumberFormatException e) {
			String msg = "Integer required for " + key + " parameter but value '" + valueString +
					"' was found instead.";
			throw new IllegalArgumentException(msg, e);
		}
	}

	@Override
	public String getType(Uri uri) {
		int match = sURIMatcher.match(uri);
		switch (match) {
		case CALLS:
			return Calls.CONTENT_TYPE;
		case CALLS_HB:
			return Calls.CONTENT_TYPE;
		case CALLS_ID:
			return Calls.CONTENT_ITEM_TYPE;
		case CALLS_FILTER:
			return Calls.CONTENT_TYPE;
			/// M: @{
		case CALLS_SEARCH_FILTER:
			return Calls.CONTENT_TYPE;
		case SEARCH_SUGGESTIONS:
			return Calls.CONTENT_TYPE;
		case CONFERENCE_CALLS_ID:
			return Calls.CONTENT_TYPE;
		case CALL_DETAILS:
			return Calls.CONTENT_TYPE;
		case CALL_DISTINCT_SEARCH:
			return Calls.CONTENT_TYPE;
		case USEFUL_NUMBER:
			return Calls.CONTENT_TYPE;
			/// @}
		default:
			throw new IllegalArgumentException("Unknown URI: " + uri);
		}
	}

	//add by liyang begin
	private static final String HB_PRIVATE_ID = "privacy_id";
	private String parseCallLogSelection(String selection, boolean flag) {
		if (!ContactsProvidersApplication.sIsHbPrivacySupport) {
			return selection;
		}
		String sel = selection;
		String defaultStr = "=0";
		if (flag) {
			defaultStr = ">-1";
		}

		if (!TextUtils.isEmpty(sel)) {
			if (!sel.contains(HB_PRIVATE_ID)) {
				sel = "(" + sel + ") AND " + HB_PRIVATE_ID + defaultStr;
			}
		} else {
			sel = HB_PRIVATE_ID + defaultStr;
		}

		Log.i(TAG, "sel = " + sel);

		return sel;
	}
	//add by liyang end


	@Override
	public Uri insert(Uri uri, ContentValues values) {
		Log.d(TAG,"insert,uri:"+uri+" values:"+values);
		//add by liyang begin
		if(values.containsKey("add_for_all_users")) values.remove("add_for_all_users");
		if(values.containsKey("post_dial_digits")) values.remove("post_dial_digits");
		if(values.containsKey("via_number")) values.remove("via_number");
		//add by liyang end
		/// M: @{
		int prio = Process.getThreadPriority(Process.myTid());
		Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);
		Uri retUri = null;
		long lStart = System.currentTimeMillis();
		LogUtils.d(TAG, "insert() + ===========,values:"+values);

		if (CONFERENCE_CALLS == sURIMatcher.match(uri)) {
			retUri = mCallLogProviderEx.insertConferenceCall(uri, values);
			LogUtils.d(TAG, "insert()  =========== Uri:" + uri);
			LogUtils.d(TAG, "insert()- =========== Time:" + (System.currentTimeMillis() - lStart));
			Process.setThreadPriority(prio);
			return retUri;
		}
		/// @}

		waitForAccess(mReadAccessLatch);
		checkForSupportedColumns(sCallsProjectionMap, values);
		// Inserting a voicemail record through call_log requires the voicemail
		// permission and also requires the additional voicemail param set.
		if (hasVoicemailValue(values)) {
			checkIsAllowVoicemailRequest(uri);
			mVoicemailPermissions.checkCallerHasWriteAccess(getCallingPackage());
		}
		if (mCallsInserter == null) {
			SQLiteDatabase db = mDbHelper.getWritableDatabase();
			mCallsInserter = new DatabaseUtils.InsertHelper(db, Tables.CALLS);
		}

		ContentValues copiedValues = new ContentValues(values);

		// Add the computed fields to the copied values.
		mCallLogInsertionHelper.addComputedValues(copiedValues);

		/**
		 * M: Original Android code:
        long rowId = getDatabaseModifier(mCallsInserter).insert(copiedValues);
        if (rowId > 0) {
            return ContentUris.withAppendedId(uri, rowId);
        }
        return null;
		 *
		 * @{
		 */

		retUri = mCallLogProviderEx.insert(uri, copiedValues);
		LogUtils.d(TAG, "insert()  =========== Uri:" + uri);
		LogUtils.d(TAG, "insert()- =========== Time:" + (System.currentTimeMillis() - lStart));
		Process.setThreadPriority(prio);
		return retUri;

		/**
		 * @}
		 */
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		Log.d(TAG,"update calls:"+values);
		waitForAccess(mReadAccessLatch);
		checkForSupportedColumns(sCallsProjectionMap, values);
		// Request that involves changing record type to voicemail requires the
		// voicemail param set in the uri.
		if (hasVoicemailValue(values)) {
			checkIsAllowVoicemailRequest(uri);
		}

		selection = parseCallLogSelection(selection, false); //add for privacy

		SelectionBuilder selectionBuilder = new SelectionBuilder(selection);
		checkVoicemailPermissionAndAddRestriction(uri, selectionBuilder, false /*isQuery*/);

		final SQLiteDatabase db = mDbHelper.getWritableDatabase();
		final int matchedUriId = sURIMatcher.match(uri);
		switch (matchedUriId) {
		case CALLS:
			break;

		case CALLS_ID:
			selectionBuilder.addClause(getEqualityClause(Calls._ID, parseCallIdFromUri(uri)));
			break;

		case CONFERENCE_CALLS_ID:
			selectionBuilder.addClause(
					getEqualityClause(ConferenceCalls._ID, parseCallIdFromUri(uri)));
			int count = db.update(Tables.CONFERENCE_CALLS,
					values, selectionBuilder.build(), selectionArgs);
			return count;

		default:
			throw new UnsupportedOperationException("Cannot update URL: " + uri);
		}


		return getDatabaseModifier(db).update(Tables.CALLS, values, selectionBuilder.build(),
				selectionArgs);
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		waitForAccess(mReadAccessLatch);

		selection = parseCallLogSelection(selection, false); //add for privacy

		SelectionBuilder selectionBuilder = new SelectionBuilder(selection);
		checkVoicemailPermissionAndAddRestriction(uri, selectionBuilder, false /*isQuery*/);

		/**
		 * M: Original Android code:
        final SQLiteDatabase db = mDbHelper.getWritableDatabase();
		 *
		 * @{
		 */

		SQLiteDatabase db = null;
		try {
			db = mDbHelper.getWritableDatabase();
		} catch (SQLiteDiskIOException err) {
			err.printStackTrace();
			return 0;
		}

		/**
		 * @}
		 */

		final int matchedUriId = sURIMatcher.match(uri);
		switch (matchedUriId) {
		case CALLS:
			/**
			 * M: Original Android code:
            return getDatabaseModifier(db).delete(Tables.CALLS,
                    selectionBuilder.build(), selectionArgs);
			 *
			 * @{
			 */

			return mCallLogProviderEx.delete(uri, selection, selectionArgs, selectionBuilder);

			/**
			 * @}
			 */
		default:
			throw new UnsupportedOperationException("Cannot delete that URL: " + uri);
		}
	}

	// Work around to let the test code override the context. getContext() is final so cannot be
	// overridden.
	protected Context context() {
		return getContext();
	}

	void adjustForNewPhoneAccount(PhoneAccountHandle handle) {
		scheduleBackgroundTask(BACKGROUND_TASK_ADJUST_PHONE_ACCOUNT, handle);
	}

	/**
	 * Returns a {@link DatabaseModifier} that takes care of sending necessary notifications
	 * after the operation is performed.
	 */
	private DatabaseModifier getDatabaseModifier(SQLiteDatabase db) {
		return new DbModifierWithNotification(Tables.CALLS, db, context());
	}

	/**
	 * Same as {@link #getDatabaseModifier(SQLiteDatabase)} but used for insert helper operations
	 * only.
	 */
	private DatabaseModifier getDatabaseModifier(DatabaseUtils.InsertHelper insertHelper) {
		return new DbModifierWithNotification(Tables.CALLS, insertHelper, context());
	}

	private static final Integer VOICEMAIL_TYPE = new Integer(Calls.VOICEMAIL_TYPE);
	private boolean hasVoicemailValue(ContentValues values) {
		return VOICEMAIL_TYPE.equals(values.getAsInteger(Calls.TYPE));
	}

	/**
	 * Checks if the supplied uri requests to include voicemails and take appropriate
	 * action.
	 * <p> If voicemail is requested, then check for voicemail permissions. Otherwise
	 * modify the selection to restrict to non-voicemail entries only.
	 */
	private void checkVoicemailPermissionAndAddRestriction(Uri uri,
			SelectionBuilder selectionBuilder, boolean isQuery) {
		if (isAllowVoicemailRequest(uri)) {
			if (isQuery) {
				mVoicemailPermissions.checkCallerHasReadAccess(getCallingPackage());
			} else {
				mVoicemailPermissions.checkCallerHasWriteAccess(getCallingPackage());
			}
		} else {
			selectionBuilder.addClause(EXCLUDE_VOICEMAIL_SELECTION);
		}
	}

	/**
	 * Determines if the supplied uri has the request to allow voicemails to be
	 * included.
	 */
	private boolean isAllowVoicemailRequest(Uri uri) {
		return uri.getBooleanQueryParameter(Calls.ALLOW_VOICEMAILS_PARAM_KEY, false);
	}

	/**
	 * Checks to ensure that the given uri has allow_voicemail set. Used by
	 * insert and update operations to check that ContentValues with voicemail
	 * call type must use the voicemail uri.
	 * @throws IllegalArgumentException if allow_voicemail is not set.
	 */
	private void checkIsAllowVoicemailRequest(Uri uri) {
		if (!isAllowVoicemailRequest(uri)) {
			throw new IllegalArgumentException(
					String.format("Uri %s cannot be used for voicemail record." +
							" Please set '%s=true' in the uri.", uri,
							Calls.ALLOW_VOICEMAILS_PARAM_KEY));
		}
	}

	/**
	 * Parses the call Id from the given uri, assuming that this is a uri that
	 * matches CALLS_ID. For other uri types the behaviour is undefined.
	 * @throws IllegalArgumentException if the id included in the Uri is not a valid long value.
	 */
	private long parseCallIdFromUri(Uri uri) {
		try {
			return Long.parseLong(uri.getPathSegments().get(1));
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid call id in uri: " + uri, e);
		}
	}

	/**
	 * Syncs any unique call log entries that have been inserted into the primary user's call log
	 * since the last time the last sync occurred.
	 */
	private void syncEntriesFromPrimaryUser(UserManager userManager) {
		final int userHandle = userManager.getUserHandle();
		if (userHandle == UserHandle.USER_OWNER
				|| userManager.getUserInfo(userHandle).isManagedProfile()) {
			return;
		}

		final long lastSyncTime = getLastSyncTime();
		final Uri uri = ContentProvider.maybeAddUserId(CallLog.Calls.CONTENT_URI,
				UserHandle.USER_OWNER);
		final Cursor cursor = getContext().getContentResolver().query(
				uri,
				CALL_LOG_SYNC_PROJECTION,
				EXCLUDE_VOICEMAIL_SELECTION + " AND " + MORE_RECENT_THAN_SELECTION,
				new String[] {String.valueOf(lastSyncTime)},
				Calls.DATE + " DESC");
		if (cursor == null) {
			return;
		}
		try {
			final long lastSyncedEntryTime = copyEntriesFromCursor(cursor);
			if (lastSyncedEntryTime > lastSyncTime) {
				setLastTimeSynced(lastSyncedEntryTime);
			}
		} finally {
			cursor.close();
		}
	}

	/**
	 * Un-hides any hidden call log entries that are associated with the specified handle.
	 *
	 * @param handle The handle to the newly registered {@link android.telecom.PhoneAccount}.
	 */
	private void adjustForNewPhoneAccountInternal(PhoneAccountHandle handle) {
		String[] handleArgs =
				new String[] { handle.getComponentName().flattenToString(), handle.getId() };

		// Check to see if any entries exist for this handle. If so (not empty), run the un-hiding
		// update. If not, then try to identify the call from the phone number.
		Cursor cursor = query(Calls.CONTENT_URI, MINIMAL_PROJECTION,
				Calls.PHONE_ACCOUNT_COMPONENT_NAME + " =? AND " + Calls.PHONE_ACCOUNT_ID + " =?",
				handleArgs, null);

		if (cursor != null) {
			try {
				if (cursor.getCount() >= 1) {
					// run un-hiding process based on phone account
					mDbHelper.getWritableDatabase().execSQL(
							UNHIDE_BY_PHONE_ACCOUNT_QUERY, handleArgs);
				} else {
					TelecomManager tm = TelecomManager.from(getContext());
					if (tm != null) {

						PhoneAccount account = tm.getPhoneAccount(handle);
						if (account != null && account.getAddress() != null) {
							// We did not find any items for the specific phone account, so run the
							// query based on the phone number instead.
							mDbHelper.getWritableDatabase().execSQL(UNHIDE_BY_ADDRESS_QUERY,
									new String[] { account.getAddress().toString() });
						}

					}
				}
			} finally {
				cursor.close();
			}
		}

	}

	/**
	 * @param cursor to copy call log entries from
	 *
	 * @return the timestamp of the last synced entry.
	 */
	@VisibleForTesting
	long copyEntriesFromCursor(Cursor cursor) {
		long lastSynced = 0;
		final ContentValues values = new ContentValues();
		final SQLiteDatabase db = mDbHelper.getWritableDatabase();
		db.beginTransaction();
		try {
			final String[] args = new String[2];
			cursor.moveToPosition(-1);
			while (cursor.moveToNext()) {
				values.clear();
				DatabaseUtils.cursorRowToContentValues(cursor, values);
				final String startTime = values.getAsString(Calls.DATE);
				final String number = values.getAsString(Calls.NUMBER);

				if (startTime == null || number == null) {
					continue;
				}

				if (cursor.isLast()) {
					try {
						lastSynced = Long.valueOf(startTime);
					} catch (NumberFormatException e) {
						Log.e(TAG, "Call log entry does not contain valid start time: "
								+ startTime);
					}
				}

				// Avoid duplicating an already existing entry (which is uniquely identified by
				// the number, and the start time)
				args[0] = startTime;
				args[1] = number;
				if (DatabaseUtils.queryNumEntries(db, Tables.CALLS,
						Calls.DATE + " = ? AND " + Calls.NUMBER + " = ?", args) > 0) {
					continue;
				}

				db.insert(Tables.CALLS, null, values);
			}
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
		return lastSynced;
	}

	private long getLastSyncTime() {
		try {
			return Long.valueOf(mDbHelper.getProperty(DbProperties.CALL_LOG_LAST_SYNCED, "0"));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private void setLastTimeSynced(long time) {
		mDbHelper.setProperty(DbProperties.CALL_LOG_LAST_SYNCED, String.valueOf(time));
	}

	private static void waitForAccess(CountDownLatch latch) {
		if (latch == null) {
			return;
		}

		while (true) {
			try {
				latch.await();
				return;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}


	private void scheduleBackgroundTask(int task, Object arg) {
		mBackgroundHandler.obtainMessage(task, arg).sendToTarget();
	}

	private void performBackgroundTask(int task, Object arg) {
		if (task == BACKGROUND_TASK_INITIALIZE) {
			try {
				final Context context = getContext();
				if (context != null) {
					final UserManager userManager = UserUtils.getUserManager(context);
					if (userManager != null &&
							!userManager.hasUserRestriction(UserManager.DISALLOW_OUTGOING_CALLS)) {
						syncEntriesFromPrimaryUser(userManager);
					}
				}
			} finally {
				mReadAccessLatch.countDown();
				mReadAccessLatch = null;
			}
		} else if (task == BACKGROUND_TASK_ADJUST_PHONE_ACCOUNT) {
			adjustForNewPhoneAccountInternal((PhoneAccountHandle) arg);
		}

	}

	private String processSelectionForBlack(int match, String selection) {
		String rejectSelection = selection;
		if (match != CALLS_JION_DATA_VIEW_ID && match != CALLS_ID) {
			if (selection == null) {
				rejectSelection = "calls.reject=0";
			} else {
				if (!selection.contains("reject")) {
					rejectSelection = "(" + selection + ") AND calls.reject=0 ";
				}
			}
		}
		return rejectSelection;
	}
}
