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

import com.google.api.client.extensions.appengine.http.urlfetch.UrlFetchTransport;
import com.google.api.client.googleapis.auth.oauth2.draft10.GoogleAccessProtectedResource;
import com.google.api.client.googleapis.auth.oauth2.draft10.GoogleAccessTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.draft10.GoogleAuthorizationRequestUrl;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.inject.Inject;
import com.google.walkaround.wave.server.Flag;
import com.google.walkaround.wave.server.FlagName;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper for making OAuth2 requests.
 *
 * @author danilatos@google.com (Daniel Danilatos)
 * @author ohler@google.com (Christian Ohler)
 */
public class OAuthRequestHelper {

  @SuppressWarnings("unused")
  private static final Logger log = Logger.getLogger(OAuthRequestHelper.class.getName());

  private final UserContext userContext;
  private final GoogleAccessProtectedResource accessThing;

  @Inject
  public OAuthRequestHelper(
      @Flag(FlagName.OAUTH_CLIENT_ID) String clientId,
      @Flag(FlagName.OAUTH_CLIENT_SECRET) String clientSecret,
      UserContext userContext) {
    this.userContext = userContext;
    this.accessThing = new GoogleAccessProtectedResource(
        getCredentials().getAccessToken(),
        new UrlFetchTransport(), new JacksonFactory(), clientId, clientSecret,
        getCredentials().getRefreshToken());
  }

  private OAuthCredentials getCredentials() {
    return userContext.getOAuthCredentials();
  }

  public String getAuthorizationHeaderValue() {
    return "OAuth " + getCredentials().getAccessToken();
  }

  public void authorize(HTTPRequest req) {
    req.setHeader(new HTTPHeader("Authorization", getAuthorizationHeaderValue()));
  }

  public void refreshToken() throws IOException {
    OAuthCredentials oldCredentials = getCredentials();
    log.info("Trying to refresh token; credentials: " + oldCredentials);
    if (!accessThing.refreshToken()) {
      log.log(Level.WARNING, "refreshToken() returned false; perhaps revoked");
      throw new NeedNewOAuthTokenException("refreshToken() returned false; perhaps revoked");
    }

    String newAccessToken = accessThing.getAccessToken();
    String newRefreshToken = accessThing.getRefreshToken();

    log.info("New access token: " + newAccessToken);

    if (newAccessToken == null) {
      throw new RuntimeException("No access token provided after refresh");
    }

    if (!oldCredentials.getRefreshToken().equals(newRefreshToken)) {
      throw new AssertionError("Unexpectedly got a different refresh token: " + newRefreshToken
        + ", had " + oldCredentials.getRefreshToken());
    }
    userContext.setOAuthCredentials(new OAuthCredentials(newRefreshToken, newAccessToken));
    log.info("Successfully refreshed token: " + userContext);
  }

}
