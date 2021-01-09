package com.davidagood.spring.oauth.clientcredentials;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@ConfigurationProperties("secret-words-client")
@ConstructorBinding
public class SecretWordsClientConfig {

	private final String url;

	public SecretWordsClientConfig(String url) {
		this.url = url;
	}

	public String getUrl() {
		return url;
	}

}
