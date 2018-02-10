package com.hb.floatview;

import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.widget.LinearLayout.LayoutParams;

import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.ArrayList;

import javax.crypto.NullCipher;

import com.android.incallui.Call;
import com.android.incallui.CallList;
import com.android.incallui.CallerInfo;
import com.android.incallui.ContactInfoCache;
import com.android.incallui.InCallPresenter;
import com.android.incallui.TelecomAdapter;
import com.android.incallui.ContactInfoCache.ContactCacheEntry;
import com.android.incallui.ContactInfoCache.ContactInfoCacheCallback;
import com.android.incallui.InCallApp;
import com.hb.HbPhoneUtils;
import com.hb.manager.AntiTouchManager;
import com.hmb.manager.aidl.MarkResult;
import com.hb.utils.SubUtils;

import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.LayoutInflater;
import android.view.GestureDetector.OnGestureListener;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.widget.CompoundButton;
import android.provider.ContactsContract.Contacts;
import android.content.ContentUris;
import android.graphics.drawable.Drawable;
import android.graphics.BitmapFactory;
import android.view.animation.*;
import android.animation.*;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.telecom.VideoProfile;
import com.android.incallui.R;
import com.android.dialer.util.TelecomUtil;


public class HbFloatView extends LinearLayout implements
		View.OnClickListener, OnGestureListener {

	private static final String LOG_TAG = "HbFloatView";

	private static final boolean DBG = true;
	/**
	 * 记录小悬浮窗的宽度
	 */
	public static int viewWidth;

	/**
	 * 记录小悬浮窗的高度
	 */
	public static int viewHeight;

	/**
	 * 记录系统状态栏的高度
	 */
	private static int statusBarHeight;

	/**
	 * 用于更新小悬浮窗的位置
	 */
	private WindowManager windowManager;

	/**
	 * 小悬浮窗的参数
	 */
	private WindowManager.LayoutParams mParams;

	/**
	 * 记录当前手指位置在屏幕上的横坐标值
	 */
	private float xInScreen;

	/**
	 * 记录当前手指位置在屏幕上的纵坐标值
	 */
	private float yInScreen;

	/**
	 * 记录手指按下时在屏幕上的横坐标的值
	 */
	private float xDownInScreen;

	/**
	 * 记录手指按下时在屏幕上的纵坐标的值
	 */
	private float yDownInScreen;

	/**
	 * 记录手指按下时在小悬浮窗的View上的横坐标的值
	 */
	private float xInView;

	/**
	 * 记录手指按下时在小悬浮窗的View上的纵坐标的值
	 */
	private float yInView;

	private TextView mName, mNumber, mArea;
	private ImageButton mHangup, mAnswer;
	private ImageView mPhoto;
	private GestureDetector gDetector;
	private Context mContext;
	private View mMain;
	private ImageView mSimIcon, mArrow;
	private ObjectAnimator mArrowAnimator; 

	public HbFloatView(Context context) {
		super(context);
		mContext = context;
		gDetector = new GestureDetector(this);
		windowManager = (WindowManager) context
				.getSystemService(Context.WINDOW_SERVICE);
		LayoutInflater.from(context).inflate(R.layout.incoming_pop, this);
		View view = findViewById(R.id.parent_container);
		viewWidth = view.getLayoutParams().width;
		viewHeight = view.getLayoutParams().height;
		mName = (TextView) findViewById(R.id.name);
		mNumber = (TextView) findViewById(R.id.number);
		mArea = (TextView) findViewById(R.id.area);
		mHangup = (ImageButton) findViewById(R.id.hangup);
		mHangup.setOnClickListener(this);
		mAnswer = (ImageButton) findViewById(R.id.answer);
		mAnswer.setOnClickListener(this);
		mPhoto = (ImageView) findViewById(R.id.photo_image);
		mSimIcon = (ImageView) findViewById(R.id.sim_icon);
		mArrow = (ImageView) findViewById(R.id.arrow);
		initArrowAnimator();
		mMain = findViewById(R.id.main_content);
//		mMain.setOnClickListener(this);
		mDensity = context.getResources().getDisplayMetrics().density;
		updateUI();
		this.setOnKeyListener(null);
		this.setOnTouchListener(new OnTouchListener() {

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				return gDetector.onTouchEvent(event);
			}
		});
	}
	
	private void initArrowAnimator() {
	 	PropertyValuesHolder pvhy = PropertyValuesHolder.ofFloat("TranslationY",
 				0,  -26f);
     	PropertyValuesHolder pvha = PropertyValuesHolder.ofFloat("alpha",
 				0.1f,  0.6f);
     	mArrowAnimator = ObjectAnimator.ofPropertyValuesHolder(
 				mArrow, pvhy, pvha);
     	mArrowAnimator.setStartDelay(100);
     	mArrowAnimator.setDuration(600);
		mArrowAnimator.setInterpolator(new LinearInterpolator());
		mArrowAnimator.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animator) {
            	mArrow.setAlpha(0.3f);
            	mArrow.setTranslationY(0);
            }
        });
		mArrowAnimator.setRepeatCount(ValueAnimator.INFINITE);
	}
	
	public void onClick(View view) {
		int id = view.getId();
		log("onClick(View " + view + ", id " + id + ")...");
    	if (!AntiTouchManager.mIsTouchEnable) {
    		return;
    	} 

		switch (id) {
		case R.id.hangup:
			hangup();
			break;
		case R.id.answer:
			// internalAnswerCall();
			answer();
			break;
		case R.id.main_content:
			InCallApp.getInstance().displayCallScreen();
			break;
		}
		FloatWindowManager.removeWindow(InCallApp.getInstance());
	}

	private void answer() {
		log("answer()...");
		Call call = CallList.getInstance().getIncomingCall();
		if(call == null) {
			return;
		}
		if(CallList.getInstance().hasCallWaiting()) {
		   	FloatWindowManager.sIsAnsweringCallWaiting = true;
		}
		TelecomAdapter.getInstance().answerCall(call.getId(),
				VideoProfile.STATE_AUDIO_ONLY);
		if(InCallApp.getInstance().getInCallActivity() == null || !InCallApp.getInstance().getInCallActivity().isResumed()) {
	    	FloatWindowManager.sIsShowAfterAnswer = true;
        }

	}

	private void hangup() {
		log("hangup()...");
		Call call = CallList.getInstance().getIncomingCall();
		if(call == null) {
			return;
		}
		TelecomAdapter.getInstance().rejectCall(call.getId(), false, null);
	}

	private void log(String msg) {
		Log.d(LOG_TAG, msg);
	}

	/**
	 * 将小悬浮窗的参数传入，用于更新小悬浮窗的位置。
	 * 
	 * @param params
	 *            小悬浮窗的参数
	 */
	public void setParams(WindowManager.LayoutParams params) {
		mParams = params;
	}

	/**
	 * 更新小悬浮窗在屏幕中的位置。
	 */
	private void updateViewPosition() {
		mParams.x = (int) (xInScreen - xInView);
		mParams.y = (int) (yInScreen - yInView);
		windowManager.updateViewLayout(this, mParams);
	}

	/**
	 * 用于获取状态栏的高度。
	 * 
	 * @return 返回状态栏高度的像素值。
	 */
	private int getStatusBarHeight() {
		if (statusBarHeight == 0) {
			try {
				Class<?> c = Class.forName("com.android.internal.R$dimen");
				Object o = c.newInstance();
				Field field = c.getField("status_bar_height");
				int x = (Integer) field.get(o);
				statusBarHeight = getResources().getDimensionPixelSize(x);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return statusBarHeight;
	}

	private ContactCacheEntry mContactCacheEntry;

	private void queryInfo() {
		Call call = CallList.getInstance().getIncomingCall();
		if(call == null) {
			FloatWindowManager.removeWindow(InCallApp.getInstance());
			return;
		}
		final ContactInfoCache cache = ContactInfoCache.getInstance(mContext);
		cache.findInfo(call, true, new ContactLookupCallback());
	}

	private class ContactLookupCallback implements ContactInfoCacheCallback {

		public ContactLookupCallback() {
		}

		@Override
		public void onContactInfoComplete(String callId, ContactCacheEntry entry) {
			mContactCacheEntry = entry;
			updateUiInternal();
		}

		@Override
		public void onImageLoadComplete(String callId, ContactCacheEntry entry) {
			mContactCacheEntry = entry;
			updateUiInternal();
		}

//        @Override
//        public void onContactInteractionsInfoComplete(String callId,
//                ContactCacheEntry entry) {
//            // TODO Auto-generated method stub
//            mContactCacheEntry = entry;
//            updateUiInternal();
//        }

	}

	void updateUI() {
		mHandler.post(new Runnable(){
			public void run() {
				queryInfo();	
			}
		});
	}

	private void updateUiInternal() {

		log("updateUI start");
		Call call = CallList.getInstance().getIncomingCall();
		if (call != null && mContactCacheEntry != null) {
			
			String areaString = "";
			String markString = "";
			
			if (!TextUtils.isEmpty(mContactCacheEntry.area)) {
				areaString = mContactCacheEntry.area;
			} 
		    		    
		    
			if (!TextUtils.isEmpty(mContactCacheEntry.name)) {
				mName.setText(mContactCacheEntry.name);
				mNumber.setText(mContactCacheEntry.number);
				mNumber.setVisibility(View.VISIBLE);
			} else {
                mName.setText(mContactCacheEntry.number);
	            mNumber.setText("");
	            mNumber.setVisibility(View.GONE);
			    MarkResult mark = mContactCacheEntry.mark;
	            String markName = "";
	            int count = -2;
		        if (mark != null) {
		            markName = mark.getName();
		            count = mark.getTagCount();
		        }
	            if (!TextUtils.isEmpty(markName)) {
	                String countString = count == -1 ? getResources().getString(R.string.mark_by_user) : getResources().getString(R.string.mark_count, count);     
	                markString = countString + markName;
	            } 
			}				
			
			if(TextUtils.isEmpty(areaString)) {
				   mArea.setText(markString);
			} else if(TextUtils.isEmpty(markString)) {
				   mArea.setText(areaString);
			} else {
				   mArea.setText(areaString + " | " + markString);
			}			

			int slot = SubUtils.getSlotBySubId(call.getSubId());
			SubUtils.setSimSubId(mSimIcon, slot);

//			if (mContactCacheEntry.photo != null && mContactCacheEntry.displayPhotoUri != null) {
//				mPhoto.setImageDrawable(HbPhoneUtils
//						.getDrawableForBitmap(mContactCacheEntry.photo));
//				mPhoto.setVisibility(View.VISIBLE);
//			} else {
//				mPhoto.setVisibility(View.GONE);
//			}
		}

		log("updateUI end");

	}

	private Handler mHandler = new Handler();

	private float mDensity = 3.0f;
    private static int sSilentUIy = 50;
    
	/**
	 * Notified when a tap occurs with the down {@link MotionEvent} that
	 * triggered it. This will be triggered immediately for every down event.
	 * All other events should be preceded by this.
	 * 
	 * @param e
	 *            The down motion event.
	 */
	@Override
	public boolean onDown(MotionEvent e) {
		// TODO Auto-generated method stub
		return false;
	}

	/**
	 * The user has performed a down {@link MotionEvent} and not performed a
	 * move or up yet. This event is commonly used to provide visual feedback to
	 * the user to let them know that their action has been recognized i.e.
	 * highlight an element.
	 * 
	 * @param e
	 *            The down motion event
	 */
	public void onShowPress(MotionEvent e) {
		// TODO Auto-generated method stub
	}

	/**
	 * Notified when a tap occurs with the up {@link MotionEvent} that triggered
	 * it.
	 * 
	 * @param e
	 *            The up motion event that completed the first tap
	 * @return true if the event is consumed, else false
	 */
	@Override
	public boolean onSingleTapUp(MotionEvent e) {
		// TODO Auto-generated method stub
		//zhangcj modify for 6753
		Call call = CallList.getInstance().getActiveOrBackgroundCall();
		if (call == null) {
			InCallApp.getInstance().displayCallScreen();
			FloatWindowManager.removeWindow(InCallApp.getInstance());
		}
		return true;
	}

	/**
	 * Notified when a scroll occurs with the initial on down
	 * {@link MotionEvent} and the current move {@link MotionEvent}. The
	 * distance in x and y is also supplied for convenience.
	 * 
	 * @param e1
	 *            The first down motion event that started the scrolling.
	 * @param e2
	 *            The move motion event that triggered the current onScroll.
	 * @param distanceX
	 *            The distance along the X axis that has been scrolled since the
	 *            last call to onScroll. This is NOT the distance betweengetContext
	 *            {@code e1} and {@code e2}.
	 * @param distanceY
	 *            The distance along the Y axis that has been scrolled since the
	 *            last call to onScroll. This is NOT the distance between
	 *            {@code e1} and {@code e2}.
	 * @return true if the event is consumed, else false
	 */
	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
			float distanceY) {
		Log.v("SlideView", "onScroll");
		float y1 = e1.getY();
		float y2 = e2.getY();
		
		if (y1 > y2 && Math.abs(y1 - y2) > sSilentUIy * mDensity) {
			FloatWindowManager.removeWindow(InCallApp.getInstance());
			// CallNotifier notifier = PhoneGlobals.getInstance().notifier;
			// notifier.silenceRinger();
			// PhoneGlobals.getInstance().notificationMgr.updateInCallNotification();
		    TelecomUtil.silenceRinger(mContext);
			return true;
		}
		return false;
	}

	/**
	 * Notified when a long press occurs with the initial on down
	 * {@link MotionEvent} that trigged it.
	 * 
	 * @param e
	 *            The initial on down motion event that started the longpress.
	 */
	@Override
	public void onLongPress(MotionEvent e) {
		// TODO Auto-generated method stub
	}

	/**
	 * Notified of a fling event when it occurs with the initial on down
	 * {@link MotionEvent} and the matching up {@link MotionEvent}. The
	 * calculated velocity is supplied along the x and y axis in pixels per
	 * second.
	 * 
	 * @param e1
	 *            The first down motion event that started the fling.
	 * @param e2
	 *            The move motion event that triggered the current onFling.
	 * @param velocityX
	 *            The velocity of this fling measured in pixels per second along
	 *            the x axis.
	 * @param velocityY
	 *            The velocity of this fling measured in pixels per second along
	 *            the y axis.
	 * @return true if the event is consumed, else false
	 */
	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
			float velocityY) {
		// TODO Auto-generated method stub
		Log.v("SlideView", "onFling");
		return false;
	}
	
	
	private ScreenBroadcastReceiver mScreenReceiver = new ScreenBroadcastReceiver();
	 @Override  
	    protected void onAttachedToWindow() {  
	        super.onAttachedToWindow();  
	        IntentFilter filter = new IntentFilter();
	        filter.addAction(Intent.ACTION_SCREEN_ON);
	        filter.addAction(Intent.ACTION_SCREEN_OFF);
	        mContext.registerReceiver(mScreenReceiver, filter);
	        mArrowAnimator.start();
	    }
	    
	    @Override  
	    protected void onDetachedFromWindow() {  
	        super.onDetachedFromWindow();  
	        mContext.unregisterReceiver(mScreenReceiver);
	        mArrowAnimator.cancel();
	    }
	
	private class ScreenBroadcastReceiver extends BroadcastReceiver {
        private String action = null;

        @Override
        public void onReceive(Context context, Intent intent) {
            action = intent.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(action)) {
            	InCallApp.getInstance().displayCallScreen();
    			FloatWindowManager.removeWindow(InCallApp.getInstance());
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
            	//zhangcj modify bug 2682 
            	mArrowAnimator.cancel();
            	mMain.setVisibility(View.GONE);
            }
        }
    }
	
	public void updateMarginTop (boolean show) {
		 LinearLayout.LayoutParams l = (LinearLayout.LayoutParams) mMain
	                .getLayoutParams();
		l.topMargin= show ? HbStatusBarView.viewHeight : 0; 
		mMain.setLayoutParams(l);
	}

}