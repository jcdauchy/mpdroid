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
import com.namelessdev.mpdroid.MainMenuActivity;

import org.a0z.mpd.MPD;
import org.a0z.mpd.MPDOutput;
import org.a0z.mpd.exception.MPDException;

import android.app.Activity;
import android.os.Bundle;
import androidx.fragment.app.ListFragment;
import android.util.Log;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class OutputsFragment extends ListFragment implements AdapterView.OnItemClickListener {

    private static final String TAG = "OutputsFragment";

    private final MPDApplication mApp = MPDApplication.getInstance();

    private ArrayList<MPDOutput> mOutputs;

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final ListAdapter arrayAdapter = new ArrayAdapter<>(getActivity(),
                android.R.layout.simple_list_item_multiple_choice, mOutputs);
        setListAdapter(arrayAdapter);

        final ListView listView = getListView();
        listView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
        listView.setOnItemClickListener(this);
        
        // Add top padding to prevent the first item from being hidden by the action bar title
        // The parent FrameLayout has paddingTop for actionBarSize, but the ListView might
        // need additional padding to ensure the first item is fully visible
        // Convert 16dp to pixels for padding (increased from 8dp for better visibility)
        final float density = getResources().getDisplayMetrics().density;
        final int topPadding = (int) (16 * density);
        listView.setPadding(listView.getPaddingLeft(), topPadding, 
                listView.getPaddingRight(), listView.getPaddingBottom());
        listView.setClipToPadding(true);

        // Not needed since MainMenuActivity will take care of telling us to refresh
        if (!(getActivity() instanceof MainMenuActivity)) {
            refreshOutputs();
        }
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mOutputs = new ArrayList<>();
    }

    @Override
    public void onItemClick(
            final AdapterView<?> parent, final View view, final int position, final long id) {
        // Get the current checked state (already toggled by ListView with CHOICE_MODE_MULTIPLE)
        // The ListView automatically toggles the checkbox, so this is the NEW desired state
        final boolean shouldBeEnabled = getListView().isItemChecked(position);
        final MPDOutput output = mOutputs.get(position);
        
        // Optimistically update the UI - ListView already toggled the checkbox
        // Only revert on error to prevent the "lost click" issue
        mApp.oMPDAsyncHelper.execAsync(new Runnable() {
            @Override
            public void run() {
                final MPD mpd = mApp.oMPDAsyncHelper.oMPD;
                try {
                    if (shouldBeEnabled) {
                        mpd.enableOutput(output.getId());
                    } else {
                        mpd.disableOutput(output.getId());
                    }
                } catch (final IOException | MPDException e) {
                    Log.e(TAG, "Failed to modify output.", e);
                    // On error, revert the UI state on the main thread
                    final Activity activity = getActivity();
                    if (activity != null) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    // Revert the checkbox to its previous state
                                    getListView().setItemChecked(position, !shouldBeEnabled);
                                } catch (Exception ex) {
                                    Log.e(TAG, "Failed to revert UI state.", ex);
                                }
                            }
                        });
                    }
                    return;
                }
                // On success, don't refresh immediately - the UI is already correct
                // This prevents the "lost click" issue where refresh overwrites the optimistic update
                // The state will be synced next time the outputs are refreshed naturally
            }
        });
    }

    public void refreshOutputs() {
        mApp.oMPDAsyncHelper.execAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<MPDOutput> mpdOutputs = mApp.oMPDAsyncHelper.oMPD.getOutputs();
                    mOutputs.clear();
                    mOutputs.addAll(mpdOutputs);
                } catch (final IOException | MPDException e) {
                    Log.e(TAG, "Failed to list outputs.", e);
                }
                final Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        @SuppressWarnings("unchecked")
                        public void run() {
                            try {
                                ((BaseAdapter) getListAdapter()).notifyDataSetChanged();
                                final ListView list = getListView();
                                for (int i = 0; i < mOutputs.size(); i++) {
                                    list.setItemChecked(i, mOutputs.get(i).isEnabled());
                                }
                            } catch (IllegalStateException e) {
                                Log.e(TAG,
                                        "Illegal Activity state while trying to refresh output list");
                            }
                        }
                    });
                }
            }
        });
    }
}
