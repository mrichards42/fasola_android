/*
 * This file is part of FaSoLa Minutes for Android.
 * Copyright (c) 2016 Mike Richards. All rights reserved.
 */

package org.fasola.fasolaminutes;

import android.database.Cursor;
import android.widget.AlphabetIndexer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A subclass of up AlphabetIndexer that allows setting custom section labels.
 *
 * <p>This is used as a base class for StringIndexer, and is used in IndexedCursorAdapter.
 */
public class LetterIndexer extends AlphabetIndexer {
    boolean mIsDesc;
    boolean mIsSorted = true;
    int mTotalCount = 0;
    String[] mSections;

    public LetterIndexer(Cursor cursor, int sortedColumnIndex, CharSequence alphabet) {
        super(cursor, sortedColumnIndex, alphabet);
        mIsDesc = alphabet.length() > 0 && alphabet.charAt(0) > alphabet.charAt(alphabet.length() - 1);
        mTotalCount = cursor != null ? cursor.getCount() : 0;
    }

    public void setCursor(Cursor cursor, int column) {
        mColumnIndex = column;
        mTotalCount = cursor != null ? cursor.getCount() : 0;
        setCursor(cursor);
    }

    public void setColumnIndex(int column) {
        mColumnIndex = column;
    }

    // mAlphabetArray is private in AlphabetIndexer, but we get get at it through getSections()
    protected void setSections(String[] sections) {
        System.arraycopy(sections, 0, getSections(), 0, sections.length);
    }

    /**
     * Overrides default sections with custom labels.
     *
     * <p>For example, an alphabet of {@code "0123"} uses "0", "1", etc. as section labels.
     * {@code setSectionLabels("First", "Second", "Third", "Fourth")} changes the section labels
     * to "First", "Second", etc. while keeping the same indexer values.
     *
     * @param sections must have the same number of labels as sections (null to clear)
     */
    public void setSectionLabels(String... sections) {
        if (sections == null || sections.length == 0)
            mSections = null;
        else
            mSections = sections;
    }

    /** Gets section labels.
     *
     * <p>Falls back to default indexer section labels if no custom labels have been set.
     *
     * @return list of section labels
     * @see #setSectionLabels
     */
    public Object[] getSectionLabels() {
        return mSections != null ? mSections : getSections();
    }

    // Handle descending sort order
    @Override
    public void setCursor(Cursor cursor) {
        super.setCursor(cursor);
        if (! mIsSorted)
            return;
        if (cursor != null) {
            // Find first and last values
            boolean isDesc = false;
            if (cursor.getCount() > 0) {
                int pos = cursor.getPosition();
                cursor.moveToFirst();
                String first = cursor.getString(mColumnIndex);
                cursor.moveToLast();
                String last = cursor.getString(mColumnIndex);
                cursor.moveToPosition(pos);
                // Try to compare first and last as integers first, then as strings
                try {
                    isDesc = Integer.parseInt(first) > Integer.parseInt(last);
                } catch (NumberFormatException e) {
                    isDesc = first.compareTo(last) > 0;
                }
            }
            // Reverse the appropriate arrays
            if (mIsDesc != isDesc) {
                mIsDesc = isDesc;
                // Reverse alphabet
                mAlphabet = new StringBuilder(mAlphabet).reverse().toString();
                // Reverse sections
                String[] sections = (String[])getSections();
                List<String> sectionReverser = Arrays.asList(sections);
                Collections.reverse(sectionReverser);
                sectionReverser.toArray(sections);
                // Reverse section labels
                if (mSections != null) {
                    sectionReverser = Arrays.asList(mSections);
                    Collections.reverse(sectionReverser);
                    sectionReverser.toArray(mSections);
                }
            }
        }
    }

    /**
     * Gets the number of items in a section.
     *
     * @param section section index
     * @return number of items in the section
     */
    int getCountForSection(int section) {
        int pos = getPositionForSection(section);
        if (section >= getSections().length - 1)
            return mTotalCount - pos;
        else
            return getPositionForSection(section + 1) - pos;
    }
}
