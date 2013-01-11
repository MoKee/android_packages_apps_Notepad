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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.util.Linkify;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;

import com.mokee.notepad.NotePad.NoteColumns;

/**
 * A generic activity for editing a note in a database. This can be used either
 * to simply view a note {@link Intent#ACTION_VIEW}, view and edit a note
 * {@link Intent#ACTION_EDIT}, or create a new note {@link Intent#ACTION_INSERT}
 * .
 */
public class NoteEditor extends Activity {
    private static final String TAG = "NoteEditor";
    private Boolean cancelModify = false;

    /**
     * Standard projection for the interesting columns of a normal note.
     */
    private static final String[] PROJECTION = new String[] {
            NoteColumns._ID, // 0
            NoteColumns.NOTE, // 1
            NoteColumns.TITLE, // 2
    };
    /** The index of the note column */
    private static final int COLUMN_INDEX_NOTE = 1;
    /** The index of the title column */
    private static final int COLUMN_INDEX_TITLE = 2;
    private static final int IS_TO_SAVE = 0;

    // This is our state data that is stored when freezing.
    private static final String ORIGINAL_CONTENT = "origContent";
    private static final String ORIGINAL_TITLE = "origTitle";

    // The different distinct states the activity can be run in.
    private static final int STATE_EDIT = 0;
    private static final int STATE_INSERT = 1;

    private static final int MAXTITLESUM = 9;

    private int mState;
    private Uri mUri;
    private Cursor mCursor;
    private EditText mText;
    private String mOriginalContent;
    private String mOriginalTitle;
    private EditText mTitleEditText;
    private ImageButton mSaveImageButton;

    /**
     * A custom EditText that draws lines between each line of text that is
     * displayed.
     */
    public static class LinedEditText extends EditText {
        private Rect mRect;
        private Paint mPaint;

        public LinedEditText(Context context, AttributeSet attrs) {
            super(context, attrs);

            mRect = new Rect();
            mPaint = new Paint();
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(Color.BLACK);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int count = getLineCount();
            Rect r = mRect;
            Paint paint = mPaint;

            for (int i = 0; i < count; i++) {
                int baseline = getLineBounds(i, r);
                // canvas.drawLine(r.left, baseline + 2, r.right, baseline + 2,
                // paint);
                canvas.drawLine(r.left, baseline + 17, r.right, baseline + 17, paint);
            }
            super.onDraw(canvas);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);

        final Intent intent = getIntent();

        // Do some setup based on the action being performed.
        final String action = intent.getAction();
        if (Intent.ACTION_EDIT.equals(action)) {
            // Requested to edit: set that state, and the data being edited.
            mState = STATE_EDIT;
            mUri = intent.getData();
        } else if (Intent.ACTION_INSERT.equals(action)) {
            // Requested to insert: set that state, and create a new entry
            // in the container.
            mState = STATE_INSERT;
            mUri = getContentResolver().insert(intent.getData(), null);

            // If we were unable to create a new note, then just finish
            // this activity. A RESULT_CANCELED will be sent back to the
            // original activity if they requested a result.
            if (mUri == null) {
                Log.e(TAG, "Failed to insert new note into " + getIntent().getData());
                finish();
                return;
            }

            // The new entry was created, so assume all will end well and
            // set the result to be returned.
            setResult(RESULT_OK, (new Intent()).setAction(mUri.toString()));

        } else {
            // Whoops, unknown action! Bail.
            Log.e(TAG, "Unknown action, exiting");
            finish();
            return;
        }

        // Set the layout for this activity. You can find it in
        // res/layout/note_editor.xml
        setContentView(R.layout.note_editor);

        // The text view for our note, identified by its ID in the XML file.
        mText = (EditText) findViewById(R.id.note);
        mText.setAutoLinkMask(Linkify.ALL);
        mText.setTextAppearance(getBaseContext(), com.android.internal.R.attr.textAppearanceLarge);
        mText.setTextSize(25);
        mText.setPadding(10, 0, 10, 5);
        mText.setLineSpacing(1.1f, 1.1f);
        // mText.addTextChangedListener(watcher);

        // Get the note!
        @SuppressWarnings("deprecation")
        Cursor managedQuery = managedQuery(mUri, PROJECTION, null, null, null);
        mCursor = managedQuery;

        // If an instance of this activity had previously stopped, we can
        // get the original text it started with.
        if (savedInstanceState != null) {
            mOriginalContent = savedInstanceState.getString(ORIGINAL_CONTENT);
            mOriginalTitle = savedInstanceState.getString(ORIGINAL_TITLE);
        }
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.title_note_editor);
        mTitleEditText = (EditText) findViewById(R.id.edit_title);
        mTitleEditText.setOnClickListener(mTitleEditTextClickListener);
        if (Intent.ACTION_EDIT.equals(action)) {
            mTitleEditText.setInputType(InputType.TYPE_TEXT_VARIATION_NORMAL);
            mTitleEditText.setCursorVisible(false);
        }

