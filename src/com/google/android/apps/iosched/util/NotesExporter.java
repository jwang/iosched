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

package com.google.android.apps.iosched.util;

import com.google.android.apps.iosched.provider.ScheduleContract.Notes;

import org.xmlpull.v1.XmlSerializer;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.format.Time;
import android.util.Xml;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Simple exporter that writes {@link Notes} contents into a {@link File} using
 * {@link Xml#newSerializer()}.
 */
public class NotesExporter {

    public static File writeExportedNotes(Context context) throws IOException {
        // TODO: allow customization by accepting dir uri and output file
        final ContentResolver resolver = context.getContentResolver();
        final File notesFile = context.getFileStreamPath("notes.xml");
        final BufferedWriter out = new BufferedWriter(new FileWriter(notesFile));
        final XmlSerializer serializer = Xml.newSerializer();

        serializer.setOutput(out);
        serializer.startDocument("UTF-8", true);
        serializer.startTag("", Tags.NOTES);

        final Time time = new Time();

        final Uri notesUri = Notes.CONTENT_URI;
        final Cursor cursor = resolver.query(notesUri, NotesQuery.PROJECTION, null, null,
                Notes.DEFAULT_SORT);
        try {
            while (cursor.moveToNext()) {
                serializer.startTag("", Tags.NOTE);
                {
                    serializer.startTag("", Tags.SESSION_ID);
                    serializer.text(cursor.getString(NotesQuery.SESSION_ID));
                    serializer.endTag("", Tags.SESSION_ID);
                }
                {
                    serializer.startTag("", Tags.TIME);
                    time.set(cursor.getLong(NotesQuery.NOTE_TIME));
                    final String timeString = time.format3339(false);
                    serializer.text(timeString);
                    serializer.endTag("", Tags.TIME);
                }
                {
                    serializer.startTag("", Tags.CONTENT);
                    serializer.text(cursor.getString(NotesQuery.NOTE_CONTENT));
                    serializer.endTag("", Tags.CONTENT);
                }
                serializer.endTag("", Tags.NOTE);
            }
        } finally {
            cursor.close();
        }

        serializer.endTag("", Tags.NOTES);
        serializer.endDocument();

        out.flush();
        out.close();

        return notesFile;
    }

    private interface Tags {
        String NOTES = "notes";
        String NOTE = "note";
        String SESSION_ID = "sessionId";
        String CONTENT = "content";
        String TIME = "time";
    }

    private interface NotesQuery {
        String[] PROJECTION = {
                Notes.SESSION_ID,
                Notes.NOTE_CONTENT,
                Notes.NOTE_TIME,
        };

        int SESSION_ID = 0;
        int NOTE_CONTENT = 1;
        int NOTE_TIME = 2;
    }
}
