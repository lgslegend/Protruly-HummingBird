/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */
package com.mediatek.contacts.list;

import com.android.contacts.common.list.ContactEntryListAdapter.ViewHolder;
import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.net.Uri.Builder;
import hb.provider.ContactsContract.Contacts;
import hb.provider.ContactsContract.Directory;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.DefaultContactListAdapter;
import com.android.contacts.common.list.PinnedHeaderListView;
import com.android.contacts.common.list.ProfileAndContactsLoader;
import com.android.contacts.common.list.ContactListAdapter.ContactQuery;
import com.android.contacts.list.MultiSelectEntryContactListAdapter.SelectedContactsListener;

import com.mediatek.contacts.util.MtkToast;

import java.util.HashMap;

public class MultiBasePickerAdapter extends DefaultContactListAdapter {
	private static final String TAG = "MultiBasePickerAdapter";

	public static final int FILTER_ACCOUNT_WITH_PHONE_NUMBER_ONLY = 100;
	public static final int FILTER_ACCOUNT_WITH_PHONE_NUMBER_OR_EMAIL = 101;

	private ListView mListView;
	private CursorLoader mLoader;

	private int mFilterAccountOptions;

	public final class PickListItemCache {

		public final class PickListItemData {
			public int contactIndicator;

			public int simIndex;

			public String displayName;

			public String lookupUri;

			public PickListItemData(int contactIndicator2, int simIndex2, String displayName2,
					String lookupUri2) {
				contactIndicator = contactIndicator2;
				simIndex = simIndex2;
				displayName = displayName2;
				lookupUri = lookupUri2;
			}

			@Override
			public String toString() {
				return "[PickListItemData]@" + hashCode() + " contactIndicator: "
						+ contactIndicator + ", simIndex: " + simIndex + ", displayName: "
						+ displayName + ", lookupUri: " + lookupUri;
			}

		}

		private HashMap<Long, PickListItemData> mMap = new HashMap<Long, PickListItemData>();

		public void add(long id, int contactIndicator, int simIndex, String displayName,
				String lookupUri) {
			mMap.put(Long.valueOf(id), new PickListItemData(contactIndicator, simIndex,
					displayName, lookupUri));
		}

		public void add(final Cursor cursor) {
			long id = cursor.getInt(ContactQuery.CONTACT_ID);
			//			int contactIndicator = cursor.getInt(cursor
			//					.getColumnIndexOrThrow(Contacts.INDICATE_PHONE_SIM));
			//			int simIndex = cursor.getInt(cursor.getColumnIndexOrThrow(Contacts.INDEX_IN_SIM));
			int contactIndicator=-1;
			int simIndex=-1;
			String displayName = cursor.getString(ContactQuery.CONTACT_DISPLAY_NAME);
			String lookupUri = cursor.getString(ContactQuery.CONTACT_LOOKUP_KEY);
			mMap.put(Long.valueOf(id), new PickListItemData(contactIndicator, simIndex,
					displayName, lookupUri));
		}

		// Clear the cache data
		public void clear() {
			mMap.clear();
		}

		// The cache is empty or not
		public boolean isEmpty() {
			return mMap.isEmpty();
		}

		public int getCacheSize() {
			return mMap.size();
		}

		public PickListItemData getItemData(long id) {
			return mMap.get(Long.valueOf(id));
		}
	}

	private PickListItemCache mPickListItemCache = new PickListItemCache();

	public MultiBasePickerAdapter(Context context, ListView lv) {
		super(context);
		mListView = lv;
	}

	//	@Override
	//	protected ContactListItemView newView(Context context, int partition, Cursor cursor,
	//			int position, ViewGroup parent) {
	//		Log.i(TAG, "[newView]partition = " + partition + ",position = " + position);
	//		View itemView=super.newView(context, partition,cursor, position, parent);
	//		final ViewHolderForContacts viewHolder = (ViewHolderForContacts) itemView.getTag();
	//		if(viewHolder==null) return null;
	//		final  ContactListItemView view=viewHolder.view;
	//
	//		// Enable check box
	//		view.setCheckable(false);
	//		viewHolder.checkBox.setVisibility(View.VISIBLE);
	//
	//		///Fixed ALPS02292698:The selected contact will change to blue background @{
	//		// For using list-view's check states
	//		//view.setActivatedStateSupported(true);
	//		view.setActivatedStateSupported(isSelectionVisible());
	//		///@}
	//
	//		return view;
	//	}

