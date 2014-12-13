package com.api.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Class to process client request and obtain parameters
 * @author PVR
 *
 */
public class ProxyRequest {

	private final String path;
	private final String query;
	private final Map<String, String> parameters;

	public ProxyRequest(String urlPath) throws MalformedURLException {
		URL url = new URL("http", "localhost", urlPath);
		this.path = url.getPath();
		this.query = url.getQuery() == null ? "" : url.getQuery();
		this.parameters = parseQuery(query);
	}

	public String getUrlPath() {
		return path;
	}

	public String getUrlQuery() {
		return query;
	}

	public String get(String parameter) {
		return parameters.get(parameter);
	}

	public Map<String, String> getParameters() {
		return parameters;
	}

	public int size() {
		return parameters.size();
	}

	private static Map<String, String> parseQuery(String query) {
		Map<String, String> parameters = new HashMap<String, String>();

		for (String item : query.split("&")) {
			String[] parameterValue = item.split("=");
			if (parameterValue.length == 2) {
				parameters.put(parameterValue[0], parameterValue[1]);
			}
		}

		return parameters;
	}
}