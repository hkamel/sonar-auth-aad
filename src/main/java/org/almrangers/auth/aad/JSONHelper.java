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

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.json.JSONArray;
import org.json.JSONObject;

public class JSONHelper {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
   * Gets AadGroup list from response object map
   * @param responseObject : it has value which contains displayName
   * @return List of AadGroup that contains displayName
   * @throws JsonProcessingException
   */
  public static List<AadGroup> fetchDirectoryObjectAadGroups(Map<String, Object> responseObject) throws JsonProcessingException {
    Map<String, Object> responseMsg = (Map<String, Object>) responseObject.get("responseMsg");
    Object value = responseMsg.get("value");
    List<AadGroup> groups = OBJECT_MAPPER.convertValue(value, new TypeReference<List<AadGroup>>() {});
    return groups;
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
   * Fetches NextLink Url from response
   * @param responseObject
   * @return
   */
  public static String fetchNextPageLink(Map<String, Object> responseObject) {
    Map<String, Object> responseMsg = (Map<String, Object>) responseObject.get("responseMsg");

    Object nextLink = responseMsg.get("@odata.nextLink");
    return nextLink == null ? null : nextLink.toString();
  }
}
