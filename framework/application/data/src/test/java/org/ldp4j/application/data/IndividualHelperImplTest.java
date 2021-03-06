/**
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 *   This file is part of the LDP4j Project:
 *     http://www.ldp4j.org/
 *
 *   Center for Open Middleware
 *     http://www.centeropenmiddleware.com/
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 *   Copyright (C) 2014-2016 Center for Open Middleware.
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
 *   Artifact    : org.ldp4j.framework:ldp4j-application-data:0.2.2
 *   Bundle      : ldp4j-application-data-0.2.2.jar
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 */
package org.ldp4j.application.data;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.ldp4j.application.data.IndividualReference.anonymous;
import static org.ldp4j.application.data.IndividualReference.external;
import static org.ldp4j.application.data.IndividualReference.managed;
import static org.ldp4j.application.data.IndividualReference.newIndividual;
import static org.ldp4j.application.data.IndividualReference.relative;

import java.net.URI;

import org.junit.Test;
import org.ldp4j.application.vocabulary.LDP;
import org.ldp4j.application.vocabulary.RDF;

public class IndividualHelperImplTest extends DataTestingSupport {

	private static final String HAS_TITLE = "http://www.ldp4j.org/example#hasTitle";
	private static final String HAS_LINK = "http://www.ldp4j.org/example#hasLink";
	private static final String SHOULD_LINK_TO = "http://www.ldp4j.org/example#shouldLinkTo";

	private DataSet exampleDataSet() {
		DataSet dataSet=
			DataDSL.
				dataSet().
					individual(managed(MANAGED_INDIVIDUAL_ID)).
						hasProperty(HAS_TITLE).
							withValue("managed").
							withValue("custom").
						hasLink(HAS_LINK).
							referringTo(external(EXTERNAL)).
							referringTo(managed(RESOURCE_NAME,MANAGER_ID)).
						hasLink(HAS_LINK).
							toIndividual(external(EXTERNAL)).
								hasLink(HAS_LINK).
									referringTo(managed(RESOURCE_NAME,MANAGER_ID)).
								hasProperty(SHOULD_LINK_TO).
									withValue("managed").
					individual(relative(MANAGED_INDIVIDUAL_ID,RELATIVE_PATH)).
						hasProperty(HAS_TITLE).
							withValue("relative").
							withValue("custom").
						hasLink(HAS_LINK).
							referringTo(external(EXTERNAL)).
							referringTo(relative(MANAGED_INDIVIDUAL_ID,RELATIVE_PATH)).
						hasLink(HAS_LINK).
							toIndividual(external(EXTERNAL)).
								hasLink(HAS_LINK).
									referringTo(relative(MANAGED_INDIVIDUAL_ID,RELATIVE_PATH)).
								hasProperty(SHOULD_LINK_TO).
									withValue("relative").
					individual(newIndividual(SELF)).
						hasProperty(HAS_TITLE).
							withValue("self").
							withValue("custom").
						hasLink(HAS_LINK).
							referringTo(external(EXTERNAL)).
							referringTo(newIndividual(SELF)).
						hasLink(HAS_LINK).
							toIndividual(external(EXTERNAL)).
								hasLink(HAS_LINK).
									referringTo(newIndividual(SELF)).
								hasProperty(SHOULD_LINK_TO).
									withValue("self").
					individual(newIndividual(NEW)).
						hasProperty(HAS_TITLE).
							withValue("new").
							withValue("custom").
						hasLink(HAS_LINK).
							referringTo(external(EXTERNAL)).
							referringTo(newIndividual(NEW)).
						hasLink(HAS_LINK).
							toIndividual(external(EXTERNAL)).
								hasLink(HAS_LINK).
									referringTo(newIndividual(NEW)).
								hasProperty(SHOULD_LINK_TO).
									withValue("new").
					individual(anonymous(BLANK_NODE)).
						hasProperty(HAS_TITLE).
							withValue("bnode").
							withValue("custom").
						hasLink(HAS_LINK).
							referringTo(external(EXTERNAL)).
							referringTo(anonymous(BLANK_NODE)).
						hasLink(HAS_LINK).
							toIndividual(external(EXTERNAL)).
								hasLink(HAS_LINK).
									referringTo(anonymous(BLANK_NODE)).
								hasProperty(SHOULD_LINK_TO).
									withValue("bnode").
					individual(external(EXTERNAL)).
						hasProperty(HAS_TITLE).
							withValue("external").
							withValue("custom").
						hasLink(HAS_LINK).
							referringTo(external(EXTERNAL)).
						hasLink(HAS_LINK).
							toIndividual(external(EXTERNAL)).
								hasProperty(SHOULD_LINK_TO).
									withValue("external").
					build();
		return dataSet;
	}

