/*
   Copyright 2013, 2015, 2016 Nationale-Nederlanden

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package nl.nn.adapterframework.validation;


import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.validation.ValidatorHandler;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;

import nl.nn.adapterframework.configuration.ConfigurationException;
import nl.nn.adapterframework.core.INamedObject;
import nl.nn.adapterframework.core.IPipeLineSession;
import nl.nn.adapterframework.core.PipeRunException;
import nl.nn.adapterframework.util.AppConstants;
import nl.nn.adapterframework.util.ClassUtils;
import nl.nn.adapterframework.util.LogUtil;
import nl.nn.adapterframework.util.StreamUtil;
import nl.nn.adapterframework.util.Variant;
import nl.nn.adapterframework.util.XmlExternalEntityResolver;

/**
 * baseclass for validating input message against a XML-Schema.
 *
 * <p><b>Configuration:</b>
 * <table border="1">
 * <tr><th>attributes</th><th>description</th><th>default</th></tr>
 * <tr><td>className</td><td>nl.nn.adapterframework.pipes.XmlValidator</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setFullSchemaChecking(boolean) fullSchemaChecking}</td><td>Perform addional memory intensive checks</td><td><code>false</code></td></tr>
 * <tr><td>{@link #setThrowException(boolean) throwException}</td><td>Should the XmlValidator throw a PipeRunException on a validation error (if not, a forward with name "failure" should be defined.</td><td><code>false</code></td></tr>
 * <tr><td>{@link #setReasonSessionKey(String) reasonSessionKey}</td><td>if set: key of session variable to store reasons of mis-validation in</td><td>failureReason</td></tr>
 * <tr><td>{@link #setXmlReasonSessionKey(String) xmlReasonSessionKey}</td><td>like <code>reasonSessionKey</code> but stores reasons in xml format and more extensive</td><td>xmlFailureReason</td></tr>
 * <tr><td>{@link #setValidateFile(boolean) validateFile}</td><td>when set <code>true</code>, the input is assumed to be the name of the file to be validated. Otherwise the input itself is validated</td><td><code>false</code></td></tr>
 * <tr><td>{@link #setCharset(String) charset}</td><td>characterset used for reading file, only used when {@link #setValidateFile(boolean) validateFile} is <code>true</code></td><td>UTF-8</td></tr>
 * </table>
 * <br>
 * N.B. noNamespaceSchemaLocation may contain spaces, but not if the schema is stored in a .jar or .zip file on the class path.
 * @author Johan Verrips IOS
 * @author Jaco de Groot
 */
public abstract class AbstractXmlValidator {
	protected static Logger log = LogUtil.getLogger(AbstractXmlValidator.class);

	public static final String XML_VALIDATOR_PARSER_ERROR_MONITOR_EVENT = "Invalid XML: parser error";
	public static final String XML_VALIDATOR_NOT_VALID_MONITOR_EVENT = "Invalid XML: does not comply to XSD";
	public static final String XML_VALIDATOR_VALID_MONITOR_EVENT = "valid XML";


	protected SchemasProvider schemasProvider;
    private boolean throwException = false;
    private boolean fullSchemaChecking = false;
	private String reasonSessionKey = "failureReason";
	private String xmlReasonSessionKey = "xmlFailureReason";

	private boolean validateFile=false;
	private String charset=StreamUtil.DEFAULT_INPUT_STREAM_ENCODING;
	protected boolean warn = AppConstants.getInstance().getBoolean("xmlValidator.warn", true);
    protected boolean needsInit = true;
    protected boolean lazyInit = AppConstants.getInstance().getBoolean("xmlValidator.lazyInit", false);

    protected String logPrefix = "";
    protected boolean addNamespaceToSchema = false;
	protected String importedSchemaLocationsToIgnore;
    protected boolean useBaseImportedSchemaLocationsToIgnore = false;
	protected String importedNamespacesToIgnore;
	protected Boolean ignoreUnknownNamespaces;
	protected boolean ignoreCaching = false;

    public boolean isAddNamespaceToSchema() {
        return addNamespaceToSchema;
    }

    public void setAddNamespaceToSchema(boolean addNamespaceToSchema) {
        this.addNamespaceToSchema = addNamespaceToSchema;
    }

	public void setImportedSchemaLocationsToIgnore(String string) {
		importedSchemaLocationsToIgnore = string;
	}

	public String getImportedSchemaLocationsToIgnore() {
		return importedSchemaLocationsToIgnore;
	}

    public boolean isUseBaseImportedSchemaLocationsToIgnore() {
        return useBaseImportedSchemaLocationsToIgnore;
    }

    public void setUseBaseImportedSchemaLocationsToIgnore(boolean useBaseImportedSchemaLocationsToIgnore) {
        this.useBaseImportedSchemaLocationsToIgnore = useBaseImportedSchemaLocationsToIgnore;
    }

	public void setImportedNamespacesToIgnore(String string) {
		importedNamespacesToIgnore = string;
	}

