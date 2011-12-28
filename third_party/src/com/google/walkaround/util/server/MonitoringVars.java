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

package com.google.walkaround.util.server;

/**
 * Interface to variables for monitoring purposes.
 *
 * The methods never throw exceptions; they log at level WARNING instead.  This
 * is so that errors that occur while recording monitoring information don't
 * interfere with request processing.  Even the invalid counter name
 * {@code null} doesn't lead to an exception.
 *
 * Counter names with invalid characters will be ignored and logged, or
 * converted to valid counter names (and possibly logged).  Which characters are
 * permitted depends on the implementation.
 *
 * @author ohler@google.com (Christian Ohler)
 */
public interface MonitoringVars {

  final MonitoringVars NULL_IMPL = new MonitoringVars() {
    @Override public void incrementCounter(String name) {}
    @Override public void incrementCounter(String name, long increment) {}
  };

  void incrementCounter(String name);
  void incrementCounter(String name, long increment);

}
