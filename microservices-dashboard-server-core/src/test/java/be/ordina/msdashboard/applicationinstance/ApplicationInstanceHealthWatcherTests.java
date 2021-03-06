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

package be.ordina.msdashboard.applicationinstance;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
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

import be.ordina.msdashboard.applicationinstance.commands.UpdateApplicationInstanceHealth;
import be.ordina.msdashboard.applicationinstance.events.ActuatorEndpointsUpdated;
import be.ordina.msdashboard.applicationinstance.events.ApplicationInstanceHealthDataRetrievalFailed;
import be.ordina.msdashboard.applicationinstance.events.ApplicationInstanceHealthDataRetrieved;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.rule.OutputCapture;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Links;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.when;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Unit tests for the {@link ApplicationInstanceHealthWatcher application instance health watcher}.
 *
 * @author Steve De Zitter
 * @author Tim Ysewyn
 */
@RunWith(MockitoJUnitRunner.class)
public class ApplicationInstanceHealthWatcherTests {

	@Rule
	public OutputCapture outputCapture = new OutputCapture();

	@Mock
	private ApplicationInstanceService applicationInstanceService;
	@Mock
	private WebClient webClient;
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
	private ApplicationInstanceHealthWatcher healthWatcher;

	@Before
	public void setupMocks() {
		when(this.webClient.get()).thenReturn(this.requestHeadersUriSpec);
		when(this.requestHeadersUriSpec.uri("http://localhost:8080/actuator/health"))
				.thenReturn(this.requestHeadersSpec);
		when(this.requestHeadersSpec.retrieve()).thenReturn(this.responseSpec);
	}

	@Test
	public void shouldNotRetrieveTheHealthDataAfterActuatorEndpointsHaveBeenUpdatedWithoutHealthLink() {
		ActuatorEndpointsUpdated event =
				ApplicationInstanceEventMother.actuatorEndpointsUpdated("a-1", "a",
						new Links(new Link("http://localhost:8080/actuator/info", "info")));

		this.healthWatcher.retrieveHealthData(event);

		verifyZeroInteractions(this.webClient);
		verifyZeroInteractions(this.requestHeadersUriSpec);
		verifyZeroInteractions(this.requestHeadersSpec);
		verifyZeroInteractions(this.responseSpec);
		verifyZeroInteractions(this.applicationInstanceService);
	}

	@Test
	public void shouldRetrieveTheHealthDataAfterActuatorEndpointsHaveBeenUpdatedWithHealthLink() {
		ActuatorEndpointsUpdated event =
				ApplicationInstanceEventMother.actuatorEndpointsUpdated("a-1", "a",
						new Links(new Link("http://localhost:8080/actuator/health", "health")));

		when(this.responseSpec.bodyToMono(ApplicationInstanceHealthWatcher.HealthWrapper.class)).thenReturn(Mono
				.just(new ApplicationInstanceHealthWatcher.HealthWrapper(Status.UP, null)));

		this.healthWatcher.retrieveHealthData(event);

		assertHealthInfoRetrievalSucceeded((ApplicationInstance) event.getSource());
	}

	@Test
	public void shouldHandleApplicationInstanceEventHandlesError() {
		URI healthActuatorEndpoint = URI.create("http://localhost:8080/actuator/health");
		ActuatorEndpointsUpdated event =
				ApplicationInstanceEventMother.actuatorEndpointsUpdated("a-1", "a",
				new Links(new Link(healthActuatorEndpoint.toString(), "health")));

		when(this.responseSpec.bodyToMono(ApplicationInstanceHealthWatcher.HealthWrapper.class))
				.thenReturn(Mono.error(new RuntimeException("OOPSIE!")));

		this.healthWatcher.retrieveHealthData(event);

		assertHealthInfoRetrievalFailed((ApplicationInstance) event.getSource(), healthActuatorEndpoint);
	}

	@Test
	public void shouldOnlyRetrieveHealthDataForInstancesThatAreNotDeleted() {
		ApplicationInstance firstInstance = ApplicationInstanceMother.instance("a-1", "a");
		firstInstance.delete();
		ApplicationInstance secondInstance = ApplicationInstanceMother.instance("a-2", "a",
				URI.create("http://localhost:8080"),
				new Links(new Link("http://localhost:8080/actuator/health", "health")));
		List<ApplicationInstance> applicationInstances = Arrays.asList(firstInstance, secondInstance);

		when(this.applicationInstanceService.getApplicationInstances()).thenReturn(applicationInstances);
		when(this.responseSpec.bodyToMono(ApplicationInstanceHealthWatcher.HealthWrapper.class))
				.thenReturn(Mono.just(new ApplicationInstanceHealthWatcher.HealthWrapper(Status.UP, new HashMap<>())));

		this.healthWatcher.retrieveHealthDataForAllApplicationInstances();

		assertHealthInfoRetrievalSucceeded(Collections.singletonList(secondInstance));
	}

