/*
 * Copyright (C) 2010-2014 The MPDroid Project
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

package com.namelessdev.mpdroid;

import com.namelessdev.mpdroid.cover.CachedCover;
import com.namelessdev.mpdroid.helpers.CoverManager;

import org.a0z.mpd.MPD;
import org.a0z.mpd.MPDStatistics;
import org.a0z.mpd.exception.MPDException;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import android.provider.SearchRecentSuggestions;
import androidx.annotation.NonNull;
import android.text.format.Formatter;
import android.util.Log;

import java.io.IOException;

public class SettingsFragment extends PreferenceFragmentCompat {

    private static final String TAG = "SettingsFragment";

    private final MPDApplication mApp = MPDApplication.getInstance();

    private CheckBoxPreference mAlbumArtLibrary;

    private EditTextPreference mAlbums;

    private EditTextPreference mArtists;

    private EditTextPreference mCacheUsage1;

    private EditTextPreference mCacheUsage2;

    private CheckBoxPreference mCheckBoxPreference;

    private Preference mCoverFilename;

    private Handler mHandler;

    private PreferenceScreen mInformationScreen;

    private CheckBoxPreference mLocalCoverCheckbox;

    private Preference mMusicPath;

    private boolean mPreferencesBound;

    private EditTextPreference mSongs;

    private EditTextPreference mVersion;

    public SettingsFragment() {
        super();
        mPreferencesBound = false;
    }

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);
        refreshDynamicFields();
    }

    public void onConnectionStateChanged() {
        if (mInformationScreen == null) {
            return; // Not in the root preference screen
        }
        final MPD mpd = mApp.oMPDAsyncHelper.oMPD;
        final boolean isConnected = mpd.isConnected();

        mInformationScreen.setEnabled(isConnected);

        if (isConnected && mVersion != null && mArtists != null && mAlbums != null && mSongs != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final String versionText = mpd.getMpdVersion();
                    final MPDStatistics mpdStatistics = mpd.getStatistics();

                    mHandler.post(new Runnable() {

                        @Override
                        public void run() {
                            if (mVersion != null) {
                                mVersion.setSummary(versionText);
                            }
                            if (mArtists != null) {
                                mArtists.setSummary(String.valueOf(mpdStatistics.getArtists()));
                            }
                            if (mAlbums != null) {
                                mAlbums.setSummary(String.valueOf(mpdStatistics.getAlbums()));
                            }
                            if (mSongs != null) {
                                mSongs.setSummary(String.valueOf(mpdStatistics.getSongs()));
                            }
                        }
                    });
                }
            }).start();
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings, rootKey);

        mHandler = new Handler();

        // Only initialize preferences that exist in the current preference screen
        // (rootKey is null for the root screen, or the key of the nested PreferenceScreen)
        mInformationScreen = (PreferenceScreen) findPreference("informationScreen");
        if (mInformationScreen != null && rootKey == null) {
            // Only remove tabletUI preference on the root screen
            if (!getResources().getBoolean(R.bool.isTablet)) {
                final PreferenceScreen interfaceCategory = (PreferenceScreen) findPreference(
                        "nowPlayingScreen");
                if (interfaceCategory != null) {
                    final Preference tabletUI = findPreference("tabletUI");
                    if (tabletUI != null) {
                        interfaceCategory.removePreference(tabletUI);
                    }
                }
            }
        }

        mVersion = (EditTextPreference) findPreference("version");
        mArtists = (EditTextPreference) findPreference("artists");
        mAlbums = (EditTextPreference) findPreference("albums");
        mSongs = (EditTextPreference) findPreference("songs");

        mLocalCoverCheckbox = (CheckBoxPreference) findPreference(
                "enableLocalCover");
        mMusicPath = findPreference("musicPath");
        mCoverFilename = findPreference("coverFileName");
        if (mLocalCoverCheckbox != null && mMusicPath != null && mCoverFilename != null) {
            if (mLocalCoverCheckbox.isChecked()) {
                mMusicPath.setEnabled(true);
                mCoverFilename.setEnabled(true);
            } else {
                mMusicPath.setEnabled(false);
                mCoverFilename.setEnabled(false);
            }
        }

        mCacheUsage1 = (EditTextPreference) findPreference("cacheUsage1");
        mCacheUsage2 = (EditTextPreference) findPreference("cacheUsage2");

        // Album art library listing requires cover art cache
        mCheckBoxPreference = (CheckBoxPreference) findPreference(
                "enableLocalCoverCache");
        mAlbumArtLibrary = (CheckBoxPreference) findPreference(
                "enableAlbumArtLibrary");
        if (mCheckBoxPreference != null && mAlbumArtLibrary != null) {
            mAlbumArtLibrary.setEnabled(mCheckBoxPreference.isChecked());
        }

        /** Allow these to be changed individually, pauseOnPhoneStateChange might be overridden. */
        final CheckBoxPreference phonePause = (CheckBoxPreference) findPreference(
                "pauseOnPhoneStateChange");
        final CheckBoxPreference phoneStateChange = (CheckBoxPreference) findPreference(
                "playOnPhoneStateChange");

        mPreferencesBound = true;
        refreshDynamicFields();
    }

    @Override
    public boolean onPreferenceTreeClick(final Preference preference) {
        // Handle PreferenceScreen navigation - if it's a PreferenceScreen with nested preferences,
        // navigate to it. If it has an intent, let the default behavior handle it.
        if (preference instanceof PreferenceScreen) {
            final PreferenceScreen preferenceScreen = (PreferenceScreen) preference;
            // If it has an intent, let the default behavior handle it
            if (preferenceScreen.getIntent() != null) {
                return super.onPreferenceTreeClick(preference);
            }
            // Otherwise, navigate to show nested preferences
            if (preferenceScreen.getPreferenceCount() > 0) {
                onNavigateToScreen(preferenceScreen);
                return true;
            }
        }

        // Handle preferences with null keys
        if (preference.getKey() == null) {
            return super.onPreferenceTreeClick(preference);
        }

        if ("refreshMPDDatabase".equals(preference.getKey())) {
            try {
                mApp.oMPDAsyncHelper.oMPD.refreshDatabase();
            } catch (final IOException | MPDException e) {
                Log.e(TAG, "Failed to refresh the database.", e);
            }
            return true;
        } else if ("clearLocalCoverCache".equals(preference.getKey())) {
            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.clearLocalCoverCache)
                    .setMessage(R.string.clearLocalCoverCachePrompt)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialog, final int which) {
                            // Todo : The covermanager must already have been
                            // initialized, get rid of the getInstance arguments
                            CoverManager.getInstance().clear();
                            mCacheUsage1.setSummary("0.00B");
                            mCacheUsage2.setSummary("0.00B");
                        }
                    })
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialog, final int which) {
                            // do nothing
                        }
                    })
                    .show();
            return true;

        } else if ("enableLocalCover".equals(preference.getKey())) {
            if (mLocalCoverCheckbox.isChecked()) {
                mMusicPath.setEnabled(true);
                mCoverFilename.setEnabled(true);
            } else {
                mMusicPath.setEnabled(false);
                mCoverFilename.setEnabled(false);
            }
            return true;
        } else if ("enableLocalCoverCache".equals(preference.getKey())) {
            // album art library listing requires cover art cache
            if (mCheckBoxPreference != null && mAlbumArtLibrary != null) {
                if (mCheckBoxPreference.isChecked()) {
                    mAlbumArtLibrary.setEnabled(true);
                } else {
                    mAlbumArtLibrary.setEnabled(false);
                    mAlbumArtLibrary.setChecked(false);
                }
            }
            return true;

        } else if ("pauseOnPhoneStateChange".equals(preference.getKey())) {
            /**
             * Allow these to be changed individually,
             * pauseOnPhoneStateChange might be overridden.
             */
            final CheckBoxPreference phonePause = (CheckBoxPreference) findPreference(
                    "pauseOnPhoneStateChange");
            final CheckBoxPreference phoneStateChange = (CheckBoxPreference) findPreference(
                    "playOnPhoneStateChange");
        } else if ("clearSearchHistory".equals(preference.getKey())) {
            final SearchRecentSuggestions suggestions = new SearchRecentSuggestions(getActivity(),
                    SearchRecentProvider.AUTHORITY, SearchRecentProvider.MODE);
            suggestions.clearHistory();
            preference.setEnabled(false);
            return true;
        }

        return super.onPreferenceTreeClick(preference);

    }

    public void refreshDynamicFields() {
        if (getActivity() == null || !mPreferencesBound) {
            return;
        }
        final long size = new CachedCover().getCacheUsage();
        final String usage = Formatter.formatFileSize(mApp, size);
        if (mCacheUsage1 != null) {
            mCacheUsage1.setSummary(usage);
        }
        if (mCacheUsage2 != null) {
            mCacheUsage2.setSummary(usage);
        }
        onConnectionStateChanged();
    }

}
