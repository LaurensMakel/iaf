/*
 * $Log: AbstractXmlValidator.java,v $
 * Revision 1.5  2012-10-01 07:59:29  m00f069
 * Improved messages stored in reasonSessionKey and xmlReasonSessionKey
 * Cleaned XML validation code and documentation a bit.
 *
 * Revision 1.4  2012/09/19 21:40:37  Jaco de Groot <jaco.de.groot@ibissource.org>
 * Added ignoreUnknownNamespaces attribute
 *
 * Revision 1.3  2012/09/19 09:49:58  Jaco de Groot <jaco.de.groot@ibissource.org>
 * - Set reasonSessionKey to "failureReason" and xmlReasonSessionKey to "xmlFailureReason" by default
 * - Fixed check on unknown namspace in case root attribute or xmlReasonSessionKey is set
 * - Fill reasonSessionKey with a message when an exception is thrown by parser instead of the ErrorHandler being called
 * - Added/fixed check on element of soapBody and soapHeader
 * - Cleaned XML validation code a little (e.g. moved internal XmlErrorHandler class (double code in two classes) to an external class, removed MODE variable and related code)
 *
 * Revision 1.2  2012/09/13 08:25:17  Jaco de Groot <jaco.de.groot@ibissource.org>
 * - Throw exception when XSD doesn't exist (to prevent adapter from starting).
 * - Ignore warning schema_reference.4: Failed to read schema document 'http://www.w3.org/2001/xml.xsd'.
 * - Made SoapValidator use 1.1 XSD only by default (using two generates the warning s4s-elt-invalid-content.1: The content of 'reasontext' is invalid. Element 'attribute' is invalid, misplaced, or occurs too often.).
 * - Introduced xmlValidator.lazyInit property.
 * - Don't lazy init by default (restored old behaviour).
 *
 * Revision 1.1  2012/08/23 11:57:43  Jaco de Groot <jaco.de.groot@ibissource.org>
 * Updates from Michiel
 *
 * Revision 1.6  2012/03/16 15:35:44  Jaco de Groot <jaco.de.groot@ibissource.org>
 * Michiel added EsbSoapValidator and WsdlXmlValidator, made WSDL's available for all adapters and did a bugfix on XML Validator where it seems to be dependent on the order of specified XSD's
 *
 * Revision 1.5  2011/12/08 10:57:49  Peter Leeuwenburgh <peter.leeuwenburgh@ibissource.org>
 * fixed javadoc
 *
 * Revision 1.4  2011/11/30 13:51:49  Peter Leeuwenburgh <peter.leeuwenburgh@ibissource.org>
 * adjusted/reversed "Upgraded from WebSphere v5.1 to WebSphere v6.1"
 *
 * Revision 1.1  2011/10/19 14:49:43  Peter Leeuwenburgh <peter.leeuwenburgh@ibissource.org>
 * Upgraded from WebSphere v5.1 to WebSphere v6.1
 *
 * Revision 1.2  2011/09/13 13:39:48  Gerrit van Brakel <gerrit.van.brakel@ibissource.org>
 * added getLogPrefix()
 *
 * Revision 1.1  2011/08/22 09:51:36  Gerrit van Brakel <gerrit.van.brakel@ibissource.org>
 * new baseclasses for XmlValidation
 *
 */
package nl.nn.adapterframework.util;


import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import nl.nn.adapterframework.configuration.ConfigurationException;
import nl.nn.adapterframework.configuration.ConfigurationWarnings;
import nl.nn.adapterframework.core.INamedObject;
import nl.nn.adapterframework.core.IPipeLineSession;
import nl.nn.adapterframework.core.PipeRunException;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.parser.XMLErrorHandler;
import org.apache.xerces.xni.parser.XMLParseException;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

