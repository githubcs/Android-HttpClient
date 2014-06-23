package com.levelup.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import org.apache.http.protocol.HTTP;
import org.json.JSONObject;

import android.net.Uri;
import android.text.TextUtils;

import com.levelup.http.signed.AbstractRequestSigner;

/**
 * Basic HTTP request to be passed to {@link HttpClient}
 * @see HttpRequestGet for a more simple API 
 * @see HttpRequestPost for a more simple POST API
 * @param <T> type of the data read from the HTTP response
 */
public class BaseHttpRequest<T> implements TypedHttpRequest<T> {
	private final Uri uri;
	private final Map<String,String> mRequestSetHeaders = new HashMap<String, String>();
	private final Map<String, HashSet<String>> mRequestAddHeaders = new HashMap<String, HashSet<String>>();
	private LoggerTagged mLogger;
	private HttpConfig mHttpConfig = BasicHttpConfig.instance;
	private HttpURLConnection httpResponse;
	private final String method;
	private final InputStreamParser<T> streamParser;
	private final HttpBodyParameters bodyParams;
	private final RequestSigner signer;
	private UploadProgressListener mProgressListener;

	/**
	 * Builder for 
	 * @param <T> type of the data read from the HTTP response
	 */
	public static class Builder<T> {
		private static final String DEFAULT_HTTP_METHOD = "GET";
		private static final String DEFAULT_POST_METHOD = "POST";

		private HttpBodyParameters bodyParams;
		private Uri uri;
		private InputStreamParser<T> streamParser;
		private String httpMethod = "GET";
		private RequestSigner signer;

		/**
		 * Constructor for the {@link BaseHttpRequest} builder, setting {@code GET} method by default
		 */
		public Builder() {
			setHttpMethod(DEFAULT_HTTP_METHOD);
		}

		/**
		 * Set the class that will be responsible to send the HTTP body of the query
		 * <p>sets {@code POST} method by default
		 * @param bodyParams the object that will write the HTTP body to the remote server
		 * @return Current Builder
		 */
		public Builder<T> setBody(HttpBodyParameters bodyParams) {
			return setBody(DEFAULT_POST_METHOD, bodyParams);
		}

		/**
		 * Set the class that will be responsible to send the HTTP body of the query
		 * @param postMethod HTTP method to use with this request, {@code GET} and {@code HEAD} not possible
		 * @param bodyParams the object that will write the HTTP body to the remote server
		 * @return Current Builder
		 * @see {@link #setHttpMethod(String)}
		 */
		public Builder<T> setBody(String postMethod, HttpBodyParameters bodyParams) {
			setHttpMethod(postMethod);
			if (null!=bodyParams && httpMethod!=null && !isMethodWithBody(httpMethod))
				throw new IllegalArgumentException("invalid body for HTTP method:"+httpMethod);
			this.bodyParams = bodyParams;
			return this;
		}

		/**
		 * Sets the HTTP method to use for the request like {@code GET}, {@code POST} or {@code HEAD}
		 * @param httpMethod HTTP method to use with this request
		 * @return Current Builder
		 */
		public Builder<T> setHttpMethod(String httpMethod) {
			if (TextUtils.isEmpty(httpMethod))
				throw new IllegalArgumentException("invalid null HTTP method");
			if (null!=bodyParams && !isMethodWithBody(httpMethod))
				throw new IllegalArgumentException("invalid HTTP method with body:"+httpMethod);
			this.httpMethod = httpMethod;
			return this;
		}

		/**
		 * Set the URL that will be queried on the remote server
		 * @param url requested on the server
		 * @return Current Builder
		 */
		public Builder<T> setUrl(String url) {
			return setUrl(url, null);
		}

		/**
		 * Set the URL that will be queried on the remote server
		 * @param url requested on the server
		 * @param uriParams parameters to add to the URL
		 * @return Current Builder
		 */
		public Builder<T> setUrl(String url, HttpUriParameters uriParams) {
			Uri uri = Uri.parse(url);
			if (null==uriParams) {
				this.uri = uri;
			} else {
				Uri.Builder uriBuilder = uri.buildUpon();
				uriParams.addUriParameters(uriBuilder);
				this.uri = uriBuilder.build();
			}
			return this;
		}

		/**
		 * Set the URL that will be queried on the remote server
		 * @param uri requested on the server
		 * @return Current Builder
		 */
		public Builder<T> setUri(Uri uri) {
			this.uri = uri;
			return this;
		}

		/**
		 * Set the parser that will be responsible for transforming the response body from the server into object {@code T}
		 * @param streamParser HTTP response body parser
		 * @return Current Builder
		 */
		public Builder<T> setStreamParser(InputStreamParser<T> streamParser) {
			this.streamParser = streamParser;
			return this;
		}

