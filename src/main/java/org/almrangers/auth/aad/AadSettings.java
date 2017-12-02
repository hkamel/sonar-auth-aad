/**
 * Azure Active Directory Authentication Plugin for SonarQube

 * Copyright (c) 2016 Microsoft Corporation
 * All rights reserved.
 *
 * The MIT License (MIT)

 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:

 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.almrangers.auth.aad;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.sonar.api.config.Configuration;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.server.ServerSide;

import static java.lang.String.format;
import static java.lang.String.valueOf;
import static org.sonar.api.PropertyType.BOOLEAN;
import static org.sonar.api.PropertyType.SINGLE_SELECT_LIST;

@ServerSide
public class AadSettings {
  private static final String CLIENT_ID = "sonar.auth.aad.clientId.secured";
  private static final String CLIENT_SECRET = "sonar.auth.aad.clientSecret.secured";
  private static final String ENABLED = "sonar.auth.aad.enabled";
  private static final String ALLOW_USERS_TO_SIGN_UP = "sonar.auth.aad.allowUsersToSignUp";
  private static final String TENANT_ID = "sonar.auth.aad.tenantId";
  private static final String ENABLE_GROUPS_SYNC = "sonar.auth.aad.enableGroupsSync";
  private static final String LOGIN_STRATEGY = "sonar.auth.aad.loginStrategy";
  public static final String LOGIN_STRATEGY_UNIQUE = "Unique";
  public static final String LOGIN_STRATEGY_PROVIDER_ID = "Same as Azure AD login";
  public final static String LOGIN_STRATEGY_DEFAULT_VALUE = LOGIN_STRATEGY_UNIQUE;
  private static final String MULTI_TENANT = "sonar.auth.aad.multiTenant";

  private static final String SUBCATEGORY = "(1) Azure Active Directory Authentication";
  private static final String GROUPSYNCSUBCATEGORY = "(2) Azure Active Directory Groups Synchronization";

  private static final String ROOT_URL = "https://login.microsoftonline.com";
  private static final String AUTHORIZATION_URL = "oauth2/authorize";
  private static final String AUTHORITY_URL = "oauth2/token";
  private static final String COMMON_URL = "common";
  public static final String SECURE_RESOURCE_URL = "https://graph.windows.net";

  public static final String AUTH_REQUEST_FORMAT = "%s?client_id=%s&response_type=code&redirect_uri=%s&state=%s";
  public static final String GROUPS_REQUEST_FORMAT = "https://graph.windows.net/%s/users/%s/memberOf?api-version=1.6";

  private final Configuration settings;

  public AadSettings(Configuration settings) {
    this.settings = settings;
  }

  public static List<PropertyDefinition> authenticationProperties() {
    return new ArrayList<>(Arrays.asList(
      PropertyDefinition.builder(ENABLED)
        .name("Enabled")
        .description("Enable Azure AD users to login. Value is ignored if client ID and secret are not defined.")
        .subCategory(SUBCATEGORY)
        .type(BOOLEAN)
        .defaultValue(valueOf(false))
        .index(1)
        .build(),
      PropertyDefinition.builder(CLIENT_ID)
        .name("Client ID")
        .description("Client ID provided by Azure AD when registering the application.")
        .subCategory(SUBCATEGORY)
        .index(2)
        .build(),
      PropertyDefinition.builder(CLIENT_SECRET)
        .name("Client Secret")
        .description("Client key provided by Azure AD when registering the application.")
        .subCategory(SUBCATEGORY)
        .index(3)
        .build(),
      PropertyDefinition.builder(MULTI_TENANT)
        .name("Multi-tenant Azure Application")
        .description("multi-tenant application")
        .subCategory(SUBCATEGORY)
        .type(BOOLEAN)
        .defaultValue(valueOf(false))
        .index(4)
        .build(),
      PropertyDefinition.builder(TENANT_ID)
        .name("Tenant ID")
        .description("Azure AD Tenant ID.")
        .subCategory(SUBCATEGORY)
        .index(5)
        .build(),
      PropertyDefinition.builder(ALLOW_USERS_TO_SIGN_UP)
        .name("Allow users to sign-up")
        .description("Allow new users to authenticate. When set to 'false', only existing users will be able to authenticate to the server.")
        .subCategory(SUBCATEGORY)
        .type(BOOLEAN)
        .defaultValue(valueOf(true))
        .index(6)
        .build(),
      PropertyDefinition.builder(LOGIN_STRATEGY)
        .name("Login generation strategy")
        .description(format("When the login strategy is set to '%s', the user's login will be auto-generated the first time so that it is unique. " +
          "When the login strategy is set to '%s', the user's login will be the Azure AD login.",
          LOGIN_STRATEGY_UNIQUE, LOGIN_STRATEGY_PROVIDER_ID))
        .subCategory(SUBCATEGORY)
        .type(SINGLE_SELECT_LIST)
        .defaultValue(LOGIN_STRATEGY_DEFAULT_VALUE)
        .options(LOGIN_STRATEGY_UNIQUE, LOGIN_STRATEGY_PROVIDER_ID)
        .index(7)
        .build()
    ));
  }
  
  public static List<PropertyDefinition> groupProperties() {
	    return new ArrayList<>(Arrays.asList(
	    	      PropertyDefinition.builder(AadSettings.ENABLE_GROUPS_SYNC)
	    	        .name("Enable Groups Synchronization")
	    	        .description(format(
	    	          "Enable groups synchronization from Azure AD to SonarQube, For each Azure AD group user belongs to,"
	    	          + "the user will be associated to a group with the same name(if it exists) in SonarQube.",
	    	          AadSettings.LOGIN_STRATEGY_UNIQUE, AadSettings.LOGIN_STRATEGY_PROVIDER_ID))
	    	        .subCategory(GROUPSYNCSUBCATEGORY)
	    	        .type(BOOLEAN)
	    	        .defaultValue(valueOf(false))
	    	        .index(1)
	    	        .build()
	    ));
}


  public Optional<String> clientId() {
    return settings.get(CLIENT_ID);
  }

  public Optional<Boolean> allowUsersToSignUp() {
    return settings.getBoolean(ALLOW_USERS_TO_SIGN_UP);
  }

  public Optional<Boolean> enableGroupSync() {
    return settings.getBoolean(ENABLE_GROUPS_SYNC);
  }

  public Optional<Boolean> multiTenant() {
    return settings.getBoolean(MULTI_TENANT);
  }

  public Optional<String> tenantId() {
    return settings.get(TENANT_ID);
  }

  public Optional<String> clientSecret() {
    return settings.get(CLIENT_SECRET);
  }

  public boolean isEnabled() {
    return settings.getBoolean(ENABLED).orElse(Boolean.FALSE) && clientId().isPresent() && clientSecret().isPresent() && loginStrategy().isPresent();
  }

  private String getEndpoint() {
    if (multiTenant().orElse(Boolean.FALSE))
      return COMMON_URL;
    else
      return tenantId().orElse(null);
  }

  public String authorizationUrl() {
    return String.format("%s/%s/%s", ROOT_URL, getEndpoint(), AUTHORIZATION_URL);
  }

  public String authorityUrl() {
    return String.format("%s/%s/%s", ROOT_URL, getEndpoint(), AUTHORITY_URL);
  }

  public Optional<String>  loginStrategy() {
    return settings.get(LOGIN_STRATEGY);
  }
}
