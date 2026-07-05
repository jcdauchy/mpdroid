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

package com.namelessdev.mpdroid.service;

import com.namelessdev.mpdroid.ConnectionInfo;
import com.namelessdev.mpdroid.R;
import com.namelessdev.mpdroid.helpers.MPDControl;

import org.a0z.mpd.MPDStatus;

import android.content.res.Resources;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.StringRes;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;

/**
 * StreamHandler hooks Android's audio framework to the
 * user's MPD streaming server to allow local audio playback.
 *
 * @author Arnaud Barisain Monrose (Dream_Team)
 */
public final class StreamHandler implements OnAudioFocusChangeListener, Player.Listener {

    /** This is the class unique Binder identifier. */
    static final int LOCAL_UID = 400;

    /** Messages that can be sent to clients. */
    public static final int IS_ACTIVE = LOCAL_UID + 1;

    /** Message to send to start this handler. */
    public static final int START = LOCAL_UID + 2;

    /** Message to send to stop this handler. */
    public static final int STOP = LOCAL_UID + 3;

    /** Kills (or hides) the notification if StreamHandler started it. */
    static final int REQUEST_NOTIFICATION_STOP = LOCAL_UID + 4;

    /** Keeps the notification alive, but puts it in non-streaming status. */
    static final int STREAMING_STOP = LOCAL_UID + 5;

    /** Let notification know it's time to display buffering banner. */
    static final int BUFFERING_BEGIN = LOCAL_UID + 6;

    /** Remove the buffering banner from the notification handler. */
    static final int BUFFERING_END = LOCAL_UID + 7;

    /** Like STREAMING_STOP, but does allows streaming to continue on audio state change. */
    static final int STREAMING_PAUSE = LOCAL_UID + 8;

    /**
     * Reports how far ahead of the playback position ExoPlayer currently has buffered, in
     * milliseconds, as the message's {@code arg1}.
     */
    public static final int BUFFER_STATUS = LOCAL_UID + 9;

    private static final boolean DEBUG = MPDroidService.DEBUG;

    /**
     * Called as an argument to windDownResources() when a
     * message is not required to send to the service.
     */
    private static final int INVALID_INT = -1;

    /** How often the buffered-ahead duration is reported to clients, in milliseconds. */
    private static final long BUFFER_STATUS_INTERVAL_MS = 1000L;

    /** How far ahead of playback position ExoPlayer is allowed to buffer, in milliseconds. */
    private static final int MAX_BUFFER_MS = 60_000;

    /** How far ahead ExoPlayer tries to stay buffered once already playing, in milliseconds. */
    private static final int MIN_BUFFER_MS = 15_000;

    private static final String TAG = "StreamHandler";

    private static final String FULLY_QUALIFIED_NAME = "com.namelessdev.mpdroid.service." + TAG;

    public static final String ACTION_START = FULLY_QUALIFIED_NAME + ".ACTION_START";

    public static final String ACTION_STOP = FULLY_QUALIFIED_NAME + ".ACTION_STOP";

    private final ConnectionInfo mConnectionInfo
            = MPDroidService.MPD_ASYNC_HELPER.getConnectionSettings();

    /** Handler used to periodically poll and report the current buffered-ahead duration. */
    private final Handler mBufferStatusHandler = new Handler(Looper.getMainLooper());

    private final Runnable mBufferStatusRunnable = new Runnable() {
        @Override
        public void run() {
            reportBufferStatus();
            mBufferStatusHandler.postDelayed(this, BUFFER_STATUS_INTERVAL_MS);
        }
    };

    /** The service context used to acquire the wake lock. */
    private final MPDroidService mServiceContext;

    /** The audio manager used to obtain audio focus. */
    private AudioManager mAudioManager = null;

    /** Keep track of the number of errors encountered. */
    private int mErrorIterator = 0;

    /** Is this handler active? */
    private boolean mIsActive = false;

    /** Is MPD playing? */
    private boolean mIsPlaying = false;

    private ExoPlayer mPlayer = null;

    /** Keep track of the initial buffering, before the first playback of a stream start. */
    private boolean mPreparingStream = false;

    /** Service handler used for communicating with service. */
    private Handler mServiceHandler = null;

