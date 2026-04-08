package com.concil.edi.commons.service.extractor;

import com.concil.edi.commons.entity.Layout;
import com.concil.edi.commons.enums.FileType;
import com.concil.edi.commons.enums.ValueOrigin;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;

/**
 * Extractor for TAG value origin (XML files).
 * Extracts values using XPath expressions from des_tag field.
 * Supports nested paths within the buffer.
 */
@Component
public class XmlTagExtractor implements ValueExtractor {
    
    private final DocumentBuilderFactory documentBuilderFactory;
    private final XPathFactory xPathFactory;
    
    public XmlTagExtractor() {
        this.documentBuilderFactory = DocumentBuilderFactory.newInstance();
        this.xPathFactory = XPathFactory.newInstance();
    }
    
    @Override
    public String extractValue(byte[] buffer, String filename, IdentificationRule rule, Layout layout) {
        if (rule.getDesTag() == null || rule.getDesTag().isEmpty()) {
            return null;
        }
        
        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(buffer));
            
            XPath xpath = xPathFactory.newXPath();
            NodeList nodes = (NodeList) xpath.evaluate(rule.getDesTag(), document, XPathConstants.NODESET);
            
            if (nodes.getLength() > 0) {
                return nodes.item(0).getTextContent();
            }
            
        } catch (Exception e) {
            // Log warning and return null on error (malformed XML or invalid XPath)
            return null;
        }
        
        return null;
    }
    
    @Override
    public boolean supports(ValueOrigin valueOrigin, FileType fileType) {
        return valueOrigin == ValueOrigin.TAG;
    }
}
