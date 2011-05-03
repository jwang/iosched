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
import com.google.android.apps.iosched.provider.ScheduleContract.Sessions;
import com.google.android.apps.iosched.provider.ScheduleContract.Vendors;
import com.google.android.apps.iosched.util.UIUtils;

import android.app.SearchManager;
import android.app.TabActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TabHost;
import android.widget.TextView;

public class SearchActivity extends TabActivity {

    public static final String TAG_SESSIONS = "sessions";
    public static final String TAG_VENDORS = "vendors";

    private String mQuery;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        onNewIntent(getIntent());
    }

    @Override
    public void onNewIntent(Intent intent) {
        mQuery = intent.getStringExtra(SearchManager.QUERY);
        final CharSequence title = getString(R.string.title_search_query, mQuery);

        setTitle(title);
        ((TextView) findViewById(R.id.title_text)).setText(title);

        final TabHost host = getTabHost();
        host.setCurrentTab(0);
        host.clearAllTabs();

        setupSessionsTab();
        setupVendorsTab();
    }

    public void onHomeClick(View v) {
        UIUtils.goHome(this);
    }

    public void onSearchClick(View v) {
        UIUtils.goSearch(this);
    }

    /** Build and add "sessions" tab. */
    private void setupSessionsTab() {
        final TabHost host = getTabHost();

        final Uri sessionsUri = Sessions.buildSearchUri(mQuery);
        final Intent intent = new Intent(Intent.ACTION_VIEW, sessionsUri);
        intent.addCategory(Intent.CATEGORY_TAB);

        // Sessions content comes from reused activity
        host.addTab(host.newTabSpec(TAG_SESSIONS)
                .setIndicator(buildIndicator(R.string.search_sessions))
                .setContent(intent));
    }

    /** Build and add "vendors" tab. */
    private void setupVendorsTab() {
        final TabHost host = getTabHost();

        final Uri vendorsUri = Vendors.buildSearchUri(mQuery);
        final Intent intent = new Intent(Intent.ACTION_VIEW, vendorsUri);
        intent.addCategory(Intent.CATEGORY_TAB);

        // Vendors content comes from reused activity
        host.addTab(host.newTabSpec(TAG_VENDORS)
                .setIndicator(buildIndicator(R.string.search_vendors))
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
}