    /**
     * The {@code ExoPlayer} streaming interface for the {@code MPDroidService},
     *
     * @param serviceContext The {@code MPDroidService} instance/context.
     * @param serviceHandler The {@code MPDroidService} {@code Handler}.
     * @param audioManager   The {@code AudioManager} from the service; don't acquire a
     *                       {@code AudioManager} from this context as {@code AudioManager} is
     *                       touchy about whom grabs focus.
     */
    StreamHandler(final MPDroidService serviceContext, final Handler serviceHandler,
            final AudioManager audioManager) {
        super();
        if (DEBUG) {
            Log.d(TAG, "StreamHandler constructor.");
        }

        mServiceContext = serviceContext;
        mAudioManager = audioManager;
        mServiceHandler = serviceHandler;
    }

    /**
     * A function to translate 'what' fields to literal debug name, used primarily for debugging.
     *
     * @param what A 'what' field.
     * @return The literal field name.
     */
    public static String getHandlerValue(final int what) {
        final String result;

        switch (what) {
            case IS_ACTIVE:
                result = "IS_ACTIVE";
                break;
            case START:
                result = "START";
                break;
            case STOP:
                result = "STOP";
                break;
            case REQUEST_NOTIFICATION_STOP:
                result = "REQUEST_NOTIFICATION_STOP";
                break;
            case STREAMING_STOP:
                result = "STREAMING_STOP";
                break;
            case BUFFERING_BEGIN:
                result = "BUFFERING_BEGIN";
                break;
            case BUFFERING_END:
                result = "BUFFERING_END";
                break;
            case STREAMING_PAUSE:
                result = "STREAMING_PAUSE";
                break;
            case BUFFER_STATUS:
                result = "BUFFER_STATUS";
                break;
            default:
                result = "{unknown}: " + what;
                break;
        }
        return "StreamHandler." + result;
    }

    /** This is where the stream start is managed. */
    private void beginStreaming() {
        if (DEBUG) {
            Log.d(TAG, "StreamHandler.beginStreaming()");
        }
        if (mPlayer == null) {
            windUpResources();
        }

        mServiceHandler.sendEmptyMessage(BUFFERING_BEGIN);
        final String streamSource = getStreamSource();
        mPreparingStream = true;
        mServiceHandler.removeMessages(STOP);

        mPlayer.setMediaItem(MediaItem.fromUri(streamSource));
        mPlayer.prepare();
    }

    /** Get the current server streaming URL. */
    private String getStreamSource() {
        return "http://" + mConnectionInfo.streamServer + ':' + mConnectionInfo.streamPort + '/';
    }

    boolean isActive() {
        return mIsActive;
    }

