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
import com.google.android.apps.iosched.provider.ScheduleContract.Tracks;
import com.google.android.apps.iosched.util.NotifyingAsyncQueryHandler;
import com.google.android.apps.iosched.util.UIUtils;
import com.google.android.apps.iosched.util.NotifyingAsyncQueryHandler.AsyncQueryListener;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 * {@link ListActivity} that displays a set of {@link Tracks}, as requested
 * through {@link Intent#getData()}.
 */
public class TracksActivity extends ListActivity implements AsyncQueryListener {

    private String mFocusTag;
    private TracksAdapter mAdapter;

    private NotifyingAsyncQueryHandler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tracks);

        final String customTitle = getIntent().getStringExtra(Intent.EXTRA_TITLE);
        ((TextView) findViewById(R.id.title_text)).setText(
                customTitle != null ? customTitle : getTitle());

        mAdapter = new TracksAdapter(this);
        setListAdapter(mAdapter);

        final Intent intent = getIntent();
        final Uri tracksUri = intent.getData();

        mFocusTag = intent.getStringExtra(TrackDetailActivity.EXTRA_FOCUS_TAG);

        // Filter our tracks query to only include those with valid results
        String[] projection = TracksQuery.PROJECTION;
        String selection = null;
        if (TrackDetailActivity.TAG_SESSIONS.equals(mFocusTag)) {
            // Only show tracks with at least one session
            projection = TracksQuery.PROJECTION_WITH_SESSIONS_COUNT;
            selection = Tracks.SESSIONS_COUNT + ">0";

        } else if (TrackDetailActivity.TAG_VENDORS.equals(mFocusTag)) {
            // Only show tracks with at least one vendor
            projection = TracksQuery.PROJECTION_WITH_VENDORS_COUNT;
            selection = Tracks.VENDORS_COUNT + ">0";
        }

        // Start background query to load tracks
        mHandler = new NotifyingAsyncQueryHandler(getContentResolver(), this);
        mHandler.startQuery(tracksUri, projection, selection, null, Tracks.DEFAULT_SORT);
    }

    /** {@inheritDoc} */
    public void onQueryComplete(int token, Object cookie, Cursor cursor) {
        startManagingCursor(cursor);
        mAdapter.changeCursor(cursor);
    }

    /** Handle "home" title-bar action. */
    public void onHomeClick(View v) {
        UIUtils.goHome(this);
    }

    /** Handle "refresh" title-bar action. */
    public void onRefreshClick(View v) {
    }

    /** Handle "search" title-bar action. */
    public void onSearchClick(View v) {
        UIUtils.goSearch(this);
    }

    /** {@inheritDoc} */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        // Launch viewer for specific track
        final Cursor cursor = (Cursor)mAdapter.getItem(position);
        final String trackId = cursor.getString(TracksQuery.TRACK_ID);
        final Uri trackUri = Tracks.buildTrackUri(trackId);
        final Intent intent = new Intent(Intent.ACTION_VIEW, trackUri);

        // Pass along any request for focus on a specific tag
        intent.putExtra(TrackDetailActivity.EXTRA_FOCUS_TAG, mFocusTag);

        startActivity(intent);
    }

    /**
     * {@link CursorAdapter} that renders a {@link TracksQuery}.
     */
    private class TracksAdapter extends CursorAdapter {
        public TracksAdapter(Context context) {
            super(context, null);
        }

        /** {@inheritDoc} */
        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return getLayoutInflater().inflate(R.layout.list_item_track, parent, false);
        }

        /** {@inheritDoc} */
        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            final TextView textView = (TextView) view.findViewById(android.R.id.text1);
            textView.setText(cursor.getString(TracksQuery.TRACK_NAME));

            // Assign track color to visible block
            final View iconView = view.findViewById(android.R.id.icon1);
            LayerDrawable iconDrawable = (LayerDrawable) iconView.getBackground();
            iconDrawable.getDrawable(0).setColorFilter(
                    cursor.getInt(TracksQuery.TRACK_COLOR), PorterDuff.Mode.SRC_ATOP);
        }
    }

    /** {@link Tracks} query parameters. */
    private interface TracksQuery {
        String[] PROJECTION = {
                BaseColumns._ID,
                Tracks.TRACK_ID,
                Tracks.TRACK_NAME,
                Tracks.TRACK_COLOR,
        };

        String[] PROJECTION_WITH_SESSIONS_COUNT = {
                BaseColumns._ID,
                Tracks.TRACK_ID,
                Tracks.TRACK_NAME,
                Tracks.TRACK_COLOR,
                Tracks.SESSIONS_COUNT,
        };

        String[] PROJECTION_WITH_VENDORS_COUNT = {
                BaseColumns._ID,
                Tracks.TRACK_ID,
                Tracks.TRACK_NAME,
                Tracks.TRACK_COLOR,
                Tracks.VENDORS_COUNT,
        };

        int _ID = 0;
        int TRACK_ID = 1;
        int TRACK_NAME = 2;
        int TRACK_COLOR = 3;
    }
}
