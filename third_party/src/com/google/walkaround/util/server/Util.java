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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Miscellaneous utilities
 *
 * @author danilatos@google.com (Daniel Danilatos)
 */
public final class Util {
  private Util(){}

  /**
   * Slurps the contents of a file and returns them as a string.
   *
   * IOExceptions are re-thrown as RuntimeExceptions.
   */
  public static String slurpRequired(String file) {
    try {
      return slurp(new FileReader(file));
    } catch (IOException e) {
      throw new RuntimeException("Could not slurp file: " + file
          + " (user.dir=" + System.getProperty("user.dir") + ")", e);
    }
  }

  public static String slurp(Reader in) throws IOException {
    BufferedReader reader = new BufferedReader(in);
    String line = null;
    StringBuilder b = new StringBuilder();
    while ((line = reader.readLine()) != null) {
      b.append(line);
      b.append("\n");
    }
    return b.toString();
  }

  public static String slurpUtf8(InputStream stream) throws IOException {
    return slurp(new InputStreamReader(stream, "UTF-8"));
  }

  public static String chomp(String str) {
    return (str.charAt(str.length() - 1) == '\n') ? str.substring(0, str.length() - 1) : str;
  }

  /**
   * Returns the first {@code size} characters of the given string.
   */
  public static String abbrev(String longString, int size) {
    return longString.length() <= size ? longString
        : longString.substring(0, size);
  }

  public static String pathCat(String base, String suffix) {
    if (!base.endsWith("/") && !suffix.isEmpty()) {
      base += "/";
    }
    return base + suffix;
  }

  public static URL makeUrl(String base, String suffix) {
    try {
      return new URL(pathCat(base, suffix));
    } catch (MalformedURLException e) {
      throw new RuntimeException("Internal error", e);
    }
  }
}
