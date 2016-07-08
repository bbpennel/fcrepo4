/*
 * Licensed to DuraSpace under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * DuraSpace licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fcrepo.kernel.modeshape.services;

import static org.fcrepo.kernel.api.FedoraTypes.FEDORA_TOMBSTONE;
import static org.fcrepo.kernel.modeshape.utils.FedoraTypesUtils.getJcrNode;
import static org.fcrepo.kernel.modeshape.utils.FedoraTypesUtils.touchLdpMembershipResource;
import static org.fcrepo.kernel.modeshape.utils.NamespaceTools.validatePath;
import static org.slf4j.LoggerFactory.getLogger;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.fcrepo.kernel.api.exception.InvalidResourceIdentifierException;
import org.fcrepo.kernel.api.exception.RepositoryRuntimeException;
import org.fcrepo.kernel.api.models.FedoraResource;
import org.fcrepo.kernel.api.services.NodeService;
import org.fcrepo.kernel.modeshape.FedoraResourceImpl;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

/**
 * Service for managing access to Fedora 'nodes' (either datastreams or objects, we don't care.)
 *
 * @author Chris Beer
 * @author ajs6f
 * @since May 9, 2013
 */
@Component
public class NodeServiceImpl extends AbstractService implements NodeService {

    private static final Logger LOGGER = getLogger(NodeServiceImpl.class);

    /* (non-Javadoc)
     * @see org.fcrepo.kernel.api.services.Service#exists(javax.jcr.Session, java.lang.String)
     */
    @Override
    public boolean exists(final Session session, final String path) {
        try {
            validatePath(session, path);
            return session.nodeExists(path);
        } catch (final IllegalArgumentException e) {
            throw new InvalidResourceIdentifierException("Illegal path: " + path);
        } catch (final RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }

    /**
     * Retrieve an existing Fedora resource at the given path
     *
     * @param session a JCR session
     * @param path a JCR path
     * @return Fedora resource at the given path
     */
    @Override
    public FedoraResource find(final Session session, final String path) {
        try {
            return new FedoraResourceImpl(session.getNode(path));
        } catch (final RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }

    /**
     * Copy an existing object from the source path to the destination path
     *
     * @param session a JCR session
     * @param source the source path
     * @param destination the destination path
     */
    @Override
    public void copyObject(final Session session, final String source, final String destination) {
        try {
            session.getWorkspace().copy(source, destination);
            touchLdpMembershipResource(getJcrNode(find(session, destination)));
        } catch (final RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }

    /**
     * Move an existing object from the source path to the destination path
     *
     * @param session the session
     * @param source the source path
     * @param destination the destination path
     */
    @Override
    public void moveObject(final Session session, final String source, final String destination) {
        try {
            final FedoraResource srcResource = find(session, source);
            final Node sourceNode = getJcrNode(srcResource);
            final String name = sourceNode.getName();
            final Node parent = sourceNode.getDepth() > 0 ? sourceNode.getParent() : null;

            session.getWorkspace().move(source, destination);

            if (parent != null) {
                createTombstone(parent, name);
            }

            touchLdpMembershipResource(getJcrNode(find(session, source)));
            touchLdpMembershipResource(getJcrNode(find(session, destination)));

        } catch (final RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }

    private static void createTombstone(final Node parent, final String path) throws RepositoryException {
        final FedoraResourceImpl fedoraResource = new FedoraResourceImpl(parent);
        final Node n  = fedoraResource.findOrCreateChild(parent, path, FEDORA_TOMBSTONE);
        LOGGER.info("Created tombstone at {} ", n.getPath());
    }

    /**
     * @param session the session
     * @param path the path
     */
    @Override
    public FedoraResource findOrCreate(final Session session, final String path) {
        throw new RepositoryRuntimeException("unimplemented");
    }
}
