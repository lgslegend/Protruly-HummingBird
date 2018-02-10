/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.dialer.calllog;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.os.Handler;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.android.contacts.common.testing.NeededForTesting;

/**
 * Maintains a list that groups adjacent items sharing the same value of a "group-by" field.
 *
 * The list has three types of elements: stand-alone, group header and group child. Groups are
 * collapsible and collapsed by default. This is used by the call log to group related entries.
 */
abstract class GroupingListAdapter extends RecyclerView.Adapter {

    private static final int GROUP_METADATA_ARRAY_INITIAL_SIZE = 16;
    private static final int GROUP_METADATA_ARRAY_INCREMENT = 128;
    private static final long GROUP_OFFSET_MASK    = 0x00000000FFFFFFFFL;
    private static final long GROUP_SIZE_MASK     = 0x7FFFFFFF00000000L;
    private static final long EXPANDED_GROUP_MASK = 0x8000000000000000L;

    public static final int ITEM_TYPE_STANDALONE = 0;
    public static final int ITEM_TYPE_GROUP_HEADER = 1;
    public static final int ITEM_TYPE_IN_GROUP = 2;
	private static final String TAG = "GroupingListAdapter";

    /**
     * Information about a specific list item: is it a group, if so is it expanded.
     * Otherwise, is it a stand-alone item or a group member.
     */
    protected static class PositionMetadata {
        int itemType;
        boolean isExpanded;
        int cursorPosition;
        int childCount;
        private int groupPosition;
        private int listPosition = -1;
    }

    private Context mContext;
    private Cursor mCursor;

    /**
     * Count of list items.
     */
    protected int mCount;

    private int mRowIdColumnIndex;

    /**
     * Count of groups in the list.
     */
    private int mGroupCount;

    /**
     * Information about where these groups are located in the list, how large they are
     * and whether they are expanded.
     */
    private long[] mGroupMetadata;

    private SparseIntArray mPositionCache = new SparseIntArray();
    private int mLastCachedListPosition;
    private int mLastCachedCursorPosition;
    private int mLastCachedGroup;

    /**
     * A reusable temporary instance of PositionMetadata
     */
    private PositionMetadata mPositionMetadata = new PositionMetadata();