        mSaveImageButton = (ImageButton) findViewById(R.id.save_note);
        mSaveImageButton.setOnClickListener(saveNoteClickListener);

    }

    private OnClickListener saveNoteClickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            saveNote();
            finish();
        }
    };

    @SuppressWarnings("deprecation")
    @Override
    protected void onResume() {
        super.onResume();
        // If we didn't have any trouble retrieving the data, it is now
        // time to get at the stuff.
        if (mCursor != null) {
            // Requery in case something changed while paused (such as the
            // title)
            mCursor.requery();
            // Make sure we are at the one and only row in the cursor.
            mCursor.moveToFirst();

            // Modify our overall title depending on the mode we are running in.
            if (mState == STATE_EDIT) {
                // Set the title of the Activity to include the note title
                String title = mCursor.getString(COLUMN_INDEX_TITLE);
                // Resources res = getResources();
                // String text =
                // String.format(res.getString(R.string.title_edit), title);
                // setTitle(text);
                mTitleEditText.setText(title);
            } else if (mState == STATE_INSERT) {
                // setTitle(getText(R.string.title_create));
            }

            // This is a little tricky: we may be resumed after previously being
            // paused/stopped. We want to put the new text in the text view,
            // but leave the user where they were (retain the cursor position
            // etc). This version of setText does that for us.
            String note = mCursor.getString(COLUMN_INDEX_NOTE);
            mText.setTextKeepState(note);

            String title = mCursor.getString(COLUMN_INDEX_TITLE);
            // If we hadn't previously retrieved the original text, do so
            // now. This allows the user to revert their changes.
            if (mOriginalContent == null) {
                mOriginalContent = note;
            }

            if (mOriginalTitle == null) {
                mOriginalTitle = title;
            }

        } else {
            setTitle(getText(R.string.error_title));
            mText.setText(getText(R.string.error_message));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Save away the original text, so we still have it if the activity
        // needs to be killed while paused.
        outState.putString(ORIGINAL_CONTENT, mOriginalContent);
        outState.putString(ORIGINAL_TITLE, mOriginalTitle);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // The user is going somewhere, so make sure changes are saved

        String text = mText.getText().toString();
        int length = text.length();
        String title = mTitleEditText.getText().toString();
        int length2 = title.length();

        // If this activity is finished, and there is no text, then we
        // simply delete the note entry.
        // Note that we do this both for editing and inserting... it
        // would be reasonable to only do it when inserting.
        if (isFinishing() && (length == 0) && (length2 == 0) && mCursor != null) {
            setResult(RESULT_CANCELED);
            deleteNote();
        }
        else if (cancelModify) {
            cancelModify = false;
        }
        else {
            saveNote();
        }
    }

    private final void saveNote() {
        // Make sure their current
        // changes are safely saved away in the provider. We don't need
        // to do this if only editing.
        if (mState == STATE_EDIT
                && mText.getText().toString().equals(mCursor.getString(COLUMN_INDEX_NOTE))
                && mTitleEditText.getText().toString()
                        .equals(mCursor.getString(COLUMN_INDEX_TITLE))) {

        }
        else {
            if (mCursor != null) {
                // Get out updates into the provider.
                ContentValues values = new ContentValues();

                // Bump the modification time to now.
                values.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
                values.put(NoteColumns.TITLE, mTitleEditText.getText().toString());

                String text = mText.getText().toString();
                int length = text.length();
                String titleString = mTitleEditText.getText().toString();
                if (titleString.length() == 0) {
                    String title = text.substring(0, Math.min(MAXTITLESUM, length));
                    values.put(NoteColumns.TITLE, title);
                }

                // Write our text back into the provider.
                values.put(NoteColumns.NOTE, text);

                // Commit all of our changes to persistent storage. When the
                // update completes
                // the content provider will notify the cursor of the change,
                // which will
                // cause the UI to be updated.
                try {
                    getContentResolver().update(mUri, values, null, null);
                } catch (NullPointerException e) {
                    Log.e(TAG, e.getMessage());
                }
            }
        }
    }

    /**
     * Take care of deleting a note. Simply deletes the entry.
     */
    private final void deleteNote() {
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
            getContentResolver().delete(mUri, null, null);
            // mText.setText("");
        }
    }

    private OnClickListener mTitleEditTextClickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            InputMethodManager imm = (InputMethodManager) getBaseContext().getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(getCurrentFocus(), 0);
            mTitleEditText.setInputType(~InputType.TYPE_TEXT_VARIATION_NORMAL);
            mTitleEditText.setCursorVisible(true);
        }
    };

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (mState == STATE_INSERT && mText.getText().length() == 0
                && mTitleEditText.getText().length() == 0) {
            finish();
        }
        else if (mState == STATE_EDIT
                && mText.getText().toString().equals(mCursor.getString(COLUMN_INDEX_NOTE))
                && mTitleEditText.getText().toString()
                        .equals(mCursor.getString(COLUMN_INDEX_TITLE))) {
            finish();
        }
        else {
            showDialog(IS_TO_SAVE);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case IS_TO_SAVE:
                return new AlertDialog.Builder(NoteEditor.this)
                        .setIcon(R.drawable.alert_dialog_icon)
                        .setTitle(R.string.is_to_save)
                        .setPositiveButton(R.string.dialog_ok,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        saveNote();
                                        finish();
                                    }
                                })
                        .setNegativeButton(R.string.dialog_no,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {

                                        if (mState == STATE_INSERT) {
                                            getContentResolver().delete(mUri, null, null);
                                        }
                                        cancelModify = true;
                                        finish();
                                    }
                                })
                        .create();

            default:
                break;
        }
        return null;
    }
}
