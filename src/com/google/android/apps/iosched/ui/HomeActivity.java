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

import static com.google.android.apps.iosched.util.UIUtils.formatSessionSubtitle;

import com.google.android.apps.iosched.R;
import com.google.android.apps.iosched.provider.ScheduleContract.Blocks;
import com.google.android.apps.iosched.provider.ScheduleContract.Notes;
import com.google.android.apps.iosched.provider.ScheduleContract.Rooms;
import com.google.android.apps.iosched.provider.ScheduleContract.Sessions;
import com.google.android.apps.iosched.provider.ScheduleContract.Tracks;
import com.google.android.apps.iosched.service.SyncService;
import com.google.android.apps.iosched.util.DetachableResultReceiver;
import com.google.android.apps.iosched.util.NotifyingAsyncQueryHandler;
import com.google.android.apps.iosched.util.UIUtils;
import com.google.android.apps.iosched.util.NotifyingAsyncQueryHandler.AsyncQueryListener;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Front-door {@link Activity} that displays high-level features the schedule
 * application offers to users.
 */
public class HomeActivity extends Activity implements AsyncQueryListener,
        DetachableResultReceiver.Receiver {
    private static final String TAG = "HomeActivity";

    /** State held between configuration changes. */
    private State mState;

    private Handler mMessageHandler = new Handler();
    private NotifyingAsyncQueryHandler mQueryHandler;

    private TextView mCountdownTextView;
    private View mNowPlayingLoadingView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        mNowPlayingLoadingView = findViewById(R.id.now_playing_loading);

        mState = (State) getLastNonConfigurationInstance();
        final boolean previousState = mState != null;

        if (previousState) {
            // Start listening for SyncService updates again
            mState.mReceiver.setReceiver(this);
            updateRefreshStatus();
            reloadNowPlaying(true);

        } else {
            mState = new State();
            mState.mReceiver.setReceiver(this);
            onRefreshClick(null);
        }

        // Set up handler for now playing session query.
        mQueryHandler = new NotifyingAsyncQueryHandler(getContentResolver(), this);
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        // Clear any strong references to this Activity, we'll reattach to
        // handle events on the other side.
        mState.mReceiver.clearReceiver();
        return mState;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // If 'tap here to enable Wifi' was shown, check if the user has enabled Wifi
        if (mState.mNowPlayingUri != null || mState.mNowPlayingGotoWifi) {
            reloadNowPlaying(false);
        } else if (mState.mNoResults) {
            showNowPlayingNoResults();
        }
    }

    /** Handle "refresh" title-bar action. */
    public void onRefreshClick(View v) {
        // trigger off background sync
        final Intent intent = new Intent(Intent.ACTION_SYNC, null, this, SyncService.class);
        intent.putExtra(SyncService.EXTRA_STATUS_RECEIVER, mState.mReceiver);
        startService(intent);

        reloadNowPlaying(true);
    }

    /** Handle "search" title-bar action. */
    public void onSearchClick(View v) {
        UIUtils.goSearch(this);
    }

    /** Handle "schedule" action. */
    public void onScheduleClick(View v) {
        // Launch overall conference schedule
        startActivity(new Intent(this, ScheduleActivity.class));
    }

    /** Handle "map" action. */
    public void onMapClick(View v) {
        // Launch map of conference venue
        startActivity(new Intent(this, MapActivity.class));
    }

    /** Handle "sessions" action. */
    public void onSessionsClick(View v) {
        // Launch sessions clustered by track
        final Intent intent = new Intent(Intent.ACTION_VIEW, Tracks.CONTENT_URI);
        intent.putExtra(Intent.EXTRA_TITLE, getString(R.string.title_session_tracks));
        intent.putExtra(TrackDetailActivity.EXTRA_FOCUS_TAG, TrackDetailActivity.TAG_SESSIONS);
        startActivity(intent);
    }

    /** Handle "starred" action. */
    public void onStarredClick(View v) {
        // Launch list of sessions user has starred
        startActivity(new Intent(this, StarredActivity.class));
    }

    /** Handle "sandbox" or "vendors" action. */
    public void onVendorsClick(View v) {
        // Launch list of vendors at conference
        final Intent intent = new Intent(Intent.ACTION_VIEW, Tracks.CONTENT_URI);
        intent.putExtra(Intent.EXTRA_TITLE, getString(R.string.title_vendor_tracks));
        intent.putExtra(TrackDetailActivity.EXTRA_FOCUS_TAG, TrackDetailActivity.TAG_VENDORS);
        startActivity(intent);
    }

    /** Handle "my notes" action. */
    public void onNotesClick(View v) {
        // Launch list of notes user has taken
        startActivity(new Intent(Intent.ACTION_VIEW, Notes.CONTENT_URI));
    }

    /** Handle "now playing" action. */
    public void onNowPlayingClick(View v) {
        // Shortcut launch directly to session the user is currently attending
        // (or to enable wifi if wifi is off)
        if (mState.mNowPlayingGotoWifi) {
            Intent wifiSettingsIntent = new Intent();
            wifiSettingsIntent.setAction(Settings.ACTION_WIFI_SETTINGS);
            startActivity(wifiSettingsIntent);
        } else if (mState.mNowPlayingUri != null) {
            startActivity(new Intent(Intent.ACTION_VIEW, mState.mNowPlayingUri));
        }
    }

    /** Handle "now playing > more" action. */
    public void onNowPlayingMoreClick(View v) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Sessions.buildSessionsAtDirUri(System.currentTimeMillis()));
        intent.putExtra(Intent.EXTRA_TITLE, getString(R.string.title_now_playing));
        startActivity(intent);
    }

    /** Handle "now playing" logo (Google I/O logo in before/after state) action. */
    public void onNowPlayingLogoClick(View v) {
        startActivity(new Intent(Intent.ACTION_VIEW, UIUtils.CONFERENCE_URL));
    }

    private void reloadNowPlaying(boolean forceRelocate) {
        mMessageHandler.removeCallbacks(mCountdownRunnable);

        final long currentTimeMillis = System.currentTimeMillis();

        if (mNowPlayingLoadingView == null) // Landscape orientation
            return;

        ViewGroup homeRoot = (ViewGroup) findViewById(R.id.home_root);
        View nowPlaying = findViewById(R.id.now_playing);
        if (nowPlaying != null) {
            homeRoot.removeView(nowPlaying);
            nowPlaying = null;
        }

        // Show Loading... and load the view corresponding to the current state
        mNowPlayingLoadingView.setVisibility(View.VISIBLE);
        mState.mNoResults = false;
        mState.mNowPlayingGotoWifi = false;
        if (currentTimeMillis < UIUtils.CONFERENCE_START_MILLIS) {
            nowPlaying = createNowPlayingBeforeView();
        } else if (currentTimeMillis > UIUtils.CONFERENCE_END_MILLIS) {
            nowPlaying = createNowPlayingAfterView();
        } else {
            nowPlaying = createNowPlayingDuringView(forceRelocate);
        }

        homeRoot.addView(nowPlaying, new LayoutParams(
                LayoutParams.FILL_PARENT,
                (int) getResources().getDimension(R.dimen.now_playing_height)));
    }

    private View createNowPlayingBeforeView() {
        // Before conference, show countdown.
        final View nowPlaying = getLayoutInflater().inflate(R.layout.now_playing_before, null);
        final TextView nowPlayingTitle = (TextView) nowPlaying.findViewById(
                R.id.now_playing_title);

        mCountdownTextView = nowPlayingTitle;
        mMessageHandler.post(mCountdownRunnable);
        mNowPlayingLoadingView.setVisibility(View.GONE);
        nowPlaying.setVisibility(View.VISIBLE);
        return nowPlaying;
    }

    private View createNowPlayingAfterView() {
        // After conference, show canned text.
        final View nowPlaying = getLayoutInflater().inflate(R.layout.now_playing_after, null);
        mNowPlayingLoadingView.setVisibility(View.GONE);
        nowPlaying.setVisibility(View.VISIBLE);
        return nowPlaying;
    }

    private View createNowPlayingDuringView(boolean forceRelocate) {
        // Conference in progress, show now playing.
        final View nowPlaying = getLayoutInflater().inflate(R.layout.now_playing_during, null);
        nowPlaying.setVisibility(View.GONE);
        if (!forceRelocate && mState.mNowPlayingUri != null) {
            mQueryHandler.startQuery(mState.mNowPlayingUri, SessionsQuery.PROJECTION);
        }
        return nowPlaying;
    }

    /**
     * Event that updates countdown timer. Posts itself again to
     * {@link #mMessageHandler} to continue updating time.
     */
    private Runnable mCountdownRunnable = new Runnable() {
        public void run() {
            int remainingSec = (int) Math.max(0,
                    (UIUtils.CONFERENCE_START_MILLIS - System.currentTimeMillis()) / 1000);
            final boolean conferenceStarted = remainingSec == 0;

            if (conferenceStarted) {
                // Conference started while in countdown mode, switch modes and
                // bail on future countdown updates.
                mMessageHandler.postDelayed(new Runnable() {
                    public void run() {
                        reloadNowPlaying(true);
                    }
                }, 100);
                return;
            }

            final int secs = remainingSec % 86400;
            final int days = remainingSec / 86400;
            final String str = getResources().getQuantityString(
                    R.plurals.now_playing_countdown, days, days,
                    DateUtils.formatElapsedTime(secs));
            mCountdownTextView.setText(str);

            // Repost ourselves to keep updating countdown
            mMessageHandler.postDelayed(mCountdownRunnable, 1000);
        }
    };

    private void showNowPlayingNoResults() {
        mState.mNoResults = true;
        runOnUiThread(new Runnable() {
            public void run() {
                final View loadingView = findViewById(R.id.now_playing_loading);
                if (loadingView == null) return;

                loadingView.setVisibility(View.GONE);
                findViewById(R.id.now_playing).setVisibility(View.VISIBLE);
                ((TextView) findViewById(R.id.now_playing_title)).setText(
                        R.string.now_playing_no_results);
            }
        });
    }

    /** {@inheritDoc} */
    public void onQueryComplete(int token, Object cookie, Cursor cursor) {
        try {
            if (!cursor.moveToFirst()) {
                showNowPlayingNoResults();
                return;
            }

            mState.mNowPlayingUri = Sessions.buildSessionUri(cursor
                    .getString(SessionsQuery.SESSION_ID));

            // Format time block this session occupies
            final long blockStart = cursor.getLong(SessionsQuery.BLOCK_START);
            final long blockEnd = cursor.getLong(SessionsQuery.BLOCK_END);

            final String roomName = cursor.getString(SessionsQuery.ROOM_NAME);
            final String subtitle = formatSessionSubtitle(blockStart, blockEnd, roomName, this);

            findViewById(R.id.now_playing_loading).setVisibility(View.GONE);
            findViewById(R.id.now_playing).setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.now_playing_title)).setText(cursor
                    .getString(SessionsQuery.TITLE));
            ((TextView) findViewById(R.id.now_playing_subtitle)).setText(subtitle);

        } finally {
            cursor.close();
        }
    }

    private void updateRefreshStatus() {
        findViewById(R.id.btn_title_refresh).setVisibility(
                mState.mSyncing ? View.GONE : View.VISIBLE);
        findViewById(R.id.title_refresh_progress).setVisibility(
                mState.mSyncing ? View.VISIBLE : View.GONE);
    }

    /** {@inheritDoc} */
    public void onReceiveResult(int resultCode, Bundle resultData) {
        switch (resultCode) {
            case SyncService.STATUS_RUNNING: {
                mState.mSyncing = true;
                updateRefreshStatus();
                break;
            }
            case SyncService.STATUS_FINISHED: {
                mState.mSyncing = false;
                updateRefreshStatus();
                reloadNowPlaying(true);
                break;
            }
            case SyncService.STATUS_ERROR: {
                // Error happened down in SyncService, show as toast.
                mState.mSyncing = false;
                updateRefreshStatus();
                final String errorText = getString(R.string.toast_sync_error, resultData
                        .getString(Intent.EXTRA_TEXT));
                Toast.makeText(HomeActivity.this, errorText, Toast.LENGTH_LONG).show();
                break;
            }
        }
    }

    /**
     * State specific to {@link HomeActivity} that is held between configuration
     * changes. Any strong {@link Activity} references <strong>must</strong> be
     * cleared before {@link #onRetainNonConfigurationInstance()}, and this
     * class should remain {@code static class}.
     */
    private static class State {
        public DetachableResultReceiver mReceiver;
        public Uri mNowPlayingUri = null;
        public boolean mNowPlayingGotoWifi = false;
        public boolean mSyncing = false;
        public boolean mNoResults = false;

        private State() {
            mReceiver = new DetachableResultReceiver(new Handler());
        }
    }

    private interface SessionsQuery {
        String[] PROJECTION = {
                Blocks.BLOCK_START,
                Blocks.BLOCK_END,
                Sessions.SESSION_ID,
                Sessions.TITLE,
                Rooms.ROOM_NAME,
        };

        int BLOCK_START = 0;
        int BLOCK_END = 1;
        int SESSION_ID = 2;
        int TITLE = 3;
        int ROOM_NAME = 4;
    }
}
