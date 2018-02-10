package com.hb.interception.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;


import android.content.Context;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.location.CountryDetector;
import com.hb.interception.R;


public class FormatUtils {

	public static String formatTimeStampString(Context context,
			long when, boolean isConv) {
		Time then = new Time();
		then.set(when);
		Time now = new Time();
		now.setToNow();
		String sRet;

		if (then.year != now.year) {
			if (isConv) {
				sRet = DateFormat.format("yyyy-MM-dd", when).toString();
			} else {
				sRet = DateFormat.format("yyyy-MM-dd  kk:mm", when).toString();
			}
		} else {
			if (then.yearDay == now.yearDay) {
				int format_flags = DateUtils.FORMAT_NO_NOON_MIDNIGHT
						| DateUtils.FORMAT_ABBREV_ALL
						| DateUtils.FORMAT_CAP_AMPM;
				format_flags |= DateUtils.FORMAT_SHOW_TIME;
				sRet = DateUtils.formatDateTime(context, when, format_flags);
			} else {
				if (isConv) {
					sRet = DateFormat.format("MM-dd", when).toString();
				} else {
					sRet = DateFormat.format("yyyy-MM-dd  kk:mm", when)
							.toString();
				}
			}
		}
		return sRet;
	}
	
	

	/**
	 * 格式化时间
	 * 
	 * @param timeStamp
	 * @return
	 */
	public static String formatDateTime(long timeStamp) {
		String formatPattern1 = "今天";
		String formatPattern2 = "昨天";
		String formatPattern3 = "MM月dd日";
		String formatPattern4 = "yyyy年MM月dd日";
		Date date;
		Calendar current;
		Calendar today;
		Calendar yesterday;
		Calendar thisyear;
		
		date = new Date(timeStamp);

		// liyang add:
		current = Calendar.getInstance();// 当前

		today = Calendar.getInstance(); // 今天
		today.set(Calendar.YEAR, current.get(Calendar.YEAR));
		today.set(Calendar.MONTH, current.get(Calendar.MONTH));
		today.set(Calendar.DAY_OF_MONTH, current.get(Calendar.DAY_OF_MONTH));
		// Calendar.HOUR——12小时制的小时数 Calendar.HOUR_OF_DAY——24小时制的小时数
		today.set(Calendar.HOUR_OF_DAY, 0);
		today.set(Calendar.MINUTE, 0);
		today.set(Calendar.SECOND, 0);

		yesterday = Calendar.getInstance(); // 昨天
		yesterday.set(Calendar.YEAR, current.get(Calendar.YEAR));
		yesterday.set(Calendar.MONTH, current.get(Calendar.MONTH));
		yesterday.set(Calendar.DAY_OF_MONTH,
				current.get(Calendar.DAY_OF_MONTH) - 1);
		yesterday.set(Calendar.HOUR_OF_DAY, 0);
		yesterday.set(Calendar.MINUTE, 0);
		yesterday.set(Calendar.SECOND, 0);

		thisyear = Calendar.getInstance(); // 今年
		thisyear.set(Calendar.YEAR, current.get(Calendar.YEAR));
		thisyear.set(Calendar.MONTH, 0);
		thisyear.set(Calendar.DAY_OF_MONTH, 0);
		thisyear.set(Calendar.HOUR_OF_DAY, 0);
		thisyear.set(Calendar.MINUTE, 0);
		thisyear.set(Calendar.SECOND, 0);
		// liyang add end.

		current.setTime(date);

		// return new SimpleDateFormat(formatPattern4).format(date);

		if (current.after(today)) {
			return formatPattern1;
		} else if (current.before(today) && current.after(yesterday)) {
			return formatPattern2;
		} else if (current.before(thisyear)) {
			return new SimpleDateFormat(formatPattern4).format(date);
		} else {
			return new SimpleDateFormat(formatPattern3).format(date);
		}
	}
	
	public static String formatTimeStampStringForItem(Context context, long when) {
		boolean is24Hr = "24".equals(Settings.System.getString(context.getContentResolver(), Settings.System.TIME_12_24));
		Time then = new Time();
		then.set(when);
		Time now = new Time();
		now.setToNow();
		int format_flags = 0;/*DateUtils.FORMAT_NO_NOON_MIDNIGHT |
        /// M: Fix ALPS00419488 to show 12:00, so mark DateUtils.FORMAT_ABBREV_ALL
        //DateUtils.FORMAT_ABBREV_ALL |
        ;*/

		// If the message is from a different year, show the date and year.
		if (then.year != now.year) {
			format_flags |= DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_DATE;
		} else if (now.yearDay - then.yearDay >= 7 ) {
			// If it is from a different day than today, show only the date.
			format_flags |= DateUtils.FORMAT_SHOW_DATE;
		} else if (now.yearDay - then.yearDay >= 3){
			// Otherwise, if the message is from today, show the time.
			format_flags |= DateUtils.FORMAT_SHOW_WEEKDAY;
		} else if (now.yearDay - then.yearDay == 2){
			return context.getString(R.string.date_the_day_before);
		}else if (now.yearDay - then.yearDay == 1){
			return context.getString(R.string.date_yesterday);
		}else if ((now.hour * 60 + now.minute) - (then.hour * 60 + then.minute) >= 5){
			format_flags |= DateUtils.FORMAT_SHOW_TIME;
			if(!is24Hr) {
				format_flags |= DateUtils.FORMAT_CAP_AMPM;
			}
		}else {
			return context.getString(R.string.date_just_now);
		}

		return DateUtils.formatDateTime(context, when, format_flags);
	}
	
	public static final String getCurrentCountryIso(Context context) {
		CountryDetector detector = (CountryDetector) context
				.getSystemService(Context.COUNTRY_DETECTOR);
		return detector.detectCountry().getCountryIso();
	}

}