
package org.fcrepo.messaging.legacy;

import static com.google.common.base.Throwables.propagate;
import static javax.jcr.observation.Event.NODE_ADDED;
import static javax.jcr.observation.Event.NODE_REMOVED;
import static javax.jcr.observation.Event.PROPERTY_ADDED;
import static javax.jcr.observation.Event.PROPERTY_CHANGED;
import static javax.jcr.observation.Event.PROPERTY_REMOVED;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.Writer;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.observation.Event;
import javax.jms.JMSException;
import javax.jms.Message;

import org.apache.abdera.model.Category;
import org.apache.abdera.model.Entry;
import org.fcrepo.utils.FedoraTypesUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;

public class LegacyMethod {

    //TODO Figure out where to get the base url
    private static final String BASE_URL = "http://localhost:8080/rest";

    private static final Properties FEDORA_TYPES = new Properties();

    public static final String FEDORA_ID_SCHEME = "xsd:string";
    
    public static final String DSID_CATEGORY_LABEL = "fedora-types:dsID";

    public static final String PID_CATEGORY_LABEL = "fedora-types:pid";

    private static final String INGEST_METHOD = "ingest";
    
    private static final String MODIFY_OBJ_METHOD = "modifyObject";
    
    private static final String PURGE_OBJ_METHOD = "purgeObject";
    
    private static final String ADD_DS_METHOD = "addDatastream";
    
    private static final String MODIFY_DS_METHOD = "modifyDatastream";
    
    private static final String PURGE_DS_METHOD = "purgeDatastream";
    
    private static final String[] METHODS = new String[] {INGEST_METHOD,
    	MODIFY_OBJ_METHOD, PURGE_OBJ_METHOD, ADD_DS_METHOD, MODIFY_DS_METHOD,
    	PURGE_DS_METHOD};

    private static final List<String> METHOD_NAMES = Arrays.asList(METHODS);

    private static final Logger LOGGER = getLogger(LegacyMethod.class);

    private static final String MAP_PROPERTIES =
            "/org/fcrepo/messaging/legacy/map.properties";

    static {
        try (final InputStream is =
                LegacyMethod.class.getResourceAsStream(MAP_PROPERTIES)) {
            FEDORA_TYPES.load(is);
        } catch (final IOException e) {
            // it's in the jar.s
            throw propagate(e);
        }
    }

    private final Entry delegate;

    public LegacyMethod(final Event jcrEvent, final Node resource)
            throws RepositoryException {
        this(EntryFactory.newEntry());

        final boolean isDatastreamNode =
                FedoraTypesUtils.isFedoraDatastream.apply(resource);
        final boolean isObjectNode =
                FedoraTypesUtils.isFedoraObject.apply(resource) &&
                        !isDatastreamNode;

        if (isDatastreamNode || isObjectNode) {
            setMethodName(mapMethodName(jcrEvent.getType(), isObjectNode));
            final String returnValue = getReturnValue(jcrEvent, resource);
            setContent(getEntryContent(getMethodName(), returnValue));
            if (isDatastreamNode) {
                setPid(resource.getParent().getName());
                setDsId(resource.getName());
            } else {
                setPid(resource.getName());
            }
        } else {
            setMethodName(null);
        }
        final String userID =
                jcrEvent.getUserID() == null ? "unknown" : jcrEvent.getUserID();
        setUserId(userID);
        setModified(new Date(jcrEvent.getDate()));
    }

    public LegacyMethod(final Entry atomEntry) {
        delegate = atomEntry;
    }

    public LegacyMethod(final String atomEntry) {
        delegate =
                EntryFactory.parse(new StringReader(atomEntry));
    }

    public Entry getEntry() {
        return delegate;
    }

    public void setContent(final String content) {
        delegate.setContent(content);
    }

    public void setUserId(String val) {
        if (val == null) {
            delegate.addAuthor("unknown", null, BASE_URL);
        } else {
        	delegate.addAuthor(val, null, BASE_URL);
        }
    }

    public String getUserID() {
        return delegate.getAuthor().getName();
    }

    public void setModified(final Date date) {
        delegate.setUpdated(date);
    }

    public Date getModified() {
        return delegate.getUpdated();
    }

    public void setMethodName(final String val) {
        delegate.setTitle(val).setBaseUri(BASE_URL);
    }

    public String getMethodName() {
        return delegate.getTitle();
    }
    
    private void setLabelledCategory(String label, String val) {
        final List<Category> vals = delegate.getCategories(FEDORA_ID_SCHEME);
        Category found = null;
        if (vals != null && !vals.isEmpty()) {
            for(Category c: vals) {
                if (label.equals(c.getLabel())) {
                    found = c.setTerm(val);
                }
            }
        }
        if (found == null) {
            delegate.addCategory(FEDORA_ID_SCHEME, val, label);
        }
    }
    
