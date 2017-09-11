package com.soulgalore.crawler.core.impl;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BufferingResponseListener;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import com.soulgalore.crawler.core.CrawlerURL;
import com.soulgalore.crawler.core.HTMLPageResponse;
import com.soulgalore.crawler.core.HTMLPageResponseFetcher;
import com.soulgalore.crawler.util.StatusCode;

public class JettyClientResponseFetcher implements HTMLPageResponseFetcher {

	public static void main(String[] args) {
		JettyClientResponseFetcher test = new JettyClientResponseFetcher();
		test.get(new CrawlerURL("http://www.mkyong.com/"), false, new HashMap<>(), false);
	}

	@Override
	public HTMLPageResponse get(CrawlerURL url, boolean fetchBody, Map<String, String> requestHeaders, boolean followRedirectsToNewDomain) {
		// TODO Auto-generated method stub
		// return null;
		// Configure HttpClient here
		// httpClient.doStart();
		if (url.isWrongSyntax()) {
			return new HTMLPageResponse(url, StatusCode.SC_MALFORMED_URI.getCode(), Collections.<String, String>emptyMap(), "", "", 0, "", 0);
		}
		HttpClient httpClient = new HttpClient(new SslContextFactory());

		// final HttpGet get = new HttpGet(url.getUri());
		// newRequest.he

		// HttpEntity entity = null;
		final long start = System.currentTimeMillis();
		final Map<String, String> headersAndValues = new HashMap<>();

		try {
			httpClient.start();
			Request newRequest = httpClient.newRequest(url.getUrl());
			for (String key : requestHeaders.keySet()) {
				// get.setHeader(key, requestHeaders.get(key));
				newRequest.header(key, requestHeaders.get(key));
			}
			final CountDownLatch latch = new CountDownLatch(1);
			final HTMLPageResponse response2;
			final AtomicReference<HTMLPageResponse> responseRef = new AtomicReference<>();
			final int size;
			newRequest.onResponseHeaders(response -> {
				HttpFields headers = response.getHeaders();
				for (String name : headers.getFieldNamesCollection()) {
					headersAndValues.put(name, headers.getValuesList(name).get(0));
				}

			}).send(new BufferingResponseListener() {

				@Override
				public void onComplete(Result result) {
					System.out.println("********************complete******************************");
					// TODO Auto-generated method stub
					String encoding = getEncoding();
					String body = getContentAsString(encoding);
					// System.out.println(body);
					// body = contentAsString;
					int size = body.length();
					// getMediaType()

					// System.out.println(res);
					System.out.println(Thread.currentThread().getName());
					// result.getResponse().getStatus()
					responseRef.set(
							new HTMLPageResponse(url, result.getResponse().getStatus(), headersAndValues, body, encoding, size, getMediaType(), 0));
					latch.countDown();
				}

				@Override
				public void onFailure(Response response, Throwable failure) {
					// TODO Auto-generated method stub
					super.onFailure(response, failure);
					latch.countDown();
				}

			});

			latch.await();
			HTMLPageResponse htmlPageResponse = responseRef.get();
			System.out.println(htmlPageResponse.getUrl());
			System.out.println(htmlPageResponse.getResponseType());
			// return new HTMLPageResponse(url, sc, headersAndValues, body, encoding, size, type, fetchTime);
			return htmlPageResponse;

		} catch (Exception e) {
			System.err.println(e);
			return new HTMLPageResponse(url, StatusCode.SC_SERVER_RESPONSE_UNKNOWN.getCode(), Collections.<String, String>emptyMap(), "", "", 0, "",
					-1);
		} finally {
			// get.releaseConnection();
		}
	}

	@Override
	public void shutdown() {
		// TODO Auto-generated method stub

	}

}