	@Test
	public void shouldOnlyRetrieveHealthDataForInstancesWithAHealthActuatorEndpoint() {
		ApplicationInstance firstInstance = ApplicationInstanceMother.instance("a-1", "a");
		ApplicationInstance secondInstance = ApplicationInstanceMother.instance("a-2", "a",
				URI.create("http://localhost:8080"),
				new Links(new Link("http://localhost:8080/actuator/health", "health")));
		List<ApplicationInstance> applicationInstances = Arrays.asList(firstInstance, secondInstance);

		when(this.applicationInstanceService.getApplicationInstances()).thenReturn(applicationInstances);
		when(this.responseSpec.bodyToMono(ApplicationInstanceHealthWatcher.HealthWrapper.class))
				.thenReturn(Mono.just(new ApplicationInstanceHealthWatcher.HealthWrapper(Status.UP, new HashMap<>())));

		this.healthWatcher.retrieveHealthDataForAllApplicationInstances();

		assertHealthInfoRetrievalSucceeded(Collections.singletonList(secondInstance));
	}

	@Test
	public void shouldUpdateTheHealthOfAnApplicationInstanceWhenHealthDataRetrieved() {
		ApplicationInstanceHealthDataRetrieved event = ApplicationInstanceEventMother
				.applicationInstanceHealthDataRetrieved("a-1", "a", Health.up().build());

		this.healthWatcher.updateHealthForApplicationInstance(event);

		ArgumentCaptor<UpdateApplicationInstanceHealth> captor = ArgumentCaptor.forClass(UpdateApplicationInstanceHealth.class);
		verify(this.applicationInstanceService).updateApplicationInstanceHealth(captor.capture());
		UpdateApplicationInstanceHealth command = captor.getValue();
		assertThat(command.getId()).isEqualTo("a-1");
		assertThat(command.getHealthStatus()).isEqualTo(Status.UP);
		verifyNoMoreInteractions(this.applicationInstanceService);
		verifyZeroInteractions(this.applicationEventPublisher);
	}

	private void assertHealthInfoRetrievalSucceeded(ApplicationInstance instance) {
		verify(this.applicationEventPublisher).publishEvent(this.applicationEventArgumentCaptor.capture());
		verifyNoMoreInteractions(this.applicationEventPublisher);

		assertThat(this.outputCapture.toString())
				.contains(String.format("Retrieved health information for application instance [%s]", instance.getId()));

		ApplicationInstanceHealthDataRetrieved healthInfoRetrieved =
				(ApplicationInstanceHealthDataRetrieved) this.applicationEventArgumentCaptor.getValue();
		assertThat(healthInfoRetrieved).isNotNull();
		assertThat(healthInfoRetrieved.getHealth()).isNotNull();
		assertThat(healthInfoRetrieved.getHealth().getStatus()).isEqualTo(Status.UP);
		assertThat(healthInfoRetrieved.getHealth().getDetails()).isNotNull();
		assertThat(healthInfoRetrieved.getSource()).isEqualTo(instance);
	}

	private void assertHealthInfoRetrievalFailed(ApplicationInstance instance, URI actuatorEndpoint) {
		verify(this.applicationEventPublisher).publishEvent(this.applicationEventArgumentCaptor.capture());
		verifyNoMoreInteractions(this.applicationEventPublisher);

		assertThat(this.outputCapture.toString()).contains(
				String.format("Could not retrieve health information for [%s]", actuatorEndpoint));

		ApplicationInstanceHealthDataRetrievalFailed healthInfoRetrievalFailed =
				(ApplicationInstanceHealthDataRetrievalFailed) this.applicationEventArgumentCaptor.getValue();
		assertThat(healthInfoRetrievalFailed).isNotNull();
		assertThat(healthInfoRetrievalFailed.getSource()).isEqualTo(instance);
	}

	private void assertHealthInfoRetrievalSucceeded(List<ApplicationInstance> applicationInstances) {
		String logOutput = this.outputCapture.toString();
		assertThat(logOutput).contains("Retrieving [HEALTH] data for all application instances");
		applicationInstances.forEach((applicationInstance) -> {
			assertThat(logOutput).contains(String.format("Retrieving [HEALTH] data for %s", applicationInstance.getId()));
			assertThat(logOutput).contains(String.format("Retrieved health information for application instance [%s]",
					applicationInstance.getId()));
		});

		verify(this.applicationEventPublisher, times(applicationInstances.size()))
				.publishEvent(this.applicationEventArgumentCaptor.capture());
		verifyNoMoreInteractions(this.applicationEventPublisher);

		List<ApplicationInstanceHealthDataRetrieved> healthInfoRetrievals =
				(List) this.applicationEventArgumentCaptor.getAllValues();

		healthInfoRetrievals.forEach(healthInfoRetrieved -> {
			ApplicationInstance instance = (ApplicationInstance) healthInfoRetrieved.getSource();

			assertThat(healthInfoRetrieved).isNotNull();
			assertThat(healthInfoRetrieved.getHealth()).isNotNull();
			assertThat(healthInfoRetrieved.getHealth().getStatus()).isEqualTo(Status.UP);
			assertThat(applicationInstances).contains(instance);
		});
	}

}
