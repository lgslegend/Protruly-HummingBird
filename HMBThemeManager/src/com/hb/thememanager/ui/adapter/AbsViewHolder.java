package com.hb.thememanager.ui.adapter;

import java.util.List;

import android.content.Context;
import android.view.View;
import android.widget.ListAdapter;

import com.hb.thememanager.model.Theme;

public abstract class AbsViewHolder<T> {
	private Context mContext;
	private ListAdapter mAdapter;
	public AbsViewHolder(Context context,ListAdapter adapter){
		mContext = context;
		mAdapter = adapter;
	}
	
	public abstract void holdConvertView(View convertView);

	public abstract void bindDatas(int position,List<T> themes);
	
	public Context getContext(){
		return mContext;
	}
	
	public  ListAdapter getAdapter(){
		return mAdapter;
	}
	
	
}
