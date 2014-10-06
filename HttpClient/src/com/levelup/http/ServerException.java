package com.levelup.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import android.support.annotation.Nullable;

/**
 * Thrown when the server returns an HTTP error
 * <p>It contains an object corresponding to the parsed response body
 *
 * @author Created by robUx4 on 24/09/2014.
 */
public class ServerException extends HttpError {
	private final Object serverError;
	private final int httpStatusCode;
	private final HttpResponse response;
	private final HttpRequestInfo request;

	public ServerException(ImmutableHttpRequest request, Object serverError) {
		this.serverError = serverError;
		this.httpStatusCode = getHttpStatusCode(request.getHttpResponse());
		this.response = request.getHttpResponse();
		this.request = request.getHttpRequest();
	}

	/**
	 * @return The error object parsed by {@link com.levelup.http.ResponseHandler#errorParser}.
	 * May be {@code null}
	 */
	@Nullable
	public Object getServerError() {
		return serverError;
	}

	@Override
	public boolean isTemporaryFailure() {
		return httpStatusCode >= 500;
	}

	public List<Header> getReceivedHeaders() {
		if (null!=response) {
			try {
				final Map<String, List<String>> responseHeaders = response.getHeaderFields();
				if (null != responseHeaders) {
					ArrayList<Header> receivedHeaders = new ArrayList<Header>(responseHeaders.size());
					for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
						for (String value : entry.getValue()) {
							receivedHeaders.add(new Header(entry.getKey(), value));
						}
					}
					return receivedHeaders;
				}
			} catch (IllegalStateException ignored) {
				// okhttp 2.0.0 issue https://github.com/square/okhttp/issues/689
			} catch (IllegalArgumentException e) {
				// okhttp 2.0.0 issue https://github.com/square/okhttp/issues/875
			} catch (NullPointerException e) {
				// issue https://github.com/square/okhttp/issues/348
			}
		}
		return Collections.emptyList();
	}

	@Override
	public String getMessage() {
		return "serverError="+ String.valueOf(serverError);
	}

	@Override
	public int getStatusCode() {
		return httpStatusCode;
	}

	@Override
	public HttpRequestInfo getHttpRequest() {
		return request;
	}

	@Override
	public HttpResponse getHttpResponse() {
		return response;
	}

}
