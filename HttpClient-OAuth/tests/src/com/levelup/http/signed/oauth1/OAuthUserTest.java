package com.levelup.http.signed.oauth1;

import junit.framework.TestCase;

import com.levelup.http.BaseHttpRequest;
import com.levelup.http.HttpClient;
import com.levelup.http.HttpRequest;
import com.levelup.http.InputStreamStringParser;
import com.levelup.http.UriParams;
import com.levelup.http.signed.OAuthClientApp;
import com.levelup.http.signed.OAuthUser;

public class OAuthUserTest extends TestCase {

	private static final OAuthClientApp testApp = new OAuthClientApp() {
		@Override
		public String getConsumerSecret() {
			return "secret";
		}

		@Override
		public String getConsumerKey() {
			return "consumer-key";
		}
	};

	public void testNullUser() throws Exception {
		RequestSignerOAuth1 signer = new RequestSignerOAuth1(testApp, null);
		UriParams uriParams = new UriParams(1);
		uriParams.add("msg", "signed message");

		BaseHttpRequest<String> get = new BaseHttpRequest.Builder<String>().
				setSigner(signer).
				setUrl("http://www.levelupstudio.com/", uriParams).
				setStreamParser(InputStreamStringParser.instance).
				build();
		HttpClient.parseRequest(get);
	}

	public void testEmptyUser() throws Exception {
		OAuthUser emptyUser = new OAuthUser() {
			@Override
			public String getTokenSecret() {
				return null;
			}

			@Override
			public String getToken() {
				return null;
			}
		};

		RequestSignerOAuth1 signer = new RequestSignerOAuth1(testApp, emptyUser);
		UriParams uriParams = new UriParams(1);
		uriParams.add("msg", "signed message");

		BaseHttpRequest<String> get = new BaseHttpRequest.Builder<String>().
				setSigner(signer).
				setUrl("http://www.levelupstudio.com/", uriParams).
				setStreamParser(InputStreamStringParser.instance).
				build();
		HttpClient.parseRequest(get);
	}

}
