package nl.nn.adapterframework.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.IOUtils;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.SAXException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Michiel Meeuwissen
 */
public class XsdUtilsTest {


    @BeforeClass
    public static void setup() {
        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setIgnoreAttributeOrder(true);
    }

    @Test
    public void targetNameSpaceAddingEmpty() throws XMLStreamException {
        InputStream in = new ByteArrayInputStream("<doesntmatterwhat />".getBytes());
        assertEquals(in, XsdUtils.targetNameSpaceAdding(in, ""));
        assertEquals(in, XsdUtils.targetNameSpaceAdding(in, null));
    }

    @Test
    public void targetNameSpaceAddingAndDefaultNamespace() throws XMLStreamException, IOException, SAXException {
        String testXsd = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\"><element /></xs:schema>";
        String expected = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\" xmlns=\"http://wub2nn.nn.nl/GetIntermediaryAgreementDetails\" targetNamespace=\"http://wub2nn.nn.nl/GetIntermediaryAgreementDetails\"><element /></xs:schema>";
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        IOUtils.copy(XsdUtils.targetNameSpaceAdding(new ByteArrayInputStream(testXsd.getBytes()), "http://wub2nn.nn.nl/GetIntermediaryAgreementDetails"), result);
        Diff diff = XMLUnit.compareXML(expected, result.toString());
        assertTrue(diff.toString() + " " + result.toString(), diff.identical());
    }


    @Test
    public void targetNameSpaceAdding() throws XMLStreamException, IOException, SAXException {
        // existing default namespace should of course be conserved
        String testXsd = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\" xmlns=\"http://someothernamespace\"><element /></xs:schema>";
        String expected = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\" xmlns=\"http://someothernamespace\" targetNamespace=\"http://wub2nn.nn.nl/GetIntermediaryAgreementDetails\"><element /></xs:schema>";
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        IOUtils.copy(XsdUtils.targetNameSpaceAdding(new ByteArrayInputStream(testXsd.getBytes()), "http://wub2nn.nn.nl/GetIntermediaryAgreementDetails"), result);
        Diff diff = XMLUnit.compareXML(expected, result.toString());
        assertTrue(diff.toString() + " " + result.toString(), diff.identical());
    }

}