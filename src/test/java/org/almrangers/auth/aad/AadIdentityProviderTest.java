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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.config.Settings;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.server.authentication.OAuth2IdentityProvider;

public class AadIdentityProviderTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();
  Settings settings = new MapSettings();
  AadSettings aadSettings = new AadSettings(settings);
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

	doReturn(ClassLoader.class.getResourceAsStream("/get-members-page1.json"))
	  .when(mockConnection)
	  .getInputStream();
	
	
	URL mockUrl2 = mock(URL.class);
	HttpURLConnection mockConnection2 = mock(HttpURLConnection.class);

	doReturn(mockUrl2)
	  .when(underTest)
	  .getUrl("userId", "$skiptoken=X'1234567890'");

	doReturn (mockConnection2)
	  .when(mockUrl2)
	  .openConnection();

	doReturn(ClassLoader.class.getResourceAsStream("/get-members-page2.json"))
	  .when(mockConnection2)
	  .getInputStream();
	
	assertEquals(5, underTest.getUserGroupsMembership("token", "userId").size());
  }

  @Test
  public void check_fields() {
    assertThat(underTest.getKey()).isEqualTo("aad");
    assertThat(underTest.getName()).isEqualTo("Azure AD");
    assertThat(underTest.getDisplay().getIconPath()).isEqualTo("/static/authaad/azure.svg");
    assertThat(underTest.getDisplay().getBackgroundColor()).isEqualTo("#336699");
  }

  @Test
  public void init() {
    setSettings(true);
    OAuth2IdentityProvider.InitContext context = mock(OAuth2IdentityProvider.InitContext.class);
    when(context.generateCsrfState()).thenReturn("state");
    when(context.getCallbackUrl()).thenReturn("http://localhost/callback");

    underTest.init(context);

    verify(context).redirectTo("https://login.microsoftonline.com/null/oauth2/authorize?client_id=id&response_type=code&redirect_uri=http://localhost/callback&state=state");
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

  private void setSettings(boolean enabled) {
    if (enabled) {
      settings.setProperty("sonar.auth.aad.clientId.secured", "id");
      settings.setProperty("sonar.auth.aad.clientSecret.secured", "secret");
      settings.setProperty("sonar.auth.aad.loginStrategy", AadSettings.LOGIN_STRATEGY_DEFAULT_VALUE);
      settings.setProperty("sonar.auth.aad.enabled", true);
    } else {
      settings.setProperty("sonar.auth.aad.enabled", false);
    }
  }

}
