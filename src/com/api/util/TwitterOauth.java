package com.api.util;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.scribe.builder.ServiceBuilder;
import org.scribe.builder.api.TwitterApi;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.oauth.OAuthService;

/**
 * Class to sign request with Oauth authentication before sending it to proxied API
 * @author PVR
 *
 */
public class TwitterOauth {

	private String resourceUrl;
	private OAuthRequest request;
	private String action;
	private final Logger logger = Logger.getLogger(TwitterOauth.class.getName());

	public TwitterOauth(String resource, String req) {
		this.resourceUrl = resource;
		this.action = req;
	}
	
	/**
	 * Sign request with Oauth authentication
	 * @return singed request
	 */
	public OAuthRequest getSignedRequest() {
		try {
			OAuthService oservice = new ServiceBuilder()
					.provider(TwitterApi.class)
					.apiKey("t9PlQtAIGQrJC2HGAozoiobwh")
					.apiSecret(
							"lvo9xu58Pv5Evntmv5QNO4SVt1kugNGlMvBvpgLhsWL3Zy596U")
					.build();

			Token token = new Token(
					"2916652344-XScJAIqHF8E785vYHTazui6zECYJ76I8PMgIr28",
					"jETmnuRoCHJKysaEed70k75daWNxw3mllo2vfN4xuEoIE");
			if (action.equals("get")) {
				request = new OAuthRequest(Verb.GET, resourceUrl);
			}
			if (action.equals("post")) {
				request = new OAuthRequest(Verb.POST, resourceUrl);
			}
			oservice.signRequest(token, request);
		} catch (Exception e) {
			logger.log(Level.WARNING, e.getMessage(),e);
		}
		return request;
	}
}
