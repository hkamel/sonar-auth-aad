/*******************************************************************************
 * Copyright © Microsoft Open Technologies, Inc.
 *
 * All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * THIS CODE IS PROVIDED *AS IS* BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING WITHOUT LIMITATION
 * ANY IMPLIED WARRANTIES OR CONDITIONS OF TITLE, FITNESS FOR A
 * PARTICULAR PURPOSE, MERCHANTABILITY OR NON-INFRINGEMENT.
 *
 * See the Apache License, Version 2.0 for the specific language
 * governing permissions and limitations under the License.
 ******************************************************************************/
package org.almrangers.auth.aad;

import java.lang.reflect.Field;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class JSONHelper {

  private JSONHelper() {
    // Utility class
  }

  /**
   * This method parses an JSON Array out of a collection of JSON Objects
   * within a string.
   *
   * @param jsonObject The JSON String that holds the collection.
   * @return An JSON Array that would contains all the collection object.
   * @throws Exception
   */
  public static JSONArray fetchDirectoryObjectJSONArray(JSONObject jsonObject) {
    return jsonObject.optJSONObject("responseMsg").optJSONArray("value");
  }

  /**
   * This method parses a JSON field out of a json object
   *
   * @param jsonObject The JSON String that holds the collection.
   * @return next page link 
   * @throws Exception
   */
  public static String fetchNextPageLink(JSONObject jsonObject) {
    return jsonObject.optJSONObject("responseMsg").has("@odata.nextLink") ? jsonObject.optJSONObject("responseMsg").get("@odata.nextLink").toString() : null;
  }

  /**
   * This is a generic method that copies the simple attribute values from an
   * argument jsonObject to an argument generic object.
   *
   * @param jsonObject The jsonObject from where the attributes are to be copied.
   * @param destObject The object where the attributes should be copied into.
   * @throws Exception Throws a Exception when the operation are unsuccessful.
   */
  public static <T> void convertJSONObjectToDirectoryObject(JSONObject jsonObject, T destObject) throws Exception {

    // Get the list of all the field names.
    Field[] fieldList = destObject.getClass().getDeclaredFields();

    // For all the declared field.
    for (int i = 0; i < fieldList.length; i++) {
      // If the field is of type String, that is
      // if it is a simple attribute.
      if (fieldList[i].getType().equals(String.class)) {
        // Invoke the corresponding set method of the destObject using
        // the argument taken from the jsonObject.
        destObject
          .getClass()
          .getMethod(String.format("set%s", WordUtils.capitalize(fieldList[i].getName())),
            String.class)
          .invoke(destObject, jsonObject.optString(fieldList[i].getName()));
      }
    }
  }
}
