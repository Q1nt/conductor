/*
 * Copyright 2018 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.conductor.client.util;

import com.netflix.conductor.client.exceptions.ConductorClientException;
import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * An implementation of {@link ExternalPayloadStorage} for storing large JSON payload data.
 */
public class PayloadStorage implements ExternalPayloadStorage {
    private static final Logger logger = LoggerFactory.getLogger(PayloadStorage.class);

    /**
     * This method is not intended to be used in the client.
     * The client makes a request to the server to get the {@link ExternalStorageLocation}
     */
    @Override
    public ExternalStorageLocation getExternalUri(Operation operation, PayloadType payloadType) {
        throw new UnsupportedOperationException();
    }

    /**
     * Uploads the given payload to the path specified.
     *
     * @param path        the location to which the object is to be uploaded
     * @param payload     an {@link InputStream} containing the json payload which is to be uploaded
     * @param payloadSize the size of the json payload in bytes
     */
    @Override
    public void upload(String path, InputStream payload, long payloadSize) {
        HttpURLConnection connection = null;
        try {
            URL url = new URI(path).toURL();

            connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("PUT");

            try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(connection.getOutputStream())) {
                int length;
                while ((length = payload.read()) != -1) {
                    bufferedOutputStream.write(length);
                }

                // Check the HTTP response code
                int responseCode = connection.getResponseCode();
                logger.debug("Upload completed with HTTP response code: {}", responseCode);
            }
        } catch (URISyntaxException | MalformedURLException e) {
            String errorMsg = String.format("Invalid path specified: %s", path);
            logger.error(errorMsg, e);
            throw new ConductorClientException(errorMsg, e);
        } catch (IOException e) {
            String errorMsg = String.format("Error uploading to S3: %s", path);
            logger.error(errorMsg, e);
            throw new ConductorClientException(errorMsg, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     *
     * @param path the location from where the object is to be downloaded
     * @return
     */
    @Override
    public InputStream download(String path) {
        HttpURLConnection connection = null;
        try {
            URL url = new URI(path).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(false);

            // Check the HTTP response code
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                logger.debug("Download completed with HTTP response code: {}", connection.getResponseCode());
                return connection.getInputStream();
            }
            logger.info("No file to download. Response code: {}", responseCode);
            return null;
        } catch (URISyntaxException | MalformedURLException e) {
            String errorMsg = String.format("Invalid path specified: %s", path);
            logger.error(errorMsg, e);
            throw new ConductorClientException(errorMsg, e);
        } catch (IOException e) {
            String errorMsg = String.format("Error downloading from S3: %s", path);
            logger.error(errorMsg, e);
            throw new ConductorClientException(errorMsg, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
