package com.levelup.http;

import org.json.JSONObject;

import android.content.Context;
import android.test.AndroidTestCase;

import com.levelup.http.parser.BodyToJSONObject;

public class BodyToJSONObjectTest extends AndroidTestCase {

	@Override
	public void setContext(Context context) {
		super.setContext(context);
		HttpClient.setup(context);
	}
	
	public void testBogusData() throws Exception {
		BaseHttpRequest<JSONObject> request = new BaseHttpRequest.Builder<JSONObject>().
				setUrl("http://android.com/").
				setResponseHandler(BodyToJSONObject.RESPONSE_HANDLER).
				build();

		try {
			HttpClient.parseRequest(request);
		} catch (HttpDataParserException e) {
			assertNotNull(e.getMessage());
			assertTrue(e.getCause().getMessage().startsWith("Bad JSON data"));
			assertNotNull(e.getCause().getSourceData());
		}
	}
}