/*
   Copyright 2017 Nationale-Nederlanden

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
package nl.nn.adapterframework.align;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.ValidatorHandler;

import org.apache.xerces.impl.xs.XMLSchemaLoader;
import org.apache.xerces.xs.XSModel;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

/**
 * XML Schema guided JSON to XML converter;
 * 
 * @author Gerrit van Brakel
 */
public class Json2Xml extends Tree2Xml<Object> {

	private final boolean DEBUG=false; 
	
	private boolean insertElementContainerElements;
	private boolean strictSyntax;

	public Json2Xml(ValidatorHandler validatorHandler, boolean insertElementContainerElements) {
		this(validatorHandler, insertElementContainerElements, false);
	}

	public Json2Xml(ValidatorHandler validatorHandler, boolean insertElementContainerElements, boolean strictSyntax) {
		super(validatorHandler);
		this.insertElementContainerElements=insertElementContainerElements;
		this.strictSyntax=strictSyntax;
	}

	public Json2Xml(ValidatorHandler validatorHandler, List<XSModel> schemaInformation, boolean insertElementContainerElements) {
		this(validatorHandler, schemaInformation, insertElementContainerElements, false);
	}

	public Json2Xml(ValidatorHandler validatorHandler, List<XSModel> schemaInformation, boolean insertElementContainerElements, boolean strictSyntax) {
		super(validatorHandler, schemaInformation);
		this.insertElementContainerElements=insertElementContainerElements;
		this.strictSyntax=strictSyntax;
	}

	@Override
	public void startParse(Object node) throws SAXException {
		JSONObject root = (JSONObject)node;
		String names[] = JSONObject.getNames(root);
		if (names==null || names.length==0) {
			throw new SAXException("no names found");
		}
		if (names.length>1) {
			String namesList=names[0];
			for (int i=1;i<names.length; i++) {
				namesList+=","+names[i];
				if (i>5) {
					namesList+=", ...";
					break;
				}
			}
			throw new SAXException("too many names ["+namesList+"]");
		}
		setRootElement(names[0]);
		try {
			super.startParse(((JSONObject)node).get(names[0]));
		} catch (JSONException e) {
			throw new SAXException(e);
		}
	}
	

	@Override
	public String getText(Object node) {
		String result;
		if (node instanceof JSONObject) {
			result=((JSONObject)node).toString();
		} else {
			result=node.toString();
		}
		if ("{}".equals(result)) {
			result="";
		}
		if (DEBUG && log.isDebugEnabled()) log.debug("getText() node ["+node+"] = ["+result+"]");
		return result;
	}

