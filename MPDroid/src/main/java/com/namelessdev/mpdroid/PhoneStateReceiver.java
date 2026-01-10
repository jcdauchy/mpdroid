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

package com.namelessdev.mpdroid;

import com.namelessdev.mpdroid.helpers.MPDControl;

import org.a0z.mpd.MPDStatus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

public class PhoneStateReceiver extends BroadcastReceiver {

    private static MPDApplication getApp() {
        return MPDApplication.getInstance();
    }

    private static SharedPreferences getSettings() {
        final MPDApplication app = getApp();
        if (app == null) {
            return null;
        }
        return MPDApplication.getDefaultSharedPreferences(app);
    }

    private static final boolean DEBUG = false;

    // Used to trace when the app pauses / resumes playback
    private static final String PAUSED_MARKER = "wasPausedInCall";

    private static final String TAG = "PhoneStateReceiver";

    /**
     * Checks if device is connected to a local network (WiFi or Ethernet).
     * Uses modern NetworkCallback API for Android 6.0+ (API 23+), falls back to
     * deprecated API for older versions.
     */
    private static boolean isLocalNetworkConnected() {
        final MPDApplication app = getApp();
        if (app == null) {
            return false;
        }
        final ConnectivityManager cm =
                (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }

        // Use modern API for Android 6.0+ (API 23+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                final Network activeNetwork = cm.getActiveNetwork();
                if (activeNetwork == null) {
                    return false;
                }
                final NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
                if (capabilities == null) {
                    return false;
                }
                // Check if network has WiFi or Ethernet transport
                return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                       capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
            } catch (final Exception e) {
                Log.w(TAG, "Error checking network capabilities", e);
                // Fall through to deprecated API
            }
        }

        // Fallback to deprecated API for older Android versions
        try {
            final NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected()) {
                final int networkType = networkInfo.getType();
                return networkType == ConnectivityManager.TYPE_WIFI ||
                       networkType == ConnectivityManager.TYPE_ETHERNET;
            }
        } catch (final Exception e) {
            Log.w(TAG, "Error checking network info", e);
        }

        return false;
    }

    private static void setPausedMarker(final boolean value) {
        final SharedPreferences settings = getSettings();
        if (settings != null) {
            settings.edit()
                    .putBoolean(PAUSED_MARKER, value)
                    .commit();
        }
    }

    private static boolean shouldPauseForCall() {
        final MPDApplication app = getApp();
        if (app == null || app.oMPDAsyncHelper == null || app.oMPDAsyncHelper.oMPD == null) {
            return false;
        }
        boolean result = false;
        final boolean isPlaying =
                app.oMPDAsyncHelper.oMPD.getStatus().isState(MPDStatus.STATE_PLAYING);

        if (isPlaying) {
            if (app.isLocalAudible()) {
                if (DEBUG) {
                    Log.d(TAG, "App is local audible.");
                }
                result = true;
            } else {
                final SharedPreferences settings = getSettings();
                if (settings != null) {
                    result = settings.getBoolean("pauseOnPhoneStateChange", false);
                    if (DEBUG) {
                        Log.d(TAG, "pauseOnPhoneStateChange: " + result);
                    }
                }
            }
        }

        return result;
    }

    @Override
    public final void onReceive(final Context context, final Intent intent) {
        final MPDApplication app = getApp();
        if (app == null) {
            return;
        }
        final String telephonyState = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

        if (isLocalNetworkConnected() || app.isLocalAudible() && telephonyState != null) {
            if (DEBUG) {
                Log.d(TAG, "Telephony State: " + telephonyState);
            }
            if ((telephonyState.equalsIgnoreCase(TelephonyManager.EXTRA_STATE_RINGING) ||
                    telephonyState.equalsIgnoreCase(TelephonyManager.EXTRA_STATE_OFFHOOK)) &&
                    shouldPauseForCall()) {
                if (DEBUG) {
                    Log.d(TAG, "Pausing for incoming call.");
                }
                MPDControl.run(MPDControl.ACTION_PAUSE);
                setPausedMarker(true);
            } else if (telephonyState.equalsIgnoreCase(TelephonyManager.EXTRA_STATE_IDLE)) {
                final SharedPreferences settings = getSettings();
                if (settings != null) {
                    final boolean playOnCallStop = settings.getBoolean("playOnPhoneStateChange", false);
                    if (playOnCallStop && settings.getBoolean(PAUSED_MARKER, false)) {
                        if (DEBUG) {
                            Log.d(TAG, "Resuming play after call.");
                        }
                        MPDControl.run(MPDControl.ACTION_PLAY);
                    }
                }
                setPausedMarker(false);
            }
        }
    }
}