package com.soulgalore.crawler.core.impl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.security.auth.x500.X500Principal;
import javax.swing.JScrollBar;

import org.apache.http.HttpStatus;
import org.eclipse.jetty.util.HostMap;

import com.google.inject.Inject;
import com.soulgalore.crawler.core.Crawler;
import com.soulgalore.crawler.core.CrawlerConfiguration;
import com.soulgalore.crawler.core.CrawlerResult;
import com.soulgalore.crawler.core.CrawlerURL;
import com.soulgalore.crawler.core.HTMLPageResponse;
import com.soulgalore.crawler.core.HTMLPageResponseCallable;
import com.soulgalore.crawler.core.HTMLPageResponseFetcher;
import com.soulgalore.crawler.core.PageURLParser;
import com.soulgalore.crawler.util.StatusCode;

/**
 * Crawl urls within the same domain.
 * 
 */
public class JettyCrawler {

	private final HTMLPageResponseFetcher responseFetcher;
	private final ExecutorService service;
	private final PageURLParser parser;

	/**
	 * Create a new crawler.
	 * 
	 * @param theResponseFetcher
	 *            the response fetcher to use.
	 * @param theService
	 *            the thread pool.
	 * @param theParser
	 *            the parser.
	 */
	public JettyCrawler(HTMLPageResponseFetcher theResponseFetcher, ExecutorService theService, PageURLParser theParser) {
		service = theService;
		responseFetcher = theResponseFetcher;
		parser = theParser;
	}
	
	public JettyCrawler(PageURLParser theParser) {
		parser = theParser;
		responseFetcher = null;
		service = null;
	}
	
	public static void main(String[] args) throws URISyntaxException {
		JettyCrawler jc = new JettyCrawler(new AhrefPageURLParser());
//		jc.getNextLevelLinks(allUrls, currUrls, verifiedUrls, requestHeaders);
		CrawlerConfiguration config = CrawlerConfiguration.builder().setStartUrl("https://www.scientificamerican.com/podcast/60-second-science/").setMaxLevels(2).build();
		
		
		jc.getUrl(config);
	}
	
	public static String getDomainName(String url) throws URISyntaxException {
	    URI uri = new URI(url);
	    return uri.getHost();

	}

	/**
	 * Shutdown the crawler.
	 */
	public void shutdown() {
		if (service != null)
			service.shutdown();
		if (responseFetcher != null)
			responseFetcher.shutdown();
	}
	
	public void getUrl(CrawlerConfiguration configuration) throws URISyntaxException {
		final Map<String, String> requestHeaders = configuration.getRequestHeadersMap();
//		final HTMLPageResponse resp = verifyInput(configuration.getStartUrl(), requestHeaders);

		String host = getDomainName(configuration.getStartUrl());
		int level = 0;
		Set<CrawlerURL> currUrls = new HashSet<>();
		final Set<CrawlerURL> allUrls = new LinkedHashSet<CrawlerURL>();

		CrawlerURL startURI = new CrawlerURL(configuration.getStartUrl());
		currUrls.add(startURI);
		allUrls.add(startURI);
		
		while (level < configuration.getMaxLevels()) {
			currUrls.forEach(url -> System.out.println(url));
			System.out.println("---------------------------");
			currUrls = getNextLevelLinks(allUrls, currUrls, host, requestHeaders);
			currUrls.forEach(url -> System.out.println(url));
			level++;
		}

//		verifiedUrls.add(resp);

//		final String host = resp.getPageUrl().getHost();
//		getNextLevelLinks(allUrls, currUrls, verifiedUrls, new HashMap<>());
	}

