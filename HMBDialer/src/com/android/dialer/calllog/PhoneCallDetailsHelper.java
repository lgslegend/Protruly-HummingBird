/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.dialer.calllog;

import com.android.contacts.common.util.HbUtils;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager;
import android.R.raw;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import hb.provider.CallLog;
import hb.provider.CallLog.Calls;
import hb.provider.ContactsContract.CommonDataKinds.Phone;
import android.telecom.PhoneAccount;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import com.android.contacts.common.testing.NeededForTesting;
import com.android.contacts.common.util.PhoneNumberHelper;
import com.android.dialer.DialerApplication;
import com.android.dialer.PhoneCallDetails;
import com.android.dialer.R;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.PhoneNumberUtil;
import com.google.common.collect.Lists;
import java.util.ArrayList;

/**
 * Helper class to fill in the views in {@link PhoneCallDetailsViews}.
 */
public class PhoneCallDetailsHelper {
	/** The maximum number of icons will be shown to represent the call types in a group. */
	private static final int MAX_CALL_TYPE_ICONS = 3;

	private static final String TAG = "PhoneCallDetailsHelper";

	private final Context mContext;
	private final Resources mResources;
	/** The injected current time in milliseconds since the epoch. Used only by tests. */
	private Long mCurrentTimeMillisForTest;
	private final TelecomCallLogCache mTelecomCallLogCache;

	/**
	 * List of items to be concatenated together for accessibility descriptions
	 */
	private ArrayList<CharSequence> mDescriptionItems = Lists.newArrayList();

	/**
	 * Creates a new instance of the helper.
	 * <p>
	 * Generally you should have a single instance of this helper in any context.
	 *
	 * @param resources used to look up strings
	 */
	public PhoneCallDetailsHelper(
			Context context,
			Resources resources,
			TelecomCallLogCache telecomCallLogCache) {
		mContext = context;
		mResources = resources;
		mTelecomCallLogCache = telecomCallLogCache;
		mResources1 = new SimResources(context);
	}

	private int scrollStauts=0;

	public void setScrollStauts(int scrollStauts) {
		this.scrollStauts = scrollStauts;
	}

	private StringBuilder firstStringBuilder=null;
	private StringBuffer secondStringBuffer;
	/** Fills the call details views with content. */
	public void setPhoneCallDetails(PhoneCallDetailsViews views, PhoneCallDetails details,CallLogListItemViewHolder callLogListItemViewHolder,int itemCount) {

		firstStringBuilder=new StringBuilder();
		secondStringBuffer=new StringBuffer();
		boolean isVoicemail = false;
		int count = details.callTypes.length;
		int pos=callLogListItemViewHolder.position;


		//		if(views.call_type_icon!=null) views.call_type_icon.setBackground(getCallTypeDrawable(mContext, details.callTypes[0]));	
		if(views.datetime!=null) views.datetime.setText(/*getCallDate(details)*/HbUtils.formatTimeStampStringForItem(mContext,details.date));
		if(views.sim_icon!=null){
//			Log.d(TAG,"isMultiSimEnabled:"+DialerApplication.isMultiSimEnabled+" subscription_id:"+details.subscription_id);
			if(DialerApplication.isMultiSimEnabled&&details.subscription_id!=null){
				int slotId=-1;
				try{
					slotId=SubscriptionManager.getSlotId(Integer.parseInt(details.subscription_id));
				}catch(Exception e){
					Log.d(TAG, "e:"+e);
				}
//				Log.d(TAG, "subid:"+details.subscription_id+" slotId:"+slotId);
				callLogListItemViewHolder.slotId=slotId;
				if (slotId == 1) {
					views.sim_icon.setBackground(mContext.getDrawable(R.drawable.hb_sim2_icon));
					views.sim_icon.setVisibility(View.VISIBLE);
				} else if (slotId == 0) {
					views.sim_icon.setBackground(mContext.getDrawable(R.drawable.hb_sim1_icon));
					views.sim_icon.setVisibility(View.VISIBLE);
				}else{
					views.sim_icon.setVisibility(View.GONE);
				}
			}else{
				views.sim_icon.setVisibility(View.GONE);
			}
		}
		
		secondStringBuffer.append(getCallTypeText(mContext, details.callTypes[0],details.duration)+"  ");	
		if(details.name!=null && details.displayNumber!=null){
			secondStringBuffer.append(details.displayNumber+"  ");
		}		
		if(!TextUtils.isEmpty(details.geocode)){
			secondStringBuffer.append(details.geocode.replace(" ", "")+"  ");
		}
		if(TextUtils.isEmpty(details.name) && !TextUtils.isEmpty(details.mark)){
			secondStringBuffer.append(details.mark);
		}
		//第二行
		views.numberTextView.setText(secondStringBuffer.toString());


		//第一行，显示名字和数量
		final CharSequence nameText;
		final CharSequence displayNumber = details.displayNumber;
		if (TextUtils.isEmpty(details.name)) {
			nameText = displayNumber;
			// We have a real phone number as "nameView" so make it always LTR
			views.nameView.setTextDirection(View.TEXT_DIRECTION_LTR);
		} else {
			nameText = details.name;
		}
		firstStringBuilder.append(nameText);
		if(count>1){
			firstStringBuilder.append(" ("+count+")");
		}
		if (isVoicemail && !TextUtils.isEmpty(details.transcription)) {
			firstStringBuilder.append(" "+details.transcription);
		}
		views.nameView.setText(firstStringBuilder.toString());
		if(details.callTypes[0]==3||details.callTypes[0]==5){
			views.nameView.setTextColor(mContext.getResources().getColor(R.color.hb_missed_calllog_text_color));
		}else{
			views.nameView.setTextColor(mContext.getColor(R.color.hb_list_main_text_color));
		}
		
		if(pos==itemCount-1) views.devider.setVisibility(View.GONE);
		else views.devider.setVisibility(View.VISIBLE);
	}

