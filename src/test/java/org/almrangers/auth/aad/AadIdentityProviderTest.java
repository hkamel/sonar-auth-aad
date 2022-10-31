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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;

import com.microsoft.aad.adal4j.AuthenticationResult;
import com.microsoft.aad.adal4j.UserInfo;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.server.authentication.OAuth2IdentityProvider;

public class AadIdentityProviderTest {

  private static final String GIVEN_NAME = "GivenName";
  private static final String FAMILY_NAME = "FamilyName";
  private static final String DISPLAYABLE_ID = "DisplayableId";
  private static final String EXPECTED_NAME = GIVEN_NAME + " " + FAMILY_NAME;
  private static final String JSON_WITH_NAME = "{\"name\": \"" + EXPECTED_NAME + "\"}";
  private static final String EMPTY_JSON = "{}";

  @Rule
  public ExpectedException thrown = ExpectedException.none();
  MapSettings settings = new MapSettings();
  AadSettings aadSettings = new AadSettings(settings.asConfig());
  AadIdentityProvider underTest = spy(new AadIdentityProvider(aadSettings));

  @Test
  public void shouldHandleGetMembershipsPagination() throws IOException {

	URL mockUrl = mock(URL.class);
	HttpURLConnection mockConnection = mock(HttpURLConnection.class);

	doReturn(mockUrl)
	  .when(underTest)
	  .getUrl("userId", null);

	doReturn (mockConnection)
	  .when(mockUrl)
	  .openConnection();

	doReturn(getClass().getClassLoader().getResourceAsStream("get-members-page1.json"))
	  .when(mockConnection)
	  .getInputStream();
	
	
	URL mockUrl2 = mock(URL.class);
	HttpURLConnection mockConnection2 = mock(HttpURLConnection.class);

	doReturn(mockUrl2)
	  .when(underTest)
	  .getUrl("userId", "https://graph.microsoft.com/v1.0/536e97e9-0d29-43ec-b8d5-a505d3ee6a8f/users/abc.xyz@example.com/memberOf?$skiptoken=RANDOMTOKEN");

	doReturn (mockConnection2)
	  .when(mockUrl2)
	  .openConnection();

	doReturn(getClass().getClassLoader().getResourceAsStream("get-members-page2.json"))
	  .when(mockConnection2)
	  .getInputStream();
	
	assertEquals(5, underTest.getUserGroupsMembership("token", "userId").size());
  }

  @Test
  public void check_fields() {
    assertThat(underTest.getKey()).isEqualTo("aad");
    assertThat(underTest.getName()).isEqualTo("Microsoft");
    assertThat(underTest.getDisplay().getIconPath()).isEqualTo("/static/authaad/ms-symbol.svg");
    assertThat(underTest.getDisplay().getBackgroundColor()).isEqualTo("#2F2F2F");
  }

  @Test
  public void init() {
    setSettings(true);
    OAuth2IdentityProvider.InitContext context = mock(OAuth2IdentityProvider.InitContext.class);
    when(context.generateCsrfState()).thenReturn("state");
    when(context.getCallbackUrl()).thenReturn("http://localhost/callback");

    underTest.init(context);

    verify(context).redirectTo("https://login.microsoftonline.com/null/oauth2/authorize?client_id=id&response_type=code&redirect_uri=http://localhost/callback&state=state&scope=openid");
  }

  @Test
  public void is_enabled() {
    settings.setProperty("sonar.auth.aad.clientId.secured", "id");
    settings.setProperty("sonar.auth.aad.clientSecret.secured", "secret");
    settings.setProperty("sonar.auth.aad.loginStrategy", AadSettings.LOGIN_STRATEGY_DEFAULT_VALUE);
    settings.setProperty("sonar.auth.aad.enabled", true);
    assertThat(underTest.isEnabled()).isTrue();

    settings.setProperty("sonar.auth.aad.enabled", false);
    assertThat(underTest.isEnabled()).isFalse();
  }

  @Test
  public void allow_signups() {
    settings.setProperty("sonar.auth.aad.allowUsersToSignUp", true);
    assertThat(underTest.allowsUsersToSignUp()).isTrue();

    settings.setProperty("sonar.auth.aad.allowUsersToSignUp", false);
    assertThat(underTest.allowsUsersToSignUp()).isFalse();
  }