	@Override
	public Iterable<Object> getChildrenByName(Object node, String name) throws SAXException {
		if (DEBUG && log.isDebugEnabled()) log.debug("getChildrenByName() childname ["+name+"] isParentOfSingleMultipleOccurringChildElement ["+isParentOfSingleMultipleOccurringChildElement()+"] isSingleMultipleOccurringChildElement ["+isSingleMultipleOccurringChildElement()+"] isMultipleOccuringChildElement ["+isPresentInSet(multipleOccurringChildElements, name)+"] node ["+node+"]");
		try {
			if (node instanceof LinkedList) {
				if (DEBUG && log.isDebugEnabled()) log.debug("getChildrenByName() child ["+name+"], found LinkedList, straight json in compact mode");
				if (strictSyntax) {
					throw new SAXException("getChildrenByName() child ["+name+"], found LinkedList, straight json found while in compacting mode and strict syntax checking");
				}
				return (List<Object>)node;
			}
			if (node instanceof JSONArray && insertElementContainerElements && isParentOfSingleMultipleOccurringChildElement()) {
				if (DEBUG && log.isDebugEnabled()) log.debug("getChildrenByName() child ["+name+"] not found, but current node isParentOfSingleMultipleOccurringChildElement, assuming child needs to be inserted");
				List<Object> result = new LinkedList<Object>(); 
				JSONArray ja = (JSONArray)node;
				for (int i=0;i<ja.length();i++) {
					result.add(ja.get(i));
					if (DEBUG && log.isDebugEnabled()) log.debug("getChildrenByName() name ["+name+"] adding to array ["+ja.get(i)+"]");
				}
				return result;
			}
			if (!(node instanceof JSONObject)) {
				if (DEBUG && log.isDebugEnabled()) log.debug("getChildrenByName() parent node is not a JSONObject, but a ["+node.getClass().getName()+"]");
				return null;
			} 
			JSONObject o = (JSONObject)node;
			Object child = o.opt(name);
			if (child==null) {
				if (node instanceof JSONArray && insertElementContainerElements) {
					if (DEBUG && log.isDebugEnabled()) log.debug("getChildrenByName() child ["+name+"] not found, but current node is array");
					List<Object> result = new LinkedList<Object>(); 
					result.add(node);
					return result;
				}
				if (DEBUG && log.isDebugEnabled()) log.debug("getChildrenByName() no children named ["+name+"] node ["+node+"]");
				return null;
			} 
			List<Object> result = new LinkedList<Object>(); 
			if (child instanceof JSONArray) {
				if (DEBUG && log.isDebugEnabled()) log.debug("getChildrenByName() child named ["+name+"] is a JSONArray, current node isSingleMultipleOccurringChildElement ["+isSingleMultipleOccurringChildElement()+"] insertElementContainerElements ["+insertElementContainerElements+"]");
				// if it could be necessary to insert elementContainers, we cannot return the individual elements now, because then the containing element would be duplicated
				// we also cannot use the isSingleMultipleOccurringChildElement, because it is not valid yet
				if ((insertElementContainerElements || !strictSyntax) && !isPresentInSet(multipleOccurringChildElements, name)) {
					result.add(child);
					if (DEBUG && log.isDebugEnabled()) log.debug("getChildrenByName() singleMultipleOccurringChildElement ["+name+"] returning array node (insertElementContainerElements=true)");
				} else {
					if (DEBUG && log.isDebugEnabled()) log.debug("getChildrenByName() name ["+name+"] returning elements of array node (insertElementContainerElements=false or not singleMultipleOccurringChildElement)");
					JSONArray ja = (JSONArray)child;
					for (int i=0;i<ja.length();i++) {
						result.add(ja.get(i));
						if (DEBUG && log.isDebugEnabled()) log.debug("getChildrenByName() name ["+name+"] adding to array ["+ja.get(i)+"]");
					}
				}
				return result;
			}
			result.add(child);
			if (DEBUG && log.isDebugEnabled()) log.debug("getChildrenByName() name ["+name+"] returning ["+child+"]");
			return result;
		} catch (JSONException e) {
			throw new SAXException(e);
		}
	}
	
	@Override
	public Set<String> getAllChildNames(Object node) throws SAXException {
		if (DEBUG && log.isDebugEnabled()) log.debug("getAllChildNames() node isParentOfSingleMultipleOccurringChildElement ["+isParentOfSingleMultipleOccurringChildElement()+"] isSingleMultipleOccurringChildElement ["+isSingleMultipleOccurringChildElement()+"] ["+node.getClass().getName()+"]["+node+"]");
		try {
			if (isParentOfSingleMultipleOccurringChildElement() && (insertElementContainerElements || !strictSyntax) && node instanceof JSONArray) {
				log.debug("isParentOfSingleMultipleOccurringChildElement() && insertElementContainerElements && node instanceof JSONArray, returning ["+multipleOccurringChildElements+"]");
				return multipleOccurringChildElements;
			}
			if (!(node instanceof JSONObject)) {
				log.warn("getAllChildNames() parent node is not a JSONObject, but a ["+node.getClass().getName()+"] isParentOfSingleMultipleOccurringChildElement ["+isParentOfSingleMultipleOccurringChildElement()+"] isSingleMultipleOccurringChildElement ["+isSingleMultipleOccurringChildElement()+"] value ["+node+"], returning null");				
				return null;
			} 
			JSONObject o = (JSONObject)node;
			JSONArray names= o.names();
			if (names==null) {
				if (DEBUG && log.isDebugEnabled()) log.debug("getAllChildNames() no children");
				return new HashSet<String>();
			}
			Set<String> result = new HashSet<String>(); 
			for (int i=0;i<names.length();i++) {
				String name=(String)names.get(i);
				result.add(name);
				if (DEBUG && log.isDebugEnabled()) log.debug("getAllChildNames() name ["+name+"] added to set");
			}
			return result;
		} catch (JSONException e) {
			throw new SAXException(e);
		}
	}


