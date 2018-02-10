package com.hb.interception.database;

import java.util.ArrayList;
import com.google.android.mms.pdu.EncodedStringValue;
import com.google.android.mms.pdu.PduPersister;
import com.hb.interception.R;
import com.hb.interception.util.SimUtils;

import java.util.List;


import android.content.Context;
import android.database.Cursor;
public class MmsDatabaseHelper {
	
	public static List<MmsItem> queryMms(Cursor cursor,Context context) {
		List<MmsItem> list=new ArrayList<MmsItem>();
//		String number;
		long id;
		int type;
		long date;
		String body;
		long thread_id;
		String rejectName;
		int reject;
		int slotid;
		MmsItem mmsItem = null;
		do {
			id = cursor.getLong(cursor.getColumnIndex("_id"));
			thread_id=cursor.getLong(cursor.getColumnIndex("thread_id"));
//			number = cursor.getString(cursor.getColumnIndex("address")).replace("-","").replace(" ","");
			date = cursor.getLong(cursor.getColumnIndex("date"));
			type = cursor.getInt(cursor.getColumnIndex("msg_box"));
			body=cursor.getString(cursor.getColumnIndex("sub"));
			if(body==null){
				body=context.getResources().getString(R.string.no_sub);
			}else{
				EncodedStringValue v = new EncodedStringValue(cursor.getInt(cursor.getColumnIndex("sub_cs")),
	                    PduPersister.getBytes(body));
				body=v.getString();
			}
			rejectName = cursor.getString(cursor.getColumnIndex("reject_tag"));
			reject = cursor.getInt(cursor.getColumnIndex("reject"));
			int subid = Integer.valueOf(cursor.getInt(cursor.getColumnIndex("sub_id")));
			slotid = SimUtils.getSlotbyId(context, subid);
			mmsItem = new MmsItem();
			mmsItem.setId(id);
			mmsItem.setThread_id(thread_id);
//			mmsItem.setNumber(number);
			mmsItem.setBody(body);
			mmsItem.setType(type);
			mmsItem.setDate(date);
			mmsItem.setReject(reject);
			mmsItem.setRejectName(rejectName);
			mmsItem.setSlotid(slotid);
			list.add(mmsItem);
		} while (cursor.moveToNext());
		cursor.close();
	
		return list;
	}

}
