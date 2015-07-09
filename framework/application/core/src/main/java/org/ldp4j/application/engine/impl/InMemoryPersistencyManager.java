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
 *   Artifact    : org.ldp4j.framework:ldp4j-application-core:1.0.0-SNAPSHOT
 *   Bundle      : ldp4j-application-core-1.0.0-SNAPSHOT.jar
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 */
package org.ldp4j.application.engine.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import org.ldp4j.application.data.Name;
import org.ldp4j.application.engine.constraints.ConstraintReportRepository;
import org.ldp4j.application.engine.context.EntityTag;
import org.ldp4j.application.engine.endpoint.Endpoint;
import org.ldp4j.application.engine.endpoint.EndpointRepository;
import org.ldp4j.application.engine.lifecycle.LifecycleException;
import org.ldp4j.application.engine.lifecycle.Managed;
import org.ldp4j.application.engine.resource.Container;
import org.ldp4j.application.engine.resource.Resource;
import org.ldp4j.application.engine.resource.ResourceId;
import org.ldp4j.application.engine.resource.ResourceRepository;
import org.ldp4j.application.engine.spi.PersistencyManager;
import org.ldp4j.application.engine.template.BasicContainerTemplate;
import org.ldp4j.application.engine.template.ContainerTemplate;
import org.ldp4j.application.engine.template.DirectContainerTemplate;
import org.ldp4j.application.engine.template.IndirectContainerTemplate;
import org.ldp4j.application.engine.template.MembershipAwareContainerTemplate;
import org.ldp4j.application.engine.template.ResourceTemplate;
import org.ldp4j.application.engine.template.TemplateLibrary;
import org.ldp4j.application.engine.template.TemplateVisitor;
import org.ldp4j.application.engine.transaction.TransactionManager;

final class InMemoryPersistencyManager implements PersistencyManager, Managed {

	private final InMemoryResourceRepository resourceRepository;
	private final InMemoryEndpointRepository endpointRepository;
	private final InMemoryConstraintReportRepository constraintReportRepository;
	private final InMemoryTransactionManager transactionManager;

	private TemplateLibrary templateLibrary;

	InMemoryPersistencyManager() {
		this.resourceRepository=new InMemoryResourceRepository();
		this.endpointRepository=new InMemoryEndpointRepository();
		this.constraintReportRepository=new InMemoryConstraintReportRepository();
		this.transactionManager = new InMemoryTransactionManager();
	}

	private ResourceTemplate findTemplate(String id) {
		checkNotNull(id,"TemplateId identifier cannot be null");
		ResourceTemplate result = this.templateLibrary.findById(id);
		if(result==null) {
			throw new IllegalStateException("Could not find template '"+id+"'");
		}
		return result;
	}

	private <T extends Resource> ResourceTemplate findInstantiableTemplate(String templateId, boolean requireContainer) {
		ResourceTemplate template = findTemplate(templateId);
		if(!(template instanceof ContainerTemplate) && requireContainer) {
			throw new IllegalStateException("Cannot create a container from non-container template '"+template.id()+"'");
		}
		return template;
	}

	private InMemoryResource createResource(ResourceTemplate template, Name<?> resourceId, Resource parent) {
		checkNotNull(resourceId,"ResourceSnapshot identifier cannot be null");
		final ResourceId parentId=parent!=null?parent.id():null;
		final ResourceId id=ResourceId.createId(resourceId, template);
		final AtomicReference<InMemoryResource> result=new AtomicReference<InMemoryResource>();
		template.
			accept(
				new TemplateVisitor() {
					@Override
					public void visitResourceTemplate(ResourceTemplate template) {
						result.set(new InMemoryResource(id,parentId));
					}
					@Override
					public void visitContainerTemplate(ContainerTemplate template) {
						result.set(new InMemoryContainer(id,parentId));
					}
					@Override
					public void visitBasicContainerTemplate(BasicContainerTemplate template) {
						visitContainerTemplate(template);
					}
					@Override
					public void visitMembershipAwareContainerTemplate(MembershipAwareContainerTemplate template) {
						visitContainerTemplate(template);
					}
					@Override
					public void visitDirectContainerTemplate(DirectContainerTemplate template) {
						visitContainerTemplate(template);
					}
					@Override
					public void visitIndirectContainerTemplate(IndirectContainerTemplate template) {
						visitContainerTemplate(template);
					}
				}
			);
		InMemoryResource resource = result.get();
		resource.setTemplateLibrary(this.templateLibrary);
		return resource;
	}

	ResourceRepository resourceRepository() {
		return this.resourceRepository;
	}

	EndpointRepository endpointRepository() {
		return this.endpointRepository;
	}

	ConstraintReportRepository constraintReportRepository() {
		return this.constraintReportRepository;
	}

	TransactionManager transactionManager() {
		return this.transactionManager;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void useTemplates(TemplateLibrary library) {
		this.templateLibrary=library;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Resource createResource(String templateId, Name<?> resourceId, Resource parent) {
		return createResource(findTemplate(templateId),resourceId,parent);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <T extends Resource> T createResource(String templateId, Name<?> resourceId, Resource parent, Class<? extends T> expectedResourceClass) {
		ResourceTemplate template=
			findInstantiableTemplate(
				templateId,
				Container.class.isAssignableFrom(expectedResourceClass));
		Resource newResource = createResource(template,resourceId,parent);
		return expectedResourceClass.cast(newResource);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Endpoint createEndpoint(Resource resource, String path, EntityTag entityTag, Date creationDate) {
		checkNotNull(resource,"Endpoint's resource cannot be null");
		checkNotNull(entityTag,"Endpoint's entity tag cannot be null");
		checkNotNull(creationDate,"Endpoint's Last modified data cannot be null");
		return new InMemoryEndpoint(this.endpointRepository.nextIdentifier(),path,resource.id(),creationDate,entityTag);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void init() throws LifecycleException {
		this.resourceRepository.init();
		this.constraintReportRepository.init();
		this.endpointRepository.init();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void shutdown() throws LifecycleException {
		this.endpointRepository.shutdown();
		this.constraintReportRepository.shutdown();
		this.resourceRepository.shutdown();
	}

}