	/**
	 * Builds a string containing the call location and date.
	 *
	 * @param details The call details.
	 * @return The call location and date string.
	 */
	private CharSequence getCallLocationAndDate(PhoneCallDetails details) {
		mDescriptionItems.clear();

		// Get type of call (ie mobile, home, etc) if known, or the caller's location.
		CharSequence callTypeOrLocation = getCallTypeOrLocation(details);

		// Only add the call type or location if its not empty.  It will be empty for unknown
		// callers.
		if (!TextUtils.isEmpty(callTypeOrLocation)) {
			mDescriptionItems.add(callTypeOrLocation);
		}
		// The date of this call, relative to the current time.
		mDescriptionItems.add(getCallDate(details));

		// Create a comma separated list from the call type or location, and call date.
		return DialerUtils.join(mResources, mDescriptionItems);
	}

	/**
	 * For a call, if there is an associated contact for the caller, return the known call type
	 * (e.g. mobile, home, work).  If there is no associated contact, attempt to use the caller's
	 * location if known.
	 * @param details Call details to use.
	 * @return Type of call (mobile/home) if known, or the location of the caller (if known).
	 */
	public CharSequence getCallTypeOrLocation(PhoneCallDetails details) {
		CharSequence numberFormattedLabel = null;
		// Only show a label if the number is shown and it is not a SIP address.
		if (!TextUtils.isEmpty(details.number)
				&& !PhoneNumberHelper.isUriNumber(details.number.toString())
				&& !mTelecomCallLogCache.isVoicemailNumber(details.accountHandle, details.number)) {

			if (TextUtils.isEmpty(details.name) && !TextUtils.isEmpty(details.geocode)) {
				numberFormattedLabel = details.geocode;
			} else if (!(details.numberType == Phone.TYPE_CUSTOM
					&& TextUtils.isEmpty(details.numberLabel))) {
				// Get type label only if it will not be "Custom" because of an empty number label.
				numberFormattedLabel = Phone.getTypeLabel(
						mResources, details.numberType, details.numberLabel);
			}
		}

		if (!TextUtils.isEmpty(details.name) && TextUtils.isEmpty(numberFormattedLabel)) {
			numberFormattedLabel = details.displayNumber;
		}
		return numberFormattedLabel;
	}

	/**
	 * Get the call date/time of the call, relative to the current time.
	 * e.g. 3 minutes ago
	 * @param details Call details to use.
	 * @return String representing when the call occurred.
	 */
	public CharSequence getCallDate(PhoneCallDetails details) {
		return DateUtils.getRelativeTimeSpanString(details.date,
				getCurrentTimeMillis(),
				DateUtils.MINUTE_IN_MILLIS,
				DateUtils.FORMAT_ABBREV_RELATIVE);
	}

	/** Sets the text of the header view for the details page of a phone call. */
	@NeededForTesting
	public void setCallDetailsHeader(TextView nameView, PhoneCallDetails details) {
		final CharSequence nameText;
		if (!TextUtils.isEmpty(details.name)) {
			nameText = details.name;
		} else if (!TextUtils.isEmpty(details.displayNumber)) {
			nameText = details.displayNumber;
		} else {
			nameText = mResources.getString(R.string.unknown);
		}

		nameView.setText(nameText);
	}

	@NeededForTesting
	public void setCurrentTimeForTest(long currentTimeMillis) {
		mCurrentTimeMillisForTest = currentTimeMillis;
	}

	/**
	 * Returns the current time in milliseconds since the epoch.
	 * <p>
	 * It can be injected in tests using {@link #setCurrentTimeForTest(long)}.
	 */
	private long getCurrentTimeMillis() {
		if (mCurrentTimeMillisForTest == null) {
			return System.currentTimeMillis();
		} else {
			return mCurrentTimeMillisForTest;
		}
	}

	/** Sets the call count and date. */
	private void setCallCountAndDate(PhoneCallDetailsViews views, Integer callCount,
			CharSequence dateText) {/*
		// Combine the count (if present) and the date.
		final CharSequence text;
		if (callCount != null) {
			text = mResources.getString(
					R.string.call_log_item_count_and_date, callCount.intValue(), dateText);
		} else {
			text = dateText;
		}

		views.callLocationAndDate.setText(text);
			 */}

