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
import com.google.android.apps.iosched.provider.ScheduleContract.Rooms;
import com.google.android.apps.iosched.provider.ScheduleContract.Sessions;
import com.google.android.apps.iosched.provider.ScheduleContract.Speakers;
import com.google.android.apps.iosched.provider.ScheduleContract.Tracks;
import com.google.android.apps.iosched.provider.ScheduleContract.Vendors;
import com.google.android.apps.iosched.service.SyncService;
import com.google.android.apps.iosched.util.FractionalTouchDelegate;
import com.google.android.apps.iosched.util.NotifyingAsyncQueryHandler;
import com.google.android.apps.iosched.util.UIUtils;
import com.google.android.apps.iosched.util.NotifyingAsyncQueryHandler.AsyncQueryListener;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TabActivity;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.CompoundButton.OnCheckedChangeListener;

/**
 * {@link Activity} that displays details about a specific
 * {@link Sessions#SESSION_ID}, as requested through {@link Intent#getData()}.
 */
public class SessionDetailActivity extends TabActivity implements AsyncQueryListener,
        OnCheckedChangeListener {
    private static final String TAG = "SessionDetailActivity";

    /**
     * Since {@link Sessions} can belong to multiple {@link Tracks}, the parent
     * {@link Activity} can send this extra specifying a {@link Tracks}
     * {@link Uri} that should be used for coloring the title-bar.
     */
    public static final String EXTRA_TRACK = "com.google.android.iosched.extra.TRACK";

    private static final String MODERATOR_PACKAGE = "com.google.android.apps.moderator";

    private static final String TAG_SUMMARY = "summary";
    private static final String TAG_NOTES = "notes";
    private static final String TAG_MODERATOR = "moderator";

    private String mSessionId;
    private Uri mSessionUri;

    private String mTitleString;
    private String mHashtag;
    private String mRoomId;

    private TextView mTitle;
    private TextView mSubtitle;
    private CompoundButton mStarred;

    private TextView mAbstract;
    private TextView mRequirements;

    private NotifyingAsyncQueryHandler mHandler;

    private boolean mSessionCursor = false;
    private boolean mSpeakersCursor = false;
    private boolean mHasSummaryContent = false;

    private Uri mModeratorUri;
    private Uri mWaveUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_detail);

        mTitle = (TextView) findViewById(R.id.session_title);
        mSubtitle = (TextView) findViewById(R.id.session_subtitle);
        mStarred = (CompoundButton) findViewById(R.id.star_button);

        mStarred.setFocusable(true);
        mStarred.setClickable(true);

        // Larger target triggers star toggle
        final View starParent = findViewById(R.id.list_item_session);
        FractionalTouchDelegate.setupDelegate(starParent, mStarred, new RectF(0.6f, 0f, 1f, 0.8f));

        mAbstract = (TextView) findViewById(R.id.session_abstract);
        mRequirements = (TextView) findViewById(R.id.session_requirements);

        final Intent intent = getIntent();
        mSessionUri = intent.getData();
        mSessionId = Sessions.getSessionId(mSessionUri);

        setupSummaryTab();
        setupNotesTab();

        // Start background queries to load session and track details
        final Uri trackUri = resolveTrackUri(intent);
        final Uri speakersUri = Sessions.buildSpeakersDirUri(mSessionId);

        mHandler = new NotifyingAsyncQueryHandler(getContentResolver(), this);
        mHandler.startQuery(SessionsQuery._TOKEN, mSessionUri, SessionsQuery.PROJECTION);
        mHandler.startQuery(TracksQuery._TOKEN, trackUri, TracksQuery.PROJECTION);
        mHandler.startQuery(SpeakersQuery._TOKEN, speakersUri, SpeakersQuery.PROJECTION);
    }

    /** Build and add "summary" tab. */
    private void setupSummaryTab() {
        final TabHost host = getTabHost();

        // Summary content comes from existing layout
        host.addTab(host.newTabSpec(TAG_SUMMARY)
                .setIndicator(buildIndicator(R.string.session_summary))
                .setContent(R.id.tab_session_summary));
    }

    /** Build and add "notes" tab. */
    private void setupNotesTab() {
        final TabHost host = getTabHost();

        final Uri notesUri = Sessions.buildNotesDirUri(mSessionId);
        final Intent intent = new Intent(Intent.ACTION_VIEW, notesUri);
        intent.addCategory(Intent.CATEGORY_TAB);
        intent.putExtra(NotesActivity.EXTRA_SHOW_INSERT, true);

        // Notes content comes from reused activity
        host.addTab(host.newTabSpec(TAG_NOTES)
                .setIndicator(buildIndicator(R.string.session_notes))
                .setContent(intent));
    }

    /** Build and add "moderator" tab. */
    private void setupModeratorTab(Cursor sessionsCursor) {
        final TabHost host = getTabHost();

        // Insert Moderator when available
        final View moderatorBlock = findViewById(R.id.moderator_block);
        final String moderatorLink = sessionsCursor.getString(SessionsQuery.MODERATOR_LINK);
        final boolean validModerator = !TextUtils.isEmpty(moderatorLink);
        if (validModerator) {
            mModeratorUri = Uri.parse(moderatorLink);

            // Set link, but handle clicks manually
            final TextView textView = (TextView) findViewById(R.id.moderator_link);
            textView.setText(mModeratorUri.toString());
            textView.setMovementMethod(null);
            textView.setClickable(true);
            textView.setFocusable(true);

            // Start background fetch of moderator status
            startModeratorStatusFetch(moderatorLink);

            moderatorBlock.setVisibility(View.VISIBLE);
        } else {
            moderatorBlock.setVisibility(View.GONE);
        }

        // Insert Wave when available
        final View waveBlock = findViewById(R.id.wave_block);
        final String waveLink = sessionsCursor.getString(SessionsQuery.WAVE_LINK);
        final boolean validWave = !TextUtils.isEmpty(waveLink);
        if (validWave) {
            // Rewrite incoming Wave URL to punch through user-agent check
            mWaveUri = Uri.parse(waveLink).buildUpon()
                    .appendQueryParameter("nouacheck", "1").build();

            // Set link, but handle clicks manually
            final TextView textView = (TextView) findViewById(R.id.wave_link);
            textView.setText(mWaveUri.toString());
            textView.setMovementMethod(null);
            textView.setClickable(true);
            textView.setFocusable(true);

            waveBlock.setVisibility(View.VISIBLE);
        } else {
            waveBlock.setVisibility(View.GONE);
        }

        if (validModerator || validWave) {
            // Moderator content comes from existing layout
            host.addTab(host.newTabSpec(TAG_MODERATOR)
                    .setIndicator(buildIndicator(R.string.session_interact))
                    .setContent(R.id.tab_session_moderator));
        }
    }

    /** Handle Moderator link. */
    public void onModeratorClick(View v) {
        boolean appPresent = false;
        try {
            final PackageManager pm = getPackageManager();
            final ApplicationInfo ai = pm.getApplicationInfo(MODERATOR_PACKAGE, 0);
            if (ai != null) appPresent = true;
        } catch (NameNotFoundException e) {
            Log.w(TAG, "Problem searching for moderator: " + e.toString());
        }

        if (appPresent) {
            // Directly launch intent when already installed
            startModerator();
        } else {
            // Otherwise suggest installing app from Market
            showDialog(R.id.dialog_moderator);
        }
    }

    /** Handle Wave link. */
    public void onWaveClick(View v) {
        showDialog(R.id.dialog_wave);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case R.id.dialog_moderator: {
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.dialog_moderator_title)
                        .setIcon(android.R.drawable.ic_dialog_info)
                        .setMessage(R.string.dialog_moderator_message)
                        .setNegativeButton(R.string.dialog_moderator_market,
                                new ModeratorMarketClickListener())
                        .setPositiveButton(R.string.dialog_moderator_web,
                                new ModeratorStartClickListener())
                        .setCancelable(true)
                        .create();
            }
            case R.id.dialog_wave: {
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.dialog_wave_title)
                        .setIcon(android.R.drawable.ic_dialog_info)
                        .setMessage(R.string.dialog_wave_message)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, new WaveConfirmClickListener())
                        .setCancelable(true)
                        .create();
            }
        }
        return null;
    }

    private class ModeratorStartClickListener implements DialogInterface.OnClickListener {
        public void onClick(DialogInterface dialog, int which) {
            startModerator();
        }
    }

    private class ModeratorMarketClickListener implements DialogInterface.OnClickListener {
        public void onClick(DialogInterface dialog, int which) {
            final Uri marketUri = Uri.parse("http://market.android.com/search?q=pname:"
                    + MODERATOR_PACKAGE);
            startActivity(new Intent(Intent.ACTION_VIEW, marketUri));
        }
    }

    private class WaveConfirmClickListener implements DialogInterface.OnClickListener {
        public void onClick(DialogInterface dialog, int which) {
            startWave();
        }
    }

    private void startModerator() {
        startActivity(new Intent(Intent.ACTION_VIEW, mModeratorUri));
    }

    private void startWave() {
        startActivity(new Intent(Intent.ACTION_VIEW, mWaveUri));
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

    /**
     * Derive {@link Tracks#CONTENT_ITEM_TYPE} {@link Uri} based on incoming
     * {@link Intent}, using {@link #EXTRA_TRACK} when set.
     */
    private Uri resolveTrackUri(Intent intent) {
        final Uri trackUri = intent.getParcelableExtra(EXTRA_TRACK);
        if (trackUri != null) {
            return trackUri;
        } else {
            return Sessions.buildTracksDirUri(mSessionId);
        }
    }

    /** {@inheritDoc} */
    public void onQueryComplete(int token, Object cookie, Cursor cursor) {
        if (token == SessionsQuery._TOKEN) {
            onSessionQueryComplete(cursor);
        } else if (token == TracksQuery._TOKEN) {
            onTrackQueryComplete(cursor);
        } else if (token == SpeakersQuery._TOKEN) {
            onSpeakersQueryComplete(cursor);
        } else {
            cursor.close();
        }
    }

    /** Handle {@link SessionsQuery} {@link Cursor}. */
    private void onSessionQueryComplete(Cursor cursor) {
        try {
            mSessionCursor = true;
            if (!cursor.moveToFirst()) return;

            // Format time block this session occupies
            final long blockStart = cursor.getLong(SessionsQuery.BLOCK_START);
            final long blockEnd = cursor.getLong(SessionsQuery.BLOCK_END);
            final String roomName = cursor.getString(SessionsQuery.ROOM_NAME);
            final String subtitle = UIUtils.formatSessionSubtitle(blockStart,
                    blockEnd, roomName, this);

            mTitleString = cursor.getString(SessionsQuery.TITLE);
            mTitle.setText(mTitleString);
            mSubtitle.setText(subtitle);

            mHashtag = cursor.getString(SessionsQuery.HASHTAG);
            if (TextUtils.isEmpty(mHashtag)) mHashtag = "";

            mRoomId = cursor.getString(SessionsQuery.ROOM_ID);

            // Unregister around setting checked state to avoid triggering
            // listener since change isn't user generated.
            mStarred.setOnCheckedChangeListener(null);
            mStarred.setChecked(cursor.getInt(SessionsQuery.STARRED) != 0);
            mStarred.setOnCheckedChangeListener(this);

            final String sessionAbstract = cursor.getString(SessionsQuery.ABSTRACT);
            if (!TextUtils.isEmpty(sessionAbstract)) {
                UIUtils.setTextMaybeHtml(mAbstract, sessionAbstract);
                mAbstract.setVisibility(View.VISIBLE);
                mHasSummaryContent = true;
            } else {
                mAbstract.setVisibility(View.GONE);
            }

            final View requirementsBlock = findViewById(R.id.session_requirements_block);
            final String sessionRequirements = cursor.getString(SessionsQuery.REQUIREMENTS);
            if (!TextUtils.isEmpty(sessionRequirements)) {
                UIUtils.setTextMaybeHtml(mRequirements, sessionRequirements);
                requirementsBlock.setVisibility(View.VISIBLE);
                mHasSummaryContent = true;
            } else {
                requirementsBlock.setVisibility(View.GONE);
            }

            setupModeratorTab(cursor);

            // Show empty message when all data is loaded, and nothing to show
            if (mSpeakersCursor && !mHasSummaryContent) {
                findViewById(android.R.id.empty).setVisibility(View.VISIBLE);
            }
        } finally {
            cursor.close();
        }
    }

    /** Handle {@link TracksQuery} {@link Cursor}. */
    private void onTrackQueryComplete(Cursor cursor) {
        try {
            if (!cursor.moveToFirst()) return;

            // Use found track to build title-bar
            ((TextView) findViewById(R.id.title_text)).setText(cursor
                    .getString(TracksQuery.TRACK_NAME));
            UIUtils.setTitleBarColor(findViewById(R.id.title_container),
                    cursor.getInt(TracksQuery.TRACK_COLOR));
        } finally {
            cursor.close();
        }
    }

    private void onSpeakersQueryComplete(Cursor cursor) {
        try {
            mSpeakersCursor = true;

            // TODO: remove any existing speakers from layout, since this cursor
            // might be from a data change notification.
            final ViewGroup speakersGroup = (ViewGroup) findViewById(R.id.session_speakers_block);
            final LayoutInflater inflater = getLayoutInflater();

            boolean hasSpeakers = false;

            while (cursor.moveToNext()) {
                final String speakerName = cursor.getString(SpeakersQuery.SPEAKER_NAME);
                final String speakerCompany = cursor.getString(SpeakersQuery.SPEAKER_COMPANY);
                if (TextUtils.isEmpty(speakerName)) continue;

                final View speakerView = inflater.inflate(R.layout.speaker_detail,
                        speakersGroup, false);

                final String speaker = getString(R.string.speaker_template, speakerName,
                        speakerCompany);
                ((TextView) speakerView.findViewById(R.id.speaker_header)).setText(speaker);

                final String speakerAbstract = cursor.getString(SpeakersQuery.SPEAKER_ABSTRACT);
                final TextView abstractView = (TextView) speakerView
                        .findViewById(R.id.speaker_abstract);
                UIUtils.setTextMaybeHtml(abstractView, speakerAbstract);

                speakersGroup.addView(speakerView);
                hasSpeakers = true;
                mHasSummaryContent = true;
            }

            speakersGroup.setVisibility(hasSpeakers ? View.VISIBLE : View.GONE);

            // Show empty message when all data is loaded, and nothing to show
            if (mSessionCursor && !mHasSummaryContent) {
                findViewById(android.R.id.empty).setVisibility(View.VISIBLE);
            }

        } finally {
            cursor.close();
        }
    }

    /** Handle "home" title-bar action. */
    public void onHomeClick(View v) {
        UIUtils.goHome(this);
    }

    /** Handle "share" title-bar action. */
    public void onShareClick(View v) {
        // TODO: consider bringing in shortlink to session
        final String shareString = getString(R.string.share_template, mTitleString, mHashtag);

        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, shareString);

        startActivity(Intent.createChooser(intent, getText(R.string.title_share)));
    }

    /** Handle "map" title-bar action. */
    public void onMapClick(View v) {
        final Intent intent = new Intent(this, MapActivity.class);
        if (mRoomId != null && mRoomId.startsWith("officehours")) {
            intent.putExtra(MapActivity.EXTRA_ROOM, MapActivity.OFFICE_HOURS_ROOM_ID);
        } else {
            intent.putExtra(MapActivity.EXTRA_ROOM, mRoomId);
        }
        startActivity(intent);
    }

    /** Handle "search" title-bar action. */
    public void onSearchClick(View v) {
        UIUtils.goSearch(this);
    }

    /** Handle toggling of starred checkbox. */
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        final ContentValues values = new ContentValues();
        values.put(Vendors.STARRED, isChecked ? 1 : 0);
        mHandler.startUpdate(mSessionUri, values);
    }

    private static HttpClient sHttpClient;

    private static synchronized HttpClient getHttpClient(Context context) {
        if (sHttpClient == null) {
            sHttpClient = SyncService.getHttpClient(context);
        }
        return sHttpClient;
    }

    private void startModeratorStatusFetch(String moderatorLink) {
        try {
            // Extract series and topic from moderator link
            // NOTE: this makes some ugly assumptions that the "t=" parameter
            // occurs at the end of the URL.
            final String clause = moderatorLink.substring(moderatorLink.indexOf("t=") + 2);

            final int dotIndex = clause.indexOf('.');
            final int seriesId = Integer.parseInt(clause.substring(0, dotIndex), 16);
            final int topicId = Integer.parseInt(clause.substring(dotIndex + 1), 16);

            // Kick off background request to find current status
            final String remoteUrl = "http://www.googleapis.com/moderator/v1/series/" + seriesId
                    + "/topics/" + topicId;
            new ModeratorStatusTask().execute(remoteUrl);

        } catch (Exception e) {
            Log.w(TAG, "Problem while parsing Moderator URL", e);
        }
    }

    private class ModeratorStatusTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            final String param = params[0];

            try {
                final Context context = SessionDetailActivity.this;
                final HttpClient httpClient = getHttpClient(context);
                final HttpResponse resp = httpClient.execute(new HttpGet(param));
                final HttpEntity entity = resp.getEntity();

                final int statusCode = resp.getStatusLine().getStatusCode();
                if (statusCode != HttpStatus.SC_OK || entity == null) return null;

                final String respString = EntityUtils.toString(entity);
                final JSONObject respJson = new JSONObject(respString);

                final JSONObject data = respJson.getJSONObject("data");
                final JSONObject counters = respJson.getJSONObject("counters");

                final int questions = counters.getInt("submissions");
                final int votes = counters.getInt("noteVotes") + counters.getInt("plusVotes")
                        + counters.getInt("minusVotes");

                return getString(R.string.session_moderator_status, questions, votes);
            } catch(Exception e) {
                Log.w(TAG, "Problem while loading Moderator status: " + e.toString());
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            final TextView status = (TextView) findViewById(R.id.moderator_status);
            if (result == null) {
                status.setVisibility(View.GONE);
            } else {
                status.setVisibility(View.VISIBLE);
                status.setText(result);
            }
        }
    }

    /** {@link Sessions} query parameters. */
    private interface SessionsQuery {
        int _TOKEN = 0x1;

        String[] PROJECTION = {
                Blocks.BLOCK_START,
                Blocks.BLOCK_END,
                Sessions.TYPE,
                Sessions.TITLE,
                Sessions.ABSTRACT,
                Sessions.REQUIREMENTS,
                Sessions.STARRED,
                Sessions.MODERATOR_URL,
                Sessions.WAVE_URL,
                Sessions.ROOM_ID,
                Sessions.HASHTAG,
                Rooms.ROOM_NAME,
        };

        int BLOCK_START = 0;
        int BLOCK_END = 1;
        int TYPE = 2;
        int TITLE = 3;
        int ABSTRACT = 4;
        int REQUIREMENTS = 5;
        int STARRED = 6;
        int MODERATOR_LINK = 7;
        int WAVE_LINK = 8;
        int ROOM_ID = 9;
        int HASHTAG = 10;
        int ROOM_NAME = 11;
    }

    /** {@link Tracks} query parameters. */
    private interface TracksQuery {
        int _TOKEN = 0x2;

        String[] PROJECTION = {
                Tracks.TRACK_NAME,
                Tracks.TRACK_COLOR,
        };

        int TRACK_NAME = 0;
        int TRACK_COLOR = 1;
    }

    private interface SpeakersQuery {
        int _TOKEN = 0x3;

        String[] PROJECTION = {
                Speakers.SPEAKER_NAME,
                Speakers.SPEAKER_COMPANY,
                Speakers.SPEAKER_ABSTRACT,
        };

        int SPEAKER_NAME = 0;
        int SPEAKER_COMPANY = 1;
        int SPEAKER_ABSTRACT = 2;
    }
}