		/**
		 * Set the object that will be responsible for signing the {@link HttpRequest}
		 * @param signer object that will sign the {@link HttpRequest}
		 * @return Current Builder
		 */
		public Builder<T> setSigner(RequestSigner signer) {
			if (null==signer) {
				throw new IllegalArgumentException();
			}
			this.signer = signer;
			return this;
		}

		/**
		 * Build the HTTP request to run through {@link HttpClient}
		 */
		public BaseHttpRequest<T> build() {
			return new BaseHttpRequest<T>(this);
		}
	}

	private static boolean isMethodWithBody(String httpMethod) {
		return !TextUtils.equals(httpMethod, "GET") && !TextUtils.equals(httpMethod, "HEAD");
	}

	/**
	 * Constructor with a string HTTP URL
	 */
	protected BaseHttpRequest(String url) {
		this(new Builder<T>().setUrl(url));
	}

	/**
	 * Constructor with a {@link Uri} constructor
	 */
	protected BaseHttpRequest(Uri uri) {
		this(new Builder<T>().setUri(uri));
	}

	protected BaseHttpRequest(Builder<T> builder) {
		this.uri = builder.uri;
		this.method = builder.httpMethod;
		this.streamParser = builder.streamParser;
		this.bodyParams = builder.bodyParams;
		this.signer = builder.signer;
	}

	@Override
	public final String getHttpMethod() {
		return method;
	}

	@Override
	public final InputStreamParser<T> getInputStreamParser() {
		return streamParser;
	}

	/**
	 * Set the {@link LoggerTagged} that will be used to send logs for this request. {@code null} is OK
	 */
	public void setLogger(LoggerTagged logger) {
		mLogger = logger;
	}

	@Override
	public LoggerTagged getLogger() {
		return mLogger;
	}

	/**
	 * Set a {@link HttpConfig}, by default {@link BasicHttpConfig} is used
	 */
	@Override
	public void setHttpConfig(HttpConfig config) {
		mHttpConfig = config;
	}

	@Override
	public HttpConfig getHttpConfig() {
		return mHttpConfig;
	}

	@Override
	public void settleHttpHeaders() throws HttpException {
		if (null != bodyParams) {
			bodyParams.settleHttpHeaders(this);
		} else if (!isMethodWithBody(method)) {
			setHeader(HTTP.CONTENT_LEN, "0");
		}
		if (null!=signer)
			signer.sign(this);
	}

	@Override
	public void setConnectionProperties(HttpURLConnection connection) throws ProtocolException {
		if (null != bodyParams) {
			connection.setDoInput(true);
			connection.setDoOutput(true);

			bodyParams.setConnectionProperties(connection);
		}

		for (Entry<String, String> entry : mRequestSetHeaders.entrySet())
			connection.setRequestProperty(entry.getKey(), entry.getValue());
		for (Entry<String, HashSet<String>> entry : mRequestAddHeaders.entrySet()) {
			for (String value : entry.getValue())
				connection.addRequestProperty(entry.getKey(), value);
		}
	}

	@Override
	public void addHeader(String key, String value) {
		HashSet<String> values = mRequestAddHeaders.get(key);
		if (null==values) {
			values = new HashSet<String>();
			mRequestAddHeaders.put(key, values);
		}
		values.add(value);
	}

	@Override
	public void setHeader(String key, String value) {
		mRequestAddHeaders.remove(key);
		if (null==value)
			mRequestSetHeaders.remove(key);
		else
			mRequestSetHeaders.put(key, value);
	}

	@Override
	public String getHeader(String name) {
		if (mRequestSetHeaders.containsKey(name))
			return mRequestSetHeaders.get(name);
		if (mRequestAddHeaders.containsKey(name) && !mRequestAddHeaders.get(name).isEmpty())
			return mRequestAddHeaders.get(name).toArray(EMPTY_STRINGS)[0];
		return null;
	}

	private static final String[] EMPTY_STRINGS = {};

	public Header[] getAllHeaders() {
		List<Header> headers = null==HttpClient.getDefaultHeaders() ? new ArrayList<Header>() : new ArrayList<Header>(Arrays.asList(HttpClient.getDefaultHeaders()));
		for (Entry<String, String> setHeader : mRequestSetHeaders.entrySet()) {
			headers.add(new Header(setHeader.getKey(), setHeader.getValue()));
		}
		for (Entry<String, HashSet<String>> entries : mRequestAddHeaders.entrySet()) {
			for (String entry : entries.getValue()) {
				headers.add(new Header(entries.getKey(), entry));
			}
		}
		return headers.toArray(new Header[headers.size()]);
	}