	@Override
	protected void processArray(Object node, String name, String childElementName, String childElementNameSpace, boolean mandatory, Set<String> unProcessedChildren, Set<String> processedChildren) throws SAXException {
		if (DEBUG && log.isDebugEnabled()) log.debug("processArray() nodeName ["+name+"] childElementName ["+childElementName+"], node ["+node+"]");
		// this method is only called when isParentOfSingleMultipleOccurringChildElement is true
		if (insertElementContainerElements || !strictSyntax) {
			if (unProcessedChildren.isEmpty()) {
				if (DEBUG && log.isDebugEnabled()) log.debug("unProcessedChildren.isEmpty()");
				if  (node instanceof JSONArray) {
					if (DEBUG && log.isDebugEnabled()) log.debug("processArray() name ["+name+"] node is JSONArray, inserting array element container element, node ["+node+"]");
					unProcessedChildren.add(childElementName);
				}
				super.processArray(node, name, childElementName, childElementNameSpace, mandatory, unProcessedChildren, processedChildren);
			} else {
				if  (node instanceof JSONArray) {
					// at this point we're expecting arrayElement containers, but we already have an array.
					// So now we will handle each of the elements as a child
					if (DEBUG && log.isDebugEnabled()) log.debug("processArray() node instanceof JSONArray, handling each of the nodes as a ["+childElementName+"]");
					try {
						JSONArray ja=(JSONArray)node;
						for (int i=0;i<ja.length();i++) {
							handleNode(ja.get(i), childElementName, childElementNameSpace);
						}
						// mark that we have processed the arrayElement containers
						processedChildren.add(childElementName);
						unProcessedChildren.remove(childElementName);
					} catch (JSONException e) {
						throw new SAXException(e);
					}
				} else {
					// straight json compatibility
					// when we arrive here, the insertion of a container element needs to be compensated.
					try {
						JSONObject obj=(JSONObject)node;
						Object childObject=obj.opt(childElementName);
						if (childObject==null) {
							log.warn("processArray() node ["+name+"] ParentOfSingleMultipleOccurringChildElement ["+isParentOfSingleMultipleOccurringChildElement()+"] child ["+childElementName+"] not found, nodetype ["+node.getClass().getName()+"] node ["+node+"], will insert elementcontainer ["+childElementName+"] --> need to be verified");
							handleNode(node, childElementName, childElementNameSpace);
							unProcessedChildren.clear();
							processedChildren.add(name);
						} else {
							List<Object> helper=new LinkedList<Object>(); // We'll hack this by returning a LinkedList. getChildElementsByName() will recognize this.
							if (childObject instanceof JSONArray) {
								JSONArray ja=(JSONArray)childObject;
								for (int i=0;i<ja.length();i++) {
									helper.add(ja.get(i));
								}
							} else {
								helper.add(childObject);
							}
							processChildElement(helper, name, childElementName, childElementNameSpace, mandatory, unProcessedChildren, processedChildren);
						}
					} catch (JSONException e) {
						throw new SAXException(e);
					}
				}
			}
		} else {
			super.processArray(node, name, childElementName, childElementNameSpace, mandatory, unProcessedChildren, processedChildren);
		}
	}
	
	public static String translate(JSONObject json, URL schemaURL, boolean compactJsonArrays, String targetNamespace) throws SAXException, IOException {
		return translate(json, schemaURL, compactJsonArrays, false, targetNamespace);
	}
	public static String translate(JSONObject json, URL schemaURL, boolean compactJsonArrays, boolean strictSyntax, String targetNamespace) throws SAXException, IOException {

		// create the ValidatorHandler
    	SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		Schema schema = sf.newSchema(schemaURL); 
		ValidatorHandler validatorHandler = schema.newValidatorHandler();
 	
		// create the XSModel
		XMLSchemaLoader xsLoader = new XMLSchemaLoader();
		XSModel xsModel = xsLoader.loadURI(schemaURL.toExternalForm());
		List<XSModel> schemaInformation= new LinkedList<XSModel>();
		schemaInformation.add(xsModel);

		// create the validator, setup the chain
		Json2Xml j2x = new Json2Xml(validatorHandler,schemaInformation,compactJsonArrays,strictSyntax);
		if (targetNamespace!=null) {
			j2x.setTargetNamespace(targetNamespace);
		}
    	Source source=j2x.asSource(json);
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        String xml=null;
		try {
	        TransformerFactory tf = TransformerFactory.newInstance();
	        Transformer transformer = tf.newTransformer();
	        transformer.transform(source, result);
	        writer.flush();
	        xml = writer.toString();
		} catch (TransformerConfigurationException e) {
			throw new SAXException(e);
		} catch (TransformerException e) {
			throw new SAXException(e);
		}
    	return xml;
 	}

}