/**
 * baseclass for validating input message against a XML-Schema.
 *
 * <p><b>Configuration:</b>
 * <table border="1">
 * <tr><th>attributes</th><th>description</th><th>default</th></tr>
 * <tr><td>className</td><td>nl.nn.adapterframework.pipes.XmlValidator</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setSchema(String) schema}</td><td>The filename of the schema on the classpath. See doc on the method. (effectively the same as noNamespaceSchemaLocation)</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setNoNamespaceSchemaLocation(String) noNamespaceSchemaLocation}</td><td>A URI reference as a hint as to the location of a schema document with no target namespace. See doc on the method.</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setSchemaLocation(String) schemaLocation}</td><td>Pairs of URI references (one for the namespace name, and one for a hint as to the location of a schema document defining names for that namespace name). See doc on the method.</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setSchemaSessionKey(String) schemaSessionKey}</td><td>&nbsp;</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setFullSchemaChecking(boolean) fullSchemaChecking}</td><td>Perform addional memory intensive checks</td><td><code>false</code></td></tr>
 * <tr><td>{@link #setThrowException(boolean) throwException}</td><td>Should the XmlValidator throw a PipeRunException on a validation error (if not, a forward with name "failure" should be defined.</td><td><code>false</code></td></tr>
 * <tr><td>{@link #setReasonSessionKey(String) reasonSessionKey}</td><td>if set: key of session variable to store reasons of mis-validation in</td><td>none</td></tr>
 * <tr><td>{@link #setXmlReasonSessionKey(String) xmlReasonSessionKey}</td><td>like <code>reasonSessionKey</code> but stores reasons in xml format and more extensive</td><td>none</td></tr>
 * <tr><td>{@link #setRoot(String) root}</td><td>name of the root element</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setValidateFile(boolean) validateFile}</td><td>when set <code>true</code>, the input is assumed to be the name of the file to be validated. Otherwise the input itself is validated</td><td><code>false</code></td></tr>
 * <tr><td>{@link #setCharset(String) charset}</td><td>characterset used for reading file, only used when {@link #setValidateFile(boolean) validateFile} is <code>true</code></td><td>UTF-8</td></tr>
 * </table>
 * <br>
 * N.B. noNamespaceSchemaLocation may contain spaces, but not if the schema is stored in a .jar or .zip file on the class path.
 * @version Id
 * @author Johan Verrips IOS / Jaco de Groot (***@dynasol.nl)
 */
public abstract class AbstractXmlValidator {
	protected Logger log = LogUtil.getLogger(this);

	public static final String XML_VALIDATOR_PARSER_ERROR_MONITOR_EVENT = "Invalid XML: parser error";
	public static final String XML_VALIDATOR_ILLEGAL_ROOT_MONITOR_EVENT = "Invalid XML: wrong root";
	public static final String XML_VALIDATOR_NOT_VALID_MONITOR_EVENT = "Invalid XML: does not comply to XSD";
	public static final String XML_VALIDATOR_VALID_MONITOR_EVENT = "valid XML";

    private String schemaLocation = null;
    private String noNamespaceSchemaLocation = null;
	private String schemaSessionKey = null;
    private boolean throwException = false;
    private boolean fullSchemaChecking = false;
	private String reasonSessionKey = "failureReason";
	private String xmlReasonSessionKey = "xmlFailureReason";
	private String root = null;
	protected Set<List<String>> singleLeafValidations = null;
	private boolean validateFile=false;
	private String charset=StreamUtil.DEFAULT_INPUT_STREAM_ENCODING;
	protected boolean warn = AppConstants.getInstance().getBoolean("xmlValidator.warn", true);
    protected boolean needsInit = true;
    protected boolean lazyInit = AppConstants.getInstance().getBoolean("xmlValidator.lazyInit", false);

    protected String logPrefix = "";
    protected boolean addNamespaceToSchema = false;
	protected Boolean ignoreUnknownNamespaces;
	protected String mainFailureMessage;

    public boolean isAddNamespaceToSchema() {
        return addNamespaceToSchema;
    }

    public void setAddNamespaceToSchema(boolean addNamespaceToSchema) {
        this.addNamespaceToSchema = addNamespaceToSchema;
    }

