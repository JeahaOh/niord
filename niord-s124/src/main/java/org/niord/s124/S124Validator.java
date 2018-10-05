package org.niord.s124;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.util.JAXBSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;

public class S124Validator {

    private final Validator validator;

    @SuppressWarnings("unused")
    public S124Validator() throws SAXException {
        Schema mySchema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(new File("src/main/resources/xsd/S124/1.0/20180910/S124.xsd"));
        validator = mySchema.newValidator();
    }

    public List<ValidationError> validateSchema(JAXBElement<?> jaxbElement) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(jaxbElement.getValue().getClass());
        JAXBSource source = new JAXBSource(jaxbContext, jaxbElement);

        List<ValidationError> validationErrors = new LinkedList<>();

        validator.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException e) {
                validationErrors.add(new ValidationError("WARNING", e.getMessage(), e.getLineNumber(), e.getColumnNumber()));
            }

            @Override
            public void fatalError(SAXParseException e) {
                validationErrors.add(new ValidationError("FATAL", e.getMessage(), e.getLineNumber(), e.getColumnNumber()));
            }

            @Override
            public void error(SAXParseException e) {
                validationErrors.add(new ValidationError("ERROR", e.getMessage(), e.getLineNumber(), e.getColumnNumber()));
            }
        });

        try {
            validator.validate(source);
        } catch (SAXException e) {
            validationErrors.add(new ValidationError("UNKNOWN", e.getMessage(), null, null));
        } catch (IOException e) {
            validationErrors.add(new ValidationError("IO", e.getMessage(), null, null));
        }

        return validationErrors;
    }

    public void printXml(JAXBElement<?> jaxbElement, OutputStream out) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(jaxbElement.getValue().getClass());
        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(jaxbElement, out);
    }

    public static class ValidationError {
        ValidationError(String type, String message, Integer lineNumber, Integer columnNumber) {
            this.type = type;
            this.message = message;
            this.lineNumber = lineNumber;
            this.columnNumber = columnNumber;
        }

        public String getType() {
            return type;
        }

        public String getMessage() {
            return message;
        }

        public Integer getLineNumber() {
            return lineNumber;
        }

        public Integer getColumnNumber() {
            return columnNumber;
        }

        @Override
        public String toString() {
            StringBuffer sb = new StringBuffer();

            sb.append(type);
            sb.append(": ");
            sb.append("Line ");
            sb.append(lineNumber != null ? lineNumber : -1);
            sb.append(", column ");
            sb.append(columnNumber != null ? columnNumber : -1);
            sb.append(": ");
            sb.append(message);

            return sb.toString();
        }

        private final String type;
        private final String message;
        private final Integer lineNumber;
        private final Integer columnNumber;
    }

}
