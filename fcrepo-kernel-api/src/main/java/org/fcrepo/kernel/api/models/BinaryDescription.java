package org.fcrepo.kernel.api.models;

import java.net.URI;

public interface BinaryDescription extends FedoraResource {

    /**
     * @return The size in bytes of content associated with this datastream.
     */
    long getContentSize();

    /**
     * Get the pre-calculated content digest for the binary payload
     *
     * @return a URI with the format algorithm:value
     */
    URI getContentDigest();

    /**
     * @return The MimeType of content associated with this datastream.
     */
    String getMimeType();

    /**
     * Return the file name for the binary content
     *
     * @return original file name for the binary content, or the object's id.
     */
    String getFilename();
}
