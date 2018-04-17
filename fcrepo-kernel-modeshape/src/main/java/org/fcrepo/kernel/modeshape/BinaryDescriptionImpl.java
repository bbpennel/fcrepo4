package org.fcrepo.kernel.modeshape;

import static java.util.stream.Stream.empty;
import static org.apache.jena.datatypes.xsd.XSDDatatype.XSDstring;
import static org.fcrepo.kernel.api.RequiredRdfContext.MINIMAL;
import static org.fcrepo.kernel.modeshape.FedoraJcrConstants.FIELD_DELIMITER;
import static org.slf4j.LoggerFactory.getLogger;

import java.net.URI;
import java.util.Set;
import java.util.stream.Stream;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;

import org.apache.jena.rdf.model.Resource;
import org.fcrepo.kernel.api.RdfStream;
import org.fcrepo.kernel.api.TripleCategory;
import org.fcrepo.kernel.api.exception.RepositoryRuntimeException;
import org.fcrepo.kernel.api.identifiers.IdentifierConverter;
import org.fcrepo.kernel.api.models.BinaryDescription;
import org.fcrepo.kernel.api.models.FedoraResource;
import org.fcrepo.kernel.api.models.NonRdfSourceDescription;
import org.fcrepo.kernel.api.rdf.DefaultRdfStream;
import org.slf4j.Logger;


public class BinaryDescriptionImpl extends FedoraResourceImpl implements BinaryDescription {

    private static final Logger LOGGER = getLogger(BinaryDescriptionImpl.class);

    public BinaryDescriptionImpl(final Node node) {
        super(node);
    }

    @Override
    public long getContentSize() {
        try {
            if (hasProperty(CONTENT_SIZE)) {
                return getProperty(CONTENT_SIZE).getLong();
            }
        } catch (final RepositoryException e) {
            LOGGER.info("Could not get contentSize(): {}", e.getMessage());
        }

        return -1L;
    }

    @Override
    public URI getContentDigest() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getMimeType() {
        try {
            if (hasProperty(HAS_MIME_TYPE)) {
                return getProperty(HAS_MIME_TYPE).getString().replace(FIELD_DELIMITER + XSDstring.getURI(), "");
            }
            return "application/octet-stream";
        } catch (final RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }

    @Override
    public String getFilename() {
        try {
            if (hasProperty(FILENAME)) {
                return getProperty(FILENAME).getString().replace(FIELD_DELIMITER + XSDstring.getURI(), "");
            }
            return node.getParent().getName();
        } catch (final RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }

    @Override
    public RdfStream getTriples(final IdentifierConverter<Resource, FedoraResource> idTranslator,
                                final Set<? extends TripleCategory> contexts) {

        final FedoraResource described;
        if (isMemento()) {
            described = getDescription().getDescribedResource();
        } else {
            described = new FedoraBinaryImpl(getNode());
        }

        return new DefaultRdfStream(idTranslator.reverse().convert(described).asNode(), contexts.stream()
                .filter(contextMap::containsKey)
                .map(x -> contextMap.get(x).apply(this).apply(idTranslator).apply(contexts.contains(MINIMAL)))
                .reduce(empty(), Stream::concat));
    }

    @Override
    public FedoraResource getDescription() {
        final NonRdfSourceDescription description = new NonRdfSourceDescriptionImpl(getDescriptionNode());
        return description;
    }

    @Override
    protected Node getDescriptionNode() {
        try {
            return getNode().getParent();
        } catch (final RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }

    @Override
    public FedoraResource getDescribedResource() {
        if (isMemento()) {
            // find the associated description memento if it exists
            final Node node = getDescriptionNode();
            try {
                final String path = node.getPath();
                final String mementoId = path.substring(path.lastIndexOf("/") + 1);
                final Node binaryDescNode = node.getNode("../../" + LDPCV_BINARY_TIME_MAP + "/" + mementoId);

                return new FedoraBinaryImpl(binaryDescNode);
            } catch (final PathNotFoundException e) {
                return null;
            } catch (final RepositoryException e) {
                throw new RepositoryRuntimeException(e);
            }

        } else {
            return new FedoraBinaryImpl(getNode());
        }
    }

    /**
     * Check if the JCR node has a fedora:Description mixin
     *
     * @param node the JCR node
     * @return true if the JCR node has the fedora:Description mixin
     */
    public static boolean hasMixin(final Node node) {
        try {
            return node.isNodeType(FEDORA_DESCRIPTION);
        } catch (final RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }
}
