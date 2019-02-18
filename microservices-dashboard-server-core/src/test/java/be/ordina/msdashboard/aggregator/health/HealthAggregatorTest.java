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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import reactor.core.publisher.Mono;

import be.ordina.msdashboard.applicationinstance.ApplicationInstance;
import be.ordina.msdashboard.applicationinstance.ApplicationInstanceMother;
import be.ordina.msdashboard.applicationinstance.ApplicationInstanceService;
import be.ordina.msdashboard.events.HealthInfoRetrievalFailed;
import be.ordina.msdashboard.events.HealthInfoRetrieved;
import be.ordina.msdashboard.events.NewServiceInstanceDiscovered;

import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.rule.OutputCapture;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.when;

/**
 * Unit test for the HealthAggregator.
 *
 * @author Steve De Zitter
 * @author Tim Ysewyn
 */
@RunWith(MockitoJUnitRunner.class)
public class HealthAggregatorTest {

	@Rule
	public OutputCapture outputCapture = new OutputCapture();

	@Mock
	private WebClient webClient;
	@Mock
	private ApplicationInstanceService applicationInstanceService;
	@Mock
	private ApplicationEventPublisher applicationEventPublisher;

	@Mock
	private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
	@Mock
	private WebClient.RequestHeadersSpec requestHeadersSpec;
	@Mock
	private WebClient.ResponseSpec responseSpec;

	@Captor
	private ArgumentCaptor<ApplicationEvent> applicationEventArgumentCaptor;

	@InjectMocks
	private HealthAggregator healthAggregator;

	@Before
	public void setupMocks() {
		when(this.webClient.get()).thenReturn(this.requestHeadersUriSpec);
		when(this.requestHeadersUriSpec.uri(any(URI.class))).thenReturn(this.requestHeadersSpec);
		when(this.requestHeadersSpec.retrieve()).thenReturn(this.responseSpec);
	}

	@Test
	public void shouldHandleApplicationInstanceEvent() {
		NewServiceInstanceDiscovered newServiceInstanceDiscovered =
				NewServiceInstanceDiscoveredMother.defaultNewServiceInstanceDiscovered();

		when(this.responseSpec.bodyToMono(HealthAggregator.HealthWrapper.class)).thenReturn(Mono.just(new HealthAggregator.HealthWrapper(Status.UP, new HashMap<>())));

		this.healthAggregator.handleApplicationInstanceEvent(newServiceInstanceDiscovered);

		assertHealthInfoRetrievalSucceeded((ApplicationInstance) newServiceInstanceDiscovered.getSource());
	}

	@Test
	public void shouldHandleApplicationInstanceEventHandlesError() {
		NewServiceInstanceDiscovered newServiceInstanceDiscovered =
				NewServiceInstanceDiscoveredMother.defaultNewServiceInstanceDiscovered();

		when(this.responseSpec.bodyToMono(HealthAggregator.HealthWrapper.class)).thenReturn(Mono.error(new RuntimeException("OOPSIE!")));

		this.healthAggregator.handleApplicationInstanceEvent(newServiceInstanceDiscovered);

		assertHealthInfoRetrievalFailed((ApplicationInstance) newServiceInstanceDiscovered.getSource());
	}

	private void assertHealthInfoRetrievalSucceeded(ApplicationInstance instance) {
		verify(this.applicationEventPublisher).publishEvent(this.applicationEventArgumentCaptor.capture());

		assertThat(this.outputCapture.toString())
				.contains(String.format("Retrieved health information for application instance [%s]", instance.getId()));

		HealthInfoRetrieved healthInfoRetrieved = (HealthInfoRetrieved) this.applicationEventArgumentCaptor.getValue();
		assertThat(healthInfoRetrieved).isNotNull();
		assertThat(healthInfoRetrieved.getHealth()).isNotNull();
		assertThat(healthInfoRetrieved.getHealth().getStatus()).isEqualTo(Status.UP);
		assertThat(healthInfoRetrieved.getSource()).isEqualTo(instance);
	}

	private void assertHealthInfoRetrievalFailed(ApplicationInstance instance) {
		verify(this.applicationEventPublisher).publishEvent(this.applicationEventArgumentCaptor.capture());

		assertThat(this.outputCapture.toString()).contains(
				String.format("Could not retrieve health information for [%s]", instance.getHealthEndpoint()));

		HealthInfoRetrievalFailed healthInfoRetrievalFailed = (HealthInfoRetrievalFailed) this.applicationEventArgumentCaptor.getValue();
		assertThat(healthInfoRetrievalFailed).isNotNull();
		assertThat(healthInfoRetrievalFailed.getSource()).isEqualTo(instance);
	}

	@Test
	public void shouldAggregateHealthInformation() {
		List<ApplicationInstance> applicationInstances = Arrays.asList(
				ApplicationInstanceMother.instance("a-1"),
				ApplicationInstanceMother.instance("a-2"));

		when(this.applicationInstanceService.getApplicationInstances()).thenReturn(applicationInstances);
		when(this.responseSpec.bodyToMono(HealthAggregator.HealthWrapper.class)).thenReturn(Mono.just(new HealthAggregator.HealthWrapper(Status.UP, new HashMap<>())));

		this.healthAggregator.aggregateHealthInformation();

		assertHealthInfoRetrievalSucceeded(applicationInstances);
	}

	private void assertHealthInfoRetrievalSucceeded(List<ApplicationInstance> applicationInstances) {
		assertThat(this.outputCapture.toString()).contains("Aggregating [HEALTH] information");

		verify(this.applicationEventPublisher, times(applicationInstances.size()))
				.publishEvent(this.applicationEventArgumentCaptor.capture());

		List<HealthInfoRetrieved> healthInfoRetrievals = (List) this.applicationEventArgumentCaptor.getAllValues();

		healthInfoRetrievals.forEach(healthInfoRetrieved -> {
			ApplicationInstance instance = (ApplicationInstance) healthInfoRetrieved.getSource();

			assertThat(healthInfoRetrieved).isNotNull();
			assertThat(healthInfoRetrieved.getHealth()).isNotNull();
			assertThat(healthInfoRetrieved.getHealth().getStatus()).isEqualTo(Status.UP);
			assertThat(applicationInstances).contains(instance);
		});
	}

}