	@Override
	protected void bindView(View itemView, int partition, Cursor cursor, int position) {
		super.bindView(itemView, partition, cursor, position);

		final ViewHolder viewHolder = (ViewHolder) itemView.getTag();
		if(viewHolder==null) return;
		bindCheckBoxForHb(viewHolder.checkBox,cursor,position);
	}



	@Override
	public void configureLoader(CursorLoader loader, long directoryId) {
		super.configureLoader(loader, directoryId);

		/** M: Bug Fix for ALPS00404125 @{ */
		ContactListFilter filter = getFilter();
		if (isSearchMode()) {
			String query = getQueryString();
			if (query == null) {
				query = "";
			}
			query = query.trim();
			if (!TextUtils.isEmpty(query)) {
				configureSelection(loader, directoryId, filter);
			}
		}
		Builder builder = loader.getUri().buildUpon();
		builder.appendQueryParameter("checked_ids_arg", Contacts.CONTENT_URI.toString());
		loader.setUri(builder.build());
		((ProfileAndContactsLoader) loader).setLoadStars(false);
		/** @} */
		mLoader = loader;
	}

	@Override
	protected void configureSelection(CursorLoader loader, long directoryId,
			ContactListFilter filter) {
		if (filter == null) {
			Log.i(TAG, "[configureSelection]filter is null,return.");
			return;
		}

		if (directoryId != Directory.DEFAULT) {
			Log.i(TAG, "[configureSelection]return,directoryId = " + directoryId);
			return;
		}

		super.configureSelection(loader, directoryId, filter);
		StringBuilder selection = new StringBuilder();
		selection.append(loader.getSelection());
		if (mFilterAccountOptions == FILTER_ACCOUNT_WITH_PHONE_NUMBER_ONLY) {
			selection.append(" AND " + Contacts.HAS_PHONE_NUMBER + "=1");
		} else if (mFilterAccountOptions == FILTER_ACCOUNT_WITH_PHONE_NUMBER_OR_EMAIL) {
			selection.append(" AND " + Contacts.HAS_PHONE_NUMBER + "=1");
		}
		/// M: If not show sdn numbers, set the query selection IS_SDN_CONTACT = 0.
		if (!mShowSdnNumber) {
			selection.append(" AND " + Contacts.IS_SDN_CONTACT + "=0");
		}
		loader.setSelection(selection.toString());
	}

	public void setFilterAccountOption(int filterAccountOptions) {
		mFilterAccountOptions = filterAccountOptions;
	}

	public int getContactID(int position) {
		Cursor cursor = (Cursor) getItem(position);
		if (cursor == null) {
			return 0;
		}
		return cursor.getInt(cursor.getColumnIndexOrThrow(Contacts._ID));
	}

	public void setDataSetChangedNotifyEnable(boolean enable) {
		if (mLoader != null) {
			if (enable) {
				mLoader.startLoading();
			} else {
				mLoader.stopLoading();
			}
		}
	}

	public void cacheDataItem(Cursor cursor) {
		mPickListItemCache.add(cursor);
	}

	public PickListItemCache getListItemCache() {
		return mPickListItemCache;
	}

	public void configurePinnedHeaders(PinnedHeaderListView listView) {
		super.configurePinnedHeaders(listView);
		listView.setDrawPinnedHeader(false);
	}

	/**
	 * For bug fix ALPS01753724,ALPS01751868.
	 */
	public boolean hasStableIds() {
		return false;
	}

	/** @} */
	/*=================for checkbox=========================*/

	private SelectedContactsListener mSelectedContactsListener;

	public interface SelectedContactsListener {
		void onSelectedContactsChangedViaCheckBox();
	}

	private final OnClickListener mCheckBoxClickListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			final CheckBox checkBox = (CheckBox) v;
			final Long contactId = (Long) checkBox.getTag();

			/* fix CR ALPS02350421 intercept more than 3500 selected @{*/
			int multiChoiceLimitCount = AbstractPickerFragment.DEFAULT_MULTI_CHOICE_MAX_COUNT;
			boolean isSelectedLimited = getSelectedContactIds().size() >= multiChoiceLimitCount;
			boolean isChecked = checkBox.isChecked();
			if (isSelectedLimited && isChecked) {
				Log.i(TAG, "[mCheckBoxClickListener] Current selected Contact cnt > 3500,cannot " +
						"select more");
				checkBox.setChecked(false);
				String msg = (mContext.getResources()).getString(
						R.string.multichoice_contacts_limit, multiChoiceLimitCount);
				MtkToast.toast(mContext.getApplicationContext(), msg);
				return;
			}
			//@}

