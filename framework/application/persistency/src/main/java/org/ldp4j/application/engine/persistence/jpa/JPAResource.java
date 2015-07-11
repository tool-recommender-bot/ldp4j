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
 *   Artifact    : org.ldp4j.framework:ldp4j-application-persistency:1.0.0-SNAPSHOT
 *   Bundle      : ldp4j-application-persistency-1.0.0-SNAPSHOT.jar
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 */
package org.ldp4j.application.engine.persistence.jpa;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.ldp4j.application.data.constraints.Constraints;
import org.ldp4j.application.engine.constraints.ConstraintReport;
import org.ldp4j.application.engine.constraints.ConstraintReportId;
import org.ldp4j.application.engine.context.HttpRequest;
import org.ldp4j.application.engine.resource.Attachment;
import org.ldp4j.application.engine.resource.Container;
import org.ldp4j.application.engine.resource.Resource;
import org.ldp4j.application.engine.resource.ResourceId;
import org.ldp4j.application.engine.resource.ResourceVisitor;
import org.ldp4j.application.engine.template.BasicContainerTemplate;
import org.ldp4j.application.engine.template.ContainerTemplate;
import org.ldp4j.application.engine.template.DirectContainerTemplate;
import org.ldp4j.application.engine.template.IndirectContainerTemplate;
import org.ldp4j.application.engine.template.MembershipAwareContainerTemplate;
import org.ldp4j.application.engine.template.ResourceTemplate;
import org.ldp4j.application.engine.template.TemplateIntrospector;
import org.ldp4j.application.engine.template.TemplateVisitor;

import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

class JPAResource extends AbstractJPAResource implements Resource {

	private static final class VersionGenerator {

		private final ConcurrentMap<String,AtomicLong> ATTACHMENT_COUNTER=new ConcurrentHashMap<String, AtomicLong>();

		long nextVersion(String id) {
			AtomicLong counter = ATTACHMENT_COUNTER.putIfAbsent(id, new AtomicLong(-1));
			if(counter==null) {
				counter=ATTACHMENT_COUNTER.get(id);
			}
			return counter.incrementAndGet();
		}

	}

	/**
	 * Persistent key required by JPA
	 */
	private long primaryKey;

	/**
	 * Not final to enable its usage in JPA
	 */
	private List<JPAAttachment> attachments;

	/**
	 * Not final to enable its usage in JPA
	 */
	private Set<String> failures;

	/**
	 * Not final to enable its usage in JPA
	 */
	private ResourceId id;

	/**
	 * Not final to enable its usage in JPA
	 */
	private ResourceId parentId;

	private URI indirectId;

	private final VersionGenerator versionGenerator;

	private final AttachmentCollection attachmentCollection;

	protected JPAResource() {
		this.attachmentCollection=new AttachmentCollection();
		this.failures=Sets.newLinkedHashSet();
		this.versionGenerator=new VersionGenerator();
		this.attachments=Lists.newArrayList();
	}

	protected JPAResource(ResourceId id, ResourceId parentId) {
		this();
		this.id=id;
		this.parentId = parentId;
	}

	protected JPAResource(ResourceId id) {
		this(id,null);
	}

	void init() {
		this.attachmentCollection.init(this.attachments);
	}

	protected final JPAResource createChild(ResourceId resourceId, ResourceTemplate template) {
		JPAResource newResource=null;
		if(!TemplateIntrospector.newInstance(template).isContainer()) {
			newResource=new JPAResource(resourceId,this.id);
		} else {
			newResource=new JPAContainer(resourceId,this.id);
		}
		newResource.setTemplateLibrary(getTemplateLibrary());
		return newResource;
	}

	private synchronized ConstraintReportId nextConstraintReportId() {
		String failureId=null;
		do {
			failureId=UUID.randomUUID().toString();
		} while(this.failures.contains(failureId));
		ConstraintReportId reportId=ConstraintReportId.create(this.id, failureId);
		this.failures.add(failureId);
		return reportId;
	}

