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

package com.namelessdev.mpdroid.cover;

import com.namelessdev.mpdroid.helpers.CoverManager;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public abstract class AbstractWebCover implements ICoverRetriever {

    private static final boolean DEBUG = CoverManager.DEBUG;

    private static final String TAG = "AbstractWebCover";

    protected String executeGetRequest(final String requestUrl) throws IOException {
        HttpURLConnection connection = null;
        try {
            final URL url = new URL(requestUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setUseCaches(false);
            connection.setAllowUserInteraction(false);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("GET");
            connection.connect();
            final int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                return readInputStream(connection.getInputStream());
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

    protected String executeGetRequestWithConnection(final String request) throws IOException {
        return executeGetRequest(request);
    }

    protected String executePostRequest(final String requestUrl, final String request) throws IOException {
        HttpURLConnection connection = null;
        try {
            final URL url = new URL(requestUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setUseCaches(false);
            connection.setAllowUserInteraction(false);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/xml");
            connection.setRequestProperty("Accept", "application/xml");
            try (final OutputStream os = connection.getOutputStream()) {
                os.write(request.getBytes());
            }
            final int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                return readInputStream(connection.getInputStream());
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

    private String readInputStream(final InputStream content) throws IOException {
        try (final BufferedInputStream bis = new BufferedInputStream(content);
             final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            final byte[] buffer = new byte[1024];
            int read;
            while ((read = bis.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            return baos.toString();
        }
    }

    @Override
    public boolean isCoverLocal() {
        return false;
    }
}
