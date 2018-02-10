package com.hb.interception.activity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import hb.app.HbActivity;
import hb.view.menu.bottomnavigation.BottomNavigationView;
import hb.view.menu.bottomnavigation.BottomNavigationView.OnNavigationItemSelectedListener;
import hb.widget.ActionMode;
import hb.widget.ActionMode.Item;
import hb.widget.ActionModeListener;
import hb.widget.toolbar.Toolbar;
import hb.widget.toolbar.Toolbar.OnMenuItemClickListener;
import hb.widget.HbListView;
import hb.app.dialog.AlertDialog;
//import android.app.ActionBar.Tab;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract;
/*import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.CursorAdapter;*/
import hb.widget.ViewPager;
import hb.widget.PagerAdapter;
import android.support.v4.widget.CursorAdapter;
import hb.widget.tab.TabLayout;
import hb.widget.tab.TabLayout.OnTabSelectedListener;
import hb.widget.tab.TabLayout.Tab;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TabWidget;
import android.widget.TextView;
import hb.widget.SliderView;
import com.hb.interception.activity.ZzzListView;
import com.hb.interception.adapter.PhoneAdapter;
import com.hb.interception.adapter.InterceptionAdapterBase;
import com.hb.interception.adapter.SmsAdapter;
import com.hb.interception.notification.ClearBlackCallsService;
import com.hb.interception.settings.Settings;
import com.hb.interception.util.BlackUtils;
import com.hb.interception.util.InterceptionUtils;
import com.hb.interception.util.PermissionsUtil;
import com.hb.interception.InterceptionApplication;
import com.hb.interception.R;