	private boolean areCompatible(final Class<? extends Resource> clazz, ResourceTemplate template) {
		final AtomicReference<Boolean> result=new AtomicReference<Boolean>();
		template.accept(
			new TemplateVisitor() {
				@Override
				public void visitResourceTemplate(ResourceTemplate template) {
					result.set(clazz.isAssignableFrom(Resource.class));
				}
				@Override
				public void visitContainerTemplate(ContainerTemplate template) {
					result.set(clazz.isAssignableFrom(Container.class));
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
		return result.get();
	}

	@Override
	public ResourceId id() {
		return this.id;
	}

	@Override
	public void setIndirectId(URI indirectId) {
		this.indirectId=indirectId;
	}

	@Override
	public URI indirectId() {
		return this.indirectId;
	}

	@Override
	public boolean isRoot() {
		return this.parentId==null;
	}

	@Override
	public ResourceId parentId() {
		return this.parentId;
	}

	@Override
	public Attachment findAttachment(ResourceId resourceId) {
		checkNotNull(resourceId,"Attached resource identifier cannot be null");
		return this.attachmentCollection.attachmendByResourceId(resourceId);
	}

	@Override
	public Resource attach(String attachmentId, ResourceId resourceId) {
		return attach(attachmentId,resourceId,Resource.class);
	}

	@Override
	public <T extends Resource> T attach(String attachmentId, ResourceId resourceId, Class<? extends T> clazz) {
		checkNotNull(attachmentId,"Attachment identifier cannot be null");
		checkNotNull(resourceId,"Attached resource identifier cannot be null");
		checkNotNull(clazz,"Attached resource class cannot be null");
		AttachmentId aId = AttachmentId.createId(attachmentId,resourceId);
		this.attachmentCollection.checkNotAttached(aId);
		ResourceTemplate attachmentTemplate=super.getTemplate(resourceId);
		checkState(areCompatible(clazz,attachmentTemplate),"Attachment '%s' is not of type '%s' (%s)",attachmentId,clazz.getCanonicalName(),attachmentTemplate.getClass().getCanonicalName());
		JPAResource newResource=createChild(resourceId,attachmentTemplate);
		JPAAttachment newAttachment = new JPAAttachment(aId,this.versionGenerator.nextVersion(attachmentId));
		this.attachmentCollection.addAttachment(newAttachment);
		return clazz.cast(newResource);
	}

	@Override
	public boolean detach(Attachment attachment) {
		return this.attachmentCollection.removeAttachment(attachment);
	}
	@Override
	public Set<JPAAttachment> attachments() {
		return ImmutableSet.copyOf(this.attachments);
	}

	@Override
	public void accept(ResourceVisitor visitor) {
		visitor.visitResource(this);
	}

	@Override
	public ConstraintReport addConstraintReport(Constraints constraints, Date date, HttpRequest request) {
		ConstraintReportId reportId = nextConstraintReportId();
		return new JPAConstraintReport(reportId,date, request, constraints);
	}

	@Override
	public Set<ConstraintReportId> constraintReports() {
		Set<String> currentFailures=null;
		synchronized(this) {
			currentFailures=ImmutableSet.copyOf(this.failures);
		}
		Builder<ConstraintReportId> builder=ImmutableSet.builder();
		for(String failure:currentFailures) {
			builder.add(ConstraintReportId.create(this.id,failure));
		}
		return builder.build();
	}

	@Override
	public void removeFailure(ConstraintReport report) {
		if(report!=null) {
			String failureId = report.id().failureId();
			synchronized(this) {
				this.failures.remove(failureId);
			}
		}
	}

	@Override
	public String toString() {
		return stringHelper().toString();
	}

	protected ToStringHelper stringHelper() {
		return
			super.stringHelper().
				add("primaryKey",this.primaryKey).
				add("id",this.id).
				add("parentId",this.parentId).
				add("attachments",this.attachments).
				add("failures", this.failures);
	}

}