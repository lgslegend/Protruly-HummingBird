package com.hb.interception.util;
import android.util.Log;
import android.text.TextUtils;

public class AreaManager {
	
	public static final String TAG = "AreaManager";
	
	public static String getArea(String number) {
		// get location of the input phone number 
		// 获取用户输入的号码的归属地
		String location = "hbunknown";
		Log.d(TAG, " number = " + number + ", area = " + location);
		return location;
	}
}
