/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * Copyright 2017 Nextdoor.com, Inc
 *
 */

package com.nextdoor.bender.ipc.http;

import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Callable;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import com.evanlennick.retry4j.CallExecutor;
import com.evanlennick.retry4j.RetryConfig;
import com.evanlennick.retry4j.RetryConfigBuilder;
import com.evanlennick.retry4j.exception.RetriesExhaustedException;
import com.evanlennick.retry4j.exception.UnexpectedException;
import com.nextdoor.bender.ipc.TransportBuffer;
import com.nextdoor.bender.ipc.TransportException;
import com.nextdoor.bender.ipc.UnpartitionedTransport;
import com.nextdoor.bender.ipc.generic.GenericTransportBuffer;

/**
 * Generic HTTP Transport. The HTTP client must be configured by your transport factory.
 */
public class HttpTransport implements UnpartitionedTransport {
  private final HttpClient client;
  protected final boolean useGzip;
  private final long retryDelayMs;
  private final int retries;
  private final String url;

  private static final Logger logger = Logger.getLogger(HttpTransport.class);

  public HttpTransport(HttpClient client, String url, boolean useGzip, int retries,
      long retryDelayMs) {
    this.client = client;
    this.url = url;
    this.useGzip = useGzip;
    this.retries = retries;
    this.retryDelayMs = retryDelayMs;
  }

  protected ContentType getUncompressedContentType() {
    return ContentType.DEFAULT_TEXT;
  }

  protected HttpResponse sendBatchUncompressed(HttpPost httpPost, byte[] raw)
      throws TransportException {
    HttpEntity entity = new ByteArrayEntity(raw, getUncompressedContentType());
    httpPost.setEntity(entity);

    /*
     * Make call
     */
    HttpResponse resp = null;
    try {
      resp = this.client.execute(httpPost);
    } catch (IOException e) {
      throw new TransportException("failed to make call", e);
    }

    return resp;
  }

  protected HttpResponse sendBatchCompressed(HttpPost httpPost, byte[] raw)
      throws TransportException {
    /*
     * Write gzip data to Entity and set content encoding to gzip
     */
    ByteArrayEntity entity = new ByteArrayEntity(raw, ContentType.DEFAULT_BINARY);
    entity.setContentEncoding("gzip");

    httpPost.addHeader(new BasicHeader("Accept-Encoding", "gzip"));
    httpPost.setEntity(entity);

    /*
     * Make call
     */
    HttpResponse resp = null;
    try {
      resp = this.client.execute(httpPost);
    } catch (IOException e) {
      throw new TransportException("failed to make call", e);
    }

    return resp;
  }

  public void sendBatch(TransportBuffer buf) throws TransportException {
    GenericTransportBuffer buffer = (GenericTransportBuffer) buf;
    sendBatch(buffer.getInternalBuffer().toByteArray());
  }

  public void sendBatch(byte[] raw) throws TransportException {
    /*
     * Wrap the call with retry logic to avoid intermittent ES issues.
     */
    Callable<HttpResponse> callable = () -> {
      HttpResponse resp;
      String responseString = null;
      HttpPost httpPost = new HttpPost(this.url);

      /*
       * Do the call, read response, release connection so it is available for use again, and
       * finally check the response.
       */
      try {
        if (this.useGzip) {
          resp = sendBatchCompressed(httpPost, raw);
        } else {
          resp = sendBatchUncompressed(httpPost, raw);
        }

        try {
          responseString = EntityUtils.toString(resp.getEntity());
        } catch (ParseException | IOException e) {
          throw new TransportException(
              "http transport call failed because " + resp.getStatusLine().getReasonPhrase());
        }
      } finally {
        /*
         * Always release connection otherwise it blocks future requests.
         */
        httpPost.releaseConnection();
      }

      checkResponse(resp, responseString);
      return resp;
    };

    RetryConfig config = new RetryConfigBuilder()
        .retryOnSpecificExceptions(TransportException.class).withMaxNumberOfTries(this.retries + 1)
        .withDelayBetweenTries(this.retryDelayMs, ChronoUnit.MILLIS).withExponentialBackoff()
        .build();

    try {
      new CallExecutor(config).execute(callable);
    } catch (RetriesExhaustedException ree) {
      logger.warn("transport failed after " + ree.getCallResults().getTotalTries() + " tries.");
      throw new TransportException(ree.getCallResults().getLastExceptionThatCausedRetry());
    } catch (UnexpectedException ue) {
      throw new TransportException(ue);
    }
  }

  /**
   * Processes the response sent back by HTTP server. Override this method to implement custom
   * response processing logic. Note connection is already released when this method is called.
   * 
   * @param resp Response object from server.
   * @param responseString Response string message from server.
   * @throws TransportException when HTTP call was unsuccessful.
   */
  public void checkResponse(HttpResponse resp, String responseString) throws TransportException {
    /*
     * Check responses status code of the overall bulk call.
     */
    if (resp.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
      return;
    }

    throw new TransportException(
        "http transport call failed because \"" + resp.getStatusLine().getReasonPhrase()
            + "\" payload response \"" + responseString + "\"");
  }
}