    protected ContentObserver mChangeObserver = new ContentObserver(new Handler()) {

        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            onContentChanged();
        }
    };

    protected DataSetObserver mDataSetObserver = new DataSetObserver() {

        @Override
        public void onChanged() {
            notifyDataSetChanged();
        }
    };

    public GroupingListAdapter(Context context) {
        mContext = context;
        resetCache();
    }

    /**
     * Finds all groups of adjacent items in the cursor and calls {@link #addGroup} for
     * each of them.
     */
    protected abstract void addGroups(Cursor cursor);

    protected abstract void onContentChanged();

    /**
     * Cache should be reset whenever the cursor changes or groups are expanded or collapsed.
     */
    private void resetCache() {
        mCount = -1;
        mLastCachedListPosition = -1;
        mLastCachedCursorPosition = -1;
        mLastCachedGroup = -1;
        mPositionMetadata.listPosition = -1;
        mPositionCache.clear();
    }

    public void changeCursor(Cursor cursor) {
    	Log.d(TAG,"changeCursor:"+(cursor==null?"null":cursor.getCount()));
        if (cursor == mCursor) {
            return;
        }

        if (mCursor != null) {
            mCursor.unregisterContentObserver(mChangeObserver);
            mCursor.unregisterDataSetObserver(mDataSetObserver);
            mCursor.close();
        }
        mCursor = cursor;
        resetCache();
        findGroups();

        if (cursor != null) {
            cursor.registerContentObserver(mChangeObserver);
            cursor.registerDataSetObserver(mDataSetObserver);
            mRowIdColumnIndex = cursor.getColumnIndexOrThrow("_id");
            notifyDataSetChanged();
        }
    }

    @NeededForTesting
    public Cursor getCursor() {
        return mCursor;
    }

    /**
     * Scans over the entire cursor looking for duplicate phone numbers that need
     * to be collapsed.
     */
    private void findGroups() {
        mGroupCount = 0;
        mGroupMetadata = new long[GROUP_METADATA_ARRAY_INITIAL_SIZE];

        if (mCursor == null) {
            return;
        }

        addGroups(mCursor);
    }

    /**
     * Records information about grouping in the list.  Should be called by the overridden
     * {@link #addGroups} method.
     */
    protected void addGroup(int cursorPosition, int size, boolean expanded) {
    	Log.d(TAG,"addGroup,cursorPosition:"+cursorPosition+" size:"+size+" expanded:"+expanded);
        if (mGroupCount >= mGroupMetadata.length) {
            int newSize = idealLongArraySize(
                    mGroupMetadata.length + GROUP_METADATA_ARRAY_INCREMENT);
            long[] array = new long[newSize];
            System.arraycopy(mGroupMetadata, 0, array, 0, mGroupCount);
            mGroupMetadata = array;
        }

        long metadata = ((long)size << 32) | cursorPosition;
        if (expanded) {
            metadata |= EXPANDED_GROUP_MASK;
        }
        mGroupMetadata[mGroupCount++] = metadata;
    }

    // Copy/paste from ArrayUtils
    private int idealLongArraySize(int need) {
        return idealByteArraySize(need * 8) / 8;
    }

    // Copy/paste from ArrayUtils
    private int idealByteArraySize(int need) {
        for (int i = 4; i < 32; i++)
            if (need <= (1 << i) - 12)
                return (1 << i) - 12;

        return need;
    }

    @Override
    public int getItemCount() {
        if (mCursor == null) {
            return 0;
        }

        if (mCount != -1) {
            return mCount;
        }

        int cursorPosition = 0;
        int count = 0;
        for (int i = 0; i < mGroupCount; i++) {
            long metadata = mGroupMetadata[i];
            int offset = (int)(metadata & GROUP_OFFSET_MASK);
            boolean expanded = (metadata & EXPANDED_GROUP_MASK) != 0;
            int size = (int)((metadata & GROUP_SIZE_MASK) >> 32);

            count += (offset - cursorPosition);

            if (expanded) {
                count += size + 1;
            } else {
                count++;
            }

            cursorPosition = offset + size;
        }

        mCount = count + mCursor.getCount() - cursorPosition;     
        createCheckedArray();
        return mCount;
    }
    
    protected void createCheckedArray() {
    	
    }

    /**
     * Figures out whether the item at the specified position represents a
     * stand-alone element, a group or a group child. Also computes the
     * corresponding cursor position.
     */
    public void obtainPositionMetadata(PositionMetadata metadata, int position) {
        // If the description object already contains requested information, just return
        if (metadata.listPosition == position) {
            return;
        }

        int listPosition = 0;
        int cursorPosition = 0;
        int firstGroupToCheck = 0;

        // Check cache for the supplied position.  What we are looking for is
        // the group descriptor immediately preceding the supplied position.
        // Once we have that, we will be able to tell whether the position
        // is the header of the group, a member of the group or a standalone item.
        if (mLastCachedListPosition != -1) {
            if (position <= mLastCachedListPosition) {

                // Have SparceIntArray do a binary search for us.
                int index = mPositionCache.indexOfKey(position);

                // If we get back a positive number, the position corresponds to
                // a group header.
                if (index < 0) {

                    // We had a cache miss, but we did obtain valuable information anyway.
                    // The negative number will allow us to compute the location of
                    // the group header immediately preceding the supplied position.
                    index = ~index - 1;

                    if (index >= mPositionCache.size()) {
                        index--;
                    }
                }

                // A non-negative index gives us the position of the group header
                // corresponding or preceding the position, so we can
                // search for the group information at the supplied position
                // starting with the cached group we just found
                if (index >= 0) {
                    listPosition = mPositionCache.keyAt(index);
                    firstGroupToCheck = mPositionCache.valueAt(index);
                    long descriptor = mGroupMetadata[firstGroupToCheck];
                    cursorPosition = (int)(descriptor & GROUP_OFFSET_MASK);
                }
            } else {

                // If we haven't examined groups beyond the supplied position,
                // we will start where we left off previously
                firstGroupToCheck = mLastCachedGroup;
                listPosition = mLastCachedListPosition;
                cursorPosition = mLastCachedCursorPosition;
            }
        }

        for (int i = firstGroupToCheck; i < mGroupCount; i++) {
            long group = mGroupMetadata[i];
            int offset = (int)(group & GROUP_OFFSET_MASK);

            // Move pointers to the beginning of the group
            listPosition += (offset - cursorPosition);
            cursorPosition = offset;

            if (i > mLastCachedGroup) {
                mPositionCache.append(listPosition, i);
                mLastCachedListPosition = listPosition;
                mLastCachedCursorPosition = cursorPosition;
                mLastCachedGroup = i;
            }

            // Now we have several possibilities:
            // A) The requested position precedes the group
            if (position < listPosition) {
                metadata.itemType = ITEM_TYPE_STANDALONE;
                metadata.cursorPosition = cursorPosition - (listPosition - position);
                metadata.childCount = 1;
                return;
            }

            boolean expanded = (group & EXPANDED_GROUP_MASK) != 0;
            int size = (int) ((group & GROUP_SIZE_MASK) >> 32);

            // B) The requested position is a group header
            if (position == listPosition) {
                metadata.itemType = ITEM_TYPE_GROUP_HEADER;
                metadata.groupPosition = i;
                metadata.isExpanded = expanded;
                metadata.childCount = size;
                metadata.cursorPosition = offset;
                return;
            }

            if (expanded) {
                // C) The requested position is an element in the expanded group
                if (position < listPosition + size + 1) {
                    metadata.itemType = ITEM_TYPE_IN_GROUP;
                    metadata.cursorPosition = cursorPosition + (position - listPosition) - 1;
                    return;
                }

                // D) The element is past the expanded group
                listPosition += size + 1;
            } else {

                // E) The element is past the collapsed group
                listPosition++;
            }

            // Move cursor past the group
            cursorPosition += size;
        }

        // The required item is past the last group
        metadata.itemType = ITEM_TYPE_STANDALONE;
        metadata.cursorPosition = cursorPosition + (position - listPosition);
        metadata.childCount = 1;
    }

    /**
     * Returns true if the specified position in the list corresponds to a
     * group header.
     */
    public boolean isGroupHeader(int position) {
        obtainPositionMetadata(mPositionMetadata, position);
        return mPositionMetadata.itemType == ITEM_TYPE_GROUP_HEADER;
    }

    /**
     * Given a position of a groups header in the list, returns the size of
     * the corresponding group.
     */
    public int getGroupSize(int position) {
        obtainPositionMetadata(mPositionMetadata, position);
        return mPositionMetadata.childCount;
    }

    /**
     * Mark group as expanded if it is collapsed and vice versa.
     */
    @NeededForTesting
    public void toggleGroup(int position) {
        obtainPositionMetadata(mPositionMetadata, position);
        if (mPositionMetadata.itemType != ITEM_TYPE_GROUP_HEADER) {
            throw new IllegalArgumentException("Not a group at position " + position);
        }

        if (mPositionMetadata.isExpanded) {
            mGroupMetadata[mPositionMetadata.groupPosition] &= ~EXPANDED_GROUP_MASK;
        } else {
            mGroupMetadata[mPositionMetadata.groupPosition] |= EXPANDED_GROUP_MASK;
        }
        resetCache();
        notifyDataSetChanged();
    }

    public int getItemViewType(int position) {
        obtainPositionMetadata(mPositionMetadata, position);
        return mPositionMetadata.itemType;
    }

    public Object getItem(int position) {
        if (mCursor == null) {
            return null;
        }

        obtainPositionMetadata(mPositionMetadata, position);
        if (mCursor.moveToPosition(mPositionMetadata.cursorPosition)) {
            return mCursor;
        } else {
            return null;
        }
    }

    public long getItemId(int position) {
        Object item = getItem(position);
        if (item != null) {
            return mCursor.getLong(mRowIdColumnIndex);
        } else {
            return -1;
        }
    }
}