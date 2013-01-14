/*
 * Copyright (C) 2012 The MoKee Android Open Source Project
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

package com.mokee.notepad;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.mokee.notepad.NotePad.NoteColumns;

/**
 * Displays a list of notes. Will display notes from the {@link Uri} provided in
 * the intent if there is one, otherwise defaults to displaying the contents of
 * the {@link NoteProvider}
 */
public class NotesList extends ListActivity {
    private static final String TAG = "NotesList";

    /**
     * The columns we are interested in from the database
     */
    private static final String[] PROJECTION = new String[] {
            NoteColumns._ID,
            NoteColumns.TITLE,
            NoteColumns.MODIFIED_DATE,
    };

    /** The index of the title column */
    private static final int COLUMN_INDEX_TITLE = 1;

    /** The dilog index */
    private static final int SURE_TO_DELETE = 0;
    private Uri noteUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        // If no data was given in the intent (because we were started
        // as a MAIN activity), then use our default content provider.
        Intent intent = getIntent();

        if (intent.getAction().equals(Intent.ACTION_CREATE_SHORTCUT)) {
            setupShortcuts();
            finish();
            return;
        }

        if (intent.getData() == null) {
            intent.setData(NoteColumns.CONTENT_URI);
        }

        getListView().setOnCreateContextMenuListener(this);

        @SuppressWarnings("deprecation")
        Cursor cursor = managedQuery(getIntent().getData(), PROJECTION, null, null,
                NoteColumns.DEFAULT_SORT_ORDER);

        NotesListSimpleCursorAdapter adapter = new NotesListSimpleCursorAdapter(this,
                R.layout.noteslist_item, cursor, new String[] {
                        NoteColumns.TITLE, NoteColumns.MODIFIED_DATE
                }, new int[] {
                        R.id.title, R.id.datetime
                });
        setListAdapter(adapter);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.new_note, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch(id) {
            case R.id.new_note:
                startActivity(new Intent(Intent.ACTION_INSERT, getIntent().getData()));
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        Cursor cursor = (Cursor) getListAdapter().getItem(info.position);
        if (cursor == null) {
            // For some reason the requested item isn't available, do nothing
            return;
        }

        // Inflate menu from XML resource
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_context_menu, menu);

        // Set the context menu header
        menu.setHeaderTitle(cursor.getString(COLUMN_INDEX_TITLE));

        // Append to the
        // menu items for any other activities that can do stuff with it
        // as well. This does a query on the system for any activities that
        // implement the ALTERNATIVE_ACTION for our data, adding a menu item
        // for each one that is found.
        Intent intent = new Intent(null, Uri.withAppendedPath(getIntent().getData(),
                Integer.toString((int) info.id)));
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return false;
        }

        noteUri = ContentUris.withAppendedId(getIntent().getData(), info.id);

        switch (item.getItemId()) {
            case R.id.context_delete:
                // Delete the note that the context menu is for
                // getContentResolver().delete(noteUri, null, null);
                showDialog(SURE_TO_DELETE);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Uri noteUri = ContentUris.withAppendedId(getIntent().getData(), id);

        String action = getIntent().getAction();
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
            // The caller is waiting for us to return a note selected by
            // the user. The have clicked on one, so return it now.
            setResult(RESULT_OK, new Intent().setData(noteUri));
        } else {
            // Launch activity to view/edit the currently selected item
            startActivity(new Intent(Intent.ACTION_EDIT, noteUri));
        }
    }

    private void setupShortcuts() {
        Intent shortcutintent = new Intent(Intent.ACTION_MAIN);
        shortcutintent.setClassName(this, this.getClass().getName());

        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutintent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.app_name));
        Parcelable shortcutIconResource = Intent.ShortcutIconResource.fromContext(this,
                R.drawable.app_notes);
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, shortcutIconResource);

        setResult(RESULT_OK, intent);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case SURE_TO_DELETE:
                return new AlertDialog.Builder(NotesList.this)
                        .setIcon(R.drawable.alert_dialog_icon)
                        .setTitle(R.string.sure_to_delete)
                        .setPositiveButton(R.string.dialog_ok,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        getContentResolver().delete(noteUri, null, null);

                                    }
                                })
                        .setNegativeButton(R.string.dialog_no, null)
                        .create();

            default:
                break;
        }
        return null;
    }

    private class NotesListSimpleCursorAdapter extends SimpleCursorAdapter {
        private ViewBinder mViewBinder;
        protected int[] mFrom;
        protected int[] mTo;

        @SuppressWarnings("deprecation")
        public NotesListSimpleCursorAdapter(Context context, int layout, Cursor c, String[] from,
                int[] to) {

            super(context, layout, c, from, to);
            mTo = to;
            if (c != null) {
                int i;
                int count = from.length;
                if (mFrom == null || mFrom.length != count) {
                    mFrom = new int[count];
                }
                for (i = 0; i < count; i++) {
                    mFrom[i] = c.getColumnIndexOrThrow(from[i]);
                }
            } else {
                mFrom = null;
            }

        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {

            final ViewBinder binder = mViewBinder;
            final int[] from = mFrom;
            final int[] to = mTo;
            final int count = mTo.length;

            for (int i = 0; i < count; i++) {
                final View v = view.findViewById(to[i]);
                if (v != null) {
                    boolean bound = false;
                    if (binder != null) {
                        bound = binder.setViewValue(v, cursor, from[i]);
                    }

                    if (!bound) {
                        String text = cursor.getString(from[i]);
                        if (i == 1) {
                            long textTemp = cursor.getLong(from[i]);
                            if (text != null) {
                                text = String
                                        .valueOf(DateUtils.getRelativeTimeSpanString(textTemp));
                            }
                        }
                        if (text == null) {
                            text = "";
                        }

                        if (v instanceof TextView) {
                            setViewText((TextView) v, text);
                        } else if (v instanceof ImageView) {
                            setViewImage((ImageView) v, text);
                        } else {
                            throw new IllegalStateException(v.getClass().getName() + " is not a " +
                                    " view that can be bounds by this SimpleCursorAdapter");
                        }
                    }
                }
            }
        }
    }
}
