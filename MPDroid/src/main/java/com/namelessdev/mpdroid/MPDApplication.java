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

import com.namelessdev.mpdroid.helpers.MPDAsyncHelper;
import com.namelessdev.mpdroid.helpers.MPDAsyncHelper.ConnectionListener;
import com.namelessdev.mpdroid.helpers.UpdateTrackInfo;
import com.namelessdev.mpdroid.service.MPDroidService;
import com.namelessdev.mpdroid.service.NotificationHandler;
import com.namelessdev.mpdroid.service.ServiceBinder;
import com.namelessdev.mpdroid.service.StreamHandler;
import com.namelessdev.mpdroid.tools.SettingsHelper;
import com.namelessdev.mpdroid.tools.Tools;

import org.a0z.mpd.MPDStatusMonitor;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnKeyListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.StrictMode;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager.BadTokenException;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

public class MPDApplication extends Application implements
        ConnectionListener,
        Handler.Callback,
        MPDAsyncHelper.ConnectionInfoListener {

    private static final boolean DEBUG = false;

    private static final long DISCONNECT_TIMER = 15000L;

    private static final int SETTINGS = 5;

    private static final String TAG = "MPDApplication";

    private static MPDApplication sInstance;

    private final Collection<Object> mConnectionLocks =
            Collections.synchronizedCollection(new LinkedList<>());

    public MPDAsyncHelper oMPDAsyncHelper = null;

    public UpdateTrackInfo updateTrackInfo = null;

    private AlertDialog mAlertDialog = null;

    private Activity mCurrentActivity = null;

    private Timer mDisconnectScheduler = null;

    private boolean mIsNotificationActive = false;

    private boolean mIsNotificationOverridden = false;

    private boolean mIsStreamActive = false;

    private ServiceBinder mServiceBinder;

    private SharedPreferences mSettings = null;

    private SettingsHelper mSettingsHelper = null;

    private boolean mSettingsShown = false;

    /** NetworkCallback for modern Android network monitoring (API 23+) */
    private ConnectivityManager.NetworkCallback mNetworkCallback = null;

    public static MPDApplication getInstance() {
        return sInstance;
    }

    /**
     * Gets the default shared preferences. This is a compatibility method
     * that replaces android.preference.PreferenceManager.getDefaultSharedPreferences()
     * which was removed in API 33+.
     */
    public static SharedPreferences getDefaultSharedPreferences(Context context) {
        return context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
    }

    /**
     * This adds the connection lock which disallows the service to disconnect after timeout.
     *
     * @param lockOwner The instance declaring itself as an owner of the connection lock.
     */
    public final void addConnectionLock(final Object lockOwner) {
        if (!mConnectionLocks.contains(lockOwner)) {
            mConnectionLocks.add(lockOwner);
            checkConnectionNeeded();
            cancelDisconnectScheduler();
        }
    }

    /**
     * Builds the Connection Failed dialog box for anything other than the settings activity.
     *
     * @param message The reason the connection failed.
     * @return The built {@code AlertDialog} object.
     */
    private AlertDialog.Builder buildConnectionFailedMessage(final String message) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(mCurrentActivity);
        final OnClickListener oDialogClickListener = new DialogClickListener();

        builder.setTitle(R.string.connectionFailed);
        builder.setMessage(
                getResources().getString(R.string.connectionFailedMessage, message));
        builder.setCancelable(false);

        builder.setNegativeButton(R.string.quit, oDialogClickListener);
        builder.setNeutralButton(R.string.settings, oDialogClickListener);
        builder.setPositiveButton(R.string.retry, oDialogClickListener);

        return builder;
    }

    /**
     * Builds the Connection Failed dialog box for the Settings activity.
     *
     * @param message The reason the connection failed.
     * @return The built {@code AlertDialog} object.
     */
    private AlertDialog.Builder buildConnectionFailedSettings(final String message) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(mCurrentActivity);

        builder.setCancelable(false);
        builder.setMessage(
                getResources().getString(R.string.connectionFailedMessageSetting, message));
        builder.setPositiveButton("OK", new OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
            }
        });
        return builder;
    }

    private void cancelDisconnectScheduler() {
        mDisconnectScheduler.cancel();
        mDisconnectScheduler.purge();
        mDisconnectScheduler = new Timer();
    }

    private void checkConnectionNeeded() {
        if (mConnectionLocks.isEmpty()) {
            disconnect();
        } else {
            if (!oMPDAsyncHelper.isStatusMonitorAlive()) {
                oMPDAsyncHelper.startStatusMonitor(new String[]{
                        MPDStatusMonitor.IDLE_DATABASE,
                        MPDStatusMonitor.IDLE_MIXER,
                        MPDStatusMonitor.IDLE_OPTIONS,
                        MPDStatusMonitor.IDLE_OUTPUT,
                        MPDStatusMonitor.IDLE_PLAYER,
                        MPDStatusMonitor.IDLE_PLAYLIST,
                        MPDStatusMonitor.IDLE_STICKER,
                        MPDStatusMonitor.IDLE_UPDATE
                });
            }
            if (!oMPDAsyncHelper.oMPD.isConnected() && (mCurrentActivity == null
                    || !mCurrentActivity.getClass().equals(WifiConnectionSettings.class))) {
                connect();
            }
        }
    }

    public final void connect() {
        if (!mSettingsHelper.updateConnectionSettings()) {
            // Absolutely no settings defined! Open Settings!
            if (mCurrentActivity != null && !mSettingsShown) {
                mCurrentActivity.startActivityForResult(new Intent(mCurrentActivity,
                        WifiConnectionSettings.class), SETTINGS);
                mSettingsShown = true;
            }
        }

        connectMPD();
    }

    private void connectMPD() {
        // dismiss possible dialog
        dismissAlertDialog();

        /** Returns null if the calling thread is not associated with a Looper.*/
        final Looper localLooper = Looper.myLooper();
        final boolean isUIThread =
                localLooper != null && localLooper.equals(Looper.getMainLooper());

        // show connecting to server dialog, only on the main thread.
        if (mCurrentActivity != null && isUIThread) {
            mAlertDialog = new ProgressDialog(mCurrentActivity);
            mAlertDialog.setTitle(R.string.connecting);
            mAlertDialog.setMessage(getResources().getString(R.string.connectingToServer));
            // Make dialog cancelable so users can dismiss it if connection is stuck
            mAlertDialog.setCancelable(true);
            mAlertDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(final DialogInterface dialog) {
                    // User cancelled the connection attempt
                    oMPDAsyncHelper.disconnect();
                }
            });
            try {
                mAlertDialog.show();
            } catch (final BadTokenException ignored) {
                // Can't display it. Don't care.
            }
            
            // Add a connection timeout - dismiss dialog and fail after 15 seconds
            // Increased timeout for modern Android versions which may have network delays
            final long timeout = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? 20000L : 15000L;
            if (mAlertDialog != null) {
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mAlertDialog != null && mAlertDialog instanceof ProgressDialog 
                                && mAlertDialog.isShowing()) {
                            connectionFailed("Connection timeout");
                        }
                    }
                }, timeout);
            }
        }

        cancelDisconnectScheduler();

        // really connect
        oMPDAsyncHelper.connect();
    }

    /**
     * Handles the connection failure with a {@code AlertDialog} user facing message box.
     *
     * @param message The reason the connection failed.
     */
    @Override
    public final synchronized void connectionFailed(final String message) {

        if (mAlertDialog == null || mAlertDialog instanceof ProgressDialog ||
                !mAlertDialog.isShowing()) {

            // dismiss possible dialog
            dismissAlertDialog();

            oMPDAsyncHelper.disconnect();

            if (mCurrentActivity != null && !mConnectionLocks.isEmpty()) {
                try {
                    // are we in the settings activity?
                    if (mCurrentActivity.getClass().equals(SettingsActivity.class)) {
                        mAlertDialog = buildConnectionFailedSettings(message).show();
                    } else {
                        mAlertDialog = buildConnectionFailedMessage(message).show();
                    }
                } catch (final BadTokenException ignored) {
                }
            }
        }
    }

    @Override
    public final synchronized void connectionSucceeded(final String message) {
        dismissAlertDialog();
    }

    final void disconnect() {
        cancelDisconnectScheduler();
        startDisconnectScheduler();
    }

    private void dismissAlertDialog() {
        if (mAlertDialog != null) {
            if (mAlertDialog.isShowing()) {
                try {
                    mAlertDialog.dismiss();
                } catch (final IllegalArgumentException ignored) {
                    // We don't care, it has already been destroyed
                }
            }
        }
    }

    /**
     * Called upon receiving messages from any handler, in this case most often the Service.
     *
     * @param msg The incoming message.
     * @return Whether the message was acted upon.
     */
    @Override
    public final boolean handleMessage(final Message msg) {
        boolean result = true;

        if (DEBUG) {
            Log.d(TAG, "Message received: " + ServiceBinder.getHandlerValue(msg.what) +
                    " with value: " + ServiceBinder.getHandlerValue(msg.arg1));
        }

        switch (msg.what) {
            case MPDroidService.REQUEST_UNBIND:
                if (DEBUG) {
                    Log.d(TAG, "Service requested unbind, complying.");
                }
                mServiceBinder.doUnbindService();
                break;
            case NotificationHandler.IS_ACTIVE:
                mIsNotificationActive = ServiceBinder.TRUE == msg.arg1;
                mServiceBinder.setServicePersistent(true);
                break;
            case ServiceBinder.CONNECTED:
                Log.d(TAG, "MPDApplication is bound to the service.");
                oMPDAsyncHelper.addConnectionInfoListener(this);
                break;
            case ServiceBinder.DISCONNECTED:
                oMPDAsyncHelper.removeConnectionInfoListener(this);
                break;
            case StreamHandler.IS_ACTIVE:
                mIsStreamActive = ServiceBinder.TRUE == msg.arg1;
                mServiceBinder.setServicePersistent(true);
                break;
            case ServiceBinder.SET_PERSISTENT:
                if (!isNotificationPersistent() || ServiceBinder.TRUE == msg.arg1) {
                    mServiceBinder.setServicePersistent(ServiceBinder.TRUE == msg.arg1);
                }
                break;
            default:
                result = false;
                break;
        }

        return result;
    }

    public final boolean isInSimpleMode() {
        return mSettings.getBoolean("simpleMode", false);
    }

    public final boolean isLightThemeSelected() {
        return mSettings.getBoolean("lightTheme", false);
    }

    /**
     * isLocalAudible()
     *
     * @return Returns whether it is probable that the local audio
     * system will be playing audio controlled by this application.
     */
    public final boolean isLocalAudible() {
        return isStreamActive() || Tools.isServerLocalhost();
    }

    /**
     * Checks to see if the MPDroid scheduling service is active.
     *
     * @return True if MPDroid scheduling service running, false otherwise.
     */
    public final boolean isNotificationActive() {
        return !mIsNotificationOverridden && mIsNotificationActive;
    }

    /**
     * Checks the MPDroid scheduling service and the persistent override to
     */
    public final boolean isNotificationPersistent() {
        final boolean result;

        if (oMPDAsyncHelper.getConnectionSettings().isNotificationPersistent &&
                !mIsNotificationOverridden) {
            result = true;
        } else {
            result = false;
        }

        if (DEBUG) {
            Log.d(TAG, "Notification is persistent: " + result);
        }
        return result;
    }

    /**
     * Checks for a running Streaming service.
     *
     * @return True if streaming service is running, false otherwise.
     */
    public final boolean isStreamActive() {
        if (DEBUG && mServiceBinder != null) {
            Log.d(TAG, "ServiceBound: " + mServiceBinder.isServiceBound() + " isStreamActive: " +
                    mIsStreamActive);
        }
        return mServiceBinder != null && mServiceBinder.isServiceBound() && mIsStreamActive;
    }

    public final boolean isTabletUiEnabled() {
        return getResources().getBoolean(R.bool.isTablet)
                && mSettings.getBoolean("tabletUI", true);
    }

    public final boolean hasGooglePlayDeathWarningBeenDisplayed() {
        return mSettings.getBoolean("googlePlayDeathWarningShown", false);
    }

    public final boolean hasGooglePlayThankYouBeenDisplayed() {
        return mSettings.getBoolean("googlePlayThankYouShown", false);
    }

    @SuppressLint("CommitPrefEdits")
    public final void markGooglePlayThankYouAsRead() {
        mSettings.edit().putBoolean("googlePlayThankYouShown", true).commit();
    }

    /**
     * Called upon connection configuration change.
     *
     * @param connectionInfo The new connection configuration information object.
     */
    @Override
    public final void onConnectionConfigChange(final ConnectionInfo connectionInfo) {
        if (mServiceBinder != null && mServiceBinder.isServiceBound()) {
            final Bundle bundle = new Bundle();
            bundle.setClassLoader(ConnectionInfo.class.getClassLoader());
            bundle.putParcelable(ConnectionInfo.BUNDLE_KEY, connectionInfo);
            mServiceBinder.sendMessageToService(MPDroidService.CONNECTION_INFO_CHANGED, bundle);
        }
    }

    @Override
    public final void onCreate() {
        super.onCreate();
        sInstance = this;
        Log.d(TAG, "onCreate Application");

        mSettings = getDefaultSharedPreferences(this);

        final StrictMode.VmPolicy vmPolicy = new StrictMode.VmPolicy.Builder().penaltyLog().build();
        final StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll()
                .build();
        StrictMode.setThreadPolicy(policy);
        StrictMode.setVmPolicy(vmPolicy);

        // Init the default preferences (meaning we won't have different defaults between code/xml)
        androidx.preference.PreferenceManager.setDefaultValues(this, R.xml.settings, false);

        oMPDAsyncHelper = new MPDAsyncHelper();
        mSettingsHelper = new SettingsHelper(oMPDAsyncHelper);
        oMPDAsyncHelper.addConnectionListener(this);

        mDisconnectScheduler = new Timer();
        
        // Register NetworkCallback for modern Android versions (API 23+)
        registerNetworkCallback();
    }
    
    @Override
    public void onTerminate() {
        // Clean up NetworkCallback when application terminates
        unregisterNetworkCallback();
        super.onTerminate();
    }
    
    /**
     * Registers a NetworkCallback to monitor network changes and trigger reconnection.
     * Uses modern API for Android 6.0+ (API 23+).
     */
    private void registerNetworkCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                final ConnectivityManager cm =
                        (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm == null) {
                    return;
                }

                final NetworkRequest.Builder builder = new NetworkRequest.Builder();
                // Request WiFi and Ethernet networks with internet capability
                builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                builder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
                builder.addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET);

                mNetworkCallback = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(final Network network) {
                        Log.d(TAG, "Network available, checking if reconnection needed");
                        // Use Handler to delay reconnection slightly
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (oMPDAsyncHelper != null && oMPDAsyncHelper.oMPD != null &&
                                        !oMPDAsyncHelper.oMPD.isConnected() &&
                                        !mConnectionLocks.isEmpty()) {
                                    Log.d(TAG, "Attempting to reconnect to MPD");
                                    connect();
                                }
                            }
                        }, 2000L); // Wait 2 seconds for network to stabilize
                    }

                    @Override
                    public void onLost(final Network network) {
                        Log.d(TAG, "Network lost");
                        // Connection will be detected as lost by MPDStatusMonitor
                    }

                    @Override
                    public void onCapabilitiesChanged(final Network network,
                            final NetworkCapabilities networkCapabilities) {
                        // Network capabilities changed, check if we should reconnect
                        if (networkCapabilities.hasCapability(
                                NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                                (networkCapabilities.hasTransport(
                                        NetworkCapabilities.TRANSPORT_WIFI) ||
                                 networkCapabilities.hasTransport(
                                         NetworkCapabilities.TRANSPORT_ETHERNET))) {
                            onAvailable(network);
                        }
                    }
                };

                cm.registerNetworkCallback(builder.build(), mNetworkCallback);
                Log.d(TAG, "NetworkCallback registered for network monitoring");
            } catch (final Exception e) {
                Log.w(TAG, "Failed to register NetworkCallback", e);
                mNetworkCallback = null;
            }
        }
    }

    /**
     * Unregisters the NetworkCallback. Should be called when the application is being destroyed.
     */
    private void unregisterNetworkCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mNetworkCallback != null) {
            try {
                final ConnectivityManager cm =
                        (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) {
                    cm.unregisterNetworkCallback(mNetworkCallback);
                    Log.d(TAG, "NetworkCallback unregistered");
                }
            } catch (final Exception e) {
                Log.w(TAG, "Failed to unregister NetworkCallback", e);
            }
            mNetworkCallback = null;
        }
    }

    /**
     * This removes the connection lock which allows the service to disconnect after timeout.
     *
     * @param lockOwner The instance declaring itself as an owner of the connection lock.
     */
    public final void removeConnectionLock(final Object lockOwner) {
        mConnectionLocks.remove(lockOwner);
        checkConnectionNeeded();
    }

    public final void setActivity(final Object activity) {
        if (activity instanceof Activity) {
            mCurrentActivity = (Activity) activity;
        }

        addConnectionLock(activity);
    }

    /**
     * Set this to override the persistent notification for the current session.
     *
     * @param override True to override persistent notification, false otherwise.
     */
    public final void setPersistentOverride(final boolean override) {
        if (mIsNotificationOverridden != override) {
            mIsNotificationOverridden = override;

            setupServiceBinder();
            mServiceBinder.sendMessageToService(NotificationHandler.PERSISTENT_OVERRIDDEN,
                    mIsNotificationOverridden);
        }
    }

    /**
     * Sets up the service binder class. This needs to run the initial running Activity, not in the
     * Application, to prevent the service process from spawning it's own service binder class.
     */
    public final void setupServiceBinder() {
        if (mServiceBinder == null) {
            final Handler handler = new Handler(this);
            mServiceBinder = new ServiceBinder(this, handler);
            mServiceBinder.sendMessageToService(MPDroidService.UPDATE_CLIENT_STATUS);
        }
    }

    private void startDisconnectScheduler() {
        try {
            mDisconnectScheduler.schedule(new TimerTask() {
                @Override
                public void run() {
                    Log.w(TAG, "Disconnecting (" + DISCONNECT_TIMER + " ms timeout)");
                    oMPDAsyncHelper.stopStatusMonitor();
                    oMPDAsyncHelper.disconnect();
                }
            }, DISCONNECT_TIMER);
        } catch (final IllegalStateException e) {
            Log.d(TAG, "Disconnection timer interrupted.", e);
        }
    }

    public final void startNotification() {
        if (!mIsNotificationActive) {
            if (DEBUG) {
                Log.d(TAG, "Starting notification.");
            }
            setupServiceBinder();
            mServiceBinder
                    .sendMessageToService(NotificationHandler.START, isNotificationPersistent());
        }
    }

    public final void startStreaming() {
        if (!mIsStreamActive) {
            if (DEBUG) {
                Log.d(TAG, "Starting stream.");
            }
            setupServiceBinder();
            mServiceBinder.sendMessageToService(StreamHandler.START);
        }
    }

    public final void stopNotification() {
        if (DEBUG) {
            Log.d(TAG, "Stop notification.");
        }
        setupServiceBinder();
        mServiceBinder.sendMessageToService(NotificationHandler.STOP);
    }

    public final void stopStreaming() {
        if (DEBUG) {
            Log.d(TAG, "Stop streaming.");
        }
        setupServiceBinder();
        mServiceBinder.sendMessageToService(StreamHandler.STOP);
    }

    public final void unsetActivity(final Object activity) {
        removeConnectionLock(activity);

        if (mCurrentActivity != null && mCurrentActivity.equals(activity)) {
            mCurrentActivity = null;
        }
    }

    private class DialogClickListener implements OnClickListener {

        @Override
        public final void onClick(final DialogInterface dialog, final int which) {
            switch (which) {
                case DialogInterface.BUTTON_NEUTRAL:
                    final Intent intent =
                            new Intent(mCurrentActivity, WifiConnectionSettings.class);
                    // Show Settings
                    mCurrentActivity.startActivityForResult(intent, SETTINGS);
                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    mCurrentActivity.finish();
                    break;
                case DialogInterface.BUTTON_POSITIVE:
                    connectMPD();
                    break;
                default:
                    break;
            }
        }
    }
}
