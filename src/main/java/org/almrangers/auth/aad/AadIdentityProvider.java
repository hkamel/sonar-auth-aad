/**
 * Azure Active Directory Authentication Plugin for SonarQube
 * <p>
 * Copyright (c) 2016 Microsoft Corporation
 * All rights reserved.
 * <p>
 * The MIT License (MIT)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.almrangers.auth.aad;

import com.microsoft.aad.adal4j.AuthenticationContext;
import com.microsoft.aad.adal4j.AuthenticationResult;
import com.microsoft.aad.adal4j.ClientCredential;
import com.microsoft.aad.adal4j.UserInfo;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.sonar.api.server.ServerSide;
import org.sonar.api.server.authentication.Display;
import org.sonar.api.server.authentication.OAuth2IdentityProvider;
import org.sonar.api.server.authentication.UnauthorizedException;
import org.sonar.api.server.authentication.UserIdentity;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import static java.lang.String.format;
import static org.almrangers.auth.aad.AadSettings.AUTH_REQUEST_FORMAT;
import static org.almrangers.auth.aad.AadSettings.LOGIN_STRATEGY_PROVIDER_ID;
import static org.almrangers.auth.aad.AadSettings.LOGIN_STRATEGY_UNIQUE;

@ServerSide
public class AadIdentityProvider implements OAuth2IdentityProvider {

  private static final String KEY = "aad";
  private static final String NAME = "Microsoft";
  private static final String NAME_CLAIM = "name";
  private static final int JWT_PAYLOAD_PART_INDEX = 1;
  private static final Logger LOGGER = Loggers.get(AadIdentityProvider.class);

  private final AadSettings settings;

  public AadIdentityProvider(AadSettings settings) {
    this.settings = settings;
  }

  @Override
  public Display getDisplay() {
    return Display.builder()
      .setIconPath("/static/authaad/ms-symbol.svg")
      .setBackgroundColor("#2F2F2F")
      .build();
  }

  @Override
  public String getKey() {
    return KEY;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public boolean isEnabled() {
    return settings.isEnabled();
  }

  @Override
  public boolean allowsUsersToSignUp() {
    return settings.allowUsersToSignUp();
  }

  @Override
  public void init(InitContext context) {
    String state = context.generateCsrfState();
    String authUrl = String.format(AUTH_REQUEST_FORMAT, settings.authorizationUrl(), settings.clientId(), context.getCallbackUrl(), state);
    context.redirectTo(authUrl);
  }

  @Override
  public void callback(CallbackContext context) {
    context.verifyCsrfState();
    HttpServletRequest request = context.getRequest();
    String oAuthVerifier = request.getParameter("code");
    AuthenticationContext authContext;
    AuthenticationResult result;
    ExecutorService service = null;
    Set<String> userGroups;
    try {
      service = Executors.newFixedThreadPool(1);
      authContext = new AuthenticationContext(settings.authorityUrl(), false, service);
      URI url = new URI(context.getCallbackUrl());
      ClientCredential clientCredt = new ClientCredential(settings.clientId(), settings.clientSecret());
      Future<AuthenticationResult> future = authContext.acquireTokenByAuthorizationCode(
        oAuthVerifier, url, clientCredt, settings.getGraphURL(), null);
      result = future.get();

      UserInfo aadUser = result.getUserInfo();
      UserIdentity.Builder userIdentityBuilder = UserIdentity.builder()
        .setProviderLogin(aadUser.getDisplayableId())
        .setLogin(getLogin(aadUser))
        .setName(getUserName(result))
        .setEmail(aadUser.getDisplayableId());
      if (settings.enableGroupSync()) {
        userGroups = getUserGroupsMembership(result.getAccessToken(), result.getUserInfo().getUniqueId());
        userIdentityBuilder.setGroups(userGroups);
      }
      context.authenticate(userIdentityBuilder.build());
      context.redirectToRequestedPage();
    } catch (Exception e) {
      LOGGER.error("Exception:" + e.toString());
    } finally {
      if (service != null) {
        service.shutdown();
      }
    }
  }

  String getUserName(AuthenticationResult result) {
    UserInfo userInfo = result.getUserInfo();
    if (userInfo.getGivenName() != null && userInfo.getFamilyName() != null) {
      return userInfo.getGivenName() + " " + userInfo.getFamilyName();
    }

    if (result.getIdToken() != null) {
      String base64EncodedJWTPayload = result.getIdToken().split("\\.")[JWT_PAYLOAD_PART_INDEX];
      JSONObject token = new JSONObject(new String(Base64.getDecoder().decode(base64EncodedJWTPayload)));
      if (token.has(NAME_CLAIM)) {
        return token.getString(NAME_CLAIM);
      }
    }
    LOGGER.warn(String.format("User's name not found from authentication token for user %s", userInfo.getUniqueId()));
    return userInfo.getDisplayableId();
  }

  private String getLogin(UserInfo aadUser) {
    String loginStrategy = settings.loginStrategy();
    if (LOGIN_STRATEGY_UNIQUE.equals(loginStrategy)) {
      return generateUniqueLogin(aadUser);
    } else if (LOGIN_STRATEGY_PROVIDER_ID.equals(loginStrategy)) {
      return aadUser.getDisplayableId();
    } else {
      throw new UnauthorizedException(format("Login strategy not found : %s", loginStrategy));
    }
  }

  URL getUrl(String userId, String nextPage) throws MalformedURLException {
	  String url =  String.format(settings.getGraphMembershipUrl(), settings.tenantId(), userId);
    if (null != nextPage) {
      url = nextPage;
    }
    return new URL(url);
  }

  public Set<String> getUserGroupsMembership(String accessToken, String userId) {
	Set<String> userGroups = new HashSet<>();
	String nextPage = null;
    try {
      do {
    	  URL url = getUrl(userId, nextPage);
	      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
	      connection.setRequestProperty("Authorization", accessToken);
	      connection.setRequestProperty("Accept", "application/json;odata.metadata=minimal");
	      String goodRespStr = HttpClientHelper.getResponseStringFromConn(connection, true);
	      int responseCode = connection.getResponseCode();
	      JSONObject response = HttpClientHelper.processGoodRespStr(responseCode, goodRespStr);
	      JSONArray groups;
	      groups = JSONHelper.fetchDirectoryObjectJSONArray(response);      
	      AadGroup group;
	      for (int i = 0; i < groups.length(); i++) {
	        JSONObject thisUserJSONObject = groups.optJSONObject(i);
	        group = new AadGroup();
	        JSONHelper.convertJSONObjectToDirectoryObject(thisUserJSONObject, group);
	        userGroups.add(group.getDisplayName());
	      }
	      nextPage = JSONHelper.fetchNextPageLink(response);
      } while (StringUtils.isNotEmpty(nextPage));
    } catch (Exception e) {
      LOGGER.error(e.toString());
    }
    return userGroups;
  }
  
  private String generateUniqueLogin(UserInfo aadUser) {
    return String.format("%s@%s", aadUser.getDisplayableId(), getKey());
  }

}