    /**
     * Configure the XmlValidator
     * @throws ConfigurationException when:
     * <ul><li>the schema cannot be found</li>
     * <ul><li><{@link #isThrowException()} is false and there is no forward defined
     * for "failure"</li>
     * <li>when the parser does not accept setting the properties for validating</li>
     * </ul>
     */
    public void configure(String logPrefix) throws ConfigurationException {
        this.logPrefix = logPrefix;
        if (!lazyInit) {
            init();
        }
    }

    protected void init() throws ConfigurationException {
        if (needsInit) {
            if ((StringUtils.isNotEmpty(getNoNamespaceSchemaLocation()) ||
                StringUtils.isNotEmpty(getSchemaLocation())) &&
                StringUtils.isNotEmpty(getSchemaSessionKey())) {
                throw new ConfigurationException(logPrefix + "cannot have schemaSessionKey together with schemaLocation or noNamespaceSchemaLocation");
            }
            if (StringUtils.isNotEmpty(getSchemaLocation())) {
                String resolvedLocations = XmlUtils.resolveSchemaLocations(getSchemaLocation());
                log.info(logPrefix + "resolved schemaLocation [" + getSchemaLocation() + "] to [" + resolvedLocations + "]");
                setSchemaLocation(resolvedLocations);
            }
            if (StringUtils.isNotEmpty(getNoNamespaceSchemaLocation())) {
                URL url = ClassUtils.getResourceURL(this, getNoNamespaceSchemaLocation());
                if (url == null) {
                    throw new ConfigurationException(logPrefix + "could not find schema at ["+getNoNamespaceSchemaLocation()+"]");
                }
                String resolvedLocation =url.toExternalForm();
                log.info(logPrefix + "resolved noNamespaceSchemaLocation to [" + resolvedLocation+"]");
                setNoNamespaceSchemaLocation(resolvedLocation);
            }
            if (StringUtils.isEmpty(getNoNamespaceSchemaLocation()) &&
                StringUtils.isEmpty(getSchemaLocation()) &&
                StringUtils.isEmpty(getSchemaSessionKey())) {
                throw new ConfigurationException(logPrefix + "must have either schemaSessionKey, schemaLocation or noNamespaceSchemaLocation");
            }
            needsInit = false;
        }
    }

	protected String handleFailures(
			XmlValidatorErrorHandler xmlValidatorErrorHandler,
			IPipeLineSession session, String event, Throwable t)
					throws  XmlValidatorException {
		// A SAXParseException will already be reported by the parser to the
		// XmlValidatorErrorHandler through the ErrorHandler interface.
		if (t != null && !(t instanceof SAXParseException)) {
			xmlValidatorErrorHandler.addReason(t);
		}
		String fullReasons = xmlValidatorErrorHandler.getReasons();
		if (StringUtils.isNotEmpty(getReasonSessionKey())) {
			log.debug(getLogPrefix(session)+"storing reasons under sessionKey ["+getReasonSessionKey()+"]");
			session.put(getReasonSessionKey(),fullReasons);
		}
		if (StringUtils.isNotEmpty(getXmlReasonSessionKey())) {
			log.debug(getLogPrefix(session)+"storing reasons (in xml format) under sessionKey ["+getXmlReasonSessionKey()+"]");
			session.put(getXmlReasonSessionKey(),xmlValidatorErrorHandler.getXmlReasons());
		}
		if (isThrowException()) {
			throw new XmlValidatorException(fullReasons, t);
		}
		log.warn(getLogPrefix(session)+"validation failed: "+fullReasons, t);
		return event;
	}

     /**
      * Validate the XML string
      * @param input a String
      * @param session a {@link nl.nn.adapterframework.core.IPipeLineSession Pipelinesession}

      * @throws PipeRunException when <code>isThrowException</code> is true and a validationerror occurred.
      */
    public abstract String validate(Object input, IPipeLineSession session, String logPrefix) throws XmlValidatorException;

