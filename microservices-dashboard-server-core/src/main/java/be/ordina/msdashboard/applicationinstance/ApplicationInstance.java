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
import java.util.ArrayList;
import java.util.List;

import be.ordina.msdashboard.applicationinstance.events.ApplicationInstanceCreated;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.context.ApplicationEvent;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The representation of an application's instance.
 *
 * @author Tim Ysewyn
 * @author Steve De Zitter
 */
public final class ApplicationInstance {

	private List<ApplicationEvent> changes = new ArrayList<>();

	private final String id;
	private final UriComponentsBuilder uriComponentsBuilder;

	private ApplicationInstance(String id, URI baseUrl) {
		this.id = id;
		this.uriComponentsBuilder = UriComponentsBuilder.fromUri(baseUrl);
	}

	private ApplicationInstance(Builder builder) {
		this(builder.id, builder.baseUri);
		this.changes.add(new ApplicationInstanceCreated(this));
	}

	public String getId() {
		return this.id;
	}

	public URI getHealthEndpoint() {
		return this.uriComponentsBuilder.cloneBuilder().path("/actuator/health").build().toUri();
	}

	public List<ApplicationEvent> getUncommittedChanges() {
		return this.changes;
	}

	public ApplicationInstance markChangesAsCommitted() {
		this.changes.clear();
		return this;
	}

	static ApplicationInstance from(ServiceInstance serviceInstance) {
		return Builder.withId(serviceInstance.getInstanceId()).baseUri(serviceInstance.getUri()).build();
	}

	/**
	 * Builder to create a new {@link ApplicationInstance application instance}.
	 *
	 * @author Tim Ysewyn
	 */
	static final class Builder {

		private final String id;
		private URI baseUri;

		private Builder(String id) {
			this.id = id;
		}

		static Builder withId(String id) {
			return new Builder(id);
		}

		Builder baseUri(URI baseUri) {
			this.baseUri = baseUri;
			return this;
		}

		ApplicationInstance build() {
			return new ApplicationInstance(this);
		}

	}
}
