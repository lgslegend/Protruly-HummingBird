package com.hb.netmanage;

import com.hb.netmanage.activity.DataPlanSetActivity;
import com.hb.netmanage.activity.MainActivity;
import com.hb.netmanage.receiver.SimStateReceiver;
import com.hb.netmanage.utils.NotificationUtil;
import com.hb.netmanage.utils.PreferenceUtil;
import com.hb.netmanage.utils.ToolsUtil;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import com.hb.netmanage.R;
/**
 * 
 * @author zhaolaichao
 *
 */
public class NotifyInfo {
	/**
	 * 未设置流量套餐时提示最多弹出3次
	 */
	private final static int NOTIFY_COUNT_MAX = 3;
	private static AlarmManager mAlarm;
   
	/**
	 * 未设置流量套餐时
	 * @param context
	 */
    public static void showNotify(Context context) {
		if (ToolsUtil.getCurrentNetSimSubInfo(context) != -1) {
			//检测到有sim卡插入且为默认上网卡且用户还没进行流量套餐设置时
			String activeSimImsi = ToolsUtil.getActiveSimImsi(context);
			if (TextUtils.isEmpty(activeSimImsi)){
				return;
			}
			String saveSimImsi = PreferenceUtil.getString(context, "", PreferenceUtil.CURRENT_ACTIVE_IMSI_KEY, "");
			long commonData = PreferenceUtil.getLong(context, activeSimImsi, PreferenceUtil.DATAPLAN_COMMON_KEY, 0);
			if (TextUtils.equals(activeSimImsi, saveSimImsi)) {
				if (commonData > 0) {
					//没有设置流量套餐
					return;
				}
			}
			if(commonData == 0) {
				int infoCount = PreferenceUtil.getInt(context, activeSimImsi, PreferenceUtil.NOTIFY_UNDATA_COUNT_KEY, 0);
				if (infoCount >= NOTIFY_COUNT_MAX) {
					//未设置流量套餐时提示最多弹出3次
					return;
				}
				int currectIndex = 0;
				for (int i = 0; i < DataManagerApplication.mImsiArray.length; i++) {
					String imsi = DataManagerApplication.mImsiArray[i];
					if (activeSimImsi.equals(imsi)){
						currectIndex = i;
						break;
					}
				}
				if (currectIndex >= 0) {
					Intent intentInfo = new Intent(context, MainActivity.class);
//					intentInfo.putExtra("CURRENT_INDEX", currectIndex);
					NotificationUtil.showNotification(context, "", context.getString(R.string.app_name), context.getString(R.string.dataplan_not_set_notifyinfo), intentInfo);
					//没有设置套餐
					setAlarm(context, false);
					infoCount++;
					PreferenceUtil.putInt(context, activeSimImsi, PreferenceUtil.NOTIFY_UNDATA_COUNT_KEY, infoCount);
				}
			}
		}
   }
    /**
	 * 设置提示信息
	 * @param context
	 * @param isSetDataPlan
	 */
	private static void setAlarm(Context context, boolean isSetDataPlan) {
		Intent mAlarmIntent  = new Intent(context, SimStateReceiver.class);
		//提示设置套餐action
		mAlarmIntent.setAction(SimStateReceiver.ACTION_NOTIFY_SET_DATAPLAN);
		PendingIntent pi = PendingIntent.getService(context, 0, mAlarmIntent, 0);
		 mAlarm = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
		if (!isSetDataPlan) {
			//从现在起10天时间开始
			long repeatTime = 10 * 24 * 3600 * 1000;
			mAlarm.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), repeatTime,  pi);     
		} else {
			mAlarm.cancel(pi);
		}
	}
	
}
