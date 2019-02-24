/*
 * Copyright 2015-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package be.ordina.msdashboard.security;

import org.junit.Test;

import be.ordina.msdashboard.autoconfigure.MicroservicesDashboardServerAutoConfiguration;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.discovery.composite.CompositeDiscoveryClientAutoConfiguration;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Tim Ysewyn
 */
public class SecurityConfigurationTests {

	private static final String BASIC_PREFIX = "ms-dashboard.security.client.basic";
	private static final String REGISTRATION_PREFIX = "spring.security.oauth2.client.registration.ms-dashboard";
	private static final String PROVIDER_PREFIX = "spring.security.oauth2.client.provider";

	private ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(
					MicroservicesDashboardServerAutoConfiguration.class,
					CompositeDiscoveryClientAutoConfiguration.class));

	@Test
	public void shouldConfigureBeanWithoutSecurity() {
		this.contextRunner.run(context -> {
			assertThat(context.containsBean("machine-to-machine-web-client")).isTrue();
			assertThat(context.getBean(WebClient.class)).isNotNull();
		});
	}

	@Test
	public void shouldConfigureBeanWithBasicAuth() {
		this.contextRunner
			.withPropertyValues(BASIC_PREFIX + ".username=user", BASIC_PREFIX + ".password=password")
			.run(context -> {
				assertThat(context.containsBean("machine-to-machine-web-client")).isTrue();
				assertThat(context.getBean(WebClient.class)).isNotNull();
		});
	}

	@Test
	public void shouldConfigureBeanWithOAuthUsingGitHubAsProvider() {
		this.contextRunner
			.withPropertyValues(REGISTRATION_PREFIX + ".client-id=ms-dashboard",
					REGISTRATION_PREFIX + ".client-secret=secret",
					REGISTRATION_PREFIX + ".provider=github")
			.run(context -> {
				assertThat(context.containsBean("machine-to-machine-web-client")).isTrue();
				assertThat(context.getBean(WebClient.class)).isNotNull();
		});
	}

	@Test
	public void shouldConfigureBeanWithOAuthUsingCustomProvider() {
		this.contextRunner
			.withPropertyValues(REGISTRATION_PREFIX + ".client-id=ms-dashboard",
					REGISTRATION_PREFIX + ".client-secret=secret",
					REGISTRATION_PREFIX + ".provider=keycloak",
					REGISTRATION_PREFIX + ".authorization-grant-type=client_credentials",
					PROVIDER_PREFIX + ".keycloak.authorization-uri=http://authorization-uri.com",
					PROVIDER_PREFIX + ".keycloak.token-uri=http://token-uri.com",
					PROVIDER_PREFIX + ".keycloak.user-info-uri=userInfoUri",
					PROVIDER_PREFIX + ".keycloak.user-name-attribute-name=login")
			.run(context -> {
				assertThat(context.containsBean("machine-to-machine-web-client")).isTrue();
				assertThat(context.getBean(WebClient.class)).isNotNull();
		});
	}

}
