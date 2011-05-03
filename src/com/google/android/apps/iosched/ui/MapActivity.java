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
import com.google.android.apps.iosched.provider.ScheduleContract.Rooms;
import com.google.android.apps.iosched.provider.ScheduleContract.Sessions;
import com.google.android.apps.iosched.provider.ScheduleContract.Tracks;
import com.google.android.apps.iosched.util.ParserUtils;
import com.google.android.apps.iosched.util.UIUtils;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Shows a {@link WebView} with a map of the conference venue. 
 */
public class MapActivity extends Activity {
    private static final String TAG = "MapActivity";

    /**
     * When specified, will automatically point the map to the requested room.
     */
    public static final String EXTRA_ROOM = "com.google.android.iosched.extra.ROOM";

    public static final String OFFICE_HOURS_ROOM_ID = "officehours";

    private static final String MAP_JSI_NAME = "MAP_CONTAINER";
    private static final String MAP_URL = "http://code.google.com/events/io/2010/map/embed.html";
    private static boolean CLEAR_CACHE_ON_LOAD = false;

    private WebView mWebView;
    private boolean mLoadingVisible = false;
    private boolean mMapInitialized = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        ((TextView) findViewById(R.id.title_text)).setText(getTitle());

        showLoading(true);
        mWebView = (WebView) findViewById(R.id.webview);
        mWebView.post(new Runnable() {
            public void run() {
                // Initialize web view
                if (CLEAR_CACHE_ON_LOAD) {
                    mWebView.clearCache(true);
                }

                mWebView.getSettings().setJavaScriptEnabled(true);
                mWebView.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
                mWebView.setWebChromeClient(new MapWebChromeClient());
                mWebView.setWebViewClient(new MapWebViewClient());
                mWebView.loadUrl(MAP_URL);
                mWebView.addJavascriptInterface(new MapJsiImpl(), MAP_JSI_NAME);
            }
        });
    }

    public void onHomeClick(View v) {
        UIUtils.goHome(this);
    }

    public void onRefreshClick(View v) {
        mWebView.reload();
    }

    public void onSearchClick(View v) {
        UIUtils.goSearch(this);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && mWebView.canGoBack()) {
            mWebView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void showLoading(boolean loading) {
        if (mLoadingVisible == loading)
            return;

        View refreshButton = findViewById(R.id.btn_title_refresh);
        View refreshProgress = findViewById(R.id.title_refresh_progress);
        refreshButton.setVisibility(loading ? View.GONE : View.VISIBLE);
        refreshProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        mLoadingVisible = loading;
    }

    private void runJs(String js) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Loading javascript:" + js);
        }
        mWebView.loadUrl("javascript:" + js);
    }

    private class MapWebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            showLoading(newProgress < 100);
            super.onProgressChanged(view, newProgress);
        }

        public void onConsoleMessage(String message, int lineNumber, String sourceID) {
            Log.i(TAG, "JS Console message: (" + sourceID + ": " + lineNumber + ") " + message);
        }
    }

    private class MapWebViewClient extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Page finished loading: " + url);
            }
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description,
                String failingUrl) {
            Log.e(TAG, "Error " + errorCode + ": " + description);
            Toast.makeText(view.getContext(), "Error " + errorCode + ": " + description,
                    Toast.LENGTH_LONG).show();
            super.onReceivedError(view, errorCode, description, failingUrl);
        }
    }

    /**
     * Helper method to escape JavaScript strings. Useful when passing strings to a WebView
     * via "javascript:" calls.
     */
    private static String escapeJsString(String s) {
        if (s == null)
            return "";

        return s.replace("'", "\\'").replace("\"", "\\\"");
    }

    /**
     * I/O Conference Map JavaScript interface.
     */
    private interface MapJsi {
        void openContentInfo(String test);
        void onMapReady();
    }

    private class MapJsiImpl implements MapJsi {
        public void openContentInfo(String roomId) {
            final String possibleTrackId = ParserUtils.translateTrackIdAlias(roomId);
            if (OFFICE_HOURS_ROOM_ID.equals(roomId)) {
                // The office hours room was requested.
                Uri officeHoursUri = Sessions.buildSearchUri("office hours");
                Intent officeHoursIntent = new Intent(Intent.ACTION_VIEW, officeHoursUri);
                startActivity(officeHoursIntent);
            } else if (ParserUtils.LOCAL_TRACK_IDS.contains(possibleTrackId)) {
                // This is a track; open up the sandbox for the track, since room IDs that are
                // track IDs are sandbox areas in the map.
                Uri trackVendorsUri = Tracks.buildTrackUri(possibleTrackId);
                Intent trackVendorsIntent = new Intent(Intent.ACTION_VIEW, trackVendorsUri);
                trackVendorsIntent.putExtra(TrackDetailActivity.EXTRA_FOCUS_TAG,
                        TrackDetailActivity.TAG_VENDORS);
                startActivity(trackVendorsIntent);
            } else {
                Uri roomUri = Rooms.buildSessionsDirUri(roomId);
                Intent roomIntent = new Intent(Intent.ACTION_VIEW, roomUri);
                startActivity(roomIntent);
            }
        }

        public void onMapReady() {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onMapReady");
            }

            String showRoomId = null;
            if (!mMapInitialized && getIntent().hasExtra(EXTRA_ROOM)) {
                showRoomId = getIntent().getStringExtra(EXTRA_ROOM);
            }

            if (showRoomId != null) {
                runJs("googleIo.showLocationById('" +
                        escapeJsString(showRoomId) + "');");
            }

            mMapInitialized = true;
        }
    }
}