    private String getLabelledCategory(String label) {
        final List<Category> categories = delegate.getCategories(FEDORA_ID_SCHEME);
        for (final Category c : categories) {
            if (label.equals(c.getLabel())) {
                return c.getTerm();
            }
        }
        return null;
    }

    public void setPid(final String val) {
        setLabelledCategory(PID_CATEGORY_LABEL, val);
        delegate.setSummary(val);
    }

    public String getPid() {
        return getLabelledCategory(PID_CATEGORY_LABEL);
    }

    public void setDsId(final String val) {
        setLabelledCategory(DSID_CATEGORY_LABEL, val);
    }

    public String getDsId() {
        return getLabelledCategory(DSID_CATEGORY_LABEL);
    }

    public void writeTo(final Writer writer) throws IOException {
        delegate.writeTo(writer);
    }

    private static String getEntryContent(final String methodName,
            final String returnVal) {
        final String datatype =
                (String) FEDORA_TYPES.get(methodName + ".datatype");
        return objectToString(returnVal, datatype);
    }

    private static String
            objectToString(final String obj, final String xsdType) {
        if (obj == null) {
            return "null";
        }
        // TODO how is this not a String or subclass?
        final String javaType = obj.getClass().getCanonicalName();
        String term;
        //TODO Most of these types are not yet relevant to FCR4, but we can borrow their serializations as necessary
        // several circumstances yield null canonical names
        if (javaType != null && javaType.equals("java.util.Date")) { 
            //term = convertDateToXSDString((Date) obj);
            term = "[UNSUPPORTED" + xsdType + "]";
        } else if (xsdType.equals("fedora-types:ArrayOfString")) {
            //term = array2string(obj);
            term = "[UNSUPPORTED" + xsdType + "]";
        } else if (xsdType.equals("xsd:boolean")) {
            term = obj;
        } else if (xsdType.equals("xsd:nonNegativeInteger")) {
            term = obj;
        } else if (xsdType.equals("fedora-types:RelationshipTuple")) {
            //RelationshipTuple[] tuples = (RelationshipTuple[]) obj;
            //TupleArrayTripleIterator iter =
            //		new TupleArrayTripleIterator(new ArrayList<RelationshipTuple>(Arrays
            //				.asList(tuples)));
            //ByteArrayOutputStream os = new ByteArrayOutputStream();
            //try {
            //	iter.toStream(os, RDFFormat.NOTATION_3, false);
            //} catch (TrippiException e) {
            //	e.printStackTrace();
            //}
            //term = new String(os.toByteArray());
            term = "[UNSUPPORTED" + xsdType + "]";
        } else if (javaType != null && javaType.equals("java.lang.String")) {
            term = obj;
            term = term.replaceAll("\"", "'");
        } else {
            term = "[OMITTED]";
        }
        return term;
    }

    private static String getReturnValue(final Event jcrEvent,
            final Node jcrNode) throws RepositoryException {
        switch (jcrEvent.getType()) {
            case NODE_ADDED:
                return jcrNode.getName();
            case NODE_REMOVED:
                return convertDateToXSDString(jcrEvent.getDate());
            case PROPERTY_ADDED:
                return convertDateToXSDString(jcrEvent.getDate());
            case PROPERTY_CHANGED:
                return convertDateToXSDString(jcrEvent.getDate());
            case PROPERTY_REMOVED:
                return convertDateToXSDString(jcrEvent.getDate());
        }
        return null;
    }

    private static String mapMethodName(final int eventType,
            final boolean isObjectNode) {
        switch (eventType) {
            case NODE_ADDED:
                return isObjectNode ? INGEST_METHOD : ADD_DS_METHOD;
            case NODE_REMOVED:
                return isObjectNode ? PURGE_OBJ_METHOD : PURGE_DS_METHOD;
            case PROPERTY_ADDED:
                return isObjectNode ? MODIFY_OBJ_METHOD : MODIFY_DS_METHOD;
            case PROPERTY_CHANGED:
                return isObjectNode ? MODIFY_OBJ_METHOD : MODIFY_DS_METHOD;
            case PROPERTY_REMOVED:
                return isObjectNode ? MODIFY_OBJ_METHOD : MODIFY_DS_METHOD;
        }
        return null;
    }

    /**
     * @param date Instance of java.util.Date.
     * @return the lexical form of the XSD dateTime value, e.g.
     *         "2006-11-13T09:40:55.001Z".
     */
    private static String convertDateToXSDString(final long date) {
        final DateTime dt = new DateTime(date);
        final DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
        return fmt.print(dt);
    }

    public static boolean canParse(final Message jmsMessage) {
        try {
            return EntryFactory.FORMAT.equals(jmsMessage.getJMSType()) &&
                    METHOD_NAMES.contains(jmsMessage
                            .getStringProperty("methodName"));
        } catch (final JMSException e) {
            LOGGER.info("Could not parse message: {}", jmsMessage);
            throw propagate(e);
        }
    }

}
