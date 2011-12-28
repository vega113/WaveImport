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

import com.google.appengine.api.datastore.*;

/**
 * Provides datastore instance to use.
 *
 * @author ohler@google.com (Christian Ohler)
 */
public class DatastoreProvider {

  private DatastoreProvider() {}

  private static final DatastoreService STRONG_READS =
      DatastoreServiceFactory.getDatastoreService(DatastoreServiceConfig.Builder
          .withDeadline(5 /*seconds*/)
          .implicitTransactionManagementPolicy(ImplicitTransactionManagementPolicy.NONE)
          .readPolicy(new ReadPolicy(ReadPolicy.Consistency.STRONG)));

  private static final DatastoreService EVENTUAL_READS =
      DatastoreServiceFactory.getDatastoreService(DatastoreServiceConfig.Builder
          .withDeadline(5 /*seconds*/)
          .implicitTransactionManagementPolicy(ImplicitTransactionManagementPolicy.NONE)
          .readPolicy(new ReadPolicy(ReadPolicy.Consistency.EVENTUAL)));

  public static DatastoreService strongReads() {
    return STRONG_READS;
  }

  public static DatastoreService eventualReads() {
    return EVENTUAL_READS;
  }

}