    /**
     * Enable full schema grammar constraint checking, including
     * checking which may be time-consuming or memory intensive.
     *  Currently, particle unique attribution constraint checking and particle
     * derivation resriction checking are controlled by this option.
     * <p> see property http://apache.org/xml/features/validation/schema-full-checking</p>
     * Defaults to <code>false</code>;
     */
    public void setFullSchemaChecking(boolean fullSchemaChecking) {
        this.fullSchemaChecking = fullSchemaChecking;
        this.needsInit = true;
    }
	public boolean isFullSchemaChecking() {
		return fullSchemaChecking;
	}

    /**
     * <p>The filename of the schema on the classpath. The filename (which e.g.
     * can contain spaces) is translated to an URI with the
     * ClassUtils.getResourceURL(Object,String) method (e.g. spaces are translated to %20).
     * It is not possible to specify a namespace using this attribute.
     * <p>An example value would be "xml/xsd/GetPartyDetail.xsd"</p>
     * <p>The value of the schema attribute is only used if the schemaLocation
     * attribute and the noNamespaceSchemaLocation are not set</p>
     * @see ClassUtils.getResource(Object,String)
     */
    public void setSchema(String schema) {
        setNoNamespaceSchemaLocation(schema);
        this.needsInit = true;
    }
	public String getSchema() {
		return getNoNamespaceSchemaLocation();
	}

    /**
     * <p>Pairs of URI references (one for the namespace name, and one for a
     * hint as to the location of a schema document defining names for that
     * namespace name).</p>
     * <p> The syntax is the same as for schemaLocation attributes
     * in instance documents: e.g, "http://www.example.com file%20name.xsd".</p>
     * <p>The user can specify more than one XML Schema in the list.</p>
     * <p><b>Note</b> that spaces are considered separators for this attributed.
     * This means that, for example, spaces in filenames should be escaped to %20.
     * </p>
     *
     * N.B. since 4.3.0 schema locations are resolved automatically, without the need for ${baseResourceURL}
     */
    public void setSchemaLocation(String schemaLocation) {
        this.schemaLocation = schemaLocation;
        this.needsInit = true;
    }
	public String getSchemaLocation() {
		return schemaLocation;
	}

    /**
     * <p>A URI reference as a hint as to the location of a schema document with
     * no target namespace.</p>
     */
    public void setNoNamespaceSchemaLocation(String noNamespaceSchemaLocation) {
        this.noNamespaceSchemaLocation = noNamespaceSchemaLocation;
        this.needsInit = true;
    }
	public String getNoNamespaceSchemaLocation() {
		return noNamespaceSchemaLocation;
	}

	/**
	 * <p>The sessionkey to a value that is the uri to the schema definition.</P>
	 */
	public void setSchemaSessionKey(String schemaSessionKey) {
		this.schemaSessionKey = schemaSessionKey;
	}
	public String getSchemaSessionKey() {
		return schemaSessionKey;
	}

	/**
	 * @deprecated attribute name changed to {@link #setSchemaSessionKey(String) schemaSessionKey}
	 */
	public void setSchemaSession(String schemaSessionKey) {
		ConfigurationWarnings configWarnings = ConfigurationWarnings.getInstance();
		String msg = "attribute 'schemaSession' is deprecated. Please use 'schemaSessionKey' instead.";
		configWarnings.add(log, msg);
		this.schemaSessionKey = schemaSessionKey;
        this.needsInit = true;
	}

	protected String getLogPrefix(IPipeLineSession session){
		  StringBuilder sb = new StringBuilder();
		  sb.append(ClassUtils.nameOf(this)).append(' ');
		  if (this instanceof INamedObject) {
			  sb.append("[").append(((INamedObject)this).getName()).append("] ");
		  }
		  if (session != null) {
			  sb.append("msgId [").append(session.getMessageId()).append("] ");
		  }
		  return sb.toString();
	}

    /**
     * Indicates wether to throw an error (piperunexception) when
     * the xml is not compliant.
     */
    public void setThrowException(boolean throwException) {
        this.throwException = throwException;
    }
	public boolean isThrowException() {
		return throwException;
	}

