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

package com.namelessdev.mpdroid.helpers;

import com.namelessdev.mpdroid.MPDApplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class MPDConnectionHandler extends BroadcastReceiver {

    private static final String TAG = "MPDConnectionHandler";
    private static final long RECONNECT_DELAY_MS = 2000L; // Wait 2 seconds before reconnecting

    private static MPDConnectionHandler sInstance;

    public static MPDConnectionHandler getInstance() {
        if (sInstance == null) {
            sInstance = new MPDConnectionHandler();
        }
        return sInstance;
    }

    /**
     * Checks if network is suitable for MPD connection (WiFi or Ethernet).
     */
    private static boolean isSuitableNetwork(final Context context) {
        final ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
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
                // Check if network has internet capability and WiFi/Ethernet transport
                return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                       (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
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

    /**
     * Attempts to reconnect to MPD if network is available and connection is needed.
     */
    private static void attemptReconnection(final Context context) {
        final MPDApplication app = MPDApplication.getInstance();
        if (app == null || app.oMPDAsyncHelper == null) {
            return;
        }

        // Check if we need a connection (have connection locks)
        if (app.oMPDAsyncHelper.oMPD == null) {
            return;
        }

        // Only reconnect if we're not already connected and network is available
        if (!app.oMPDAsyncHelper.oMPD.isConnected() && isSuitableNetwork(context)) {
            Log.d(TAG, "Network available, attempting to reconnect to MPD");
            // Use Handler to delay reconnection slightly to ensure network is stable
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    final MPDApplication currentApp = MPDApplication.getInstance();
                    if (currentApp != null && currentApp.oMPDAsyncHelper != null &&
                            currentApp.oMPDAsyncHelper.oMPD != null &&
                            !currentApp.oMPDAsyncHelper.oMPD.isConnected() &&
                            isSuitableNetwork(context)) {
                        currentApp.connect();
                    }
                }
            }, RECONNECT_DELAY_MS);
        }
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final String action = intent.getAction();
        if (action == null) {
            return;
        }

        switch (action) {
            case WifiManager.WIFI_STATE_CHANGED_ACTION:
                final int wifiState = intent
                        .getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                Log.d(TAG, "WIFI-STATE:" + action + " state: " + wifiState);
                
                // If WiFi is enabled, attempt reconnection
                if (wifiState == WifiManager.WIFI_STATE_ENABLED) {
                    attemptReconnection(context);
                }
                break;
                
            case WifiManager.NETWORK_STATE_CHANGED_ACTION:
                final NetworkInfo networkInfo =
                        intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                Log.d(TAG, "NETW-STATE:" + action);
                if (networkInfo != null) {
                    Log.d(TAG, "NETW-STATE: Connected: " + networkInfo.isConnected()
                            + ", state: " + networkInfo.getState());
                    
                    // If network is connected, attempt reconnection
                    if (networkInfo.isConnected()) {
                        attemptReconnection(context);
                    }
                }
                break;
                
            case ConnectivityManager.CONNECTIVITY_ACTION:
                // Handle general connectivity changes (for older Android versions)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    final NetworkInfo networkInfo2 =
                            intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
                    if (networkInfo2 != null && networkInfo2.isConnected()) {
                        attemptReconnection(context);
                    }
                }
                break;
                
            default:
                break;
        }
    }
}