	@Test
	public void testTypes$empty() {
		DataSet data=exampleDataSet();
		Individual<?,?> individual = data.individualOfId(BLANK_NODE);
		IndividualHelperImpl sut = new IndividualHelperImpl(individual);
		assertThat(sut.types(),hasSize(0));
	}

	@Test
	public void testTypes$found() {
		DataSet data=exampleDataSet();
		Individual<?,?> individual = data.individualOfId(BLANK_NODE);
		IndividualHelperImpl sut = new IndividualHelperImpl(individual);
		ExternalIndividual foundIndividual = data.individual(LDP.CONTAINER.as(URI.class), ExternalIndividual.class);
		individual.addValue(RDF.TYPE.as(URI.class), foundIndividual);
		assertThat(sut.types(),contains(LDP.CONTAINER.as(URI.class)));
	}

	@Test
	public void testTypes$discardLocalIndividual() {
		DataSet data=exampleDataSet();
		Individual<?,?> individual = data.individualOfId(BLANK_NODE);
		IndividualHelperImpl sut = new IndividualHelperImpl(individual);
		individual.addValue(RDF.TYPE.as(URI.class), individual);
		assertThat(sut.types(),hasSize(0));
	}

	@Test
	public void testTypes$discardNewIndividual() {
		DataSet data=exampleDataSet();
		Individual<?,?> individual = data.individualOfId(BLANK_NODE);
		IndividualHelperImpl sut = new IndividualHelperImpl(individual);
		NewIndividual foundIndividual = data.individual(URI.create(""), NewIndividual.class);
		individual.addValue(RDF.TYPE.as(URI.class), foundIndividual);
		assertThat(sut.types(),hasSize(0));
	}

	@Test
	public void testTypes$discardManagedIndividual() {
		DataSet data=exampleDataSet();
		Individual<?,?> individual = data.individualOfId(BLANK_NODE);
		IndividualHelperImpl sut = new IndividualHelperImpl(individual);
		ManagedIndividual foundIndividual = data.individual(ManagedIndividualId.createId(RESOURCE_NAME, MANAGER_ID), ManagedIndividual.class);
		individual.addValue(RDF.TYPE.as(URI.class), foundIndividual);
		assertThat(sut.types(),hasSize(0));
	}

	@Test
	public void testTypes$discardRelativeIndividual() {
		DataSet data=exampleDataSet();
		Individual<?,?> individual = data.individualOfId(BLANK_NODE);
		IndividualHelperImpl sut = new IndividualHelperImpl(individual);
		RelativeIndividual foundIndividual = data.individual(RelativeIndividualId.createId(ManagedIndividualId.createId(RESOURCE_NAME, MANAGER_ID),RELATIVE_PATH), RelativeIndividual.class);
		individual.addValue(RDF.TYPE.as(URI.class), foundIndividual);
		assertThat(sut.types(),hasSize(0));
	}

	@Test
	public void testTypes$discardLiteral() {
		DataSet data=exampleDataSet();
		Individual<?,?> individual = data.individualOfId(BLANK_NODE);
		IndividualHelperImpl sut = new IndividualHelperImpl(individual);
		individual.addValue(RDF.TYPE.as(URI.class), Literals.of("plain literal"));
		assertThat(sut.types(),hasSize(0));
	}

}