    /**
     * Handle the change of volume if a notification, or any other kind of
     * interrupting audio event.
     *
     * @param focusChange The type of focus change.
     */
    @Override
    public void onAudioFocusChange(final int focusChange) {
        if (DEBUG) {
            Log.d(TAG, "StreamHandler.onAudioFocusChange() with " + focusChange);
        }

        if (mPlayer != null) {
            final float duckVolume = 0.2f;

            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    if (mPlayer.isPlaying()) {
                        if (DEBUG) {
                            Log.d(TAG, "Regaining after ducked transient loss.");
                        }
                        mPlayer.setVolume(1.0f);
                    } else if (!mPreparingStream) {
                        if (DEBUG) {
                            Log.d(TAG, "Coming out of transient loss.");
                        }
                        mPlayer.play();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    MPDControl.run(MPDroidService.MPD_ASYNC_HELPER.oMPD, MPDControl.ACTION_PAUSE);
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    mPlayer.pause();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    mPlayer.setVolume(duckVolume);
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * An ExoPlayer callback invoked whenever the playback state changes, including the initial
     * buffer-up and any later stalls/rebuffers.
     *
     * @param playbackState The new {@link Player.State}.
     */
    @Override
    public void onPlaybackStateChanged(final int playbackState) {
        if (DEBUG) {
            Log.d(TAG, "onPlaybackStateChanged(" + playbackState + ") received.");
        }

        switch (playbackState) {
            case Player.STATE_BUFFERING:
                mServiceHandler.sendEmptyMessage(BUFFERING_BEGIN);
                break;
            case Player.STATE_READY:
                if (mPreparingStream) {
                    onInitialBufferComplete();
                } else {
                    mServiceHandler.sendEmptyMessage(BUFFERING_END);
                }
                break;
            case Player.STATE_ENDED:
                onStreamEnded();
                break;
            default:
                break;
        }
    }

    /**
     * An ExoPlayer callback to be invoked when there has been an error during playback.
     *
     * @param error The error that occurred.
     */
    @Override
    public void onPlayerError(final PlaybackException error) {
        if (DEBUG) {
            Log.d(TAG, "onPlayerError() received.", error);
        }

        showErrorToUser(R.string.mediaPlayerErrorIO, error);

        final int maxError = 4;

        if (mErrorIterator > 0) {
            Log.d(TAG, "Error occurred while streaming, this is try #" + mErrorIterator
                    + ", will attempt up to " + maxError + " times.");
        }

        /** This keeps from continuous errors and battery draining. */
        if (mErrorIterator > maxError) {
            stop();
        }

        /** beginStreaming() will never start otherwise. */
        mPreparingStream = false;

        /** Either way we need to stop streaming. */
        windDownResources(STREAMING_STOP);

        mErrorIterator += 1;
    }

    /**
     * Handles the first time the player reaches {@link Player#STATE_READY} after a call to
     * {@link #beginStreaming()}, analogous to the old {@code MediaPlayer.OnPreparedListener}.
     */
    private void onInitialBufferComplete() {
        final int focusResult;

        if (mIsPlaying && mIsActive) {
            focusResult = mAudioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
        } else {
            focusResult = AudioManager.AUDIOFOCUS_REQUEST_FAILED;
        }

        if (focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            mServiceHandler.sendEmptyMessage(BUFFERING_END);
            mPlayer.play();
        } else {
            if (mIsActive && mIsPlaying) {
                showErrorToUser(R.string.audioFocusFailed);
            }

            /** Because mPreparingStream is still set, this will reset the stream. */
            windDownResources(STREAMING_STOP);
        }

        mPreparingStream = false;
        mErrorIterator = 0; /** Reset the error iterator. */
    }

    /**
     * Handles the stream reaching {@link Player#STATE_ENDED}, analogous to the old
     * {@code MediaPlayer.OnCompletionListener}.
     */
    private void onStreamEnded() {
        /**
         * If MPD is restarted during streaming, the stream will end. stateChanged() won't be
         * called. If we still detect playing, restart the stream.
         */
        if (mIsPlaying) {
            tryToStream();
        } else {
            /**
             * The only way we make it here is with an empty playlist. Don't send a
             * message to the notification, it already knows to stop on empty playlist.
             */
            windDownResources(INVALID_INT);
        }
    }

    /**
     * Reports the contents of an error string to the user via a Log and Toast.
     *
     * @param resId The resourceID of the translated string to show the user.
     * @param e     The exception to go to the {@code Log}.
     */
    private void showErrorToUser(@StringRes final int resId, final Exception e) {
        final Resources resources = mServiceContext.getResources();
        final String error = resources.getString(resId);

        showErrorToUser(error, e);
    }

    /**
     * Reports the contents of an error string to the user via a Log and Toast.
     *
     * @param resId The resourceID of the translated string to show the user.
     */
    private void showErrorToUser(@StringRes final int resId) {
        showErrorToUser(resId, null);
    }

    /**
     * Reports the contents of an error string to the user via a Log and Toast.
     *
     * @param userOutput The error to show the user.
     * @param e          The exception to go to the {@code Log}, may be null.
     */
    private void showErrorToUser(final String userOutput, final Exception e) {
        if (e == null) {
            Log.e(TAG, userOutput);
        } else {
            Log.e(TAG, userOutput, e);
        }

        /** Let users know where the Toast is coming from, and why. */
        final Resources resources = mServiceContext.getResources();
        final String appName = resources.getString(R.string.app_name);
        final String toastOutput = resources.getString(R.string.streamError, appName, userOutput);

        Toast.makeText(mServiceContext, toastOutput, Toast.LENGTH_LONG).show();
    }

    void start(final int mpdState) {
        mIsActive = true;
        mIsPlaying = MPDStatus.STATE_PLAYING == mpdState;
        if (!mPreparingStream && mIsPlaying) {
            tryToStream();
        }
    }

    /**
     * A JMPDComm callback which is invoked on MPD status change.
     *
     * @param mpdStatus MPDStatus after event.
     */
    void stateChanged(final MPDStatus mpdStatus) {
        if (DEBUG) {
            Log.d(TAG, "StreamHandler.stateChanged()");
        }

        final int state = mpdStatus.getState();

        if (mIsActive) {
            switch (state) {
                case MPDStatus.STATE_PLAYING:
                    mServiceHandler.removeMessages(STOP);
                    mIsPlaying = true;
                    tryToStream();
                    break;
                case MPDStatus.STATE_STOPPED:
                    /** Detect final song and let onStreamEnded() handle it */
                    if (mpdStatus.getNextSongPos() == -1 || mpdStatus.getPlaylistLength() == 0) {
                        break;
                    }
                    /** Fall Through */
                case MPDStatus.STATE_PAUSED:
                    /**
                     * If in the middle of stream preparation, "Buffering…" notification message
                     * is likely.
                     */
                    if (mPreparingStream) {
                        windDownResources(BUFFERING_END);
                    } else {
                        windDownResources(STREAMING_PAUSE);
                    }
                    mIsPlaying = false;
                    break;
                default:
                    break;
            }
        }
    }

    void stop() {
        if (DEBUG) {
            Log.d(TAG, "StreamHandler.stop()");
        }

        mAudioManager.abandonAudioFocus(this);

        windDownResources(REQUEST_NOTIFICATION_STOP);

        mIsActive = false;
    }

    /**
     * If streaming mode is activated this will setup the Android mediaPlayer
     * framework, register the media button events, register the remote control
     * client then setup and the framework streaming.
     */
    private void tryToStream() {
        if (mPreparingStream) {
            Log.d(TAG, "A stream is already being prepared.");
        } else if (!mIsPlaying) {
            Log.d(TAG, "MPD is not currently playing, can't stream.");
        } else {
            beginStreaming();
        }
    }

    /**
     * windDownResources occurs after a delay or during stopSelf() to
     * clean up resources and give up focus to the phone and sound.
     */
    private void windDownResources(final int action) {
        if (DEBUG) {
            Log.d(TAG, "Winding down resources.");
        }

        if (action != INVALID_INT) {
            mServiceHandler.sendEmptyMessage(action);
        }

        mBufferStatusHandler.removeCallbacks(mBufferStatusRunnable);

        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }

        mPreparingStream = false;
    }

    /**
     * Reports how far ahead of the current playback position ExoPlayer has buffered, so the UI
     * can show it (e.g. in the "Now Playing" screen).
     */
    private void reportBufferStatus() {
        if (mPlayer != null) {
            final int bufferedAheadMs = (int) Math.max(0,
                    mPlayer.getBufferedPosition() - mPlayer.getCurrentPosition());
            mServiceHandler.obtainMessage(BUFFER_STATUS, bufferedAheadMs, 0).sendToTarget();
        }
    }

    /**
     * This happens at the beginning of beginStreaming() to populate all
     * necessary resources for handling the ExoPlayer stream.
     */
    private void windUpResources() {
        if (DEBUG) {
            Log.d(TAG, "Winding up resources.");
        }

        /**
         * MPD's httpd output is a continuous, unbounded stream with no fixed end and no
         * Content-Length. A generous buffer window lets ExoPlayer read well ahead of the
         * playback position so brief network hiccups don't cause an audible stall.
         */
        final DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        MIN_BUFFER_MS,
                        MAX_BUFFER_MS,
                        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
                .build();

        mPlayer = new ExoPlayer.Builder(mServiceContext)
                .setLoadControl(loadControl)
                .build();
        mPlayer.addListener(this);
        mPlayer.setWakeMode(C.WAKE_MODE_NETWORK);

        final AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build();

        /** Audio focus is requested/abandoned manually (see onAudioFocusChange()). */
        mPlayer.setAudioAttributes(audioAttributes, false);

        mBufferStatusHandler.removeCallbacks(mBufferStatusRunnable);
        mBufferStatusHandler.post(mBufferStatusRunnable);
    }
}
