package com.davidagood.spring.oauth.clientcredentials;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@Configuration
class SpringSecurityDisabler extends WebSecurityConfigurerAdapter {

	SpringSecurityDisabler() {
		super(true); // disable defaults
	}

	@Override
	protected void configure(HttpSecurity http) {
		// Do nothing
	}

}
