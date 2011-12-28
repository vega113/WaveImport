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

package com.google.walkaround.wave.server;

import com.google.common.base.Preconditions;
import com.google.walkaround.util.server.flags.FlagDeclaration;

import java.lang.annotation.Annotation;

/**
 * Walkaround flags.
 *
 * @author danilatos@google.com (Daniel Danilatos)
 * @author ohler@google.com (Christian Ohler)
 */
public enum FlagName implements FlagDeclaration {
  OAUTH_CLIENT_ID(String.class),
  OAUTH_CLIENT_SECRET(String.class),
  ENABLE_UDW(Boolean.class),
  ENABLE_DIFF_ON_OPEN(Boolean.class),
  ATTACHMENT_HEADER_BYTES_UPPER_BOUND(Integer.class),
  MAX_THUMBNAIL_SAVED_SIZE_BYTES(Integer.class),
  OBJECT_CHANNEL_EXPIRATION_SECONDS(Integer.class),
  ACCESS_CACHE_EXPIRATION_SECONDS(Integer.class),
  CLIENT_VERSION(Integer.class),
  XSRF_TOKEN_EXPIRY_SECONDS(Integer.class),
  STORE_SERVER(String.class),
  NUM_STORE_SERVERS(Integer.class),
  ANNOUNCEMENT_HTML(String.class),
  ANALYTICS_ACCOUNT(String.class),
  SECRET(String.class),
  ;

  // Stolen from com.google.inject.name.NamedImpl.
  static class FlagImpl implements Flag {
    private final FlagName value;

    FlagImpl(FlagName value) {
      Preconditions.checkNotNull(value, "Null value");
      this.value = value;
    }

    @Override public FlagName value() {
      return value;
    }

    @Override public int hashCode() {
      // This is specified in java.lang.Annotation.
      return 127 * "value".hashCode() ^ value.hashCode();
    }

    @Override public boolean equals(Object o) {
      if (!(o instanceof Flag)) {
        return false;
      }
      Flag other = (Flag) o;
      return value.equals(other.value());
    }

    @Override public String toString() {
      return "@Flag(" + value + ")";
    }

    @Override public Class<? extends Annotation> annotationType() {
      return Flag.class;
    }
  }

  private final String name;
  private final Class<?> type;

  private FlagName(Class<?> type) {
    this.name = name().toLowerCase();
    this.type = type;
  }

  @Override public Annotation getAnnotation() {
    return new FlagImpl(this);
  }

  @Override public String getName() {
    return name;
  }

  @Override public Class<?> getType() {
    return type;
  }
}
