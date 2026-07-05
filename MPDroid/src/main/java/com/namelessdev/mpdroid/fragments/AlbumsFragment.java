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
import com.namelessdev.mpdroid.adapters.ArrayIndexerAdapter;
import com.namelessdev.mpdroid.helpers.AlbumInfo;
import com.namelessdev.mpdroid.helpers.CoverAsyncHelper;
import com.namelessdev.mpdroid.helpers.CoverManager;
import com.namelessdev.mpdroid.library.ILibraryFragmentActivity;
import com.namelessdev.mpdroid.library.SimpleLibraryActivity;
import com.namelessdev.mpdroid.tools.Tools;
import com.namelessdev.mpdroid.views.AlbumDataBinder;
import com.namelessdev.mpdroid.views.holders.AlbumViewHolder;

import org.a0z.mpd.MPDCommand;
import org.a0z.mpd.exception.MPDException;
import org.a0z.mpd.item.Album;
import org.a0z.mpd.item.Artist;
import org.a0z.mpd.item.Genre;
import org.a0z.mpd.item.Item;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.StringRes;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListAdapter;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class AlbumsFragment extends BrowseFragment {

    private static final String ALBUM_YEAR_SORT_KEY = "sortAlbumsByYear";

    private static final String EXTRA_ARTIST = "artist";

    private static final String EXTRA_GENRE = "genre";

    private static final String SHOW_ALBUM_TRACK_COUNT_KEY = "showAlbumTrackCount";

    private static final String TAG = "AlbumsFragment";

    protected Artist mArtist = null;

    protected ProgressBar mCoverArtProgress;

    protected Genre mGenre = null;

    protected boolean mIsCountDisplayed;

    public AlbumsFragment() {
        this(null);
    }

    @SuppressLint("ValidFragment")
    public AlbumsFragment(final Artist artist) {
        this(artist, null);
    }

    public AlbumsFragment(final Artist artist, final Genre genre) {
        super(R.string.addAlbum, R.string.albumAdded, MPDCommand.MPD_SEARCH_ALBUM);
        init(artist, genre);
    }

    private static void refreshCover(final View view, final AlbumInfo album) {
        if (view.getTag() instanceof AlbumViewHolder) {
            final AlbumViewHolder albumViewHolder = (AlbumViewHolder) view.getTag();
            if (albumViewHolder.mAlbumCover
                    .getTag(R.id.CoverAsyncHelper) instanceof CoverAsyncHelper) {
                final CoverAsyncHelper coverAsyncHelper
                        = (CoverAsyncHelper) albumViewHolder.mAlbumCover
                        .getTag(R.id.CoverAsyncHelper);
                coverAsyncHelper.downloadCover(album, true);
            }
        }
    }

    @Override
    protected void add(final Item item, final boolean replace, final boolean play) {
        try {
            mApp.oMPDAsyncHelper.oMPD.add((Album) item, replace, play);
            Tools.notifyUser(mIrAdded, item);
        } catch (final IOException | MPDException e) {
            Log.e(TAG, "Failed to add.", e);
        }
    }

    @Override
    protected void add(final Item item, final String playlist) {
        try {
            mApp.oMPDAsyncHelper.oMPD.addToPlaylist(playlist, (Album) item);
            Tools.notifyUser(mIrAdded, item);
        } catch (final IOException | MPDException e) {
            Log.e(TAG, "Failed to add.", e);
        }
    }

    @Override
    protected void asyncUpdate() {
        final SharedPreferences settings = MPDApplication.getDefaultSharedPreferences(mApp);
        final boolean sortByYear = settings.getBoolean(ALBUM_YEAR_SORT_KEY, false);

        try {
            mItems = mApp.oMPDAsyncHelper.oMPD.getAlbums(mArtist, sortByYear, mIsCountDisplayed);

            if (sortByYear) {
                Collections.sort((List<? extends Album>) mItems, Album.SORT_BY_YEAR);
            }

            if (mGenre != null) { // filter albums not in genre
                for (int i = mItems.size() - 1; i >= 0; i--) {
                    if (!mApp.oMPDAsyncHelper.oMPD.isAlbumInGenre((Album) mItems.get(i), mGenre)) {
                        mItems.remove(i);
                    }
                }
            }
        } catch (final IOException | MPDException e) {
            Log.e(TAG, "Failed to update.", e);
        }
    }

    /**
     * Uses CoverManager to clean up a cover.
     *
     * @param item         The MenuItem from the user interaction.
     * @param isWrongCover True to blacklist the cover, false otherwise.
     */
    private void cleanupCover(final MenuItem item, final boolean isWrongCover) {
        final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item
                .getMenuInfo();

        final Album album = (Album) mItems.get((int) info.id);
        final AlbumInfo albumInfo = new AlbumInfo(album);

        if (isWrongCover) {
            CoverManager.getInstance()
                    .markWrongCover(albumInfo);
        } else {
            CoverManager.getInstance()
                    .clear(albumInfo);
        }

        refreshCover(info.targetView, albumInfo);
        updateNowPlayingSmallFragment(albumInfo);
    }

    @Override
    protected ListAdapter getCustomListAdapter() {
        if (mItems != null) {
            return new ArrayIndexerAdapter(getActivity(),
                    new AlbumDataBinder(), mItems);
        }
        return super.getCustomListAdapter();
    }

    @Override
    @StringRes
    public int getLoadingText() {
        return R.string.loadingAlbums;
    }

    @Override
    public String getTitle() {
        if (mArtist != null) {
            return mArtist.mainText();
        } else {
            return getString(R.string.albums);
        }
    }

    public AlbumsFragment init(final Artist artist, final Genre genre) {
        mArtist = artist;
        mGenre = genre;
        return this;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            init((Artist) savedInstanceState.getParcelable(EXTRA_ARTIST),
                    (Genre) savedInstanceState.getParcelable(EXTRA_GENRE));
        }
    }

    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View v,
            final ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        final MenuItem deleteAlbumItem = menu.add(DELETE_ALBUM,
                DELETE_ALBUM, 0, R.string.deleteAlbum);
        deleteAlbumItem.setOnMenuItemClickListener(this);
        final MenuItem otherCoverItem = menu.add(POPUP_COVER_BLACKLIST,
                POPUP_COVER_BLACKLIST, 0, R.string.otherCover);
        otherCoverItem.setOnMenuItemClickListener(this);
        final MenuItem resetCoverItem = menu.add(POPUP_COVER_SELECTIVE_CLEAN,
                POPUP_COVER_SELECTIVE_CLEAN, 0, R.string.resetCover);
        resetCoverItem.setOnMenuItemClickListener(this);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        final View view = super.onCreateView(inflater, container, savedInstanceState);
        mCoverArtProgress = (ProgressBar) view.findViewById(R.id.albumCoverProgress);
        return view;

    }

    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
            final long id) {
        ((ILibraryFragmentActivity) getActivity()).pushLibraryFragment(
                new SongsFragment().init((Album) mItems.get(position)),
                "songs");
    }

    @Override
    public boolean onMenuItemClick(final MenuItem item) {
        boolean result = false;

        switch (item.getGroupId()) {
            case GOTO_ARTIST:
                final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
                final Object selectedItem = mItems.get((int) info.id);
                final Intent intent = new Intent(getActivity(), SimpleLibraryActivity.class);
                final Album a = (Album) selectedItem;

                intent.putExtra("artist", a.getArtist());
                startActivityForResult(intent, -1);
                break;
            case DELETE_ALBUM:
                final AdapterContextMenuInfo deleteInfo = (AdapterContextMenuInfo) item.getMenuInfo();
                final Album albumToDelete = (Album) mItems.get((int) deleteInfo.id);
                deleteAlbum(albumToDelete);
                break;
            case POPUP_COVER_BLACKLIST:
                cleanupCover(item, true);
                break;
            case POPUP_COVER_SELECTIVE_CLEAN:
                cleanupCover(item, false);
                break;
            default:
                result = super.onMenuItemClick(item);
                break;
        }
        return result;
    }

    @Override
    public void onResume() {
        super.onResume();

        mIsCountDisplayed = MPDApplication.getDefaultSharedPreferences(mApp)
                .getBoolean(SHOW_ALBUM_TRACK_COUNT_KEY, true);
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        if (mArtist != null) {
            outState.putParcelable(EXTRA_ARTIST, mArtist);
        }

        if (mGenre != null) {
            outState.putParcelable(EXTRA_GENRE, mGenre);
        }
        super.onSaveInstanceState(outState);
    }

    /**
     * Deletes the album by making a REST call to pySpotify service.
     * 
     * Makes a DELETE request to: http://{pySpotifyHost}:{pySpotifyPort}/api/v1/delete
     * with JSON body: {"path": "album/path"}
     * 
     * @param album The album to delete
     */
    private void deleteAlbum(final Album album) {
        String albumPath = album.getPath();
        
        if (albumPath == null || albumPath.isEmpty()) {
            Log.w(TAG, "Cannot delete album: path is null or empty");
            if (getActivity() != null) {
                Toast.makeText(getActivity(), "Cannot delete album: path not available", 
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }
        
        // Normalize path separators: MPD uses forward slashes, but paths might have backslashes
        // Replace backslashes with forward slashes
        albumPath = albumPath.replace('\\', '/');
        
        // Log the path for debugging (including special characters)
        Log.d(TAG, "Deleting album - Original path: " + album.getPath());
        Log.d(TAG, "Deleting album - Normalized path: " + albumPath);
        Log.d(TAG, "Deleting album - Path bytes (UTF-8): " + 
              java.util.Arrays.toString(albumPath.getBytes(StandardCharsets.UTF_8)));
        
        // Store original path for UI removal (before normalization)
        final String originalPath = album.getPath();
        
        // Get pySpotify settings
        final com.namelessdev.mpdroid.tools.SettingsHelper settingsHelper =
                new com.namelessdev.mpdroid.tools.SettingsHelper(mApp.oMPDAsyncHelper);
        final String pySpotifyBaseUrl = settingsHelper.getPySpotifyBaseUrl();
        final String pySpotifyAuthHeader = settingsHelper.getPySpotifyAuthHeader();

        // Store normalized path in final variable for use in inner class
        final String normalizedPath = albumPath;

        // Execute REST call in background thread
        mApp.oMPDAsyncHelper.execAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    final String urlString = pySpotifyBaseUrl + "/api/v1/delete";
                    final URL url = new URL(urlString);
                    final HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                    try {
                        connection.setRequestMethod("DELETE");
                        connection.setRequestProperty("accept", "application/json");
                        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                        if (pySpotifyAuthHeader != null) {
                            connection.setRequestProperty("Authorization", pySpotifyAuthHeader);
                        }
                        connection.setDoOutput(true);
                        connection.setConnectTimeout(10000);
                        connection.setReadTimeout(10000);
                        
                        // Create JSON body - JSONObject.put() automatically escapes special characters
                        final JSONObject jsonBody = new JSONObject();
                        jsonBody.put("path", normalizedPath);
                        final String jsonInputString = jsonBody.toString();
                        
                        // Log the JSON being sent
                        Log.d(TAG, "Sending JSON: " + jsonInputString);
                        
                        // Write JSON body
                        try (final OutputStream os = connection.getOutputStream()) {
                            final byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                            os.write(input, 0, input.length);
                        }
                        
                        // Get response code
                        int responseCodeValue = -1;
                        String responseBody = "";
                        try {
                            responseCodeValue = connection.getResponseCode();
                            
                            // Read response body (if available)
                            // Some servers close connection immediately after DELETE, which is OK
                            try {
                                final java.io.InputStream inputStream = 
                                        responseCodeValue >= 200 && responseCodeValue < 300 
                                                ? connection.getInputStream() 
                                                : connection.getErrorStream();
                                if (inputStream != null) {
                                    try (final BufferedReader br = new BufferedReader(
                                            new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                                        final StringBuilder response = new StringBuilder();
                                        String responseLine;
                                        while ((responseLine = br.readLine()) != null) {
                                            response.append(responseLine.trim());
                                        }
                                        responseBody = response.toString();
                                    }
                                }
                            } catch (final java.net.ProtocolException e) {
                                // Server closed connection immediately after DELETE - this is normal
                                // Don't treat as error if we got a successful response code
                                if (responseCodeValue >= 200 && responseCodeValue < 300) {
                                    Log.d(TAG, "Server closed connection after successful DELETE (normal)");
                                } else {
                                    // Re-throw if it's an error response and we couldn't read it
                                    throw e;
                                }
                            }
                        } catch (final java.net.ProtocolException e) {
                            // If getResponseCode() itself throws ProtocolException, 
                            // the request might have succeeded but connection was closed
                            // Check if we can determine success from the exception
                            Log.w(TAG, "ProtocolException while reading response for: " + normalizedPath, e);
                            // Assume failure if we can't get response code
                            responseCodeValue = -1;
                        }
                        
                        // Make final for use in inner classes
                        final int responseCode = responseCodeValue;
                        final String finalResponseBody = responseBody;
                        
                        if (responseCode >= 200 && responseCode < 300) {
                            Log.d(TAG, "Album deleted successfully: " + normalizedPath + 
                                  ", Response: " + finalResponseBody);
                            
                            // Update MPD database to reflect the deletion
                            // According to MPD protocol: update [URI] - updates the music database
                            // URI is relative to the music_directory
                            mApp.oMPDAsyncHelper.execAsync(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        Log.d(TAG, "Updating MPD database for path: " + normalizedPath);
                                        mApp.oMPDAsyncHelper.oMPD.refreshDatabase(normalizedPath);
                                        Log.d(TAG, "MPD database update completed for: " + normalizedPath);
                                    } catch (final IOException | MPDException e) {
                                        Log.e(TAG, "Failed to update MPD database for: " + normalizedPath, e);
                                        // Don't show error to user as deletion was successful
                                        // Database update failure is non-critical
                                    }
                                }
                            });
                            
                            // Remove album from UI and refresh list on UI thread
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        // Save scroll position before removing item
                                        final int firstVisiblePosition = mList.getFirstVisiblePosition();
                                        final View firstVisibleView = mList.getChildAt(0);
                                        final int firstVisibleOffset = firstVisibleView != null 
                                                ? firstVisibleView.getTop() 
                                                : 0;
                                        
                                        // Find and remove the album from the list
                                        // Compare by path since album objects may not be the same instance
                                        int removedIndex = -1;
                                        if (mItems != null) {
                                            for (int i = mItems.size() - 1; i >= 0; i--) {
                                                final Album item = (Album) mItems.get(i);
                                                final String itemPath = item.getPath();
                                                // Compare paths (handle both normalized and original formats)
                                                if (itemPath != null && (
                                                        itemPath.equals(originalPath) || 
                                                        itemPath.equals(normalizedPath) ||
                                                        itemPath.replace('\\', '/').equals(normalizedPath))) {
                                                    mItems.remove(i);
                                                    removedIndex = i;
                                                    Log.d(TAG, "Removed album from UI list at index: " + i);
                                                    break;
                                                }
                                            }
                                        }
                                        
                                        if (removedIndex >= 0) {
                                            // Calculate target scroll position before updating adapter
                                            // Adjust position if item was removed before visible area
                                            int targetPosition = firstVisiblePosition;
                                            if (removedIndex < firstVisiblePosition) {
                                                // Item was removed before visible area, adjust position down by 1
                                                targetPosition = Math.max(0, firstVisiblePosition - 1);
                                            } else if (removedIndex == firstVisiblePosition) {
                                                // Item was the first visible, keep same position (next item will be there)
                                                targetPosition = Math.min(firstVisiblePosition, mItems.size() - 1);
                                            }
                                            // else: item was after visible area, no adjustment needed
                                            
                                            // Update adapter - must create new one since ArrayIndexerAdapter 
                                            // needs to rebuild its index when items change
                                            if (mItems != null) {
                                                final ListAdapter adapter = getCustomListAdapter();
                                                mList.setAdapter(adapter);
                                                
                                                // Notify adapter of data change (like QueueFragment does)
                                                if (adapter instanceof android.widget.BaseAdapter) {
                                                    ((android.widget.BaseAdapter) adapter).notifyDataSetChanged();
                                                }
                                                
                                                // Update empty view visibility
                                                if (mItems.isEmpty()) {
                                                    mNoResultView.setVisibility(View.VISIBLE);
                                                } else {
                                                    mNoResultView.setVisibility(View.GONE);
                                                }
                                                
                                                // Restore scroll position - use ViewTreeObserver to wait for layout
                                                final int finalTargetPosition = targetPosition;
                                                if (finalTargetPosition >= 0 && finalTargetPosition < mItems.size()) {
                                                    // Wait for layout to complete before restoring scroll
                                                    mList.getViewTreeObserver().addOnGlobalLayoutListener(
                                                            new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                                                                @Override
                                                                public void onGlobalLayout() {
                                                                    // Remove listener to avoid multiple calls
                                                                    mList.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                                                                    // Restore scroll position now that layout is complete
                                                                    if (finalTargetPosition >= 0 && finalTargetPosition < mItems.size() && mList != null) {
                                                                        mList.setSelectionFromTop(finalTargetPosition, firstVisibleOffset);
                                                                    }
                                                                }
                                                            });
                                                }
                                            }
                                        } else {
                                            Log.w(TAG, "Could not find album in list to remove: " + normalizedPath);
                                        }
                                        
                                        // Show success message
                                        Toast.makeText(getActivity(), 
                                                "Album deleted successfully", 
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        } else {
                            Log.e(TAG, "Failed to delete album: " + normalizedPath + 
                                  ", Response code: " + responseCode + 
                                  ", Response: " + finalResponseBody);
                            // Show error message on UI thread
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(getActivity(), 
                                                "Failed to delete album (HTTP " + responseCode + ")", 
                                                Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        }
                    } finally {
                        connection.disconnect();
                    }
                } catch (final Exception e) {
                    Log.e(TAG, "Error deleting album: " + normalizedPath, e);
                    // Show error message on UI thread
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getActivity(), 
                                        "Error deleting album: " + e.getMessage(), 
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
            }
        });
    }

    private void updateNowPlayingSmallFragment(final AlbumInfo albumInfo) {
        final NowPlayingSmallFragment nowPlayingSmallFragment;
        if (getActivity() != null) {
            nowPlayingSmallFragment = (NowPlayingSmallFragment) getParentFragmentManager()
                    .findFragmentById(R.id.now_playing_small_fragment);
            if (nowPlayingSmallFragment != null) {
                nowPlayingSmallFragment.updateCover(albumInfo);
            }
        }
    }
}
