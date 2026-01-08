/**
 * Azure Active Directory Authentication Plugin for SonarQube
 * <p>
 * Copyright (c) 2016 Microsoft Corporation
 * Copyright (c) 2022 Michael Johnson
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

import java.net.*;
import java.util.concurrent.ExecutorService;

import com.nimbusds.jwt.JWT;
import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import org.sonar.api.server.ServerSide;
import org.sonar.api.server.authentication.Display;
import org.sonar.api.server.authentication.OAuth2IdentityProvider;
import org.sonar.api.server.authentication.UnauthorizedException;
import org.sonar.api.server.http.HttpRequest;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

@ServerSide
public class AadIdentityProvider implements OAuth2IdentityProvider {

  private static final String KEY = "aad";
  private static final String NAME = "Microsoft";
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
    String sqState = context.generateCsrfState();

    State state = new State(sqState);
    ClientID clientId = new ClientID(settings.clientId().orElse(null));
    Scope scope = Scope.parse("openid profile email User.Read");

    try {
      AuthorizationRequest authReq = new AuthorizationRequest(
          new URI(settings.authorizationUrl()),
          ResponseType.CODE,
          ResponseMode.QUERY,
          clientId,
          new URI(context.getCallbackUrl()),
          scope,
          state);

      URI authUrl = authReq.toURI();

      context.redirectTo(authUrl.toString());
    } catch (URISyntaxException e) {
      LOGGER.error(e.toString());
    }


  }

  @Override
  public void callback(CallbackContext context) {
    try {
      onCallback(context);
    } catch (Exception e) {
      LOGGER.error("Exception:" + e);
      throw new UnauthorizedException(e.getMessage());
    }
  }

  void onCallback(CallbackContext context) throws UnauthorizedException {
    context.verifyCsrfState();

    HttpRequest request = context.getHttpRequest();
    AuthorizationCode code = new AuthorizationCode(request.getParameter("code"));
    ExecutorService service = null;

    try {
      TokenRequest tokenReq = new TokenRequest(
          new URI(settings.authorityUrl()),
          new ClientSecretBasic(
              new ClientID(settings.clientId().orElse(null)),
              new Secret(settings.clientSecret().orElse(""))),
          new AuthorizationCodeGrant(code, new URI(context.getCallbackUrl()))
      );

      // Parse and check response
      OIDCTokenResponse tokenResponse = AadTokenHelper.extractTokenResponse(tokenReq.toHTTPRequest().send());

      OIDCTokens accessTokens = tokenResponse.getOIDCTokens();

      JWT idToken = accessTokens.getIDToken();

      AadUserInfo aadUser;
      AccessToken accessToken; // The user's auth token as the default.

      if (AadTokenHelper.validateIdToken(idToken, settings)) {

        // Decide if we are going to use a user auth token or the client auth
        // token. This is used for group sync for access to MS Graph. If client
        // credential is enabled along with group sync, try to grab a client auth
        // token. Otherwise we just set the token to the user token.
        if(settings.enableGroupSync() && settings.enableClientCredential()) {
          TokenRequest clientRequest = new TokenRequest(
              new URI(settings.authorityUrl()),
              new ClientSecretBasic(
                  new ClientID(settings.clientId().orElse("")),
                  new Secret(settings.clientSecret().orElse(""))
              ),
              new ClientCredentialsGrant(),
              new Scope(settings.getGraphURL() + "/.default"));

          // Parse and check response
          TokenResponse clientResponse = TokenResponse.parse(clientRequest.toHTTPRequest().send());

          // Client token request failed, log the error
          if (!clientResponse.indicatesSuccess()) {
            TokenErrorResponse errorResponse = clientResponse.toErrorResponse();
            LOGGER.error("Issue in getting client token for group sync. Returned error: "
                + errorResponse.getErrorObject().getDescription());
            accessToken = new BearerAccessToken(); // Empty access token so we pass _something_.
          } else {
            AccessTokenResponse successResponse = clientResponse.toSuccessResponse();
            accessToken = successResponse.getTokens().getAccessToken();
          }
        } else {
          // Use the user's access token
          accessToken = accessTokens.getAccessToken();
        }

        // NOTE: The Access token IS EITHER:
        // The client credential token if client credential flow is enabled **OR**
        // The user's token if client credential flow fails or client flow is disabled
        aadUser = new AadUserInfo(idToken, accessToken, settings.enableGroupSync());

        context.authenticate(aadUser.buildUserId(settings.enableGroupSync()).build());

        context.redirectToRequestedPage();
      }

    } catch (Exception e) {
      LOGGER.error("Exception:" + e);
      throw new UnauthorizedException(e.getMessage());
    } finally {
      if (service != null) {
        service.shutdown();
      }
    }
  }

}
