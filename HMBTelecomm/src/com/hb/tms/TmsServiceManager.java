package com.hb.tms;

import java.util.ArrayList;
import java.util.List;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import com.hb.tms.*;
import com.hmb.manager.aidl.*;

public class TmsServiceManager{
	private static final String TAG = "TmsServiceManager";
    private static final String SERVICE_ACTION = "android.intent.action.CspService";
    
    private ICspService mICspService;

    private Context mContext;
    
    private static TmsServiceManager sInstance;
	public static boolean mIsServiceConnected = false;
    
    public static TmsServiceManager getInstance(Context context) {
        synchronized (TmsServiceManager.class) {
            if (sInstance == null) {
                sInstance = new TmsServiceManager(context);
            } 
            return sInstance;
        }
    }

     public static TmsServiceManager getInstance() {
          return sInstance;
        
    }
    
    private TmsServiceManager(Context context) {
        mContext = context;
    }
    
    public void bindService() {
        Intent intent = new Intent(SERVICE_ACTION);
        if (!mIsServiceConnected) {
        	try {
        		mContext.bindService(getExplicitIntent(mContext, intent), serviceConnection, Context.BIND_AUTO_CREATE|Context.BIND_IMPORTANT);
        	} catch (Exception e) {
        		e.printStackTrace();
        	}
        }
    }
    
    public void unbindService() {
    	  if (mIsServiceConnected) {
    		   	try {
    	              mContext.unbindService(serviceConnection);
            	} catch (Exception e) {
            		e.printStackTrace();
            	}
          }
    }
    
    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // TODO Auto-generated method stub
            Log.i(TAG, "onServiceConnected ");
            mIsServiceConnected = true;
            mICspService = ICspService.Stub.asInterface(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // TODO Auto-generated method stub
            mIsServiceConnected = false;
            mICspService = null;
        }
        
    };
    
    MarkResult getMark(int type, String number) {    	
		try {
			if (mICspService != null) {
				MarkResult mark = mICspService.getMark(type, number);
				return mark;
			}
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		return null;
    }
    
    String getTagName(int tagType) {
		try {
			if (mICspService != null) {
				return  mICspService.getTagName(tagType);
			}
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		return null;
    }
    
    
    private static Intent getExplicitIntent(Context context, Intent implicitIntent) {
        Log.i(TAG, "getExplicitIntent()...");
        // Retrieve all services that can match the given intent
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> resolveInfo = pm.queryIntentServices(implicitIntent, 0);
        // Make sure only one match was found
        if (resolveInfo == null || resolveInfo.size() != 1) {
            return null;
        }
        // Get component info and create ComponentName
        ResolveInfo serviceInfo = resolveInfo.get(0);
        String packageName = serviceInfo.serviceInfo.packageName;
        String className = serviceInfo.serviceInfo.name;
        Log.i(TAG, "getExplicitIntent()... packageName= " + packageName);
        Log.i(TAG, "getExplicitIntent()... className= " + className);
        ComponentName component = new ComponentName(packageName, className);
        // Create a new intent. Use the old one for extras and such reuse
        Intent explicitIntent = new Intent(implicitIntent);
        // Set the component to be explicit
        explicitIntent.setComponent(component);
        return explicitIntent;
    }
    
    
}