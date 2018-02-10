package com.hb.tms;

import com.hmb.manager.aidl.MarkResult;

import android.util.Log;

public class MarkManager {
	
	public static final String TAG = "MarkManager";
	
	public static MarkResult getMark(int type, String number) {
		MarkResult mark = TmsServiceManager.getInstance().getMark(type, number);
		Log.d(TAG, "mark = " + mark);
		return mark;
	}
	
	public static void updatetMark(String type, String number) {
		TmsServiceManager.getInstance().updateMark(type, number);
	}
}
