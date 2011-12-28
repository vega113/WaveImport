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

package com.google.walkaround.util.server.flags;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Binder;
import com.google.inject.Provider;
import com.google.inject.binder.LinkedBindingBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Map;

/**
 * Parses flags from a JSON map.
 *
 * @author danilatos@google.com (Daniel Danilatos)
 */
public class JsonFlags {

  private JsonFlags() {}

  private static <T extends Enum<T>> T parseEnumValue(Class<T> enumType, String key, String value)
      throws FlagFormatException {
    try {
      return Enum.valueOf(enumType, value);
    } catch (IllegalArgumentException e) {
      throw new FlagFormatException("Invalid flag enum value " + value + " for key " + key
          + "; valid values: " + Arrays.toString(enumType.getEnumConstants()), e);
    }
  }

  @VisibleForTesting
  @SuppressWarnings("unchecked")
  static Object parseOneFlag(FlagDeclaration decl, JSONObject json) throws FlagFormatException {
    String key = decl.getName();
    Class<?> type = decl.getType();
    try {
      if (!json.has(key)) {
        throw new FlagFormatException("Missing flag: " + key);
      }
      // Explicit check, otherwise null would be interpreted as "null" (the
      // string) for string and enum values.
      if (json.isNull(key)) {
        throw new FlagFormatException("Null value for key " + key);
      }

      if (type == String.class) {
        return json.getString(key);
      } else if (type == Boolean.class) {
        return Boolean.valueOf(json.getBoolean(key));
      } else if (type == Integer.class) {
        int val = json.getInt(key);
        if (val != json.getDouble(key)) {
          throw new FlagFormatException("Loss of precision for type int, key=" + key +
              ", value=" + json.getDouble(key));
        }
        return Integer.valueOf(val);
      } else if (type == Double.class) {
        return Double.valueOf(json.getDouble(key));
      } else if (type.isEnum()) {
        // TODO(ohler): Avoid unchecked warning here, the rest of the method should be clean.
        return parseEnumValue(type.asSubclass(Enum.class), key, json.getString(key).toUpperCase());
      } else {
        throw new IllegalArgumentException("Unknown flag type " + type.getName());
      }
    } catch (JSONException e) {
      throw new FlagFormatException("Invalid flag JSON for key " + key +
          " (possibly a bad type); map=" + json, e);
    }
  }

  /**
   * The returned map is guaranteed to map every FlagDeclaration
   * <code>decl</code> in <code>flagDeclarations</code> to a non-null Object of
   * type <code>decl.getType()</code>.
   */
  public static Map<FlagDeclaration, Object> parse(
      Iterable<? extends FlagDeclaration> flagDeclarations, String jsonString)
      throws FlagFormatException {
    JSONObject json;
    try {
      json = new JSONObject(jsonString);
    } catch (JSONException e) {
      throw new FlagFormatException("Failed to parse jsonString: " + jsonString, e);
    }
    // TODO(ohler): Detect unknown flags and error (or at least warn).
    ImmutableMap.Builder<FlagDeclaration, Object> b = ImmutableMap.builder();
    for (FlagDeclaration decl : flagDeclarations) {
      b.put(decl, parseOneFlag(decl, json));
    }
    return b.build();
  }

  // This assumes that the provider will provide an object of the type required by decl.
  @SuppressWarnings("unchecked")
  private static void bindOneFlag(Binder binder, FlagDeclaration decl, Provider<Object> provider) {
    ((LinkedBindingBuilder<Object>) binder.bind(decl.getType()).annotatedWith(decl.getAnnotation()))
        .toProvider(provider);
  }

  /**
   * Sets up bindings for the given FlagDeclarations that retrieve the flag
   * values from the given configuration provider.
   */
  // TODO(ohler): move this out of JsonFlags, it's not specific to JSON.
  public static void bind(Binder binder, Iterable<? extends FlagDeclaration> decls,
      final Provider<Map<FlagDeclaration, Object>> configProvider) {
    for (final FlagDeclaration decl : decls) {
      bindOneFlag(binder, decl, new Provider<Object>() {
            @Override public Object get() {
              return configProvider.get().get(decl);
            }
          });
    }
  }

}
