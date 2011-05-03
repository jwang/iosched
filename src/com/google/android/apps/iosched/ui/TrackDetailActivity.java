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
import com.google.android.apps.iosched.provider.ScheduleContract.Vendors;
import com.google.android.apps.iosched.util.NotifyingAsyncQueryHandler;
import com.google.android.apps.iosched.util.Sets;
import com.google.android.apps.iosched.util.UIUtils;
import com.google.android.apps.iosched.util.NotifyingAsyncQueryHandler.AsyncQueryListener;

import android.app.Activity;
import android.app.TabActivity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TabHost;
import android.widget.TextView;

import java.util.HashSet;

/**
 * {@link Activity} that displays details about a specific
 * {@link Tracks#TRACK_ID}, as requested through {@link Intent#getData()}.
 */
public class TrackDetailActivity extends TabActivity implements AsyncQueryListener {

    public static final String EXTRA_FOCUS_TAG = "com.google.android.iosched.extra.FOCUS_TAG";

    public static final String TAG_SUMMARY = "summary";
    public static final String TAG_SESSIONS = "sessions";
    public static final String TAG_VENDORS = "vendors";

    /**
     * List of specific {@link Tracks#TRACK_ID} that never have {@link Vendors},
     * which is used to hide the {@link #TAG_VENDORS} tab instead of a query.
     */
    // TODO: instead of using this static list, query for Tracks.VENDORS_COUNT
    private static HashSet<String> sTracksWithoutVendors = Sets.newHashSet("firesidechats",
            "techtalks");

    private Uri mTrackUri;
    private String mTrackId;

    private TextView mTitleText;
    private TextView mTrackAbstract;

    private NotifyingAsyncQueryHandler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_track_details);

        mTrackUri = getIntent().getData();
        mTrackId = Tracks.getTrackId(mTrackUri);

        mTitleText = (TextView) findViewById(R.id.title_text);
        mTrackAbstract = (TextView) findViewById(R.id.track_abstract);

        setupSummaryTab();
        setupSessionsTab();

        // Only add vendors tab when applicable
        final boolean hasVendors = !sTracksWithoutVendors.contains(mTrackId);
        if (hasVendors) setupVendorsTab();

        // Show specific focus tag when requested, otherwise default
        String focusTag = getIntent().getStringExtra(EXTRA_FOCUS_TAG);
        if (focusTag == null) focusTag = TAG_SESSIONS;

        getTabHost().setCurrentTabByTag(focusTag);

        // Start background query to load track details
        mHandler = new NotifyingAsyncQueryHandler(getContentResolver(), this);
        mHandler.startQuery(mTrackUri, TracksQuery.PROJECTION);
    }

    /** Build and add "summary" tab. */
    private void setupSummaryTab() {
        final TabHost host = getTabHost();

        // Summary content comes from existing layout
        host.addTab(host.newTabSpec(TAG_SUMMARY)
                .setIndicator(buildIndicator(R.string.track_summary))
                .setContent(R.id.tab_track_summary));
    }

    /** Build and add "sessions" tab. */
    private void setupSessionsTab() {
        final TabHost host = getTabHost();

        final Uri trackUri = getIntent().getData();
        final String trackId = Tracks.getTrackId(trackUri);
        final Uri sessionsUri = Tracks.buildSessionsUri(trackId);

        final Intent intent = new Intent(Intent.ACTION_VIEW, sessionsUri);
        intent.putExtra(SessionDetailActivity.EXTRA_TRACK, trackUri);
        intent.addCategory(Intent.CATEGORY_TAB);

        // Sessions content comes from reused activity
        host.addTab(host.newTabSpec(TAG_SESSIONS)
                .setIndicator(buildIndicator(R.string.track_sessions))
                .setContent(intent));
    }

    /** Build and add "vendors" tab. */
    private void setupVendorsTab() {
        final TabHost host = getTabHost();
        final Uri vendorsUri = Tracks.buildVendorsUri(mTrackId);

        final Intent intent = new Intent(Intent.ACTION_VIEW, vendorsUri);
        intent.putExtra(SessionDetailActivity.EXTRA_TRACK, mTrackUri);
        intent.addCategory(Intent.CATEGORY_TAB);

        // Vendors content comes from reused activity
        host.addTab(host.newTabSpec(TAG_VENDORS)
                .setIndicator(buildIndicator(R.string.track_vendors))
                .setContent(intent));
    }

    /**
     * Build a {@link View} to be used as a tab indicator, setting the requested
     * string resource as its label.
     */
    private View buildIndicator(int textRes) {
        final TextView indicator = (TextView) getLayoutInflater().inflate(R.layout.tab_indicator,
                getTabWidget(), false);
        indicator.setText(textRes);
        return indicator;
    }

    /** {@inheritDoc} */
    public void onQueryComplete(int token, Object cookie, Cursor cursor) {
        try {
            if (!cursor.moveToFirst()) return;
            mTitleText.setText(cursor.getString(TracksQuery.TRACK_NAME));
            mTrackAbstract.setText(cursor.getString(TracksQuery.TRACK_ABSTRACT));
            UIUtils.setTitleBarColor(findViewById(R.id.title_container),
                    cursor.getInt(TracksQuery.TRACK_COLOR));
        } finally {
            cursor.close();
        }
    }

    /** Handle "home" title-bar action. */
    public void onHomeClick(View v) {
        UIUtils.goHome(this);
    }

    /** Handle "search" title-bar action. */
    public void onSearchClick(View v) {
        UIUtils.goSearch(this);
    }

    /** {@link Tracks} query parameters. */
    private interface TracksQuery {
        String[] PROJECTION = {
                Tracks.TRACK_NAME,
                Tracks.TRACK_COLOR,
                Tracks.TRACK_ABSTRACT,
        };

        int TRACK_NAME = 0;
        int TRACK_COLOR = 1;
        int TRACK_ABSTRACT = 2;
    }
}
