package com.hb.interception.activity;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;

import com.hb.interception.adapter.BlackAdapter;
import com.hb.interception.adapter.InterceptionAdapterBase;
import com.hb.interception.util.BlackUtils;

import hb.app.HbActivity;
import hb.app.dialog.ProgressDialog;
import hb.view.menu.bottomnavigation.BottomNavigationView;
import hb.view.menu.bottomnavigation.BottomNavigationView.OnNavigationItemSelectedListener;
import hb.widget.ActionMode;
import hb.widget.ActionModeListener;
import hb.widget.SliderView;
import hb.widget.ActionMode.Item;
import hb.widget.toolbar.Toolbar;
import hb.widget.toolbar.Toolbar.OnMenuItemClickListener;
import hb.widget.HbListView;
import com.hb.interception.R;

public class InterceptionActivityBase extends HbActivity implements
		OnItemClickListener, OnItemLongClickListener, OnMenuItemClickListener,
		SliderView.OnSliderButtonLickListener {
	private static final String TAG = "InterceptionActivityBase";

	protected Context mContext;
	protected ContentResolver mContentResolver;
	protected AsyncQueryHandler mQueryHandler;

	protected /*android.app.*/ProgressDialog mDeleteProgressDialog;
	protected HbListView mList;
	protected View mEmpty;
	protected InterceptionAdapterBase mAdapter = null;
	protected Toolbar myToolbar;

	protected String mSelectAllStr;
	protected String mUnSelectAllStr;
	protected BottomNavigationView mBottomNavigationView;
	protected ActionMode mActionMode;
	
//	protected ContentObserver mChangeObserver = new ContentObserver(new Handler()) {
//
//		@Override
//		public void onChange(boolean selfUpdate) {
//			Log.i(TAG, "onChange.................................");
//			startQuery();
//		}
//	};
	
	
	protected Uri mListUri;
	protected void startQuery() {
	}

	
	protected class QueryHandler extends AsyncQueryHandler {
		private final Context context;

		public QueryHandler(ContentResolver cr, Context context) {
			super(cr);
			this.context = context;
		}

		// todo lgy
		@Override
		protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
			// TODO Auto-generated method stub
			super.onQueryComplete(token, cookie, cursor);
			processQueryComplete(context, cursor);
		
		}

		@Override
		protected void onUpdateComplete(int token, Object cookie, int result) {
			// TODO Auto-generated method stub
			super.onUpdateComplete(token, cookie, result);
		}

		@Override
		protected void onInsertComplete(int token, Object cookie, Uri uri) {
			// TODO Auto-generated method stub
			super.onInsertComplete(token, cookie, uri);
		}

		@Override
		protected void onDeleteComplete(int token, Object cookie, int result) {
			// TODO Auto-generated method stub
			super.onDeleteComplete(token, cookie, result);

		}
	}
	
	protected void processQueryComplete(Context context, Cursor cursor) {
	
	}

	private ActionModeListener mActionModeListener = new ActionModeListener() {

		@Override
		public void onActionItemClicked(Item item) {
			// TODO Auto-generated method stub
			switch (item.getItemId()) {
			case ActionMode.POSITIVE_BUTTON:
				int checkedCount = mAdapter.getCheckedItem().size();
				int all = mAdapter.getCount();
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
	
	protected void init() {
		mContext = this;
		mContentResolver = getContentResolver();
		
		mList.setOnItemClickListener(this);
		mList.setOnItemLongClickListener(this);
		mList.setEmptyView(mEmpty);
		
		mQueryHandler = new QueryHandler(mContentResolver, mContext);
		startQuery();
//		mContentResolver.registerContentObserver(mListUri, true, mChangeObserver);
		
		initToolBar();
		initBottomMenuAndActionbar();
	}

	protected void initBottomMenuAndActionbar() {
		mSelectAllStr = getResources().getString(R.string.select_all);
		mUnSelectAllStr = getResources().getString(R.string.deselect_all);
		mActionMode = getActionMode();
		mBottomNavigationView = (BottomNavigationView) findViewById(R.id.bottom_navigation_view);
		setActionModeListener(mActionModeListener);
	}

	protected void initToolBar() {
		//myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
	    myToolbar = getToolbar();
		myToolbar.setOnMenuItemClickListener(this);
	}
	
	@Override
    public void onNavigationClicked(View view) {
        finish();
    }

	@Override
	protected void onNewIntent(Intent intent) {
		setIntent(intent);
	}
	
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		if (mAdapter != null) {
			mAdapter.changeCursor(null);
		}
//		mContentResolver.unregisterContentObserver(mChangeObserver);
	}
	

	private void selectAll(boolean checked) {
		for (int position = 0; position < mAdapter.getCount(); ++position) {
			if (checked) {
				mAdapter.setCheckedItem(position);
			} else {
				mAdapter.clearCheckedItem();
			}

			int realPos = position - mList.getFirstVisiblePosition();
			if (realPos >= 0) {
				View view = mList.getChildAt(realPos);
				if (view != null) {
					final CheckBox checkBox = (CheckBox) view
							.findViewById(R.id.list_item_check_box);
					if (null != checkBox) {
						checkBox.setChecked(checked);
					}
				}
			}
		}

		updateActionMode();
	}

	private void updateActionMode() {
		if (mAdapter == null) {
			finish();
			return;
		}

		int checkedCount = mAdapter.getCheckedItem().size();
		int all = mAdapter.getCount();

		if (checkedCount >= all) {
			mActionMode.setPositiveText(mUnSelectAllStr);
		} else {
			mActionMode.setPositiveText(mSelectAllStr);
		}
		updateBottomMenuItems(checkedCount);

		updateActionModeTitle(this.getString(R.string.selected_total_num,
				checkedCount));
	}

	protected void updateBottomMenuItems(int checkedCount) {
        mBottomNavigationView.setItemEnable(R.id.delete, checkedCount > 0);
    }

	private void safeQuitDeleteMode() {
		try {
			Thread.sleep(300);
			changeToNormalMode(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected void changeToNormalMode(boolean flag) {
		initActionBar(false);

		try {
			mAdapter.clearCheckedItem();
			mAdapter.setCheckBoxEnable(false);
			mAdapter.notifyDataSetChanged();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void initActionBar(boolean flag) {
		showActionMode(flag);
		mBottomNavigationView.setVisibility(flag ? View.VISIBLE : View.GONE);
	}


	
	protected void showDialog() {
		if (mDeleteProgressDialog == null) {
			mDeleteProgressDialog = new ProgressDialog(this);
			mDeleteProgressDialog.setIndeterminate(true);
			mDeleteProgressDialog.setCancelable(false);
			mDeleteProgressDialog.setMessage(getResources().getString(R.string.removing));
		}
		mDeleteProgressDialog.show();
	}

	protected void hideDialog() {
	    if(mDeleteProgressDialog != null) {
	        mDeleteProgressDialog.dismiss();
	    }
	}
	
	protected boolean isDeleting() {
		return mDeleteProgressDialog != null && mDeleteProgressDialog.isShowing();
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
			case KeyEvent.KEYCODE_BACK:
				if (isInDeleteMode()) {
					this.changeToNormalMode(true);
					return true;
				}
				if (isDeleting()) {
					return true;
				}
				break;
			case KeyEvent.KEYCODE_MENU: {
	
				return true;
	
			}
		}

		return super.onKeyDown(keyCode, event);
	}

	protected boolean isInDeleteMode() {
		return getActionMode().isShowing();
	}
	
	protected void showDialogMenu(final int pos) {
		
	}

	@Override
	public boolean onMenuItemClick(MenuItem item) {
		return false;
	}

	@Override
    public void onSliderButtonClick(int id, View view, ViewGroup parent) {
    }

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {

		if (isInDeleteMode()) {
			selectItem(view, position);
		} else {
//			showDialogMenu(position);
		}

	}
	
	protected void selectItem(View view, int position) {
		CheckBox mCheckBox = (CheckBox) view
				.findViewById(R.id.list_item_check_box);
		if (mCheckBox == null) {
			return;
		}
		boolean isChecked = mCheckBox.isChecked();
		mCheckBox.setChecked(!isChecked);

		if (!isChecked) {
			mAdapter.setCheckedItem(position);
		} else {
			mAdapter.removeCheckedItem(position);
		}

		updateActionMode();
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view,
			int position, long id) {
		// TODO Auto-generated method stub
		if (!isInDeleteMode()) {
			mAdapter.setCheckedItem(position);
			mAdapter.setCheckBoxEnable(true);
			mAdapter.notifyDataSetChanged();
			initActionBar(true);
			updateActionMode();
			return true;
		}
		return false;

	}

}