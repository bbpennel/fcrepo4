package org.fcrepo.kernel.modeshape;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.fcrepo.kernel.api.exception.RepositoryRuntimeException;
import org.fcrepo.kernel.api.models.FedoraDatastream;
import org.fcrepo.kernel.api.models.FedoraResource;


public class FedoraDatastreamImpl extends FedoraResourceImpl implements FedoraDatastream {

    public FedoraDatastreamImpl(final Node node) {
        super(node);
    }

    /*
     * (non-Javadoc)
     * @see org.fcrepo.kernel.api.models.FedoraResource#getDescription()
     */
    @Override
    public FedoraResource getDescription() {
        return this;
    }

    /*
     * (non-Javadoc)
     * @see org.fcrepo.kernel.api.models.FedoraResource#getDescribedResource()
     */
    @Override
    public FedoraResource getDescribedResource() {
        return this;
    }

    /**
     * Check if the node has a fedora:datastream mixin
     * 
     * @param node the node
     * @return true if the node has the datastream mixin
     */
    public static boolean hasMixin(final Node node) {
        try {
            return node.isNodeType(FEDORA_DATASTREAM);
        } catch (final RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }
}
