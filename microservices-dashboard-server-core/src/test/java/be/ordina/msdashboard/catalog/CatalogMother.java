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

package be.ordina.msdashboard.catalog;

import java.util.List;

import be.ordina.msdashboard.applicationinstance.ApplicationInstance;

/**
 * @author Tim Ysewyn
 */
public final class CatalogMother {

	private CatalogMother() {
	}

	public static Catalog emptyCatalog() {
		return new Catalog();
	}

	public static Catalog catalogWith(List<ApplicationInstance> applicationInstances) {
		Catalog catalog = emptyCatalog();
		applicationInstances.forEach(catalog::addApplicationInstance);
		return catalog;
	}

}
