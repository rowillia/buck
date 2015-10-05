/*
 * Copyright 2014-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.file;

import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.util.HumanReadableException;
import com.google.common.base.Optional;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Download a file over HTTP.
 */
public class HttpDownloader implements Downloader {
  public static final int PROGRESS_REPORT_EVERY_N_BYTES = 1000;
  private final Optional<Proxy> proxy;

  public HttpDownloader(Optional<Proxy> proxy) {
    this.proxy = proxy;
  }

  @Override
  public boolean fetch(BuckEventBus eventBus, URI uri, Path output) throws IOException {
    if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) {
      return false;
    }

    DownloadEvent.Started started = DownloadEvent.started(uri);
    eventBus.post(started);

    try {
      HttpURLConnection connection = createConnection(uri);
      if (HttpURLConnection.HTTP_OK != connection.getResponseCode()) {
        throw new IOException(
            String.format("Unable to download %s: %s", uri, connection.getResponseMessage()));
      }
      long contentLength = connection.getContentLengthLong();
      try (InputStream is = new BufferedInputStream(connection.getInputStream());
           OutputStream os = new BufferedOutputStream(Files.newOutputStream(output))) {
        long read = 0;

        while (true) {
          int r = is.read();
          read++;
          if (r == -1) {
            break;
          }
          if (read % PROGRESS_REPORT_EVERY_N_BYTES == 0) {
            eventBus.post(new DownloadProgressEvent(uri, contentLength, read));
          }
          os.write(r);
        }
      }

      return true;
    } finally {
      eventBus.post(DownloadEvent.finished(started));
    }
  }

  protected HttpURLConnection createConnection(URI uri) throws IOException {
    HttpURLConnection connection;
    if (proxy.isPresent()) {
      connection = (HttpURLConnection) uri.toURL().openConnection(proxy.get());
    } else {
      connection = (HttpURLConnection) uri.toURL().openConnection();
    }
    connection.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(20));
    connection.setInstanceFollowRedirects(true);

    return connection;
  }
}
