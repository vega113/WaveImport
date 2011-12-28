/*
 * Copyright 2011 Google Inc. All Rights Reserved.
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

package com.google.walkaround.wave.server.auth;

import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 * A fetch service that signs fetch requests with OAuth2.
 *
 * @author hearnden@google.com (David Hearnden)
 * @author ohler@google.com (Christian Ohler)
 */
public final class OAuthedFetchService {

  /**
   * Detects whether an HTTP response indicates that the OAuth token has
   * expired.
   */
  public interface TokenRefreshNeededDetector {
    boolean refreshNeeded(HTTPResponse resp) throws IOException;
  }

  public static final TokenRefreshNeededDetector RESPONSE_CODE_401_DETECTOR =
      new TokenRefreshNeededDetector() {
        @Override public boolean refreshNeeded(HTTPResponse resp) {
          return resp.getResponseCode() == 401;
        }
      };

  @SuppressWarnings("unused")
  private static final Logger log = Logger.getLogger(OAuthedFetchService.class.getName());

  private final URLFetchService fetch;
  private final OAuthRequestHelper helper;

  @Inject
  public OAuthedFetchService(URLFetchService fetch, OAuthRequestHelper helper) {
    this.fetch = fetch;
    this.helper = helper;
  }

  private String describeRequest(HTTPRequest req) {
    StringBuilder b = new StringBuilder(req.getMethod() + " " + req.getURL());
    for (HTTPHeader h : req.getHeaders()) {
      b.append("\n" + h.getName() + ": " + h.getValue());
    }
    return "" + b;
  }

  private String describeResponse(HTTPResponse resp, boolean includeBody) {
    StringBuilder b = new StringBuilder(resp.getResponseCode()
        + " with " + resp.getContent().length + " bytes of content");
    for (HTTPHeader h : resp.getHeaders()) {
      b.append("\n" + h.getName() + ": " + h.getValue());
    }
    if (includeBody) {
      b.append("\n" + new String(resp.getContent(), Charsets.UTF_8));
    } else {
      b.append("\n<content elided>");
    }
    return "" + b;
  }

  private HTTPResponse fetch1(HTTPRequest req, TokenRefreshNeededDetector refreshNeeded,
      boolean tokenJustRefreshed) throws IOException {
    log.info("Sending request (token just refreshed: " + tokenJustRefreshed + "): "
        + describeRequest(req));
    helper.authorize(req);
    //log.info("req after authorizing: " + describeRequest(req));
    HTTPResponse resp = fetch.fetch(req);
    log.info("response: " + describeResponse(resp, false));
    if (refreshNeeded.refreshNeeded(resp)) {
      if (tokenJustRefreshed) {
        throw new NeedNewOAuthTokenException("Token just refreshed, still no good: "
            + describeResponse(resp, true));
      } else {
        helper.refreshToken();
        return fetch1(req, refreshNeeded, true);
      }
    } else {
      return resp;
    }
  }

  public HTTPResponse fetch(HTTPRequest request, TokenRefreshNeededDetector refreshNeeded)
      throws IOException {
    return fetch1(request, refreshNeeded, false);
  }

  public HTTPResponse fetch(HTTPRequest request) throws IOException {
    return fetch(request, RESPONSE_CODE_401_DETECTOR);
  }

  // TODO(ohler): Move these static utility methods to some other utility class.

  /** Gets the values of all headers with the name {@code headerName}. */
  public static List<String> getHeaders(HTTPResponse resp, String headerName) {
    ImmutableList.Builder<String> b = ImmutableList.builder();
    for (HTTPHeader h : resp.getHeaders()) {
      // HTTP header names are case-insensitive.  App Engine downcases them when
      // deployed but not when running locally.
      if (headerName.equalsIgnoreCase(h.getName())) {
        b.add(h.getValue());
      }
    }
    return b.build();
  }

  /**
   * Checks that exactly one header named {@code headerName} is present and
   * returns its value.
   */
  public static String getSingleHeader(HTTPResponse resp, String headerName) {
    return Iterables.getOnlyElement(getHeaders(resp, headerName));
  }

  /** Returns the body of {@code resp}, assuming that its encoding is UTF-8. */
  private static String getUtf8ResponseBodyUnchecked(HTTPResponse resp) {
    byte[] rawResponseBody = resp.getContent();
    if (rawResponseBody == null) {
      return "";
    } else {
      return new String(rawResponseBody, Charsets.UTF_8);
    }
  }

  /**
   * Checks that the Content-Type of {@code resp} is
   * {@code expectedUtf8ContentType} (which is assumed to imply UTF-8 encoding)
   * and returns the body as a String.
   */
  public static String getUtf8ResponseBody(HTTPResponse resp, String expectedUtf8ContentType)
      throws IOException {
    String contentType = getSingleHeader(resp, "Content-Type");
    if (!expectedUtf8ContentType.equals(contentType)) {
      throw new IOException("Unexpected Content-Type: " + contentType
          + " (wanted " + expectedUtf8ContentType + "); body as UTF-8: "
          + getUtf8ResponseBodyUnchecked(resp));
    }
    return getUtf8ResponseBodyUnchecked(resp);
  }

}
