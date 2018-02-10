package com.hb.thememanager.database;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

public class SharePreferenceManager {

	private static final String NAME = "Hummingbird_ThemeManager";
	public static final String KEY_APPLIED_THEME_ID = "apply_theme_id";
	public static final String KEY_APPLIED_WALLPAPER_ID = "apply_wallpaper_id";
	public static final String KEY_APPLIED_LOCKSCREEN_WALLPAPER_ID = "apply_lockscreen_wallpaper_id";
	public static final String KEY_APPLIED_RINGTONG_ID = "apply_ringtong_id";
	public static final String KEY_APPLIED_FONT_ID = "apply_font_id";
	
	
	public static void setBooleanPreference(Context context, String key, boolean value) {
			SharedPreferences preferences = context.getSharedPreferences(NAME, 
	        		Context.MODE_PRIVATE);
	        SharedPreferences.Editor editor = preferences.edit();
	        editor.putBoolean(key, value);
	        editor.commit();
	    }
	    
	    public static boolean getBooleanPreference(Context context, String key, boolean defaultValue) {
	        SharedPreferences preferences = context.getSharedPreferences(NAME, 
	        		Context.MODE_PRIVATE);
	        boolean result = preferences.getBoolean(key, defaultValue);
	        return result;
	    }
	    
	    public static void setIntPreference(Context context, String key, int value) {
	        SharedPreferences preferences = context.getSharedPreferences(NAME, 
	        		Context.MODE_PRIVATE);
	        SharedPreferences.Editor editor = preferences.edit();
	        editor.putInt(key, value);
	        editor.commit();
	    }
	    
	    public static int getIntPreference(Context context, String key, int defaultValue) {
	        SharedPreferences preferences = context.getSharedPreferences(NAME, 
	        		Context.MODE_PRIVATE);
	        int result = preferences.getInt(key, defaultValue);
	        return result;
	    }
	    
	    public static void setStringPreference(Context context, String key, String value) {
	        SharedPreferences preferences = context.getSharedPreferences(NAME, 
	        		Context.MODE_PRIVATE);
	        SharedPreferences.Editor editor = preferences.edit();
	        editor.putString(key, value);
	        editor.commit();
	    }
	    
	    public static String getStringPreference(Context context, String key, String defaultValue) {
	        SharedPreferences preferences = context.getSharedPreferences(NAME, 
	        		Context.MODE_PRIVATE);
	        String result = preferences.getString(key, defaultValue);
	        return result;
	    }
	    
	    
}
