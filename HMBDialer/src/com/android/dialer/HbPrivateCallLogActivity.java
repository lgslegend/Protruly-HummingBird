package com.android.dialer;

import hb.app.dialog.AlertDialog;
import hb.app.dialog.AlertDialog.Builder;
import hb.view.menu.bottomnavigation.BottomNavigationView;
import hb.view.menu.bottomnavigation.BottomNavigationView.OnNavigationItemSelectedListener;
import hb.widget.ActionMode;
import hb.widget.toolbar.Toolbar;

import com.android.contacts.common.activity.TransactionSafeActivity;

import com.android.dialer.R;
import com.android.dialer.calllog.CallLogFragment;
import com.android.dialer.widget.EmptyContentView;
import com.hb.privacy.PrivacyUtils;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import hb.widget.toolbar.Toolbar;

public class HbPrivateCallLogActivity extends TransactionSafeActivity implements hb.widget.toolbar.Toolbar.OnMenuItemClickListener{
	private CallLogFragment mCallLogFragment;
	private static String TAG="HbPrivateCallLogActivity";
	protected boolean mIsPrivate = true;
	private ActionMode actionMode;
	private BottomNavigationView bottomBar;
	public BottomNavigationView getmBottomBar() {
		return bottomBar;
	}
	public ActionMode getmActionMode() {
		return actionMode;
	}
	private EmptyContentView mEmptyListView;
	private Toolbar toolbar;
	
	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		Log.d(TAG,"onResume");
		if(PrivacyUtils.getCurrentAccountId()<=0){
			Toast.makeText(HbPrivateCallLogActivity.this, "当前非隐私模式", Toast.LENGTH_LONG).show();
			finish();
		}
	}
	
	protected void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "HbPrivateCallLogActivity onCreate");		
		mIsPrivate = true;
		super.onCreate(savedInstanceState);

		setHbContentView(R.layout.hb_call_log_view);

		toolbar = getToolbar();
		toolbar.setElevation(0f);
		toolbar.setTitle(getString(R.string.hb_call_detail_title));

		toolbar.setNavigationOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				Log.d(TAG,"NavigationOnClickListener");
				finish();
			}
		});
		setTitle(R.string.hb_privacy_calllogs);	

		actionMode=getActionMode();
		bottomBar = (BottomNavigationView)findViewById(R.id.bottom_navigation_view);
		Log.d(TAG,"bottomBar:"+bottomBar+" actionMode:"+actionMode);
		bottomBar.setNavigationItemSelectedListener(new OnNavigationItemSelectedListener() {

			@Override
			public boolean onNavigationItemSelected(MenuItem arg0) {
				// TODO Auto-generated method stub
				Log.d(TAG,"onNavigationItemSelected,arg0.getItemId():"+arg0.getItemId());
				switch (arg0.getItemId()) {
				case R.id.hb_contacts_delete:
					int count=mCallLogFragment.getAdapter().getCheckedCount();
					AlertDialog.Builder builder = new Builder(HbPrivateCallLogActivity.this);
					builder.setMessage(HbPrivateCallLogActivity.this.getString(R.string.hb_delete_call_log_message,count));
					builder.setTitle(null);
					builder.setNegativeButton(HbPrivateCallLogActivity.this.getString(R.string.hb_cancel), null);
					builder.setPositiveButton(HbPrivateCallLogActivity.this.getString(R.string.hb_ok), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which)
						{
							dialog.dismiss();
							Log.d(TAG,"delete");
							mCallLogFragment.deleteSelectedCallLogs();
						}
					});
					AlertDialog alertDialog = builder.create();
					alertDialog.show();
					break;

				default:
					break;
				} 
				return false;
			}
		});
		

		
		FragmentManager fm = getFragmentManager();

		Fragment frag = fm.findFragmentById(R.id.call_log_frag);
		if (null != frag && frag instanceof CallLogFragment) {
			mCallLogFragment = (CallLogFragment) frag;
		} else if (mCallLogFragment == null) {
			mCallLogFragment = new CallLogFragment();
			getFragmentManager().beginTransaction()
			.add(R.id.call_log_frag, mCallLogFragment).commitAllowingStateLoss();
		}
		mCallLogFragment.setPrivate(mIsPrivate);
		mEmptyListView = (EmptyContentView) findViewById(R.id.empty_list_view);
		mEmptyListView.setImage(R.drawable.hb_no_calllog_image);
		mCallLogFragment.setEmptyListView(mEmptyListView);
		if(mIsPrivate) {
			DialerApplication.mPrivacyActivityList.add(this);
		}
	}

	public boolean isPrivate(){
		return mIsPrivate;
	}
}