	/**
	 * Get the urls.
	 * 
	 * @param configuration
	 *            how to perform the crawl
	 * @return the result of the crawl
	 */
	public CrawlerResult getUrls(CrawlerConfiguration configuration) {

		final Map<String, String> requestHeaders = configuration.getRequestHeadersMap();
		final HTMLPageResponse resp = verifyInput(configuration.getStartUrl(), configuration.getOnlyOnPath(), requestHeaders);

		int level = 0;

		final Set<CrawlerURL> allUrls = new LinkedHashSet<CrawlerURL>();
		final Set<HTMLPageResponse> verifiedUrls = new LinkedHashSet<HTMLPageResponse>();
		final Set<HTMLPageResponse> nonWorkingResponses = new LinkedHashSet<HTMLPageResponse>();

		verifiedUrls.add(resp);

		final String host = resp.getPageUrl().getHost();

		if (configuration.getMaxLevels() > 0) {

			// set the start url
			Set<CrawlerURL> nextToFetch = new LinkedHashSet<CrawlerURL>();
			nextToFetch.add(resp.getPageUrl());

			while (level < configuration.getMaxLevels()) {

				final Map<Future<HTMLPageResponse>, CrawlerURL> futures = new HashMap<Future<HTMLPageResponse>, CrawlerURL>(nextToFetch.size());

				for (CrawlerURL testURL : nextToFetch) {
					futures.put(service.submit(new HTMLPageResponseCallable(testURL, responseFetcher, true, requestHeaders, false)), testURL);
				}

				nextToFetch = fetchNextLevelLinks(futures, allUrls, nonWorkingResponses, verifiedUrls, host, configuration.getOnlyOnPath(),
						configuration.getNotOnPath());
				level++;
			}
		} else {
			allUrls.add(resp.getPageUrl());
		}

		if (configuration.isVerifyUrls())
			verifyUrls(allUrls, verifiedUrls, nonWorkingResponses, requestHeaders);

		LinkedHashSet<CrawlerURL> workingUrls = new LinkedHashSet<CrawlerURL>();
		for (HTMLPageResponse workingResponses : verifiedUrls) {
			workingUrls.add(workingResponses.getPageUrl());
		}

		// TODO find a better fix for this
		// wow, this is a hack to fix if the first URL is redirected,
		// then we want to keep that original start url
		if (workingUrls.size() >= 1) {
			List<CrawlerURL> list = new ArrayList<CrawlerURL>(workingUrls);
			list.add(0, new CrawlerURL(configuration.getStartUrl()));
			list.remove(1);
			workingUrls.clear();
			workingUrls.addAll(list);
		}

		return new CrawlerResult(configuration.getStartUrl(), configuration.isVerifyUrls() ? workingUrls : allUrls, verifiedUrls,
				nonWorkingResponses);

	}

	public Set<CrawlerURL> getNextLevelLinks(Set<CrawlerURL> allUrls, Set<CrawlerURL> currUrls, String host,
			Map<String, String> requestHeaders) {
		JettyClientResponseFetcher fetcher = new JettyClientResponseFetcher();
		fetcher.get(currUrls, requestHeaders);
		Set<HTMLPageResponse> responses = fetcher.getResponses();
		final Set<CrawlerURL> nextLevel = new LinkedHashSet<CrawlerURL>();

		for (HTMLPageResponse response : responses) {
			if (HttpStatus.SC_OK == response.getResponseCode() && response.getResponseType().indexOf("html") > 0) {
				// we know that this links work
				final Set<CrawlerURL> allLinks = parser.get(response);

				for (CrawlerURL link : allLinks) {
					// only add if it is the same host
					if (host.equals(link.getHost()) && !allUrls.contains(link)) {
						nextLevel.add(link);
						allUrls.add(link);
					}
				}
			} else {
				allUrls.remove(response.getUrl());
			}
		}
		
		
		
		return nextLevel;
	}