	@Override
	public URL getURL() throws MalformedURLException {
		return new URL(uri.toString());
	}

	@Override
	public Uri getUri() {
		return uri;
	}

	/**
	 * This is {@code final} as {@link #outputBody(OutputStream)} should be the one extended
	 */
	@Override
	public final void outputBody(HttpURLConnection connection) throws IOException {
		if (null != bodyParams) {
			OutputStream output = null;
			try {
				output = connection.getOutputStream();
				outputBody(output);
			} finally {
				if (null != output) {
					output.close();
				}
			}
		}
	}

	public void setProgressListener(UploadProgressListener listener) {
		this.mProgressListener = listener;
	}

	public UploadProgressListener getProgressListener() {
		return mProgressListener;
	}

	public void outputBody(OutputStream output) throws IOException {
		final UploadProgressListener listener = mProgressListener;
		if (null != listener)
			listener.onParamUploadProgress(this, null, 0);
		if (null != bodyParams)
			bodyParams.writeBodyTo(output, this, listener);
		if (null != listener)
			listener.onParamUploadProgress(this, null, 100);
	}

	@Override
	public void setResponse(HttpURLConnection resp) {
		httpResponse = resp;
		CookieManager cookieMaster = HttpClient.getCookieManager();
		if (cookieMaster!=null) {
			try {
				cookieMaster.setCookieResponse(this, resp);
			} catch (IOException ignored) {
			}
		}
	}

	@Override
	public HttpURLConnection getResponse() {
		return httpResponse;
	}

	public RequestSigner getRequestSigner() {
		return signer;
	}

	protected String getToStringExtra() {
		String result = uri.toString();
		if (signer instanceof AbstractRequestSigner)
			result += " for " + ((AbstractRequestSigner) signer).getOAuthUser();
		return result;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(64);
		String simpleName = getClass().getSimpleName();
		if (simpleName == null || simpleName.length() <= 0) {
			simpleName = getClass().getName();
			int end = simpleName.lastIndexOf('.');
			if (end > 0) {
				simpleName = simpleName.substring(end+1);
			}
		}
		sb.append(simpleName);
		sb.append('{');
		sb.append(Integer.toHexString(System.identityHashCode(this)));
		sb.append(' ');
		sb.append(getToStringExtra());
		sb.append('}');
		return sb.toString();
	}

	@Override
	public HttpException.Builder newException() {
		return new HttpException.Builder(this);
	}

	@Override
	public HttpException.Builder newExceptionFromResponse(Throwable cause) {
		InputStream errorStream = null;
		HttpException.Builder builder = null;
		try {
			final HttpURLConnection response = getResponse();
			builder = newException();
			builder.setErrorCode(HttpException.ERROR_HTTP);
			builder.setHTTPResponse(response);
			builder.setCause(cause);

			MediaType type = MediaType.parse(response.getContentType());
			if (Util.MediaTypeJSON.equalsType(type)) {
				errorStream = getParseableErrorStream(response);
				JSONObject jsonData = InputStreamJSONObjectParser.instance.parseInputStream(errorStream, this);
				builder.setErrorMessage(jsonData.toString());
				builder = handleJSONError(builder, jsonData);
			} else if (null==type || "text".equals(type.type())) {
				errorStream = getParseableErrorStream(response);
				String errorData = InputStreamStringParser.instance.parseInputStream(errorStream, this);
				builder.setErrorMessage(errorData);
			}
		} catch (IOException ignored) {
		} catch (ParserException ignored) {
		} finally {
			if (null!=errorStream) {
				try {
					errorStream.close();
				} catch (NullPointerException ignored) {
					// okhttp 2.0 bug https://github.com/square/okhttp/issues/690
				} catch (IOException ignored) {
				}
			}
		}
		return builder;
	}

	private static InputStream getParseableErrorStream(HttpURLConnection response) throws IOException {
		InputStream errorStream = response.getErrorStream();
		if (null==errorStream)
			errorStream = response.getInputStream();

		if (null==errorStream)
			return null;

		if ("deflate".equals(response.getContentEncoding()) && !(errorStream instanceof InflaterInputStream))
			errorStream = new InflaterInputStream(errorStream);
		if ("gzip".equals(response.getContentEncoding()) && !(errorStream instanceof GZIPInputStream))
			errorStream = new GZIPInputStream(errorStream);

		return errorStream;
	}

	/**
	 * Handle error data returned in JSON format
	 * @param builder
	 * @param jsonData
	 * @return
	 */
	protected HttpException.Builder handleJSONError(HttpException.Builder builder, JSONObject jsonData) {
		// do nothing, we don't handle JSON errors
		return builder;
	}
}
