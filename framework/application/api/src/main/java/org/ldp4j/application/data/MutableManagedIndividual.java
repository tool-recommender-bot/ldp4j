/**
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 *   This file is part of the LDP4j Project:
 *     http://www.ldp4j.org/
 *
 *   Center for Open Middleware
 *     http://www.centeropenmiddleware.com/
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 *   Copyright (C) 2014 Center for Open Middleware.
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 *   Artifact    : org.ldp4j.framework:ldp4j-application-api:0.1.0-SNAPSHOT
 *   Bundle      : ldp4j-application-api-0.1.0-SNAPSHOT.jar
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 */
package org.ldp4j.application.data;

import java.net.URI;

final class MutableManagedIndividual extends AbstractMutableIndividual<ManagedIndividualId, ManagedIndividual> implements ManagedIndividual {

	MutableManagedIndividual(ManagedIndividualId id, MutableDataSet dataSet) {
		super(id,dataSet);
	}

	@Override
	public Name<?> name() {
		return super.id().name();
	}

	@Override
	public String managerId() {
		return super.id().managerId();
	}

	@Override
	public ManagedIndividual addValue(URI propertyId, Value value) {
		super.addPropertyValue(propertyId, value);
		return this;
	}

	@Override
	public ManagedIndividual removeValue(URI propertyId, Value value) {
		super.removePropertyValue(propertyId, value);
		return this;
	}

	@Override
	public void accept(IndividualVisitor visitor) {
		visitor.visitManagedIndividual(this);
	}

}