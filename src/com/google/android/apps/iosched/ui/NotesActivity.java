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
import com.google.android.apps.iosched.provider.ScheduleContract.Notes;
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
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 * {@link ListActivity} that displays a set of {@link Notes}, as requested
 * through {@link Intent#getData()}.
 */
public class NotesActivity extends ListActivity implements AsyncQueryListener {

    public static final String EXTRA_SHOW_INSERT = "com.google.android.iosched.extra.SHOW_INSERT";

    private NotesAdapter mAdapter;

    private boolean mShowInsert = false;

    private NotifyingAsyncQueryHandler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!getIntent().hasCategory(Intent.CATEGORY_TAB)) {
            setContentView(R.layout.activity_notes);
            ((TextView) findViewById(R.id.title_text)).setText(getTitle());

        } else {
            setContentView(R.layout.activity_notes_content);
        }

        mShowInsert = getIntent().getBooleanExtra(EXTRA_SHOW_INSERT, false);
        if (mShowInsert) {
            final ListView listView = getListView();
            final View view = getLayoutInflater().inflate(R.layout.list_item_note_create,
                    listView, false);
            listView.addHeaderView(view, null, true);
        }

        mAdapter = new NotesAdapter(this);
        setListAdapter(mAdapter);

        final Uri notesUri = getIntent().getData();

        // Start background query to load notes
        mHandler = new NotifyingAsyncQueryHandler(getContentResolver(), this);
        mHandler.startQuery(notesUri, NotesQuery.PROJECTION, Notes.DEFAULT_SORT);
    }

    /** {@inheritDoc} */
    public void onQueryComplete(int token, Object cookie, Cursor cursor) {
        startManagingCursor(cursor);
        mAdapter.changeCursor(cursor);
    }

    /** {@inheritDoc} */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        if (id >= 0) {
            // Edit an existing note
            final Uri noteUri = Notes.buildNoteUri(id);
            startActivity(new Intent(Intent.ACTION_EDIT, noteUri));

        } else {
            // Insert new note
            final Uri notesDirUri = getIntent().getData();
            startActivity(new Intent(Intent.ACTION_INSERT, notesDirUri));
        }
    }

    /** Handle "home" title-bar action. */
    public void onHomeClick(View v) {
        UIUtils.goHome(this);
    }

    /** Handle "share" title-bar action. */
    public void onShareClick(View v) {
        final String shareText = getString(R.string.share_notes);

        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("plain/html");
        intent.putExtra(Intent.EXTRA_SUBJECT, shareText);
        intent.putExtra(Intent.EXTRA_TEXT, shareText);
        intent.putExtra(Intent.EXTRA_STREAM, Notes.CONTENT_EXPORT_URI);

        startActivity(Intent.createChooser(intent, getText(R.string.title_share)));
    }

    /** Handle "search" title-bar action. */
    public void onSearchClick(View v) {
        UIUtils.goSearch(this);
    }

    /**
     * {@link CursorAdapter} that renders a {@link NotesQuery}.
     */
    private class NotesAdapter extends CursorAdapter {
        public NotesAdapter(Context context) {
            super(context, null);
        }

        /** {@inheritDoc} */
        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return getLayoutInflater().inflate(R.layout.list_item_note, parent, false);
        }

        /** {@inheritDoc} */
        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            // TODO: format notes with better layout
            ((TextView)view.findViewById(R.id.note_content)).setText(cursor
                    .getString(NotesQuery.NOTE_CONTENT));

            // TODO: format using note_before/into/after
            final long time = cursor.getLong(NotesQuery.NOTE_TIME);
            final CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(time);
            ((TextView) view.findViewById(R.id.note_time)).setText(relativeTime);
        }

        @Override
        public boolean isEmpty() {
            return mShowInsert ? false : super.isEmpty();
        }
    }

    /** {@link Notes} query parameters. */
    private interface NotesQuery {
        String[] PROJECTION = {
                BaseColumns._ID,
                Notes.NOTE_TIME,
                Notes.NOTE_CONTENT,
        };

        int _ID = 0;
        int NOTE_TIME = 1;
        int NOTE_CONTENT = 2;
    }
}
