/*
 * Copyright 2010 Google Inc.
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

package com.google.android.apps.iosched.ui;

import com.google.android.apps.iosched.R;
import com.google.android.apps.iosched.provider.ScheduleContract.Blocks;
import com.google.android.apps.iosched.ui.widget.BlockView;
import com.google.android.apps.iosched.ui.widget.BlocksLayout;
import com.google.android.apps.iosched.util.Maps;
import com.google.android.apps.iosched.util.NotifyingAsyncQueryHandler;
import com.google.android.apps.iosched.util.ParserUtils;
import com.google.android.apps.iosched.util.UIUtils;
import com.google.android.apps.iosched.util.NotifyingAsyncQueryHandler.AsyncQueryListener;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Rect;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.BaseColumns;
import android.util.Log;
import android.view.View;
import android.widget.ScrollView;

import java.util.HashMap;

/**
 * {@link Activity} that displays a high-level view of a single day of
 * {@link Blocks} across the conference. Shows them lined up against a vertical
 * ruler of times across the day.
 */
public class BlocksActivity extends Activity implements AsyncQueryListener, View.OnClickListener {
    private static final String TAG = "BlocksActivity";

    // TODO: these layouts and views are structured pretty weird, ask someone to
    // review them and come up with better organization.

    // TODO: show blocks that don't fall into columns at the bottom

    public static final String EXTRA_TIME_START = "com.google.android.iosched.extra.TIME_START";
    public static final String EXTRA_TIME_END = "com.google.android.iosched.extra.TIME_END";

    private ScrollView mScrollView;
    private BlocksLayout mBlocks;
    private View mNowView;

    private long mTimeStart = -1;
    private long mTimeEnd = -1;

    private NotifyingAsyncQueryHandler mHandler;

    private static final int DISABLED_BLOCK_ALPHA = 160;

    private static final HashMap<String, Integer> sTypeColumnMap = buildTypeColumnMap();

    private static HashMap<String, Integer> buildTypeColumnMap() {
        final HashMap<String, Integer> map = Maps.newHashMap();
        map.put(ParserUtils.BLOCK_TYPE_FOOD, 0);
        map.put(ParserUtils.BLOCK_TYPE_SESSION, 1);
        map.put(ParserUtils.BLOCK_TYPE_OFFICE_HOURS, 2);
        return map;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocks_content);

        mTimeStart = getIntent().getLongExtra(EXTRA_TIME_START, mTimeStart);
        mTimeEnd = getIntent().getLongExtra(EXTRA_TIME_END, mTimeEnd);

        mScrollView = (ScrollView) findViewById(R.id.blocks_scroll);
        mBlocks = (BlocksLayout) findViewById(R.id.blocks);
        mNowView = findViewById(R.id.blocks_now);

        mBlocks.setDrawingCacheEnabled(true);
        mBlocks.setAlwaysDrawnWithCacheEnabled(true);

        mHandler = new NotifyingAsyncQueryHandler(getContentResolver(), this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Since we build our views manually instead of using an adapter, we
        // need to manually requery every time launched.
        final Uri blocksUri = getIntent().getData();
        mHandler.startQuery(blocksUri, BlocksQuery.PROJECTION, Blocks.DEFAULT_SORT);

        // Start listening for time updates to adjust "now" bar. TIME_TICK is
        // triggered once per minute, which is how we move the bar over time.
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(mReceiver, filter, null, new Handler());

        mNowView.post(new Runnable() {
            public void run() {
                updateNowView(true);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
    }

    /** {@inheritDoc} */
    public void onQueryComplete(int token, Object cookie, Cursor cursor) {
        // Clear out any existing sessions before inserting again
        mBlocks.removeAllBlocks();

        try {
            while (cursor.moveToNext()) {
                final String type = cursor.getString(BlocksQuery.BLOCK_TYPE);
                final Integer column = sTypeColumnMap.get(type);
                // TODO: place random blocks at bottom of entire layout
                if (column == null) continue;

                final String blockId = cursor.getString(BlocksQuery.BLOCK_ID);
                final String title = cursor.getString(BlocksQuery.BLOCK_TITLE);
                final long start = cursor.getLong(BlocksQuery.BLOCK_START);
                final long end = cursor.getLong(BlocksQuery.BLOCK_END);
                final boolean containsStarred = cursor.getInt(BlocksQuery.CONTAINS_STARRED) != 0;

                final BlockView blockView = new BlockView(this, blockId, title, start, end,
                        containsStarred, column);

                final int sessionsCount = cursor.getInt(BlocksQuery.SESSIONS_COUNT);
                if (sessionsCount > 0) {
                    blockView.setOnClickListener(this);
                } else {
                    blockView.setFocusable(false);
                    blockView.setEnabled(false);
                    LayerDrawable buttonDrawable = (LayerDrawable) blockView.getBackground();
                    buttonDrawable.getDrawable(0).setAlpha(DISABLED_BLOCK_ALPHA);
                    buttonDrawable.getDrawable(2).setAlpha(DISABLED_BLOCK_ALPHA);
                }

                mBlocks.addBlock(blockView);
            }
        } finally {
            cursor.close();
        }
    }

    public void onHomeClick(View v) {
        UIUtils.goHome(this);
    }

    public void onRefreshClick(View v) {
    }

    public void onSearchClick(View v) {
        UIUtils.goSearch(this);
    }

    /** {@inheritDoc} */
    public void onClick(View view) {
        if (view instanceof BlockView) {
            final String blockId = ((BlockView) view).getBlockId();
            final Uri sessionsUri = Blocks.buildSessionsUri(blockId);
            startActivity(new Intent(Intent.ACTION_VIEW, sessionsUri));
        }
    }

    /**
     * Update position and visibility of "now" view.
     */
    private void updateNowView(boolean forceScroll) {
        final long now = System.currentTimeMillis();

        final boolean visible = now >= mTimeStart && now <= mTimeEnd;
        mNowView.setVisibility(visible ? View.VISIBLE : View.GONE);

        if (visible && forceScroll) {
            // Scroll to show "now" in center
            final int offset = mScrollView.getHeight() / 2;
            mNowView.requestRectangleOnScreen(new Rect(0, offset, 0, offset), true);
        }

        mBlocks.requestLayout();
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive time update");
            updateNowView(false);
        }
    };

    private interface BlocksQuery {
        String[] PROJECTION = {
                BaseColumns._ID,
                Blocks.BLOCK_ID,
                Blocks.BLOCK_TITLE,
                Blocks.BLOCK_START,
                Blocks.BLOCK_END,
                Blocks.BLOCK_TYPE,
                Blocks.SESSIONS_COUNT,
                Blocks.CONTAINS_STARRED,
        };

        int _ID = 0;
        int BLOCK_ID = 1;
        int BLOCK_TITLE = 2;
        int BLOCK_START = 3;
        int BLOCK_END = 4;
        int BLOCK_TYPE = 5;
        int SESSIONS_COUNT = 6;
        int CONTAINS_STARRED = 7;
    }
}
