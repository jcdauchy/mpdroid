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

import com.namelessdev.mpdroid.tools.SettingsHelper;

import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * A dedicated settings panel for the pySpotify service (host, port, credentials), with a way to
 * test that the currently entered settings actually work.
 */
@SuppressWarnings("deprecation")
public class PySpotifySettings extends PreferenceActivity {

    public static final int MAIN = 0;

    private Preference mTestConnectionPreference;

    private void testConnection() {
        final MPDApplication app = MPDApplication.getInstance();

        mTestConnectionPreference.setEnabled(false);
        mTestConnectionPreference.setSummary(R.string.pySpotifyTestingConnection);

        app.oMPDAsyncHelper.execAsync(new Runnable() {
            @Override
            public void run() {
                final SettingsHelper settingsHelper = new SettingsHelper(app.oMPDAsyncHelper);
                final String result = runConnectionTest(settingsHelper);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTestConnectionPreference.setSummary(result);
                        mTestConnectionPreference.setEnabled(true);
                        Toast.makeText(PySpotifySettings.this, result, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private String runConnectionTest(final SettingsHelper settingsHelper) {
        HttpURLConnection connection = null;

        try {
            final URL url = new URL(settingsHelper.getPySpotifyBaseUrl() + "/");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);

            final String authHeader = settingsHelper.getPySpotifyAuthHeader();
            if (authHeader != null) {
                connection.setRequestProperty("Authorization", authHeader);
            }

            final int responseCode = connection.getResponseCode();
            final String result;

            if (responseCode >= 200 && responseCode < 300) {
                result = getString(R.string.pySpotifyTestSuccess);
            } else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED
                    || responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                result = getString(R.string.pySpotifyTestAuthFailed);
            } else {
                result = getString(R.string.pySpotifyTestServerError, responseCode);
            }

            return result;
        } catch (final IOException e) {
            return getString(R.string.pySpotifyTestConnectionFailed, e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(this);
        setPreferenceScreen(screen);

        final EditTextPreference prefHost = new EditTextPreference(this);
        prefHost.getEditText().setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        prefHost.setDialogTitle(R.string.pySpotifyHost);
        prefHost.setTitle(R.string.pySpotifyHost);
        prefHost.setSummary(R.string.pySpotifyHostDescription);
        prefHost.setDefaultValue("percy.myddns.me");
        prefHost.setKey("pySpotifyHost");
        screen.addPreference(prefHost);

        final EditTextPreference prefPort = new EditTextPreference(this);
        prefPort.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
        prefPort.setDialogTitle(R.string.pySpotifyPort);
        prefPort.setTitle(R.string.pySpotifyPort);
        prefPort.setSummary(R.string.pySpotifyPortDescription);
        prefPort.setDefaultValue("55000");
        prefPort.setKey("pySpotifyPort");
        screen.addPreference(prefPort);

        final CheckBoxPreference prefHttps = new CheckBoxPreference(this);
        prefHttps.setDefaultValue(true);
        prefHttps.setTitle(R.string.pySpotifyUseHttps);
        prefHttps.setSummary(R.string.pySpotifyUseHttpsDescription);
        prefHttps.setKey("pySpotifyHttps");
        screen.addPreference(prefHttps);

        final EditTextPreference prefUsername = new EditTextPreference(this);
        prefUsername.getEditText().setInputType(InputType.TYPE_CLASS_TEXT);
        prefUsername.setDialogTitle(R.string.pySpotifyUsername);
        prefUsername.setTitle(R.string.pySpotifyUsername);
        prefUsername.setSummary(R.string.pySpotifyUsernameDescription);
        prefUsername.setDefaultValue("");
        prefUsername.setKey("pySpotifyUsername");
        screen.addPreference(prefUsername);

        final EditTextPreference prefPassword = new EditTextPreference(this);
        prefPassword.getEditText().setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        prefPassword.setDialogTitle(R.string.pySpotifyPassword);
        prefPassword.setTitle(R.string.pySpotifyPassword);
        prefPassword.setSummary(R.string.pySpotifyPasswordDescription);
        prefPassword.setDefaultValue("");
        prefPassword.setKey("pySpotifyPassword");
        screen.addPreference(prefPassword);

        mTestConnectionPreference = new Preference(this);
        mTestConnectionPreference.setTitle(R.string.pySpotifyTestConnection);
        mTestConnectionPreference.setSummary(R.string.pySpotifyTestConnectionDescription);
        mTestConnectionPreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(final Preference preference) {
                testConnection();
                return true;
            }
        });
        screen.addPreference(mTestConnectionPreference);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        final boolean result = super.onCreateOptionsMenu(menu);
        menu.add(0, MAIN, 0, R.string.mainMenu).setIcon(android.R.drawable.ic_menu_revert);

        return result;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final boolean result;

        if (item.getItemId() == MAIN) {
            final Intent intent = new Intent(this, MainMenuActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            result = true;
        } else {
            result = super.onOptionsItemSelected(item);
        }

        return result;
    }
}