	SimResources mResources1;
	public Drawable getCallTypeDrawable(Context context, int callType) {        
		switch (callType) {
		case Calls.INCOMING_TYPE:
			return mResources1.incoming;
		case Calls.OUTGOING_TYPE:
			return mResources1.outgoing;
		case Calls.MISSED_TYPE:
			return mResources1.missed;
		case Calls.VOICEMAIL_TYPE:
			return mResources1.voicemail;
		default:
			// It is possible for users to end up with calls with unknown call types in their
			// call history, possibly due to 3rd party call log implementations (e.g. to
			// distinguish between rejected and missed calls). Instead of crashing, just
			// assume that all unknown call types are missed calls.
			return mResources1.missed;
		}
	}

	public String getCallTypeText(Context context, int callType,long duration) {        
		switch (callType) {
		case Calls.INCOMING_TYPE:
			return duration>0?mResources1.incomingString:mResources1.rejectString;
		case Calls.OUTGOING_TYPE:
			return mResources1.outgoingString;
		case Calls.MISSED_TYPE:
			return mResources1.missedString;
		case Calls.VOICEMAIL_TYPE:
			return mResources1.voicemailString;
		default:
			// It is possible for users to end up with calls with unknown call types in their
			// call history, possibly due to 3rd party call log implementations (e.g. to
			// distinguish between rejected and missed calls). Instead of crashing, just
			// assume that all unknown call types are missed calls.
			return mResources1.missedString;
		}
	}

	public static class SimResources {

		/**
		 * Drawable representing an incoming answered call.
		 */
		public final Drawable incoming;

		/**
		 * Drawable respresenting an outgoing call.
		 */
		public final Drawable outgoing;

		/**
		 * Drawable representing an incoming missed call.
		 */
		public final Drawable missed;

		public final String incomingString;
		public final String outgoingString;
		public final String missedString;
		public final String rejectString;
		public final String voicemailString=null;
		/**
		 * Drawable representing a voicemail.
		 */
		public final Drawable voicemail;

		/**
		 * Drawable repesenting a video call.
		 */
		//		public final Drawable videoCall;
		//
		//		/**
		//		 * The margin to use for icons.
		//		 */
		//		public final int iconMargin;

		/**
		 * Configures the call icon drawables.
		 * A single white call arrow which points down and left is used as a basis for all of the
		 * call arrow icons, applying rotation and colors as needed.
		 *
		 * @param context The current context.
		 */
		public SimResources(Context context) {
			final android.content.res.Resources r = context.getResources();

			//            incoming = r.getDrawable(R.drawable.ic_call_arrow);
			//            incoming.setColorFilter(r.getColor(R.color.answered_call), PorterDuff.Mode.MULTIPLY);
			//
			//            // Create a rotated instance of the call arrow for outgoing calls.
			//            outgoing = BitmapUtil.getRotatedDrawable(r, R.drawable.ic_call_arrow, 180f);
			//            outgoing.setColorFilter(r.getColor(R.color.answered_call), PorterDuff.Mode.MULTIPLY);
			//
			//            // Need to make a copy of the arrow drawable, otherwise the same instance colored
			//            // above will be recolored here.
			//            missed = r.getDrawable(R.drawable.ic_call_arrow).mutate();
			//            missed.setColorFilter(r.getColor(R.color.missed_call), PorterDuff.Mode.MULTIPLY);

			incoming = r.getDrawable(R.drawable.hb_in_call_icon);
			outgoing = r.getDrawable(R.drawable.hb_out_call_icon);
			missed = r.getDrawable(R.drawable.hb_in_call_missed_icon);
			voicemail = r.getDrawable(R.drawable.ic_call_voicemail_holo_dark);

			incomingString=r.getString(R.string.incomingString);
			outgoingString=r.getString(R.string.outgoingString);
			missedString=r.getString(R.string.missedString);
			rejectString=r.getString(R.string.rejectString);

			// Get the video call icon, scaled to match the height of the call arrows.
			// We want the video call icon to be the same height as the call arrows, while keeping
			// the same width aspect ratio.
			//			Bitmap videoIcon = BitmapFactory.decodeResource(context.getResources(),
			//					R.drawable.ic_videocam_24dp);
			//			int scaledHeight = missed.getIntrinsicHeight();
			//			int scaledWidth = (int) ((float) videoIcon.getWidth() *
			//					((float) missed.getIntrinsicHeight() /
			//							(float) videoIcon.getHeight()));
			//			Bitmap scaled = Bitmap.createScaledBitmap(videoIcon, scaledWidth, scaledHeight, false);
			//			videoCall = new BitmapDrawable(context.getResources(), scaled);
			//			videoCall.setColorFilter(r.getColor(R.color.dialtacts_secondary_text_color),
			//					PorterDuff.Mode.MULTIPLY);
			//
			//			iconMargin = r.getDimensionPixelSize(R.dimen.call_log_icon_margin);
		}
	}
}
