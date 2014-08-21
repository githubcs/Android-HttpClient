package com.levelup.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;


public interface HttpResponse {
	String getContentType();
	int getResponseCode() throws IOException;
	Map<String, List<String>> getHeaderFields();
	String getHeaderField(String name);
	int getContentLength();
	String getResponseMessage() throws IOException;
	String getContentEncoding();
	void disconnect();
	InputStream getContentStream() throws IOException;
}