public class InterceptionActivity extends HbActivity implements OnItemClickListener, OnItemLongClickListener
     {
    private static final String TAG = "InterceptionActivity";
    private Context mContext;
    private boolean mGoToCall;
    private boolean mShowBoth = false;
    private int mCurrentTabIndex = 0;// 当前页卡编号
    private InterceptionViewPager mViewPager;
    private TabLayout mTabLayout;
    //private LinearLayout calls, smss;
    /*private ImageView mSmsRedDot;*/
    private ZzzListView mCalllogList;
    private ZzzListView mSmsList;
    PhoneAdapter mCallListAdapter = null;
    SmsAdapter mSmsListAdapter = null;
    SharedPreferences preferences;
    
    private final static int SMS_INDEX = 0;
    private final static int CALL_INDEX = 1;
    
        @Override
    protected void onCreate(Bundle savedInstanceState) {
        // StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
        // .detectAll()
        // .penaltyLog()
        // .penaltyDeath()
        // .build());
        super.onCreate(savedInstanceState);
        mContext = this;
        Intent intent = getIntent();
        mGoToCall = intent.getBooleanExtra("goToCall", false);
      //  mShowBoth = intent.getBooleanExtra("all", false);
        setHbContentView(R.layout.activity_main);
        /*mSmsRedDot = (ImageView)findViewById(R.id.circle2);
        calls = (LinearLayout)findViewById(R.id.calls);
        smss = (LinearLayout)findViewById(R.id.smss);*/
        preferences = getSharedPreferences("user", Context.MODE_PRIVATE);
        mCurrentTabIndex = preferences.getInt("currIndex", 0);
        if (mCurrentTabIndex == CALL_INDEX) {
            mGoToCall = true;
        }
        /* if (InterceptionApplication.getInstance().getCountSms() > 0) {
            mShowBoth = InterceptionApplication.getInstance().getCountCall() > 0;
            mGoToCall = false;
            sendNotiCancelBroadcast();
        } else if (InterceptionApplication.getInstance().getCountCall() > 0) {
        	mGoToCall = true;
            sendNotiCancelBroadcast();
        }
        if (mShowBoth) {
            mSmsRedDot.setVisibility(View.VISIBLE);
        }*/
        mCurrentTabIndex = mGoToCall ? CALL_INDEX : SMS_INDEX;

        mInterceptionItemClickHelper = new InterceptionItemClickHelper(this);
        mCalllogQueryManager = new CalllogQueryManager(this);
        mSmsQueryManager = new SmsQueryManager(this);

        InitTabBarAndViewPager();
        initBottomMenuAndActionbar();
        initToolBar();

        getContentQuery();

        boolean hasReadContactPermission = PermissionsUtil.hasPermission(this, CONTACT_READ_PERMISSION);
        boolean hasWriteContactPermission = PermissionsUtil.hasPermission(this, CONTACT_WRITE_PERMISSION);
        boolean hasReadSmsPermission = PermissionsUtil.hasPermission(this, SMS_READ_PERMISSION);
        boolean hasWriteSmsPermission = PermissionsUtil.hasPermission(this, SMS_WRITE_PERMISSION);
        boolean hasReadCalllogPermission = PermissionsUtil.hasPermission(this, CALLLOG_READ_PERMISSION);
        boolean hasWriteCalllogPermission = PermissionsUtil.hasPermission(this, CALLLOG_WRITE_PERMISSION);
        boolean hasSendSmsPermission = PermissionsUtil.hasPermission(this, SEND_SMS_PERMISSION);
        boolean hasCallPhonePermission = PermissionsUtil.hasPermission(this, CALL_PHONE_PERMISSION);
        boolean hasCallPrivilegedPermission = PermissionsUtil.hasPermission(this, CALL_PRIVILEGED_PERMISSION);
        if(!hasReadContactPermission || !hasWriteContactPermission
               || !hasReadSmsPermission || !hasWriteSmsPermission 
               || !hasReadCalllogPermission || !hasWriteCalllogPermission 
               || !hasSendSmsPermission || !hasCallPhonePermission 
               || !hasCallPrivilegedPermission){
          requestPermissions(new String[] {CONTACT_READ_PERMISSION, CONTACT_WRITE_PERMISSION,
                  SMS_READ_PERMISSION, SMS_WRITE_PERMISSION, CALLLOG_READ_PERMISSION, CALLLOG_WRITE_PERMISSION,
                  SEND_SMS_PERMISSION, CALL_PHONE_PERMISSION, CALL_PRIVILEGED_PERMISSION},
                  CONTACT_PERMISSION_REQUEST_CODE);
            return;
        }

    }
        
        private static final String CONTACT_READ_PERMISSION = "android.permission.READ_CONTACTS";
        private static final String CONTACT_WRITE_PERMISSION = "android.permission.WRITE_CONTACTS";
        private static final String SMS_READ_PERMISSION = "android.permission.READ_SMS";
        private static final String SMS_WRITE_PERMISSION = "android.permission.WRITE_SMS";
        private static final String CALLLOG_READ_PERMISSION = "android.permission.READ_CALL_LOG";
        private static final String CALLLOG_WRITE_PERMISSION = "android.permission.WRITE_CALL_LOG";
        private static final String SEND_SMS_PERMISSION = "android.permission.SEND_SMS";
        private static final String CALL_PHONE_PERMISSION = "android.permission.CALL_PHONE";
        private static final String CALL_PRIVILEGED_PERMISSION = "android.permission.CALL_PRIVILEGED";
        private static final int CONTACT_PERMISSION_REQUEST_CODE = 1;
        @Override
        public void onRequestPermissionsResult(int requestCode, String[] permissions,
                int[] grantResults) {
            if (requestCode == CONTACT_PERMISSION_REQUEST_CODE) {
                if (grantResults.length >= 1 && PackageManager.PERMISSION_GRANTED == grantResults[0]) {
                    // Force a refresh of the data since we were missing the permission before this.
                } else {
                  finish();
                }            
            }
        }

    RedDotTextView mRedDotTextView;
    /**
     * 初始化ViewPager
     */
    private void InitTabBarAndViewPager() {
        mTabLayout = (TabLayout) this.findViewById(R.id.tab_layout);

        List<String> titles = new ArrayList<String>();
        titles.add(getString(R.string.tab_sms_reject_title));
        titles.add(getString(R.string.tab_phone_reject_title));

        /*mRedDotTextView = new RedDotTextView(this);
        mRedDotTextView.setText("");
        mRedDotTextView.setVisibility(1);*/
        mTabLayout.addTab(mTabLayout.newTab().setText(titles.get(0)));
        mTabLayout.addTab(mTabLayout.newTab().setText(titles.get(1)));
        //mTabLayout.addTab(mTabLayout.newTab().setCustomView(mRedDotTextView));

        List<View> listViews = new ArrayList<View>();
        LayoutInflater mInflater = getLayoutInflater();

        View phone_tab = mInflater.inflate(R.layout.phone_tab, null);
        mCalllogList = (ZzzListView)phone_tab.findViewById(R.id.phone_list);
        mCalllogList.setOnItemClickListener(this);
        mCalllogList.setOnItemLongClickListener(this);
        mCalllogQueryManager.setView(phone_tab);

        View sms_tab = mInflater.inflate(R.layout.sms_tab, null);
        mSmsList = (ZzzListView)sms_tab.findViewById(R.id.sms_list);
        mSmsList.setOnItemClickListener(this);
        mSmsList.setOnItemLongClickListener(this);
        mSmsQueryManager.setView(sms_tab);

        listViews.add(sms_tab);
        listViews.add(phone_tab);

        mViewPager = (InterceptionViewPager)findViewById(R.id.viewpager);
        mViewPager.setAdapter(new MyPagerAdapter(listViews, titles));
//        mViewPager.setOnPageChangeListener(new MainOnPageChangeListener());
        mViewPager.setCurrentItem(mCurrentTabIndex);
        mTabLayout.setupWithViewPager(mViewPager);
        mTabLayout.setOnTabSelectedListener(mMainOnTabSelectedListener);
    }
    
    private MainOnTabSelectedListener mMainOnTabSelectedListener = new MainOnTabSelectedListener();

    private class MainOnTabSelectedListener implements TabLayout.OnTabSelectedListener {
        @Override
        public void onTabSelected(Tab tab) {
            if(tab.getPosition() == 0) {
                mCurrentTabIndex = 0;
            } else {
                mCurrentTabIndex = 1;
            }
            mViewPager.setCurrentItem(mCurrentTabIndex);
            sendNotiCancelBroadcast(mCurrentTabIndex);
        }

        @Override
        public void onTabUnselected(Tab tab) {
        }

        @Override
        public void onTabReselected(Tab tab) {
        }
    }

    public class MyPagerAdapter extends PagerAdapter {
        public List<View> mListViews;
        private List<String> mTitles;

        public MyPagerAdapter(List<View> list, List<String> titles) {
            mListViews = list;
            mTitles = titles;
        }

        @Override
        public void destroyItem(View container, int position, Object object) {
            ((ViewPager)container).removeView(mListViews.get(position));
        }

        @Override
        public Object instantiateItem(View container, int position) {
            ((ViewPager)container).addView(mListViews.get(position));
            return mListViews.get(position);
        }

        @Override
        public void startUpdate(View arg0) {
        }

        @Override
        public void finishUpdate(View arg0) {
        }

        @Override
        public int getCount() {
            return mListViews.size();
        }

        @Override
        public boolean isViewFromObject(View arg0, Object arg1) {
            return arg0 == (arg1);
        }

        @Override
        public void restoreState(Parcelable arg0, ClassLoader arg1) {
        }

        @Override
        public Parcelable saveState() {
            return null;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mTitles.get(position);
        }
    }

    /**
     * 页卡切换监听
     */
    public class MainOnPageChangeListener implements ViewPager.OnPageChangeListener {

        @Override
        public void onPageSelected(int arg0) {
            switch (arg0) {
                case 0:
                    if (mCurrentTabIndex == SMS_INDEX) {
                        /*calls.setSelected(true);
                        smss.setSelected(false);*/
                    }
                    break;
                case 1:
                    if (mShowBoth) {
                        mShowBoth = false;
                        //mSmsRedDot.setVisibility(View.GONE);
                    }
                    if (mCurrentTabIndex == CALL_INDEX) {
                        /*calls.setSelected(false);
                        smss.setSelected(true);*/
                    }
                    break;
            }
            mCurrentTabIndex = arg0;
        }

        @Override
        public void onPageScrolled(int arg0, float arg1, int arg2) {
        }

        @Override
        public void onPageScrollStateChanged(int arg0) {
        }
    }

    private void sendNotiCancelBroadcast(int mode) {
    	Log.d(TAG, "sendNotiCancelBroadcast:" + mode);
    	if (mode == ClearBlackCallsService.SMS_NOTIFY) {
    		if(InterceptionApplication.getInstance().getCountSms() <= 0) {
    			return;
    		}
    		InterceptionApplication.getInstance().setCountSms(0);
    	} else if (mode == ClearBlackCallsService.CALL_NOTIFY) {
    		if(InterceptionApplication.getInstance().getCountCall() <= 0) {
    			return;
    		}
    		InterceptionApplication.getInstance().setCountCall(0);
    	}
        Intent clearIntent = new Intent(this, ClearBlackCallsService.class);
        clearIntent.setAction(ClearBlackCallsService.ACTION_CLEAR_HANGUP_BLACK_CALLS);
        clearIntent.putExtra(ClearBlackCallsService.ACTION_CLEAR_MODE,  mode);
        startService(clearIntent);
    }

    private void getContentQuery() {
        mCalllogQueryManager.startQuery();
        mSmsQueryManager.startQueryMms();
    }

    @Override
    protected void onResume() {
        // TODO Auto-generated method stub
        super.onResume();
        if (mCalllogQueryManager.isShouldRequery()) {
            mCalllogQueryManager.startQuery();
        }

        if (mSmsQueryManager.isShouldRequery()) {
            mSmsQueryManager.startQueryMms();
        }
        sendNotiCancelBroadcast(mCurrentTabIndex);
    }
    
    @Override
    protected void onPause() {
        // TODO Auto-generated method stub
        super.onPause();
        sendNotiCancelBroadcast(mCurrentTabIndex);
        /*SharedPreferences preferences = getSharedPreferences("user", Context.MODE_PRIVATE);
        Editor editor = preferences.edit();
        editor.putInt("currIndex", mCurrentTabIndex);
        editor.commit();*/
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        mCalllogQueryManager.destroy();
        mSmsQueryManager.destroy();
    }
    
    //zhangj fix bug 2879
    @Override
	public void onBackPressed() {
		super.onBackPressed();
		sendNotiCancelBroadcast(mCurrentTabIndex);
	}

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        // TODO Auto-generated method stub
        if (isQuerying()) {
            return true;
        }
        if (!isInDeleteMode()) {
            /*calls.setEnabled(false);
            smss.setEnabled(false);*/
            if (mCurrentTabIndex == CALL_INDEX) {
                //calls.setSelected(false);
                mCallListAdapter.setCheckedItem(position);
                mCallListAdapter.setCheckBoxEnable(true);
                mCallListAdapter.notifyDataSetChanged();
                mBottomNavigationView.clearItems();
                mBottomNavigationView.inflateMenu(R.menu.bottom_menu_call);
            } else {
                //smss.setSelected(false);
                mSmsListAdapter.setCheckedItem(position);
                mSmsListAdapter.setCheckBoxEnable(true);
                mSmsListAdapter.notifyDataSetChanged();
                mBottomNavigationView.clearItems();
                mBottomNavigationView.inflateMenu(R.menu.bottom_menu_sms);
            }
            initActionBar(true);
            updateActionMode();
            return true;
        }
        return false;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // TODO Auto-generated method stub
        if (isQuerying()) {
            return;
        }

        if (!isInDeleteMode()) {
//            if (mCurrentTabIndex == CALL_INDEX) {
//                Cursor pcursor = (Cursor)mCalllogList.getItemAtPosition(position);
//                if (pcursor == null) {
//                    return;
//                }
//
//                mInterceptionItemClickHelper.initData(pcursor);
//                mInterceptionItemClickHelper.showDialogMenu();
//            } else 
         if (mCurrentTabIndex == SMS_INDEX) 
            {
                Cursor pcursor = (Cursor)mSmsList.getItemAtPosition(position);
                if (pcursor == null) {
                    return;
                }
                mInterceptionItemClickHelper.viewSms(pcursor);
            }
        } else {
            final CheckBox checkBox = (CheckBox)view.findViewById(R.id.list_item_check_box);
            if (null != checkBox) {
                boolean checked = checkBox.isChecked();
                checkBox.setChecked(!checked);
                InterceptionAdapterBase ca = mCurrentTabIndex == CALL_INDEX ? mCallListAdapter : mSmsListAdapter;
                if (!checked) {
                    ca.setCheckedItem(position);
                } else {
                    ca.removeCheckedItem(position);
                }
                updateActionMode();
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (mInterceptionItemClickHelper.isDialogShowing()) {
                    return true;
                }
                if (isInDeleteMode()) {
                    changeToNormalMode(true);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_MENU: {
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private String mSelectAllStr;
    private String mUnSelectAllStr;
    private BottomNavigationView mBottomNavigationView;
    public ActionMode mActionMode;

    private ActionModeListener mActionModeListener = new ActionModeListener() {

        @Override
        public void onActionItemClicked(Item item) {
            // TODO Auto-generated method stub
            switch (item.getItemId()) {
                case ActionMode.POSITIVE_BUTTON:
                    InterceptionAdapterBase ca = mCurrentTabIndex == CALL_INDEX ? mCallListAdapter : mSmsListAdapter;
                    int checkedCount = ca.getCheckedItem().size();
                    int all = ca.getCount();
                    selectAll(checkedCount < all);
                    break;
                case ActionMode.NAGATIVE_BUTTON:
                    safeQuitDeleteMode();
                    break;
                default:
            }
        }

        @Override
        public void onActionModeDismiss(ActionMode arg0) {
            // TODO Auto-generated method stub
            
        }
        
        @Override
        public void onActionModeShow(ActionMode arg0) {
            // TODO Auto-generated method stub
            updateActionMode();
        }
    };

    private void selectAll(boolean checked) {
        InterceptionAdapterBase ca = mCurrentTabIndex == CALL_INDEX ? mCallListAdapter : mSmsListAdapter;
        if (checked) {
            for (int position = 0; position < ca.getCount(); ++position) {
                ca.setCheckedItem(position);
            }
        } else {
            ca.clearCheckedItem();
        }

        ca.notifyDataSetChanged();
        updateActionMode();
    }

    private boolean isInDeleteMode() {
        return getActionMode().isShowing();
    }

    private void safeQuitDeleteMode() {
        try {
            Thread.sleep(300);
            changeToNormalMode(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateActionMode() {
        InterceptionAdapterBase ca = mCurrentTabIndex == CALL_INDEX ? mCallListAdapter : mSmsListAdapter;
        if (ca == null) {
            finish();
            return;
        }

        int checkedCount = ca.getCheckedItem().size();
        int all = ca.getCount();

        if (checkedCount >= all) {
            mActionMode.setPositiveText(mUnSelectAllStr);
        } else {
            mActionMode.setPositiveText(mSelectAllStr);
        }
        mActionMode.setNagativeText(mContext.getResources().getString(R.string.cancel));

        if (checkedCount > 0) {
            setBottomMenuEnable(true);
        } else {
            setBottomMenuEnable(false);
        }

        updateActionModeTitle(mContext.getString(R.string.selected_total_num, checkedCount));
    }

    private void setBottomMenuEnable(boolean flag) {
        //mBottomNavigationView.setEnabled(flag);
        mBottomNavigationView.setItemEnable(R.id.restoreCall, flag);
        mBottomNavigationView.setItemEnable(R.id.delCall, flag);
        mBottomNavigationView.setItemEnable(R.id.restoreSms, flag);
        mBottomNavigationView.setItemEnable(R.id.delSms, flag);
    }

    void changeToNormalMode(boolean flag) {
        initActionBar(false);

        /*calls.setEnabled(true);
        smss.setEnabled(true);*/
        InterceptionAdapterBase ca = mCurrentTabIndex == CALL_INDEX ? mCallListAdapter : mSmsListAdapter;
        if (ca != null) {
            ca.setCheckBoxEnable(false);
            ca.clearCheckedItem();
            ca.notifyDataSetChanged();
        }
        /*if (mCurrentTabIndex == CALL_INDEX) {
            calls.setSelected(true);
        } else {
            smss.setSelected(true);
        }*/
    }

    private void initActionBar(boolean flag) {
        showActionMode(flag);
//        mTabLayout.setVisibility(flag ? View.GONE : View.VISIBLE);
        setTabViewState(!flag);
        mViewPager.setScrollble(!flag);
        mBottomNavigationView.setVisibility(flag ? View.VISIBLE : View.GONE);
    }
    
    private void setTabViewState(boolean enable){
        LinearLayout ll  = (LinearLayout)mTabLayout.getChildAt(0);
        final int tabCount = ll.getChildCount();
        for (int i = 0; i < tabCount; i++) {
            final View child = ll.getChildAt(i);
            child.setEnabled(enable) ;
        }
    }
    
    private void initBottomMenuAndActionbar() {
        mSelectAllStr = mContext.getResources().getString(R.string.select_all);
        mUnSelectAllStr = mContext.getResources().getString(R.string.deselect_all);
        mActionMode = getActionMode();
        mBottomNavigationView = (BottomNavigationView)findViewById(R.id.bottom_navigation_view);

        mBottomNavigationView.setNavigationItemSelectedListener(new OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                return mInterceptionItemClickHelper.handleBottomMenuItemClick(item);
            }
        });
        setActionModeListener(mActionModeListener);
    }

    private void initToolBar() {
        Toolbar myToolbar = getToolbar();
        myToolbar.setTitle(R.string.app_name);
        myToolbar.inflateMenu(R.menu.toolbar_menu_settings);
        myToolbar.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                // TODO Auto-generated method stub
                Log.d(TAG, "onMenuItemClick--->" + item.getTitle());
                if (item.getItemId() == R.id.action_settings) {
                    Intent intent = new Intent(InterceptionActivity.this, Settings.class);
                    startActivity(intent);
                }
                return false;
            }
        });
    }

//    @Override
//    public void onSliderButtonClick(int id, View view, ViewGroup parent) {
//
//        /*Log.d(TAG, "onSliderButtonClick1,id:" + id + ", view:" + view + ", parent:" + parent +
//                ", parent.getTag():" + ((SliderView) parent).getTag(R.id.slider_tag));
//
//        //Cursor cursor  = (Cursor)mListView.getItemAtPosition(position);
//        //Conversation conv = Conversation.from(ConversationList.this, cursor);
//        //long threadId = conv.getThreadId();
//
//        long threadId = Long.parseLong(((SliderView) parent).getTag(R.id.slider_tag).toString());
//        if (DEBUG) Log.d(TAG, "onSliderButtonClick, threadId = " + threadId);
//
//        switch (id) {
//            case ConversationListAdapter.SLIDER_BTN_POSITION_DELETE:
//                //Toast.makeText(this, "view: 1, threadId = "+threadId, Toast.LENGTH_SHORT).show();
//                confirmDeleteThread(threadId, mQueryHandler);
//                break;
//            default:
//                break;
//        }
//        if (((SliderView) parent).isOpened()) {
//            ((SliderView) parent).close(false);
//        }*/
//        int position = Integer.parseInt(((SliderView) parent).getTag(R.id.swipe_view).toString());
//        switch (id) {
//            case InterceptionUtils.SLIDER_BTN_POSITION_DELETE:
//                doRecoverReject(position);
//                break;
//            default:
//                break;
//        }
//        if (((SliderView) parent).isOpened()) {
//            ((SliderView) parent).close(false);
//        }
//    }

    private void doRecoverReject(int position) {
        if(mCurrentTabIndex == 0) {
            recoverCalllogReject(position);
        } else {
            final int pos = position;
            int msgId = -1;
            if(mCurrentTabIndex == CALL_INDEX) {
                msgId = R.string.delete_calllog_reject_dialg_msg;
            } else {
                msgId = R.string.delete_sms_reject_dialg_msg; 
            }
            AlertDialog dialogs = new AlertDialog.Builder(this)
                    .setTitle(R.string.delete_reject)
                    .setMessage(msgId)
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                        int whichButton) {
                                    if(mCurrentTabIndex == CALL_INDEX) {
                                        recoverCalllogReject(pos);
                                    } else {
                                        recoverSmsReject();
                                    }
                                }
                            }).setNegativeButton(android.R.string.cancel, null)
                    .show();
            dialogs.setCanceledOnTouchOutside(false);
        }
    }

    private void recoverSmsReject() {
        
    }

    private void recoverCalllogReject(int pos) {
        Cursor c = (Cursor)mCalllogList.getItemAtPosition(pos);
        if (c == null) {
            return;
        }
        String number = c.getString(c.getColumnIndex("number"));
        mInterceptionItemClickHelper.removeBlack(number);
    }

    CalllogQueryManager mCalllogQueryManager;
    SmsQueryManager mSmsQueryManager;
    InterceptionItemClickHelper mInterceptionItemClickHelper;

    private boolean isQuerying() {
        return mCalllogQueryManager.isQuery() || mSmsQueryManager.isQuery();
    }
    
    void setPhoneAdapter(PhoneAdapter adapter) {
        mCallListAdapter = adapter;
//        mCallListAdapter.setListener(this);
    }
    
    void setSmsAdapter(SmsAdapter adapter) {
        mSmsListAdapter = adapter;
//        mSmsListAdapter.setListener(this);
    }
    
    @Override
    public void onNavigationClicked(View view) {
        finish();
    }

}
