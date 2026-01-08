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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.*;

import okhttp3.HttpUrl;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.server.authentication.OAuth2IdentityProvider;
import org.sonar.api.server.authentication.UnauthorizedException;
import org.sonar.api.server.http.HttpRequest;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;

public class AadIdentityProviderTest {

  MapSettings settings = new MapSettings();
  AadSettings aadSettings = new AadSettings(settings.asConfig());
  AadIdentityProvider underTest = spy(new AadIdentityProvider(aadSettings));

  @Test
  public void check_fields() {
    assertThat(underTest.getKey()).isEqualTo("aad");
    assertThat(underTest.getName()).isEqualTo("Microsoft");
    assertThat(underTest.getDisplay().getIconPath()).isEqualTo("/static/authaad/ms-symbol.svg");
    assertThat(underTest.getDisplay().getBackgroundColor()).isEqualTo("#2F2F2F");
  }

  @Test
  // This test simulates trying to log in but getting a bad "code" from the
  // auth process, causing the request for auth and id tokens to fail.
  public void fail_login_on_bad_auth_code() {
    setSettings(true);
    OAuth2IdentityProvider.CallbackContext context = mock(OAuth2IdentityProvider.CallbackContext.class);
    HttpRequest request = mock(HttpRequest.class);

    when(request.getParameter("code")).thenReturn("9Q4mHqIAmAHORqpwwUaAxnGh");
    when(context.getHttpRequest()).thenReturn(request);

    assertThrows(UnauthorizedException.class,
        () -> underTest.onCallback(context)
    );

  }

  @Test
  public void init() {
    setSettings(true);
    OAuth2IdentityProvider.InitContext context = mock(OAuth2IdentityProvider.InitContext.class);
    when(context.generateCsrfState()).thenReturn("state");
    when(context.getCallbackUrl()).thenReturn("http://localhost/callback");

    underTest.init(context);

    ArgumentCaptor<String> redirectUrl = ArgumentCaptor.forClass(String.class);
    verify(context).redirectTo(redirectUrl.capture());

    // The redirect URL may not always have the query parameters in the same order
    // depending on how we're testing. The mess below is to test the individual
    // portions of the URL to make sure they're equivalent.

    HttpUrl expectedUrl = HttpUrl.parse("https://login.microsoftonline.com/null/oauth2/v2.0/authorize?response_type=code&redirect_uri=http%3A%2F%2Flocalhost%2Fcallback&state=state&client_id=id&response_mode=query&scope=openid+profile+email+User.Read");
    HttpUrl actualUrl = HttpUrl.parse(redirectUrl.getValue());

    // Split the expected URL into "path" and "query" parts to test them independently.
    assert expectedUrl != null;
    String expectedUrlPath = expectedUrl.toString().split("\\?")[0];

    HashMap<String, String> expectedUrlQuery = new HashMap<>();
    for(int i = 0, size = expectedUrl.querySize(); i < size; i++) {
      expectedUrlQuery.put(
          expectedUrl.queryParameterName(i),
          expectedUrl.queryParameterValue(i)
      );
    }

    // Split the actual URL into "path" and "query" parts to test them independently.
    assert actualUrl != null;
    String actualUrlPath = actualUrl.toString().split("\\?")[0];

    HashMap<String, String> actualUrlQuery = new HashMap<>();
    for(int i = 0, size = actualUrl.querySize(); i < size; i++) {
      actualUrlQuery.put(
          actualUrl.queryParameterName(i),
          actualUrl.queryParameterValue(i)
      );
    }

    assertThat(actualUrlPath).isEqualTo(expectedUrlPath);
    assertThat(actualUrlQuery).isEqualTo(expectedUrlQuery);
  }

  @Test
  public void is_enabled() {
    settings.setProperty("sonar.auth.aad.clientId.secured", "id");
    settings.setProperty("sonar.auth.aad.clientSecret.secured", "secret");
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

  private void setSettings(boolean enabled) {
    if (enabled) {
      settings.setProperty("sonar.auth.aad.clientId.secured", "id");
      settings.setProperty("sonar.auth.aad.clientSecret.secured", "secret");
      settings.setProperty("sonar.auth.aad.directoryLocation", AadSettings.DIRECTORY_LOC_GLOBAL);
      settings.setProperty("sonar.auth.aad.enabled", true);
    } else {
      settings.setProperty("sonar.auth.aad.enabled", false);
    }
  }

}
