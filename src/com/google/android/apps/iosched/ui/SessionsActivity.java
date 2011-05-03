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

import static com.google.android.apps.iosched.util.UIUtils.buildStyledSnippet;
import static com.google.android.apps.iosched.util.UIUtils.formatSessionSubtitle;

import com.google.android.apps.iosched.R;
import com.google.android.apps.iosched.provider.ScheduleContract.Blocks;
import com.google.android.apps.iosched.provider.ScheduleContract.Rooms;
import com.google.android.apps.iosched.provider.ScheduleContract.Sessions;
import com.google.android.apps.iosched.util.NotifyingAsyncQueryHandler;
import com.google.android.apps.iosched.util.UIUtils;
import com.google.android.apps.iosched.util.NotifyingAsyncQueryHandler.AsyncQueryListener;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.BaseColumns;
import android.text.Spannable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 * {@link ListActivity} that displays a set of {@link Sessions}, as requested
 * through {@link Intent#getData()}.
 */
public class SessionsActivity extends ListActivity implements AsyncQueryListener {

    private Uri mTrackUri;
    private CursorAdapter mAdapter;

    private NotifyingAsyncQueryHandler mHandler;
    private Handler mMessageQueueHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!getIntent().hasCategory(Intent.CATEGORY_TAB)) {
            setContentView(R.layout.activity_sessions);

            final String customTitle = getIntent().getStringExtra(Intent.EXTRA_TITLE);
            ((TextView) findViewById(R.id.title_text)).setText(
                    customTitle != null ? customTitle : getTitle());

        } else {
            setContentView(R.layout.activity_sessions_content);
        }

        final Intent intent = getIntent();
        final Uri sessionsUri = intent.getData();

        String[] projection;
        if (!Sessions.isSearchUri(sessionsUri)) {
            mAdapter = new SessionsAdapter(this);
            projection = SessionsQuery.PROJECTION;

        } else {
            mAdapter = new SearchAdapter(this);
            projection = SearchQuery.PROJECTION;
        }

        setListAdapter(mAdapter);

        // If caller launched us with specific track hint, pass it along when
        // launching session details.
        mTrackUri = intent.getParcelableExtra(SessionDetailActivity.EXTRA_TRACK);

        // Start background query to load sessions
        mHandler = new NotifyingAsyncQueryHandler(getContentResolver(), this);
        mHandler.startQuery(sessionsUri, projection, Sessions.DEFAULT_SORT);
    }

    /** {@inheritDoc} */
    public void onQueryComplete(int token, Object cookie, Cursor cursor) {
        startManagingCursor(cursor);
        mAdapter.changeCursor(cursor);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMessageQueueHandler.post(mRefreshSessionsRunnable);
    }

    @Override
    protected void onPause() {
        mMessageQueueHandler.removeCallbacks(mRefreshSessionsRunnable);
        super.onPause();
    }

    /** {@inheritDoc} */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        // Launch viewer for specific session, passing along any track knowledge
        // that should influence the title-bar.
        final Cursor cursor = (Cursor)mAdapter.getItem(position);
        final String sessionId = cursor.getString(cursor.getColumnIndex(Sessions.SESSION_ID));
        final Uri sessionUri = Sessions.buildSessionUri(sessionId);
        final Intent intent = new Intent(Intent.ACTION_VIEW, sessionUri);
        intent.putExtra(SessionDetailActivity.EXTRA_TRACK, mTrackUri);
        startActivity(intent);
    }

    /** Handle "home" title-bar action. */
    public void onHomeClick(View v) {
        UIUtils.goHome(this);
    }

    /** Handle "search" title-bar action. */
    public void onSearchClick(View v) {
        UIUtils.goSearch(this);
    }

    /**
     * {@link CursorAdapter} that renders a {@link SessionsQuery}.
     */
    private class SessionsAdapter extends CursorAdapter {
        public SessionsAdapter(Context context) {
            super(context, null);
        }

        /** {@inheritDoc} */
        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return getLayoutInflater().inflate(R.layout.list_item_session, parent, false);
        }

        /** {@inheritDoc} */
        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            final TextView titleView = (TextView) view.findViewById(R.id.session_title);
            final TextView subtitleView = (TextView) view.findViewById(R.id.session_subtitle);
            final CheckBox starButton = (CheckBox) view.findViewById(R.id.star_button);

            titleView.setText(cursor.getString(SessionsQuery.TITLE));

            // Format time block this session occupies
            final long blockStart = cursor.getLong(SessionsQuery.BLOCK_START);
            final long blockEnd = cursor.getLong(SessionsQuery.BLOCK_END);
            final String roomName = cursor.getString(SessionsQuery.ROOM_NAME);
            final String subtitle = formatSessionSubtitle(blockStart, blockEnd, roomName, context);

            subtitleView.setText(subtitle);

            final boolean starred = cursor.getInt(SessionsQuery.STARRED) != 0;
            starButton.setVisibility(starred ? View.VISIBLE : View.INVISIBLE);
            starButton.setChecked(starred);

            // Possibly indicate that the session has occurred in the past.
            UIUtils.setSessionTitleColor(blockStart, blockEnd, titleView, subtitleView);
        }
    }

    /**
     * {@link CursorAdapter} that renders a {@link SearchQuery}.
     */
    private class SearchAdapter extends CursorAdapter {
        public SearchAdapter(Context context) {
            super(context, null);
        }

        /** {@inheritDoc} */
        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return getLayoutInflater().inflate(R.layout.list_item_session, parent, false);
        }

        /** {@inheritDoc} */
        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ((TextView) view.findViewById(R.id.session_title)).setText(cursor
                    .getString(SearchQuery.TITLE));

            final String snippet = cursor.getString(SearchQuery.SEARCH_SNIPPET);
            final Spannable styledSnippet = buildStyledSnippet(snippet);
            ((TextView) view.findViewById(R.id.session_subtitle)).setText(styledSnippet);

            final boolean starred = cursor.getInt(SearchQuery.STARRED) != 0;
            final CheckBox starButton = (CheckBox) view.findViewById(R.id.star_button);
            starButton.setVisibility(starred ? View.VISIBLE : View.INVISIBLE);
            starButton.setChecked(starred);
        }
    }

    private Runnable mRefreshSessionsRunnable = new Runnable() {
        public void run() {
            if (mAdapter != null) {
                // This is used to refresh session title colors.
                mAdapter.notifyDataSetChanged();
            }

            // Check again on the next quarter hour, with some padding to account for network
            // time differences.
            long nextQuarterHour = (SystemClock.uptimeMillis() / 900000 + 1) * 900000 + 5000;
            mMessageQueueHandler.postAtTime(mRefreshSessionsRunnable, nextQuarterHour);
        }
    };

    /** {@link Sessions} query parameters. */
    private interface SessionsQuery {
        String[] PROJECTION = {
                BaseColumns._ID,
                Sessions.SESSION_ID,
                Sessions.TITLE,
                Sessions.STARRED,
                Blocks.BLOCK_START,
                Blocks.BLOCK_END,
                Rooms.ROOM_NAME,
        };

        int _ID = 0;
        int SESSION_ID = 1;
        int TITLE = 2;
        int STARRED = 3;
        int BLOCK_START = 4;
        int BLOCK_END = 5;
        int ROOM_NAME = 6;
    }

    /** {@link Sessions} search query parameters. */
    private interface SearchQuery {
        String[] PROJECTION = {
                BaseColumns._ID,
                Sessions.SESSION_ID,
                Sessions.TITLE,
                Sessions.SEARCH_SNIPPET,
                Sessions.STARRED,
        };

        int _ID = 0;
        int SESSION_ID = 1;
        int TITLE = 2;
        int SEARCH_SNIPPET = 3;
        int STARRED = 4;
    }
}
