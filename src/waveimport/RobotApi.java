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

package waveimport;

import com.google.appengine.api.urlfetch.FetchOptions;
import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.walkaround.proto.GoogleImport.GoogleDocument;
import com.google.walkaround.proto.GoogleImport.GoogleWavelet;
import com.google.walkaround.proto.Proto.ProtocolAppliedWaveletDelta;
import com.google.walkaround.proto.RobotSearchDigest;
import com.google.walkaround.proto.gson.RobotSearchDigestGsonImpl;
import com.google.walkaround.wave.server.auth.OAuthedFetchService;
import com.google.walkaround.wave.server.auth.OAuthedFetchService.TokenRefreshNeededDetector;

import org.apache.commons.codec.binary.Base64;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.util.Pair;
import org.waveprotocol.wave.model.util.ValueUtils;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Simple interface to Google Wave's active robot API.
 *
 * We don't use the "official" Wave robot API library since it doesn't support
 * some of the APIs that we need (raw snapshot/delta export), and implementing
 * what we need is easy anyway.  The main difficulty is OAuth, but we already
 * have code for that.
 *
 * @author ohler@google.com (Christian Ohler)
 */
public class RobotApi {

  public interface Factory {
    RobotApi create(@Assisted String baseUrl);
  }

  @SuppressWarnings("unused")
  private static final Logger log = Logger.getLogger(RobotApi.class.getName());

  private final OAuthedFetchService fetch;
  private final String baseUrl;

  @Inject
  public RobotApi(OAuthedFetchService fetch,
      @Assisted String baseUrl) {
    this.fetch = fetch;
    this.baseUrl = baseUrl;
  }

  private static final String OP_ID = "op_id";

  private static final String ROBOT_API_METHOD_FETCH_WAVE = "wave.robot.fetchWave";
  private static final String ROBOT_API_METHOD_SEARCH = "wave.robot.search";
  
  private static final String WIAB_ROBOT_API_CREATE_WAVELET = "robot.createWavelet";

  private static final String EXPECTED_CONTENT_TYPE = "application/json; charset=UTF-8";

  private final TokenRefreshNeededDetector robotErrorCode401Detector =
      new TokenRefreshNeededDetector() {
        @Override public boolean refreshNeeded(HTTPResponse resp) throws IOException {
          if (resp.getResponseCode() == 401) {
            return true;
          }
          if (!EXPECTED_CONTENT_TYPE.equals(fetch.getSingleHeader(resp, "Content-Type"))) {
            return false;
          }
          JSONObject result = parseJsonResponseBody(resp);
          try {
            if (result.has("error")) {
              JSONObject error = result.getJSONObject("error");
              return error.has("code") && error.getInt("code") == 401;
            } else {
              return false;
            }
          } catch (JSONException e) {
            throw new RuntimeException("JSONException parsing response: " + result, e);
          }
        }
      };

  // Example of the kind of search request body that we send:
  // [{"id":"op_id",
  //   "method":"wave.robot.search",
  //   "params":
  //     {"query":"abc",
  //      "index":0,
  //      "numResults":100
  //     }
  //  }
  // ]
  //
  // Example of the kind of response body that we receive:
  // [{"id":"op_id",
  //   "data":
  //     {"searchResults":
  //       {"query":"abc",
  //        "numResults":1,
  //        "digests":
  //         [{"waveId":"googlewave.com!w+aaaa",
  //           "title":"aaaa",
  //           "participants":
  //             ["aaaa@googlewave.com",
  //              "aaab@googlewave.com",
  //              "aaaa@googlegroups.com"
  //             ],
  //           "lastModified":1111111111111,
  //           "snippet":"aaaa",
  //           "blipCount":2,
  //           "unreadCount":1
  //          }
  //         ]
  //       }
  //     }
  //  }
  // ]

  private JSONObject parseJsonResponseBody(HTTPResponse resp) throws IOException {
    // The response looks like this:
    // [{"id":"op_id", "data":X}]
    // We return the single item in this array.
    String body = fetch.getUtf8ResponseBody(resp, EXPECTED_CONTENT_TYPE);
    try {
      JSONArray items = new JSONArray(body);
      if (items.length() != 1) {
        throw new RuntimeException("Unexpected length: " + items.length() + ": " + items);
      }
      JSONObject item = items.getJSONObject(0);
      if (!OP_ID.equals(item.getString("id"))) {
        throw new RuntimeException("Unexpected id: " + item);
      }
      return item;
    } catch (JSONException e) {
      throw new RuntimeException("JSONException parsing response: " + body, e);
    }
  }