  @Test
  public void page_url() throws MalformedURLException {
    URL testUrl;

    testUrl = underTest.getUrl("123e4567-e89b-12d3-a456-426655440000", null);
    assertEquals("https://graph.microsoft.com/v1.0/common/users/123e4567-e89b-12d3-a456-426655440000/transitiveMemberOf", testUrl.toString());

    testUrl = underTest.getUrl("123e4567-e89b-12d3-a456-426655440000", "https://graph.microsoft.com/v1.0/536e97e9-0d29-43ec-b8d5-a505d3ee6a8f/users/abc.xyz@example.com/memberOf?$skiptoken=RANDOMTOKEN");
    assertEquals("https://graph.microsoft.com/v1.0/536e97e9-0d29-43ec-b8d5-a505d3ee6a8f/users/abc.xyz@example.com/memberOf?$skiptoken=RANDOMTOKEN", testUrl.toString());
  }

  @Test
  public void shouldParseUsersNameFromUserInfoIfNotNull() {
    UserInfo mockUserInfo = mock(UserInfo.class);
    doReturn(GIVEN_NAME).when(mockUserInfo).getGivenName();
    doReturn(FAMILY_NAME).when(mockUserInfo).getFamilyName();
    AuthenticationResult mockResult = mock(AuthenticationResult.class);
    doReturn(mockUserInfo).when(mockResult).getUserInfo();

    assertEquals(GIVEN_NAME + " " + FAMILY_NAME, underTest.getUserName(mockResult));
  }

  @Test
  public void shouldParseUsersNameFromIdTokenIfUserInfoNameNull() {
    UserInfo mockUserInfo = mock(UserInfo.class);
    doReturn(null).when(mockUserInfo).getGivenName();
    doReturn(null).when(mockUserInfo).getFamilyName();
    AuthenticationResult mockResult = mock(AuthenticationResult.class);
    doReturn(mockUserInfo).when(mockResult).getUserInfo();
    doReturn(getMockJWT(EMPTY_JSON, JSON_WITH_NAME, EMPTY_JSON)).when(mockResult).getIdToken();

    assertEquals(EXPECTED_NAME, underTest.getUserName(mockResult));
  }

  @Test
  public void shouldFallBackToAnonymousIfNoNameFoundForUser() {
    UserInfo mockUserInfo = mock(UserInfo.class);
    doReturn(null).when(mockUserInfo).getGivenName();
    doReturn(null).when(mockUserInfo).getFamilyName();
    doReturn(DISPLAYABLE_ID).when(mockUserInfo).getDisplayableId();
    AuthenticationResult mockResult = mock(AuthenticationResult.class);
    doReturn(mockUserInfo).when(mockResult).getUserInfo();
    doReturn(getMockJWT(EMPTY_JSON, EMPTY_JSON, EMPTY_JSON)).when(mockResult).getIdToken();

    assertEquals(DISPLAYABLE_ID, underTest.getUserName(mockResult));
  }

  @Test
  public void shouldHandleNullIdToken() {
    UserInfo mockUserInfo = mock(UserInfo.class);
    doReturn(null).when(mockUserInfo).getGivenName();
    doReturn(null).when(mockUserInfo).getFamilyName();
    doReturn(DISPLAYABLE_ID).when(mockUserInfo).getDisplayableId();
    AuthenticationResult mockResult = mock(AuthenticationResult.class);
    doReturn(mockUserInfo).when(mockResult).getUserInfo();
    doReturn(null).when(mockResult).getIdToken();

    assertEquals(DISPLAYABLE_ID, underTest.getUserName(mockResult));
  }

  private String getMockJWT(String header, String payload, String signature) {
    return Base64.getEncoder().encodeToString(header.getBytes())
      + "."
      + Base64.getEncoder().encodeToString(payload.getBytes())
      + "."
      + Base64.getEncoder().encodeToString(signature.getBytes());
  }

  private void setSettings(boolean enabled) {
    if (enabled) {
      settings.setProperty("sonar.auth.aad.clientId.secured", "id");
      settings.setProperty("sonar.auth.aad.clientSecret.secured", "secret");
      settings.setProperty("sonar.auth.aad.loginStrategy", AadSettings.LOGIN_STRATEGY_DEFAULT_VALUE);
      settings.setProperty("sonar.auth.aad.directoryLocation", AadSettings.DIRECTORY_LOC_GLOBAL);
      settings.setProperty("sonar.auth.aad.enabled", true);
    } else {
      settings.setProperty("sonar.auth.aad.enabled", false);
    }
  }

}
