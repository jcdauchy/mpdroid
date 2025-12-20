/*
 * Copyright (C) 2010-2014 The MPDroid Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.namelessdev.mpdroid.fragments;

import com.namelessdev.mpdroid.MPDApplication;
import com.namelessdev.mpdroid.R;
import com.namelessdev.mpdroid.adapters.ArrayAdapter;
import com.namelessdev.mpdroid.views.AlbumGridDataBinder;

import org.a0z.mpd.item.Artist;
import org.a0z.mpd.item.Genre;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.GridView;
import android.widget.ListAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class AlbumsGridFragment extends AlbumsFragment {

    private static final int MIN_ITEMS_BEFORE_FAST_SCROLL = 6;

    public AlbumsGridFragment() {
        this(null);
    }

    public AlbumsGridFragment(final Artist artist) {
        this(artist, null);
    }

    public AlbumsGridFragment(final Artist artist, final Genre genre) {
        super(artist, genre);
    }

    @Override
    protected ListAdapter getCustomListAdapter() {
        if (mItems != null) {
            return new ArrayAdapter(getActivity(), new AlbumGridDataBinder(), mItems);
        }
        return super.getCustomListAdapter();
    }

    @Override
    protected int getMinimumItemsCountBeforeFastscroll() {
        return MIN_ITEMS_BEFORE_FAST_SCROLL;
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.browsegrid, container, false);
        mList = (AbsListView) view.findViewById(R.id.grid);
        
        // Configure GridView columns based on preference
        if (mList instanceof GridView) {
            configureGridColumns((GridView) mList);
        }
        
        registerForContextMenu(mList);
        mList.setOnItemClickListener(this);
        mLoadingView = view.findViewById(R.id.loadingLayout);
        mLoadingTextView = (TextView) view.findViewById(R.id.loadingText);
        mNoResultView = view.findViewById(R.id.noResultLayout);
        mLoadingTextView.setText(getLoadingText());
        mCoverArtProgress = (ProgressBar) view.findViewById(R.id.albumCoverProgress);
        mSwipeRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.pullToRefresh);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        mIsCountDisplayed = false;
        
        // Update grid columns in case preference changed
        if (mList instanceof GridView) {
            configureGridColumns((GridView) mList);
        }
    }
    
    private void configureGridColumns(final GridView gridView) {
        final SharedPreferences settings = MPDApplication.getDefaultSharedPreferences(getActivity());
        int numColumns;
        try {
            numColumns = Integer.parseInt(settings.getString("albumGridColumns", "3"));
            // Validate: must be between 2 and 5
            if (numColumns < 2 || numColumns > 5) {
                numColumns = 3;
            }
        } catch (NumberFormatException e) {
            numColumns = 3; // Default fallback
        }
        
        // Calculate column width based on screen width, number of columns, padding, and spacing
        final DisplayMetrics metrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
        final int screenWidth = metrics.widthPixels;
        final float density = metrics.density;
        
        // Account for padding (8dp left + 8dp right = 16dp) and spacing (8dp between columns)
        final int paddingPx = (int) (16 * density); // left + right padding
        final int spacingPx = (int) (8 * density * (numColumns - 1)); // spacing between columns
        final int availableWidth = screenWidth - paddingPx - spacingPx;
        final int columnWidth = availableWidth / numColumns;
        
        gridView.setNumColumns(numColumns);
        gridView.setColumnWidth(columnWidth);
    }

    /**
     * This is required because setting the fast scroll prior to KitKat was
     * important because of a bug. This bug has since been corrected, but the
     * opposite order is now required or the fast scroll will not show.
     *
     * @param shouldShowFastScroll If the fast scroll should be shown or not
     */
    @Override
    protected void refreshFastScrollStyle(final boolean shouldShowFastScroll) {
        if (shouldShowFastScroll) {
            refreshFastScrollStyle(View.SCROLLBARS_INSIDE_INSET, true);
        } else {
            refreshFastScrollStyle(View.SCROLLBARS_OUTSIDE_OVERLAY, false);
        }
    }
}