	/**
	 * The sessionkey to store the reasons of misvalidation in.
	 */
	public void setReasonSessionKey(String reasonSessionKey) {
		this.reasonSessionKey = reasonSessionKey;
	}
	public String getReasonSessionKey() {
		return reasonSessionKey;
	}

	public void setXmlReasonSessionKey(String xmlReasonSessionKey) {
		this.xmlReasonSessionKey = xmlReasonSessionKey;
	}
	public String getXmlReasonSessionKey() {
		return xmlReasonSessionKey;
	}

	public void setRoot(String root) {
		this.root = root;
		List<String> path = new ArrayList<String>();
		path.add(root);
		addSingleLeafValidation(path);
	}
	public String getRoot() {
		return root;
	}

	public void setValidateFile(boolean b) {
		validateFile = b;
	}
	public boolean isValidateFile() {
		return validateFile;
	}

	public void setCharset(String string) {
		charset = string;
	}
	public String getCharset() {
		return charset;
	}

	public void setWarn(boolean warn) {
		this.warn = warn;
	}

    protected InputSource getInputSource(Object input) throws XmlValidatorException {
        Variant in = new Variant(input);
        final InputSource is;
        if (isValidateFile()) {
            try {
                is = new InputSource(new InputStreamReader(new FileInputStream(in.asString()), getCharset()));
            } catch (FileNotFoundException e) {
                throw new XmlValidatorException("could not find file [" + in.asString() + "]", e);
            } catch (UnsupportedEncodingException e) {
                throw new XmlValidatorException("could not use charset [" + getCharset() + "]", e);
            }
        } else {
            is = in.asXmlInputSource();
        }
        return is;
    }

	public void addSingleLeafValidation(List<String> path) {
		if (singleLeafValidations == null) {
			singleLeafValidations = new HashSet<List<String>>();
		}
		singleLeafValidations.add(path);
	}

	public void setIgnoreUnknownNamespaces(boolean b) {
		this.ignoreUnknownNamespaces = b;
	}

	public boolean getIgnoreUnknownNamespaces() {
		if (ignoreUnknownNamespaces == null) {
			if (StringUtils.isEmpty(getSchemaLocation())) {
				return true;
			} else {
				return false;
			}
		} else {
			return ignoreUnknownNamespaces;
		}
	}

	public void setMainFailureMessage(String mainFailureMessage) {
		this.mainFailureMessage = mainFailureMessage;
	}

	protected static class RetryException extends XNIException {
        public RetryException(String s) {
            super(s);
        }
    }

	protected static class MyErrorHandler implements XMLErrorHandler {
		protected Logger log = LogUtil.getLogger(this);
		protected boolean throwRetryException = false;
		protected boolean warn = true;

		public void warning(String domain, String key, XMLParseException e) throws XNIException {
			// The schema location http://www.w3.org/2001/xml.xsd is a special
			// case which we ignore. It's used in envelope-1.2.xsd and
			// soap-1.2.xsd.
			if (warn && !(e.getMessage() != null
					&& e.getMessage().startsWith("schema_reference.4: Failed to read schema document 'http://www.w3.org/2001/xml.xsd'"))) {
				ConfigurationWarnings.getInstance().add(log, e.getMessage());
			}
		}

		public void error(String domain, String key, XMLParseException e) throws XNIException {
			// In case the XSD doesn't exist throw an exception to prevent the
			// the adapter from starting.
			if (e.getMessage() != null
					&& e.getMessage().startsWith("schema_reference.4: Failed to read schema document '")) {
				throw e;
			}
			if (throwRetryException) {
				throw new RetryException(e.getMessage());
			}
			if (warn) {
				ConfigurationWarnings.getInstance().add(log, e.getMessage());
			}
		}

		public void fatalError(String domain, String key, XMLParseException e) throws XNIException {
			if (warn) {
				ConfigurationWarnings.getInstance().add(log, e.getMessage());
			}
			throw new XNIException(e.getMessage());
		}
	}
}