  private JSONObject callRobotApi(String method, Map<String, Object> params) throws IOException {
    JSONArray ops = new JSONArray();
    try {
      JSONObject jsonParams = new JSONObject();
      for (Map.Entry<String, Object> e : params.entrySet()) {
        jsonParams.put(e.getKey(), e.getValue());
      }
      JSONObject op = new JSONObject();
      op.put("params", jsonParams);
      op.put("method", method);
      op.put("id", OP_ID);
      ops.put(op);
    } catch (JSONException e) {
      throw new RuntimeException("Failed to construct JSON object", e);
    }
    HTTPRequest req = new HTTPRequest(new URL(baseUrl), HTTPMethod.POST,
        FetchOptions.Builder.disallowTruncate().followRedirects()
            .validateCertificate().setDeadline(20.0));
    log.info("payload=" + ops);
    req.setHeader(new HTTPHeader("Content-Type", "application/json; charset=UTF-8"));
    req.setPayload(ops.toString().getBytes(Charsets.UTF_8));
    System.out.println("req: " + ops.toString());
    JSONObject result = parseJsonResponseBody(fetch.fetch(req, robotErrorCode401Detector));
    log.info("result=" + ValueUtils.abbrev("" + result, 500));
    try {
      if (result.has("error")) {
        log.warning("Error result: " + result);
        JSONObject error = result.getJSONObject("error");
        throw new RuntimeException("Error from robot API: " + error);
      } else if (result.has("data")) {
        JSONObject data = result.getJSONObject("data");
        if (data.length() == 0) {
          // Apparently, the server often sends {"id":"op_id", "data":{}} when
          // something went wrong on the server side, so we translate that to an
          // IOException.
          throw new IOException("Robot API response looks like an error: " + result);
        } else {
          return data;
        }
      } else {
        throw new RuntimeException("Result has neither error nor data: " + result);
      }
    } catch (JSONException e) {
      throw new RuntimeException("JSONException parsing result: " + result, e);
    }
  }

  private JSONObject callRobotApi1(String method, Map<String, Object> params) throws IOException {
    JSONArray ops = new JSONArray();
    try {
      JSONObject jsonParams = new JSONObject();
      for (Map.Entry<String, Object> e : params.entrySet()) {
        jsonParams.put(e.getKey(), e.getValue());
      }
      JSONObject op = new JSONObject();
      op.put("params", jsonParams);
      op.put("method", method);
      op.put("id", OP_ID);
      ops.put(op);
    } catch (JSONException e) {
      throw new RuntimeException("Failed to construct JSON object", e);
    }
    HTTPRequest req = new HTTPRequest(new URL(baseUrl), HTTPMethod.POST,
        FetchOptions.Builder.disallowTruncate().followRedirects()
            .validateCertificate().setDeadline(20.0));
    log.info("payload=" + ops);
    req.setHeader(new HTTPHeader("Content-Type", "application/json; charset=UTF-8"));
    req.setPayload(ops.toString().getBytes(Charsets.UTF_8));
    System.out.println("req: " + ops.toString());
    JSONObject result = parseJsonResponseBody(fetch.fetch(req, robotErrorCode401Detector));
    log.info("result=" + ValueUtils.abbrev("" + result, 500));
    return result;
  }

  private Map<String, Object> getFetchWaveParamMap(WaveletName waveletName, Object... extraParams) {
    Preconditions.checkArgument(extraParams.length % 2 == 0,
        "extraParams must come in pairs: %s", extraParams);
    ImmutableMap.Builder<String, Object> b = ImmutableMap.builder();
    b.put("waveId", waveletName.waveId.serialise());
    b.put("waveletId", waveletName.waveletId.serialise());
    for (int i = 0; i < extraParams.length; i += 2) {
      b.put((String) extraParams[i], extraParams[i + 1]);
    }
    return b.build();
  }

  /**
   * Gets the current state of the wave.
   */
  public Pair<GoogleWavelet, ImmutableList<GoogleDocument>> getSnapshot(WaveletName waveletName)
      throws IOException {
    JSONObject resp = callRobotApi(ROBOT_API_METHOD_FETCH_WAVE,
        getFetchWaveParamMap(waveletName, "returnRawSnapshot", true));
    try {
      JSONArray snapshot = resp.getJSONArray("rawSnapshot");
      // NOTE(ohler): snapshot array is not of a uniform type: element 0 is the
      // wavelet metadata, the remaining elements are documents.
      GoogleWavelet wavelet = GoogleWavelet.parseFrom(Base64.decodeBase64(snapshot.getString(0)));
      ImmutableList.Builder<GoogleDocument> documents = ImmutableList.builder();
      for (int i = 1; i < snapshot.length(); i++) {
        documents.add(GoogleDocument.parseFrom(Base64.decodeBase64(snapshot.getString(i))));
      }
      return Pair.of(wavelet, documents.build());
    } catch (JSONException e) {
      throw new RuntimeException("Failed to parse snapshot response: " + resp, e);
    }
  }

