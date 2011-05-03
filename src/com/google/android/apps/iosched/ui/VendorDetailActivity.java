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
import com.google.android.apps.iosched.service.SyncService;
import com.google.android.apps.iosched.util.FractionalTouchDelegate;
import com.google.android.apps.iosched.util.NotifyingAsyncQueryHandler;
import com.google.android.apps.iosched.util.ParserUtils;
import com.google.android.apps.iosched.util.UIUtils;
import com.google.android.apps.iosched.util.NotifyingAsyncQueryHandler.AsyncQueryListener;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.CompoundButton.OnCheckedChangeListener;

/**
 * {@link Activity} that displays details about a specific
 * {@link Vendors#VENDOR_ID}, as requested through {@link Intent#getData()}.
 */
public class VendorDetailActivity extends Activity implements AsyncQueryListener,
        OnCheckedChangeListener {
    private static final String TAG = "VendorDetailActivity";

    private Uri mVendorUri;

    private String mTrackId;

    private TextView mName;
    private TextView mLocation;
    private CompoundButton mStarred;

    private ImageView mLogo;
    private TextView mUrl;
    private TextView mDesc;
    private TextView mProductDesc;

    private NotifyingAsyncQueryHandler mHandler;

    public static final String EXTRA_TRACK = "com.google.android.iosched.extra.TRACK";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vendor_detail);

        mName = (TextView) findViewById(R.id.vendor_name);
        mLocation = (TextView) findViewById(R.id.vendor_location);
        mStarred = (CompoundButton) findViewById(R.id.star_button);

        mStarred.setFocusable(true);
        mStarred.setClickable(true);

        // Larger target triggers star toggle
        final View starParent = findViewById(R.id.list_item_vendor);
        FractionalTouchDelegate.setupDelegate(starParent, mStarred, new RectF(0.6f, 0f, 1f, 0.8f));

        mLogo = (ImageView) findViewById(R.id.vendor_logo);
        mUrl = (TextView) findViewById(R.id.vendor_url);
        mDesc = (TextView) findViewById(R.id.vendor_desc);
        mProductDesc = (TextView) findViewById(R.id.vendor_product_desc);

        final Intent intent = getIntent();
        mVendorUri = intent.getData();

        // Start background query to load vendor details
        mHandler = new NotifyingAsyncQueryHandler(getContentResolver(), this);
        mHandler.startQuery(mVendorUri, VendorsQuery.PROJECTION);
    }

    /** {@inheritDoc} */
    public void onQueryComplete(int token, Object cookie, Cursor cursor) {
        try {
            if (!cursor.moveToFirst()) return;

            mName.setText(cursor.getString(VendorsQuery.NAME));
            mLocation.setText(cursor.getString(VendorsQuery.LOCATION));

            // Unregister around setting checked state to avoid triggering
            // listener since change isn't user generated.
            mStarred.setOnCheckedChangeListener(null);
            mStarred.setChecked(cursor.getInt(VendorsQuery.STARRED) != 0);
            mStarred.setOnCheckedChangeListener(this);

            // Start background fetch to load vendor logo
            final String logoUrl = cursor.getString(VendorsQuery.LOGO_URL);
            new VendorLogoTask().execute(logoUrl);

            mUrl.setText(cursor.getString(VendorsQuery.URL));
            mDesc.setText(cursor.getString(VendorsQuery.DESC));
            mProductDesc.setText(cursor.getString(VendorsQuery.PRODUCT_DESC));

            mTrackId = cursor.getString(VendorsQuery.TRACK_ID);

            // Assign track details when found
            // TODO: handle vendors not attached to track
            ((TextView) findViewById(R.id.title_text)).setText(cursor
                    .getString(VendorsQuery.TRACK_NAME));
            UIUtils.setTitleBarColor(findViewById(R.id.title_container),
                    cursor.getInt(VendorsQuery.TRACK_COLOR));

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

    /** Handle "map" title-bar action. */
    public void onMapClick(View v) {
        // The room ID for the sandbox, in the map, is just the track ID
        final Intent intent = new Intent(this, MapActivity.class);
        intent.putExtra(MapActivity.EXTRA_ROOM, ParserUtils.translateTrackIdAliasInverse(mTrackId));
        startActivity(intent);
    }

    /** Handle toggling of starred checkbox. */
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        final ContentValues values = new ContentValues();
        values.put(Vendors.STARRED, isChecked ? 1 : 0);
        mHandler.startUpdate(mVendorUri, values);
    }

    private static HttpClient sHttpClient;

    private static synchronized HttpClient getHttpClient(Context context) {
        if (sHttpClient == null) {
            sHttpClient = SyncService.getHttpClient(context);
        }
        return sHttpClient;
    }

    private class VendorLogoTask extends AsyncTask<String, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(String... params) {
            final String param = params[0];

            try {
                final Context context = VendorDetailActivity.this;
                final HttpClient httpClient = getHttpClient(context);
                final HttpResponse resp = httpClient.execute(new HttpGet(param));
                final HttpEntity entity = resp.getEntity();

                final int statusCode = resp.getStatusLine().getStatusCode();
                if (statusCode != HttpStatus.SC_OK || entity == null) return null;

                final byte[] respBytes = EntityUtils.toByteArray(entity);
                return BitmapFactory.decodeByteArray(respBytes, 0, respBytes.length);

            } catch(Exception e) {
                Log.w(TAG, "Problem while loading vendor logo: " + e.toString());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            if (result == null) {
                mLogo.setVisibility(View.GONE);
            } else {
                mLogo.setVisibility(View.VISIBLE);
                mLogo.setImageBitmap(result);
            }
        }
    }

    /** {@link Vendors} query parameters. */
    private interface VendorsQuery {
        String[] PROJECTION = {
                Vendors.NAME,
                Vendors.LOCATION,
                Vendors.DESC,
                Vendors.URL,
                Vendors.PRODUCT_DESC,
                Vendors.LOGO_URL,
                Vendors.STARRED,
                Vendors.TRACK_ID,
                Tracks.TRACK_NAME,
                Tracks.TRACK_COLOR,
        };

        int NAME = 0;
        int LOCATION = 1;
        int DESC = 2;
        int URL = 3;
        int PRODUCT_DESC = 4;
        int LOGO_URL = 5;
        int STARRED = 6;
        int TRACK_ID = 7;
        int TRACK_NAME = 8;
        int TRACK_COLOR = 9;
    }
}
