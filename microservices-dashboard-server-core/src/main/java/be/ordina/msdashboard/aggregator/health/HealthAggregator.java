/*
 * Copyright 2015-2018 the original author or authors.
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

package be.ordina.msdashboard.aggregator.health;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.ordina.msdashboard.applicationinstance.ApplicationInstance;
import be.ordina.msdashboard.applicationinstance.ApplicationInstanceService;
import be.ordina.msdashboard.events.HealthInfoRetrievalFailed;
import be.ordina.msdashboard.events.HealthInfoRetrieved;
import be.ordina.msdashboard.events.NewServiceInstanceDiscovered;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Aggregator for the /health endpoint of a regular Spring Boot application.
 *
 * @author Dieter Hubau
 * @author Tim Ysewyn
 */
public class HealthAggregator {

	private static final Logger logger = LoggerFactory.getLogger(HealthAggregator.class);

	private final WebClient webClient;
	private final ApplicationInstanceService applicationInstanceService;
	private final ApplicationEventPublisher publisher;

	public HealthAggregator(ApplicationInstanceService applicationInstanceService, WebClient webClient, ApplicationEventPublisher publisher) {
		this.applicationInstanceService = applicationInstanceService;
		this.webClient = webClient;
		this.publisher = publisher;
	}

	@EventListener({ NewServiceInstanceDiscovered.class })
	public void handleApplicationInstanceEvent(NewServiceInstanceDiscovered event) {
		ApplicationInstance serviceInstance = (ApplicationInstance) event.getSource();
		checkHealthInformation(serviceInstance);
	}

	@Scheduled(fixedRateString = "${aggregator.health.rate:10000}")
	public void aggregateHealthInformation() {
		logger.debug("Aggregating [HEALTH] information");
		this.applicationInstanceService.getApplicationInstances()
				.parallelStream()
				.forEach(this::checkHealthInformation);
	}

	private void checkHealthInformation(ApplicationInstance instance) {
		URI uri = instance.getHealthEndpoint();
		this.webClient.get().uri(uri).retrieve().bodyToMono(HealthWrapper.class)
				.defaultIfEmpty(new HealthWrapper(Status.DOWN, new HashMap<>()))
				.map(HealthWrapper::getHealth)
				.doOnError(exception -> {
					logger.debug("Could not retrieve health information for [" + uri + "]", exception);
					this.publisher.publishEvent(new HealthInfoRetrievalFailed(instance));
				})
				.subscribe(healthInfo -> {
					logger.debug("Retrieved health information for application instance [{}]", instance.getId());
					this.publisher.publishEvent(new HealthInfoRetrieved(instance, healthInfo));
				});
	}

	/**
	 * Wrapper for the Health class since it doesn't have correct constructors for
	 * Jackson.
	 */
	static class HealthWrapper {

		private Health health;

		@JsonCreator
		HealthWrapper(@JsonProperty("status") Status status, @JsonProperty("details") Map<String, Object> details) {
			this.health = Health.status(status).withDetails(details == null ? new HashMap<>() : details).build();
		}

		private Health getHealth() {
			return this.health;
		}

	}

}
