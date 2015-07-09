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
package org.ldp4j.application.engine.session;

import static com.google.common.base.Preconditions.checkArgument;

import java.net.URI;
import java.util.Date;
import java.util.UUID;

import org.ldp4j.application.engine.context.EntityTag;
import org.ldp4j.application.engine.endpoint.Endpoint;
import org.ldp4j.application.engine.endpoint.EndpointCreationException;
import org.ldp4j.application.engine.endpoint.EndpointManagementService;
import org.ldp4j.application.engine.endpoint.EndpointNotFoundException;
import org.ldp4j.application.engine.resource.Resource;
import org.ldp4j.application.engine.resource.ResourceId;
import org.ldp4j.application.engine.resource.ResourceRepository;
import org.ldp4j.application.engine.spi.RuntimeDelegate;
import org.ldp4j.application.engine.spi.Service;
import org.ldp4j.application.engine.spi.ServiceBuilder;
import org.ldp4j.application.engine.template.TemplateManagementService;
import org.ldp4j.application.engine.transaction.TransactionManager;
import org.ldp4j.application.ext.ResourceHandler;
import org.ldp4j.application.session.ResourceSnapshot;
import org.ldp4j.application.session.WriteSession;
import org.ldp4j.application.session.WriteSessionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WriteSessionService implements Service {

	private static class WriteSessionServiceBuilder extends ServiceBuilder<WriteSessionService> {

		private WriteSessionServiceBuilder() {
			super(WriteSessionService.class);
		}

		public WriteSessionService build() {
			return
				new WriteSessionService(
					service(TemplateManagementService.class),
					service(EndpointManagementService.class));
		}

	}

	private final class ResourceProcessor implements UnitOfWork.Visitor {

		private final Date lastModified;
		private final DelegatedWriteSession session;

		private ResourceProcessor(Date lastModified, DelegatedWriteSession session) {
			this.lastModified = lastModified;
			this.session = session;
		}

		@Override
		public void visitNew(DelegatedResourceSnapshot obj) {
			createResource(obj.delegate(),this.lastModified,this.session.getDesiredPath(obj),this.session.getIndirectId(obj));
		}

		@Override
		public void visitDirty(DelegatedResourceSnapshot obj) {
			modifyResource(obj.delegate(),this.lastModified);
		}

		@Override
		public void visitDeleted(DelegatedResourceSnapshot obj) {
			deleteResource(obj.delegate(),this.lastModified);
		}
	}

	private static final Logger LOGGER=LoggerFactory.getLogger(WriteSessionService.class);

	private final EndpointManagementService endpointManagementService;

	private final TemplateManagementService templateManagementService;

	private final ResourceRepository resourceRepository;

	private final TransactionManager transactionManager;

	private WriteSessionService(TemplateManagementService templateManagementService, EndpointManagementService endointManagementService) {
		this.templateManagementService = templateManagementService;
		this.endpointManagementService = endointManagementService;
		this.resourceRepository=RuntimeDelegate.getInstance().getResourceRepository();
		this.transactionManager=RuntimeDelegate.getInstance().getTransactionManager();
	}

	public WriteSession createSession(WriteSessionConfiguration configuration) {
		UnitOfWork.newCurrent();
		logLifecycleMessage("Created write session: %s",configuration);
		return new DelegatedWriteSession(configuration,this);
	}

	public void terminateSession(WriteSession writeSession) {
		checkArgument(writeSession instanceof DelegatedWriteSession);
		DelegatedWriteSession session=(DelegatedWriteSession)writeSession;
		try {
			switch(session.status()) {
				case ACTIVE:
					logLifecycleMessage("Force termination of active session...");
					session.discardChanges();
					break;
				case ABORTED:
					// Nothing to do
					break;
				case COMPLETED:
					// Nothing to do
					break;

			}
		} catch (WriteSessionException e) {
			LOGGER.error("Could not force termination of active session",e);
		} finally {
			UnitOfWork.setCurrent(null);
		}
	}

	public ResourceSnapshot attach(WriteSession writeSession, Resource resource, Class<? extends ResourceHandler> handlerClass) {
		return writeSession.find(ResourceSnapshot.class, resource.id().name(), handlerClass);
	}

	public Resource detach(WriteSession writeSession, ResourceSnapshot snapshot) {
		checkArgument(writeSession instanceof DelegatedWriteSession,"Invalid session");
		checkArgument(snapshot instanceof DelegatedResourceSnapshot,"Unknown resource '%s'",snapshot.name());
		DelegatedResourceSnapshot delegatedSnapshot=(DelegatedResourceSnapshot)snapshot;
		return delegatedSnapshot.delegate();
	}

	void commitSession(DelegatedWriteSession session) {
		logLifecycleMessage("Commiting session...");
		UnitOfWork.getCurrent().accept(new ResourceProcessor(new Date(),session));
		this.transactionManager.currentTransaction().commit();
	}

	void rollbackSession(DelegatedWriteSession session) {
		logLifecycleMessage("Rolling back session...");
		UnitOfWork.getCurrent().accept(new ResourceProcessor(new Date(),session));
		this.transactionManager.currentTransaction().rollback();
	}

	Resource resourceOfId(ResourceId resourceId) {
		return this.resourceRepository.resourceById(resourceId,Resource.class);
	}

	TemplateManagementService templateManagementService() {
		return this.templateManagementService;
	}

	private void logLifecycleMessage(String msg, Object... args) {
		if(LOGGER.isDebugEnabled()) {
			LOGGER.debug(String.format(msg,args));
		}
	}

	private EntityTag generateEntityTag(Resource resource) {
		return new EntityTag(UUID.randomUUID().toString());
	}

	private void createResource(Resource resource, Date lastModified, String relativePath, URI indirectId) {
		try {
			resource.setIndirectId(indirectId);
			this.resourceRepository.add(resource);
			Endpoint newEndpoint=
				this.endpointManagementService.
					createEndpointForResource(
						resource,
						relativePath,
						generateEntityTag(resource),
						lastModified);
			if(LOGGER.isTraceEnabled()) {
				LOGGER.trace("Created "+resource);
				LOGGER.trace("Created "+newEndpoint);
			}
		} catch (EndpointCreationException e) {
			throw new IllegalStateException(e);
		}
	}

	private void modifyResource(Resource resource, Date lastModified) {
		try {
			Endpoint endpoint =
				this.endpointManagementService.
					modifyResourceEndpoint(
						resource,
						generateEntityTag(resource),
						lastModified);
			if(LOGGER.isTraceEnabled()) {
				LOGGER.trace("Modified "+resource);
				LOGGER.trace("Modified "+endpoint);
			}
		} catch (EndpointNotFoundException e) {
			throw new IllegalStateException(e);
		}
	}

	private void deleteResource(Resource resource, Date lastModified) {
		try {
			this.resourceRepository.remove(resource);
			Endpoint endpoint =
				this.endpointManagementService.
					deleteResourceEndpoint(
						resource,
						lastModified);
			if(LOGGER.isTraceEnabled()) {
				LOGGER.trace("Deleted "+resource);
				LOGGER.trace("Deleted "+endpoint);
			}
		} catch (EndpointNotFoundException e) {
			throw new IllegalStateException(e);
		}
	}

	public static ServiceBuilder<WriteSessionService> serviceBuilder() {
		return new WriteSessionServiceBuilder();
	}

	public static WriteSessionService defaultService() {
		return serviceBuilder().build();
	}

}