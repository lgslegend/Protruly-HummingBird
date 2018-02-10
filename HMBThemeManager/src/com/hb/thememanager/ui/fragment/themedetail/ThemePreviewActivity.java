package com.hb.thememanager.ui.fragment.themedetail;

import java.util.ArrayList;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import hb.app.HbActivity;
import hb.widget.PagerAdapter;
import hb.widget.ViewPager;
import hb.widget.ViewPager.OnPageChangeListener;

import com.hb.thememanager.R;
import com.hb.thememanager.model.PreviewTransitionInfo;
import com.hb.thememanager.model.Theme;
import com.hb.thememanager.ui.fragment.AbsLocalThemeFragment;
import com.hb.thememanager.utils.TLog;
import com.hb.thememanager.views.PreviewAnimationImageView;
public class ThemePreviewActivity extends Activity implements PreviewAnimationImageView.Callback{
	private static final String TAG = "ThemePreview";
	private static final int PREVIEW_ITEM_LAYOUT = R.layout.theme_pkg_detail_preview_img;
	
	private int mCurrentIndex;
	private PreviewAnimationImageView mInitImageView;
	private Drawable[] mPreviewDrawables;
	private ImageView[] mAdapterViews;
	private ArrayList<PreviewTransitionInfo> mInfos;
	private ViewPager mPreviewPager;
	private PreviewPagerAdapter mAdapter;
	private Handler mHandler = new Handler();
	private boolean mHandleBackEvent = false;
	private boolean mEnter = false;
	private int mPreviewImageType;
	private int mPressBackCount = 0;
	private Runnable mInitImageInfoRunnable = new Runnable() {
		
		
		@Override
		public void run() {
			// TODO Auto-generated method stub
			if(mPreviewDrawables != null && mPreviewDrawables.length > mCurrentIndex){
				Drawable initDrawable = mPreviewDrawables[mCurrentIndex]; 
				mInitImageView.setImageDrawable(initDrawable);
				mInitImageView.setTransitionInfo(mInfos.get(mCurrentIndex));
				if(mEnter){
					mInitImageView.enter();
				}else{
					mInitImageView.setVisibility(View.VISIBLE);
					mPreviewPager.setVisibility(View.INVISIBLE);
					mInitImageView.exit();
				}
			}
		}
	};
	@Override
	protected void onCreate(Bundle savedBundleInstance) {
		// TODO Auto-generated method stub
		getWindow().getDecorView().setSystemUiVisibility(
                /*View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | */
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
		getWindow().setNavigationBarColor(getResources().getColor(R.color.preview_navigation_bar_background));
		super.onCreate(savedBundleInstance);
		setContentView(R.layout.theme_detail_preview_activity);
		mInitImageView = (PreviewAnimationImageView)findViewById(R.id.preview_init_image);
		mPreviewPager = (ViewPager)findViewById(R.id.preview_pager);
		mInitImageView.setCallback(this);
		getTransitionInfo();
	}
	
	private void getTransitionInfo(){
		mCurrentIndex = getIntent().getIntExtra(PreviewTransitionInfo.KEY_ID,-1);
		mInfos = getIntent().getParcelableArrayListExtra(PreviewTransitionInfo.KEY_INFO);
		mPreviewDrawables = ThemePkgDetailFragment.getPreviewDrawables();
		if(mPreviewDrawables != null && mCurrentIndex != -1){
			mAdapterViews = new ImageView[mPreviewDrawables.length];
			mPreviewPager.setVisibility(View.INVISIBLE);
			mEnter = true;
			mHandler.post(mInitImageInfoRunnable);
		}
		
	}

	
	@Override
	public void onAnimationEnd(int animationType) {
		// TODO Auto-generated method stub
		if(animationType == PreviewAnimationImageView.ANIMATION_ENTER){
			mPreviewPager.setVisibility(View.VISIBLE);
			mInitImageView.setVisibility(View.INVISIBLE);
			mAdapter = new PreviewPagerAdapter();
			mAdapter.initItemViews();
			mPreviewPager.setAdapter(mAdapter);
			mPreviewPager.setCurrentItem(mCurrentIndex);
		}else{
			finish();
			overridePendingTransition(0, 0);
		}
		mHandleBackEvent = true;
	}
	
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		mPreviewPager.removeAllViews();
		if(mHandler.hasCallbacks(mInitImageInfoRunnable)){
			mHandler.removeCallbacks(mInitImageInfoRunnable);
		}
		if(mPreviewDrawables != null){
			for(int i = 0;i<mPreviewDrawables.length;i++){
				mPreviewDrawables[i] = null;
			}
			mPreviewDrawables = null;
		}

		if(mInitImageView != null){
			mInitImageView.onDestory();
			mInitImageView = null;
		}

	}
	

	@Override
	public void onBackPressed() {
		// TODO Auto-generated method stub
		//do nothing
		mEnter = false;
		mHandler.post(mInitImageInfoRunnable);
	}
	
	
	
	class PreviewPagerAdapter extends PagerAdapter implements OnClickListener{
		private void initItemViews(){
			int count = getCount();
			if(count > 0){
				for(int i = 0 ; i < count;i++){
					ImageView item = (ImageView) LayoutInflater.from(ThemePreviewActivity.this)
							.inflate(PREVIEW_ITEM_LAYOUT, null);
					item.setOnClickListener(this);
					mAdapterViews[i] = item;
				}
			}
		}
		
		@Override
		public int getCount() {
			// TODO Auto-generated method stub
			if(mPreviewDrawables == null){
				return 0;
			}
			return mPreviewDrawables.length;
		}

		@Override
	    public boolean isViewFromObject(View view, Object object) {
	        return view == object;
	    }
		
		@Override
		public Object instantiateItem(View container, int position) {
			ViewPager viewPager = ((ViewPager) container);
			ImageView image = mAdapterViews[position];
			if(image == null){
				image = (ImageView) LayoutInflater.from(ThemePreviewActivity.this)
						.inflate(PREVIEW_ITEM_LAYOUT, null);
				mAdapterViews[position] = image;
			}
	        viewPager.addView(image);
	        image.setImageDrawable(mPreviewDrawables[position]);
	        return image;
		}
		
		@Override
		public void destroyItem(View container, int position, Object object) {
			ViewPager viewPager = (ViewPager) container;
	        ImageView image = mAdapterViews[position];
	        if (image != null) {
	            image.setImageDrawable(null);
	        }
	        viewPager.removeView(image);
		}
		@Override
		public void onClick(View v) {
			mCurrentIndex = mPreviewPager.getCurrentItem();
			onBackPressed();
		}
		
	}
	
	
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// TODO Auto-generated method stub

		if(mPressBackCount > 0){
			return true;
		}
		mPressBackCount ++;
		if(keyCode == KeyEvent.KEYCODE_BACK){
			if(mHandleBackEvent){
				return super.onKeyDown(keyCode, event);
			}else{
				return true;
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	
	
	
}