	/**
	 * Fetch links to the next level of the crawl.
	 * 
	 * @param responses
	 *            holding bodys where we should fetch the links.
	 * @param allUrls
	 *            every url we have fetched so far
	 * @param nonWorkingUrls
	 *            the urls that didn't work to fetch
	 * @param verifiedUrls
	 *            responses that are already verified
	 * @param host
	 *            the host we are working on
	 * @param onlyOnPath
	 *            only fetch files that match the following path. If empty, all will match.
	 * @param notOnPath
	 *            don't collect/follow urls that contains this text in the url
	 * @return the next level of links that we should fetch
	 */
	protected Set<CrawlerURL> fetchNextLevelLinks(Map<Future<HTMLPageResponse>, CrawlerURL> responses, Set<CrawlerURL> allUrls,
			Set<HTMLPageResponse> nonWorkingUrls, Set<HTMLPageResponse> verifiedUrls, String host, String onlyOnPath, String notOnPath) {

		final Set<CrawlerURL> nextLevel = new LinkedHashSet<CrawlerURL>();

		final Iterator<Entry<Future<HTMLPageResponse>, CrawlerURL>> it = responses.entrySet().iterator();

		while (it.hasNext()) {

			final Entry<Future<HTMLPageResponse>, CrawlerURL> entry = it.next();

			try {

				final HTMLPageResponse response = entry.getKey().get();
				if (HttpStatus.SC_OK == response.getResponseCode() && response.getResponseType().indexOf("html") > 0) {
					// we know that this links work
					verifiedUrls.add(response);
					final Set<CrawlerURL> allLinks = parser.get(response);

					for (CrawlerURL link : allLinks) {
						// only add if it is the same host
						if (host.equals(link.getHost()) && link.getUrl().contains(onlyOnPath)
								&& (notOnPath.equals("") ? true : (!link.getUrl().contains(notOnPath)))) {
							if (!allUrls.contains(link)) {
								nextLevel.add(link);
								allUrls.add(link);
							}
						}
					}
				} else if (HttpStatus.SC_OK != response.getResponseCode()
						|| StatusCode.SC_SERVER_REDIRECT_TO_NEW_DOMAIN.getCode() == response.getResponseCode()) {
					allUrls.remove(entry.getValue());
					nonWorkingUrls.add(response);
				} else {
					// it is of another content type than HTML or if it redirected to another domain
					allUrls.remove(entry.getValue());
				}

			} catch (InterruptedException e) {
				nonWorkingUrls.add(new HTMLPageResponse(entry.getValue(), StatusCode.SC_SERVER_RESPONSE_UNKNOWN.getCode(),
						Collections.<String, String>emptyMap(), "", "", 0, "", -1));
			} catch (ExecutionException e) {
				nonWorkingUrls.add(new HTMLPageResponse(entry.getValue(), StatusCode.SC_SERVER_RESPONSE_UNKNOWN.getCode(),
						Collections.<String, String>emptyMap(), "", "", 0, "", -1));
			}
		}
		return nextLevel;
	}

	/**
	 * Verify that all urls in allUrls returns 200. If not, they will be removed from that set and instead added to the nonworking list.
	 * 
	 * @param allUrls
	 *            all the links that has been fetched
	 * @param nonWorkingUrls
	 *            links that are not working
	 */
	private void verifyUrls(Set<CrawlerURL> allUrls, Set<HTMLPageResponse> verifiedUrls, Set<HTMLPageResponse> nonWorkingUrls,
			Map<String, String> requestHeaders) {

		Set<CrawlerURL> urlsThatNeedsVerification = new LinkedHashSet<CrawlerURL>(allUrls);

		urlsThatNeedsVerification.removeAll(verifiedUrls);

		final Set<Callable<HTMLPageResponse>> tasks = new HashSet<Callable<HTMLPageResponse>>(urlsThatNeedsVerification.size());

		for (CrawlerURL testURL : urlsThatNeedsVerification) {
			tasks.add(new HTMLPageResponseCallable(testURL, responseFetcher, true, requestHeaders, false));
		}

		try {
			// wait for all urls to verify
			List<Future<HTMLPageResponse>> responses = service.invokeAll(tasks);

			for (Future<HTMLPageResponse> future : responses) {
				if (!future.isCancelled()) {
					HTMLPageResponse response = future.get();
					if (response.getResponseCode() == HttpStatus.SC_OK && response.getResponseType().indexOf("html") > 0) {
						// remove, way of catching interrupted / execution e
						urlsThatNeedsVerification.remove(response.getPageUrl());
						verifiedUrls.add(response);
					} else if (response.getResponseCode() == HttpStatus.SC_OK) {
						// it is not HTML
						urlsThatNeedsVerification.remove(response.getPageUrl());
					} else {
						nonWorkingUrls.add(response);
					}
				}
			}

		} catch (InterruptedException e1) {
			// TODO add some logging
			e1.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// TODO: We can have a delta here if the exception occur

	}

	private HTMLPageResponse fetchOnePage(CrawlerURL url, Map<String, String> requestHeaders) {
		return responseFetcher.get(url, true, requestHeaders, true);
	}

	private HTMLPageResponse verifyInput(String startUrl, String string, Map<String, String> requestHeaders) {

		final CrawlerURL pageUrl = new CrawlerURL(startUrl);

		if (pageUrl.isWrongSyntax())
			throw new IllegalArgumentException("The url " + startUrl + " isn't a valid url ");

		// verify that the first url is reachable
		final HTMLPageResponse resp = fetchOnePage(pageUrl, requestHeaders);

		if (!StatusCode.isResponseCodeOk(resp.getResponseCode()))
			throw new IllegalArgumentException(
					"The start url: " + startUrl + " couldn't be fetched, response code " + StatusCode.toFriendlyName(resp.getResponseCode()));
		return resp;
	}

}