			if (checkBox.isChecked()) {
				getSelectedContactIds().add(contactId);
			} else {
				getSelectedContactIds().remove(contactId);
			}
			if (mSelectedContactsListener != null) {
				mSelectedContactsListener.onSelectedContactsChangedViaCheckBox();
			}
		}
	};

	private void bindCheckBox(ContactListItemView view, Cursor cursor, int position) {
//		Log.d(TAG,"[bindCheckBox]position = " + position);
		final CheckBox checkBox = view.getCheckBox();
		// checkBox.setChecked(mListView.isItemChecked(position));
		final long contactId = cursor.getLong(ContactQuery.CONTACT_ID);
		checkBox.setChecked(getSelectedContactIds().contains(contactId));
		checkBox.setTag(contactId);
		checkBox.setOnClickListener(mCheckBoxClickListener);
	}

	private void bindCheckBoxForHb(CheckBox checkBox, Cursor cursor, int position) {
//		Log.d(TAG,"[bindCheckBox]position = " + position);
		//checkBox.setChecked(mSelectedContactIds.contains(contactId));
		// checkBox.setChecked(mListView.isItemChecked(position));
		final long contactId = cursor.getLong(ContactQuery.CONTACT_ID);
		checkBox.setChecked(getSelectedContactIds().contains(contactId));
		checkBox.setTag(contactId);
		//        checkBox.setOnClickListener(mCheckBoxClickListener);
	}

	public void setSelectedContactsListener(SelectedContactsListener listener) {
		mSelectedContactsListener = listener;
	}

	public View hbNewView(Context context, int partition, Cursor cursor, final int position,
			ViewGroup parent) {
		Log.d(TAG,"hbNewView,pos:"+position);
		ViewHolder viewHolder = new ViewHolder();
		ViewGroup view = newOutView();

		View inner =LayoutInflater.from(mContext).inflate(com.hb.R.layout.list_item_1_line_multiple_choice,null);

		view.addView(inner, 1, new LayoutParams(LayoutParams.MATCH_PARENT,
				LayoutParams.WRAP_CONTENT));
		viewHolder.header=(TextView)view.findViewById(R.id.listview_item_header);
		viewHolder.name=(TextView)inner.findViewById(android.R.id.text1);
		viewHolder.name.setSingleLine(true);
		viewHolder.name.setEllipsize(TextUtils.TruncateAt.valueOf("END"));
		viewHolder.devider=view.findViewById(R.id.devider);
		viewHolder.checkBox=(CheckBox) view.findViewById(android.R.id.button1);
		view.setTag(viewHolder);
		return view;
	}
	
	public View hbNewDoublelineView(Context context, int partition, Cursor cursor, final int position,
			ViewGroup parent) {
		Log.d(TAG,"hbNewDoublelineView,position:"+position);
		ViewHolder viewHolder = new ViewHolder();
		ViewGroup view =newOutView();
		
		View inner =LayoutInflater.from(mContext).inflate(com.hb.R.layout.list_item_2_line_multiple_choice,null);		
		
		view.addView(inner, 1, new LayoutParams(LayoutParams.MATCH_PARENT,
				LayoutParams.WRAP_CONTENT));
		
		viewHolder.header=(TextView)view.findViewById(R.id.listview_item_header);
		viewHolder.name=(TextView)inner.findViewById(android.R.id.text1);
		viewHolder.name.setSingleLine(true);
		viewHolder.name.setEllipsize(TextUtils.TruncateAt.valueOf("END"));
		viewHolder.secondLine=(TextView)inner.findViewById(android.R.id.text2);
		viewHolder.secondLine.setSingleLine(true);
		viewHolder.secondLine.setEllipsize(TextUtils.TruncateAt.valueOf("END"));
		viewHolder.devider=view.findViewById(R.id.devider);
		viewHolder.checkBox=(CheckBox) view.findViewById(android.R.id.button1);
		view.setTag(viewHolder);
		return view;
	}
}