	public String getImportedNamespacesToIgnore() {
		return importedNamespacesToIgnore;
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
			needsInit = false;
		}
	}

	public void reset() {
		if (!needsInit) {
			needsInit = true;
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
			log.debug(getLogPrefix(session) + "storing reasons under sessionKey ["+getReasonSessionKey()+"]");
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

	public ValidationContext createValidationContext(IPipeLineSession session, Set<List<String>> rootValidations, Map<List<String>, List<String>> invalidRootNamespaces) throws ConfigurationException, PipeRunException {
		
		// clear session variables
		if (StringUtils.isNotEmpty(getReasonSessionKey())) {
			log.debug(logPrefix+ "removing contents of sessionKey ["+getReasonSessionKey()+ "]");
			session.remove(getReasonSessionKey());
		}

		if (StringUtils.isNotEmpty(getXmlReasonSessionKey())) {
			log.debug(logPrefix+ "removing contents of sessionKey ["+getXmlReasonSessionKey()+ "]");
			session.remove(getXmlReasonSessionKey());
		}
		return null;
	}

	public abstract ValidatorHandler getValidatorHandler(IPipeLineSession session, ValidationContext context) throws ConfigurationException, PipeRunException;
	public abstract XMLReader createValidatingParser(IPipeLineSession session, ValidationContext context) throws XmlValidatorException, ConfigurationException, PipeRunException;

	public XMLReader getValidatingParser(IPipeLineSession session, ValidationContext context) throws XmlValidatorException, ConfigurationException, PipeRunException {
		return getValidatingParser(session, context, false);
	}
	public XMLReader getValidatingParser(IPipeLineSession session, ValidationContext context, boolean resolveExternalEntities) throws XmlValidatorException, ConfigurationException, PipeRunException {
		XMLReader parser = createValidatingParser(session, context);
		if (!resolveExternalEntities) {
			parser.setEntityResolver(new XmlExternalEntityResolver());
		}
		return parser;
	}
	
	/**
	 * Validate the XML string
	 * @param input a String
	 * @param session a {@link nl.nn.adapterframework.core.IPipeLineSession pipeLineSession}
	 * @return MonitorEvent declared in{@link AbstractXmlValidator}
	 * @throws XmlValidatorException when <code>isThrowException</code> is true and a validationerror occurred.
	 * @throws PipeRunException
	 * @throws ConfigurationException
	 */
//	public String validate(Object input, IPipeLineSession session, String logPrefix) throws XmlValidatorException, PipeRunException, ConfigurationException {
//		return validate(input, session, logPrefix, rootValidations, invalidRootNamespaces, false);
//	}
	
	public String validate(Object input, IPipeLineSession session, String logPrefix, Set<List<String>> rootValidations, Map<List<String>, List<String>> invalidRootNamespaces, boolean resolveExternalEntities) throws XmlValidatorException, PipeRunException, ConfigurationException {
		ValidationContext context = createValidationContext(session, rootValidations, invalidRootNamespaces);
		XMLReader parser = getValidatingParser(session, context, resolveExternalEntities);
		return validate(input, session, logPrefix, parser, null, context);
	}
	
	public String validate(Object input, IPipeLineSession session, String logPrefix, XMLReader parser, XMLFilterImpl filter, ValidationContext context) throws XmlValidatorException, PipeRunException, ConfigurationException {

		if (filter!=null) {
			filter.setContentHandler(context.getContentHandler());
			filter.setErrorHandler(context.getErrorHandler());
		} else {
			parser.setContentHandler(context.getContentHandler());
			parser.setErrorHandler(context.getErrorHandler());
		}
		
		InputSource is = getInputSource(input);
		
		try {
			parser.parse(is);
		} catch (Exception e) {
			return finalizeValidation(context, session, e);
		}
		return finalizeValidation(context, session, null);
	}

	/**
	 * Evaluate the validation and set 'reason' session variables.
	 * 
	 * @param context: the validationContext of this attempt
	 * @param session: the PipeLineSession
	 * @param t:       the exception thrown by the validation, or null
	 * @return the result event, e.g. 'valid XML' or 'Invalid XML'
	 * @throws XmlValidatorException, when configured to do so
	 */
	public String finalizeValidation(ValidationContext context, IPipeLineSession session, Throwable t) throws XmlValidatorException {
		if (t!=null) {
			return handleFailures(context.getErrorHandler(), session, XML_VALIDATOR_PARSER_ERROR_MONITOR_EVENT, t);
		}
		if (context.getErrorHandler().hasErrorOccured()) {
			return handleFailures(context.getErrorHandler(), session, XML_VALIDATOR_NOT_VALID_MONITOR_EVENT, null);
		}
		return XML_VALIDATOR_VALID_MONITOR_EVENT;
	}

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
    }
	public boolean isFullSchemaChecking() {
		return fullSchemaChecking;
	}

    /**
     * @since 5.0
     * @param schemasProvider
     */
	public void setSchemasProvider(SchemasProvider schemasProvider) {
		this.schemasProvider = schemasProvider;
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

	public void setIgnoreUnknownNamespaces(boolean b) {
		this.ignoreUnknownNamespaces = b;
	}

	public Boolean getIgnoreUnknownNamespaces() {
		return ignoreUnknownNamespaces;
	}

    public boolean isIgnoreCaching() {
        return ignoreCaching;
    }

    public void setIgnoreCaching(boolean ignoreCaching) {
        this.ignoreCaching = ignoreCaching;
    }

    public boolean isLazyInit() {
        return lazyInit;
    }

    public void setLazyInit(boolean lazyInit) {
        this.lazyInit = lazyInit;
    }

}
