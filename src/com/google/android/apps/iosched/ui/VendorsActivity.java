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

import com.google.android.apps.iosched.R;
import com.google.android.apps.iosched.provider.ScheduleContract.Vendors;
import com.google.android.apps.iosched.util.NotifyingAsyncQueryHandler;
import com.google.android.apps.iosched.util.UIUtils;
import com.google.android.apps.iosched.util.NotifyingAsyncQueryHandler.AsyncQueryListener;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.text.Spannable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 * {@link ListActivity} that displays a set of {@link Vendors}, as requested
 * through {@link Intent#getData()}.
 */
public class VendorsActivity extends ListActivity implements AsyncQueryListener {

    private CursorAdapter mAdapter;

    private NotifyingAsyncQueryHandler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!getIntent().hasCategory(Intent.CATEGORY_TAB)) {
            setContentView(R.layout.activity_vendors);
            ((TextView) findViewById(R.id.title_text)).setText(getTitle());

        } else {
            setContentView(R.layout.activity_vendors_content);
        }

        final Uri vendorsUri = getIntent().getData();

        String[] projection;
        if (!Vendors.isSearchUri(vendorsUri)) {
            mAdapter = new VendorsAdapter(this);
            projection = VendorsQuery.PROJECTION;

        } else {
            mAdapter = new SearchAdapter(this);
            projection = SearchQuery.PROJECTION;
        }

        setListAdapter(mAdapter);

        // Start background query to load vendors
        mHandler = new NotifyingAsyncQueryHandler(getContentResolver(), this);
        mHandler.startQuery(vendorsUri, projection, Vendors.DEFAULT_SORT);
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

    /** Handle "search" title-bar action. */
    public void onSearchClick(View v) {
        UIUtils.goSearch(this);
    }

    /** {@inheritDoc} */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        // Launch viewer for specific vendor
        final Cursor cursor = (Cursor)mAdapter.getItem(position);
        final String vendorId = cursor.getString(VendorsQuery.VENDOR_ID);
        final Uri vendorUri = Vendors.buildVendorUri(vendorId);
        startActivity(new Intent(Intent.ACTION_VIEW, vendorUri));
    }

    /**
     * {@link CursorAdapter} that renders a {@link VendorsQuery}.
     */
    private class VendorsAdapter extends CursorAdapter {
        public VendorsAdapter(Context context) {
            super(context, null);
        }

        /** {@inheritDoc} */
        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return getLayoutInflater().inflate(R.layout.list_item_vendor, parent, false);
        }

        /** {@inheritDoc} */
        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ((TextView) view.findViewById(R.id.vendor_name)).setText(cursor
                    .getString(VendorsQuery.NAME));
            ((TextView) view.findViewById(R.id.vendor_location)).setText(cursor
                    .getString(VendorsQuery.LOCATION));

            final boolean starred = cursor.getInt(VendorsQuery.STARRED) != 0;
            final CheckBox starButton = (CheckBox) view.findViewById(R.id.star_button);
            starButton.setVisibility(starred ? View.VISIBLE : View.INVISIBLE);
            starButton.setChecked(starred);
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
            return getLayoutInflater().inflate(R.layout.list_item_vendor, parent, false);
        }

        /** {@inheritDoc} */
        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ((TextView) view.findViewById(R.id.vendor_name)).setText(cursor
                    .getString(SearchQuery.NAME));

            final String snippet = cursor.getString(SearchQuery.SEARCH_SNIPPET);
            final Spannable styledSnippet = buildStyledSnippet(snippet);
            ((TextView) view.findViewById(R.id.vendor_location)).setText(styledSnippet);

            final boolean starred = cursor.getInt(VendorsQuery.STARRED) != 0;
            final CheckBox starButton = (CheckBox) view.findViewById(R.id.star_button);
            starButton.setVisibility(starred ? View.VISIBLE : View.INVISIBLE);
            starButton.setChecked(starred);
        }
    }

    /** {@link Vendors} query parameters. */
    private interface VendorsQuery {
        String[] PROJECTION = {
                BaseColumns._ID,
                Vendors.VENDOR_ID,
                Vendors.NAME,
                Vendors.LOCATION,
                Vendors.STARRED,
        };

        int _ID = 0;
        int VENDOR_ID = 1;
        int NAME = 2;
        int LOCATION = 3;
        int STARRED = 4;
    }

    /** {@link Vendors} search query parameters. */
    private interface SearchQuery {
        String[] PROJECTION = {
                BaseColumns._ID,
                Vendors.VENDOR_ID,
                Vendors.NAME,
                Vendors.SEARCH_SNIPPET,
                Vendors.STARRED,
        };

        int _ID = 0;
        int VENDOR_ID = 1;
        int NAME = 2;
        int SEARCH_SNIPPET = 3;
        int STARRED = 4;
    }
}
