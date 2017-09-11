package com.soulgalore.crawler.core.impl;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.UIDefaults.LazyValue;

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

public class JettyClientResponseFetcher {

	private Set<HTMLPageResponse> responses;

	public JettyClientResponseFetcher() {
		this.responses = new HashSet<>();
	}

	public Set<HTMLPageResponse> getResponses() {
		return this.responses;
	}

	public static void main(String[] args) {
		JettyClientResponseFetcher test = new JettyClientResponseFetcher();
		Set<CrawlerURL> urls = new HashSet<>();
		urls.add(new CrawlerURL("http://www.mkyong.com/"));
		urls.add(new CrawlerURL("http://www.google.com/"));
		System.out.println("starting.....");

		test.get(urls, new HashMap<>());
		for (HTMLPageResponse rs : test.responses) {
			System.out.println(rs.getPageUrl());
			System.out.println(rs.getBody());

		}
	}

	public void getResponse(CrawlerURL url, Map<String, String> requestHeaders, CountDownLatch latch, HttpClient httpClient) {
		if (url.isWrongSyntax()) {
			HTMLPageResponse htmlPageResponse = new HTMLPageResponse(url, StatusCode.SC_MALFORMED_URI.getCode(),
					Collections.<String, String>emptyMap(), "", "", 0, "", 0);
			responses.add(htmlPageResponse);
			latch.countDown();
		}
		// HttpClient httpClient = new HttpClient(new SslContextFactory());

		// HttpEntity entity = null;
		// final long start = System.currentTimeMillis();
		final Map<String, String> headersAndValues = new HashMap<>();

		try {
			// httpClient.start();
			Request newRequest = httpClient.newRequest(url.getUrl());
			for (String key : requestHeaders.keySet()) {
				// get.setHeader(key, requestHeaders.get(key));
				newRequest.header(key, requestHeaders.get(key));
			}
			// final CountDownLatch latch = new CountDownLatch(1);
			// final AtomicReference<List<HTMLPageResponse>> responseRef = new AtomicReference<>();
			final AtomicReference<HTMLPageResponse> responseRef = new AtomicReference<>();
			// final int size;
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
					List<HTMLPageResponse> list = new ArrayList<>();
					HTMLPageResponse hpresponse = new HTMLPageResponse(url, result.getResponse().getStatus(), headersAndValues, body, encoding, size,
							getMediaType(), 0);
					// responseRef.set(hpresponse);
					responses.add(hpresponse);
					// responseRef.set(
					// new HTMLPageResponse(url, result.getResponse().getStatus(), headersAndValues, body, encoding, size, getMediaType(), 0));
					latch.countDown();
				}

				@Override
				public void onFailure(Response response, Throwable failure) {
					// TODO Auto-generated method stub
					super.onFailure(response, failure);
					latch.countDown();
				}

			});

			// latch.await();
			// HTMLPageResponse htmlPageResponse = responseRef.get();
			// System.out.println(htmlPageResponse.getUrl());
			// System.out.println(htmlPageResponse.getResponseType());
			// // return new HTMLPageResponse(url, sc, headersAndValues, body, encoding, size, type, fetchTime);
			// return htmlPageResponse;

		} catch (Exception e) {
			System.err.println(e);
			HTMLPageResponse htmlPageResponse = new HTMLPageResponse(url, StatusCode.SC_SERVER_RESPONSE_UNKNOWN.getCode(),
					Collections.<String, String>emptyMap(), "", "", 0, "", -1);
			responses.add(htmlPageResponse);
			latch.countDown();
		}
	}

	public void get(Set<CrawlerURL> urls, Map<String, String> requestHeaders) {
		try {
			HttpClient httpClient = new HttpClient(new SslContextFactory());
			httpClient.start();
			// Set<HTMLPageResponse> responses = new HashSet<>();
			final CountDownLatch latch = new CountDownLatch(urls.size());
			for (CrawlerURL crawlerURL : urls) {
				getResponse(crawlerURL, requestHeaders, latch, httpClient);
				// responses.add(response);
			}
			latch.await();
			httpClient.stop();
			System.out.println("+++++");
			// return responses;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			// return null;
		}
	}

}
