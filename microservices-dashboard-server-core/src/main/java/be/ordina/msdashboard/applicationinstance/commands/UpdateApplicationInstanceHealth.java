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

package be.ordina.msdashboard.applicationinstance.commands;

import be.ordina.msdashboard.applicationinstance.ApplicationInstance;

import org.springframework.boot.actuate.health.Status;
import org.springframework.util.Assert;

/**
 * A command to update the {@link ApplicationInstance application instance}'s health status.
 *
 * @author Tim Ysewyn
 */
public final class UpdateApplicationInstanceHealth {

	private final String id;
	private final Status healthStatus;

	public UpdateApplicationInstanceHealth(String id, Status healthStatus) {
		Assert.notNull(id, "id must not be null!");
		Assert.notNull(healthStatus, "healthStatus must not be null!");

		this.id = id;
		this.healthStatus = healthStatus;
	}

	public String getId() {
		return this.id;
	}

	public Status getHealthStatus() {
		return this.healthStatus;
	}
}