  /**
   * Gets the list of wavelets in a wave that are visible the user.
   */
  public List<WaveletId> getWaveView(WaveId waveId) throws IOException {
    JSONObject resp = callRobotApi(ROBOT_API_METHOD_FETCH_WAVE,
        ImmutableMap.<String, Object>of("waveId", waveId.serialise(), "listWavelets", true));
    try {
      JSONArray ids = resp.getJSONArray("waveletIds");
      ImmutableList.Builder<WaveletId> out = ImmutableList.builder();
      for (int i = 0; i < ids.length(); i++) {
        out.add(WaveletId.deserialise(ids.getString(i)));
      }
      List<WaveletId> view = out.build();
      log.info("getWaveView(" + waveId + ") = " + view);
      return view;
    } catch (JSONException e) {
      throw new RuntimeException("Failed to parse listWavelets response: " + resp, e);
    }
  }

  /**
   * Fetch wave with deltas
   * @author A. Kaplanov
   */
  public JSONObject fetchWaveWithDeltas(WaveId waveId, WaveletId waveletId) throws IOException {
    return callRobotApi1(ROBOT_API_METHOD_FETCH_WAVE,
        getFetchWaveParamMap(WaveletName.of(waveId, waveletId), "rawDeltasFromVersion", 0));
  }
  
  /**
   * Searches the user's waves.  Returns at most {@code maxResults} results,
   * starting with the {@code startIndex}-th result (0-based index).
   *
   * Note: Google Wave's search feature may limit the overall set of result for
   * any given query to the first N hits (for some N, perhaps 300), regardless
   * of {@code startIndex} and {@code maxResults}, so don't rely on these to
   * iterate over all waves.
   */
  public List<RobotSearchDigest> search(String query, int startIndex, int maxResults)
      throws IOException {
    log.info("search(" + query + ", " + startIndex + ", " + maxResults + ")");
    JSONObject response = callRobotApi(ROBOT_API_METHOD_SEARCH,
        ImmutableMap.<String, Object>of("query", query,
            "index", startIndex,
            "numResults", maxResults));
    System.out.println("gson :" + response.toString());
    // The response looks like this:
    // {"searchResults":
    //   {"query":"after:2008/01/01 before:2010/01/01",
    //    "numResults":1,
    //    "digests":
    //     [{"waveId":"googlewave.com!w+aaaa",
    //       "title":"aaaa",
    //       "participants":
    //         ["aaaa@googlewave.com",
    //          "aaab@googlewave.com",
    //          "aaaa@googlegroups.com"
    //         ],
    //       "lastModified":1111111111111,
    //       "snippet":"aaaa"
    //       "blipCount":2,
    //       "unreadCount":1,
    //      }
    //     ]
    //   }
    // }
    ImmutableList.Builder<RobotSearchDigest> digests = ImmutableList.builder();
    try {
      JSONObject results = response.getJSONObject("searchResults");
      try {
        if (results.getInt("numResults") != results.getJSONArray("digests").length()) {
          throw new RuntimeException("Mismatched numResults and digests array length: "
              + results.getInt("numResults") + " vs. " + results.getJSONArray("digests"));
        }
        JSONArray rawDigests = results.getJSONArray("digests");
        for (int i = 0; i < rawDigests.length(); i++) {
          JSONObject rawDigest = rawDigests.getJSONObject(i);
          try {
            RobotSearchDigest digest = new RobotSearchDigestGsonImpl();
            digest.setWaveId(WaveId.deserialise(rawDigest.getString("waveId")).serialise());
            JSONArray rawParticipants = rawDigest.getJSONArray("participants");
            for (int j = 0; j < rawParticipants.length(); j++) {
              digest.addParticipant(rawParticipants.getString(j));
            }
            digest.setTitle(rawDigest.getString("title"));
            digest.setSnippet(rawDigest.getString("snippet"));
            digest.setLastModifiedMillis(rawDigest.getLong("lastModified"));
            digest.setBlipCount(rawDigest.getInt("blipCount"));
            digest.setUnreadBlipCount(rawDigest.getInt("unreadCount"));
            digests.add(digest);
          } catch (JSONException e) {
            throw new RuntimeException("Failed to parse search digest: " + rawDigest, e);
          }
        }
      } catch (JSONException e) {
        throw new RuntimeException("Failed to parse search results: " + results, e);
      }
    } catch (JSONException e) {
      throw new RuntimeException("Failed to parse search response: " + response, e);
    }
    return digests.build();
  }

  public void wiabImportWave(Object waveletData) throws IOException {
    JSONObject response = callRobotApi(WIAB_ROBOT_API_CREATE_WAVELET,
        ImmutableMap.<String, Object>of("waveletData", waveletData));
  }  
}
