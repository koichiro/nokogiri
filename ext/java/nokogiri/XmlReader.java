/**
 * (The MIT License)
 *
 * Copyright (c) 2008 - 2011:
 *
 * * {Aaron Patterson}[http://tenderlovemaking.com]
 * * {Mike Dalessio}[http://mike.daless.io]
 * * {Charles Nutter}[http://blog.headius.com]
 * * {Sergio Arbeo}[http://www.serabe.com]
 * * {Patrick Mahoney}[http://polycrystal.org]
 * * {Yoko Harada}[http://yokolet.blogspot.com]
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * 'Software'), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED 'AS IS', WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package nokogiri;

import static nokogiri.internals.NokogiriHelpers.getNokogiriClass;
import static nokogiri.internals.NokogiriHelpers.rubyStringToString;
import static nokogiri.internals.NokogiriHelpers.stringOrBlank;
import static nokogiri.internals.NokogiriHelpers.stringOrNil;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.Stack;

import nokogiri.internals.ReaderNode;
import nokogiri.internals.ReaderNode.ElementNode;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyBoolean;
import org.jruby.RubyClass;
import org.jruby.RubyFixnum;
import org.jruby.RubyObject;
import org.jruby.RubyString;
import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;
import org.jruby.exceptions.RaiseException;
import org.jruby.javasupport.util.RuntimeHelpers;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.ByteList;
import org.jruby.util.IOInputStream;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;
import org.xml.sax.helpers.XMLReaderFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import org.jruby.RubyHash;

/**
 * Class for Nokogiri:XML::Reader
 *
 * @author sergio
 * @author Yoko Harada <yokolet@gmail.com>
 */
@JRubyClass(name="Nokogiri::XML::Reader")
public class XmlReader extends RubyObject {

    private static final int XML_TEXTREADER_MODE_INITIAL = 0;
    private static final int XML_TEXTREADER_MODE_INTERACTIVE = 1;
    private static final int XML_TEXTREADER_MODE_ERROR = 2;
    private static final int XML_TEXTREADER_MODE_EOF = 3;
    private static final int XML_TEXTREADER_MODE_CLOSED = 4;
    private static final int XML_TEXTREADER_MODE_READING = 5;
    
    ArrayDeque<ReaderNode> nodeQueue;
    private XMLStreamReader reader;
    private int state;
    private int depth;
    
    public XmlReader(Ruby runtime, RubyClass klazz) {
        super(runtime, klazz);
    }
    
    /**
     * Create and return a copy of this object.
     *
     * @return a clone of this object
     */
    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
    
    public void init(Ruby runtime) {
        nodeQueue = new ArrayDeque<ReaderNode>();
        nodeQueue.add(new ReaderNode.EmptyNode(runtime));
        depth = 0;
    }

    private void parse(ThreadContext context, IRubyObject in) {
        Ruby ruby = context.getRuntime();
        try {
            this.setState(XML_TEXTREADER_MODE_READING);
            XMLReader reader = this.createReader(ruby);
            InputSource io;
            if (in.respondsTo("read")) {
                io = new InputSource(new IOInputStream(in));
            } else {
                RubyString content = in.convertToString();
                ByteList byteList = content.getByteList();
                ByteArrayInputStream bais = new ByteArrayInputStream(byteList.unsafeBytes(), byteList.begin(), byteList.length());
                io = new InputSource(bais);
            }
            reader.parse(io);
            this.setState(XML_TEXTREADER_MODE_CLOSED);
        } catch (SAXParseException spe) {
            this.setState(XML_TEXTREADER_MODE_ERROR);
            this.nodeQueue.add(new ReaderNode.ExceptionNode(ruby, spe));
        } catch (IOException ioe) {
            throw RaiseException.createNativeRaiseException(ruby, ioe);
        } catch (SAXException saxe) {
            throw RaiseException.createNativeRaiseException(ruby, saxe);
        }
    }
    private void parse2(ThreadContext context, IRubyObject in) {
        Ruby ruby = context.getRuntime();
        this.setState(XML_TEXTREADER_MODE_READING);
        InputStream stream;
        if (in.respondsTo("read")) {
            stream = new BufferedInputStream(new IOInputStream(in));
        } else {
            RubyString content = in.convertToString();
            ByteList byteList = content.getByteList();
            stream = new ByteArrayInputStream(byteList.unsafeBytes(), byteList.begin(), byteList.length());
        }
        reader = this.createReader2(ruby, stream);
        this.setState(XML_TEXTREADER_MODE_CLOSED);
    }

    private static ReaderNode.ReaderNodeType dispatchNodeType(int nodeType) {
        switch (nodeType) {
            case XMLStreamConstants.ATTRIBUTE:
                return ReaderNode.ReaderNodeType.ATTRIBUTE;
            case XMLStreamConstants.CDATA:
                return ReaderNode.ReaderNodeType.CDATA;
            case XMLStreamConstants.CHARACTERS:
                return ReaderNode.ReaderNodeType.TEXT;
            case XMLStreamConstants.COMMENT:
                return ReaderNode.ReaderNodeType.COMMENT;
 //           case XMLStreamConstants.DTD:
 //           case XMLStreamConstants.END_DOCUMENT:
            case XMLStreamConstants.END_ELEMENT:
                return ReaderNode.ReaderNodeType.END_ELEMENT;
            case XMLStreamConstants.ENTITY_DECLARATION:
                return ReaderNode.ReaderNodeType.XML_DECLARATION;
            case XMLStreamConstants.ENTITY_REFERENCE:
                return ReaderNode.ReaderNodeType.ENTITY_REFERENCE;
//            case XMLStreamConstants.NAMESPACE:
            case XMLStreamConstants.NOTATION_DECLARATION:
                return ReaderNode.ReaderNodeType.NOTATION;
            case XMLStreamConstants.PROCESSING_INSTRUCTION:
                return ReaderNode.ReaderNodeType.PROCESSING_INSTRUCTION;
            case XMLStreamConstants.SPACE:
                return ReaderNode.ReaderNodeType.WHITESPACE;
            case XMLStreamConstants.START_DOCUMENT:
                return ReaderNode.ReaderNodeType.DOCUMENT;
            case XMLStreamConstants.START_ELEMENT:
                return ReaderNode.ReaderNodeType.ELEMENT;
        }
        return null;
    }
    
    private void setState(int state) { this.state = state; }

    @JRubyMethod
    public IRubyObject attribute(ThreadContext context, IRubyObject name) {
        Ruby ruby = context.getRuntime();
        int size = reader.getAttributeCount();
        if (size == 0) return ruby.getNil();
        String nm = rubyStringToString(name);
        for (int i = 0; i < size; i++) {
            if (nm.equals(reader.getAttributeLocalName(i))) {
                return stringOrNil(ruby, reader.getAttributeValue((i)));
            }
        }
        return ruby.getNil();
    }

    @JRubyMethod
    public IRubyObject attribute_at(ThreadContext context, IRubyObject index) {
        if (index.isNil()) return index;

        Ruby ruby = context.getRuntime();
        long i = index.convertToInteger().getLongValue();
        if(i > Integer.MAX_VALUE) {
            throw ruby.newArgumentError("value too long to be an array index");
        }

        if (i<0 || reader.getAttributeCount() <= i) return ruby.getNil();
        return stringOrBlank(ruby, reader.getAttributeValue((int)i));
    }

    @JRubyMethod
    public IRubyObject attribute_count(ThreadContext context) {
        return context.getRuntime().newFixnum(reader.getAttributeCount());
    }

    @JRubyMethod
    public IRubyObject attribute_nodes(ThreadContext context) {
        Ruby ruby = context.getRuntime();
        RubyArray array = RubyArray.newArray(ruby);
        int size = reader.getAttributeCount();
        if (size == 0) return array;
        for (int i = 0; i < size; i++) {
            // TBD
        }
        return array;
    }

    @JRubyMethod
    public IRubyObject attr_nodes(ThreadContext context) {
        return attribute_nodes(context);
    }

    @JRubyMethod(name = "attributes?")
    public IRubyObject attributes_p(ThreadContext context) {
        return context.getRuntime().newBoolean(reader.getAttributeCount() != 0);
    }
    
    @JRubyMethod
    public IRubyObject base_uri(ThreadContext context) {
        // TODO
        return context.getRuntime().getNil();
    }

    @JRubyMethod(name="default?")
    public IRubyObject default_p(ThreadContext context){
        // TODO
        return context.getRuntime().getFalse();
    }

    @JRubyMethod
    public IRubyObject depth(ThreadContext context) {
        return context.getRuntime().newFixnum(depth);
    }
    
    @JRubyMethod(name = {"empty_element?", "self_closing?"})
    public IRubyObject empty_element_p(ThreadContext context) {
        //ReaderNode readerNode = nodeQueue.peek();
        //if (readerNode == null) return context.getRuntime().getNil();
        //if (!(readerNode instanceof ElementNode)) context.getRuntime().getFalse();
        //return RubyBoolean.newBoolean(context.getRuntime(), !readerNode.hasChildren);
        return context.getRuntime().getFalse();
    }

    @JRubyMethod(meta = true, rest = true)
    public static IRubyObject from_io(ThreadContext context, IRubyObject cls, IRubyObject args[]) {
        // Only to pass the  source test.
        Ruby runtime = context.getRuntime();
        // Not nil allowed!
        if(args[0].isNil()) throw runtime.newArgumentError("io cannot be nil");

        XmlReader reader = (XmlReader) NokogiriService.XML_READER_ALLOCATOR.allocate(runtime, getNokogiriClass(runtime, "Nokogiri::XML::Reader"));
        reader.init(runtime);
        reader.setInstanceVariable("@source", args[0]);
        reader.setInstanceVariable("@errors", runtime.newArray());
        if (args.length > 2) reader.setInstanceVariable("@encoding", args[2]);
        reader.parse(context, args[0]);
        return reader;
    }

    @JRubyMethod(meta = true, rest = true)
    public static IRubyObject from_memory(ThreadContext context, IRubyObject cls, IRubyObject args[]) {
        // args[0]: string, args[1]: url, args[2]: encoding, args[3]: options 
        Ruby runtime = context.getRuntime();
        // Not nil allowed!
        if(args[0].isNil()) throw runtime.newArgumentError("string cannot be nil");

        XmlReader reader = (XmlReader) NokogiriService.XML_READER_ALLOCATOR.allocate(runtime, getNokogiriClass(runtime, "Nokogiri::XML::Reader"));
        reader.init(runtime);
        reader.setInstanceVariable("@source", args[0]);
        reader.setInstanceVariable("@errors", runtime.newArray());
        if (args.length > 2) reader.setInstanceVariable("@encoding", args[2]);

        reader.parse(context, args[0]);
        return reader;
    }

    @JRubyMethod
    public IRubyObject node_type(ThreadContext context) {
        Ruby ruby = context.getRuntime();
        ReaderNode.ReaderNodeType node_type = dispatchNodeType(reader.getEventType());
        if (node_type == null) return RubyFixnum.zero(ruby);
        return ruby.newFixnum(node_type.getValue());
    }

    @JRubyMethod
    public IRubyObject inner_xml(ThreadContext context) {
        return stringOrBlank(context.getRuntime(), getInnerXml(nodeQueue, nodeQueue.peek()));
    }
    
    private String getInnerXml(ArrayDeque<ReaderNode> nodeQueue, ReaderNode current) {
        if (current.depth < 0) return null;
        if (!current.hasChildren) return null;
        StringBuffer sb = new StringBuffer();
        int currentDepth = (Integer)current.depth;
        for (ReaderNode node : nodeQueue) {
            if (((Integer)node.depth) > currentDepth) sb.append(node.getString());
        }
        return new String(sb);
    }
    
    @JRubyMethod
    public IRubyObject outer_xml(ThreadContext context) {
        return stringOrBlank(context.getRuntime(), getOuterXml(nodeQueue, nodeQueue.peek()));
    }
    
    private String getOuterXml(ArrayDeque<ReaderNode> nodeQueue, ReaderNode current) {
        if (current.depth < 0) return null;
        StringBuffer sb = new StringBuffer();
        int initialDepth = (Integer)current.depth - 1;
        for (ReaderNode node : nodeQueue) {
            if (((Integer)node.depth) > initialDepth) sb.append(node.getString());
        }
        return new String(sb);
    }

    @JRubyMethod
    public IRubyObject lang(ThreadContext context) {
        // TODO
        return context.getRuntime().getNil();
    }

    @JRubyMethod
    public IRubyObject local_name(ThreadContext context) {
        return stringOrNil(context.getRuntime(), reader.getLocalName());
    }

    @JRubyMethod
    public IRubyObject name(ThreadContext context) {
        return stringOrNil(context.getRuntime(), reader.getName().toString());
    }

    @JRubyMethod
    public IRubyObject namespace_uri(ThreadContext context) {
        return stringOrNil(context.getRuntime(), reader.getNamespaceURI());
    }

    @JRubyMethod
    public IRubyObject namespaces(ThreadContext context) {
        Ruby ruby = context.getRuntime();
        RubyHash hash = RubyHash.newHash(ruby);
        for (int i=0; i < reader.getNamespaceCount(); i++) {
            IRubyObject k = stringOrBlank(ruby, reader.getNamespacePrefix(i));
            IRubyObject v = stringOrBlank(ruby, reader.getNamespaceURI(i));
            if (context.getRuntime().is1_9()) hash.op_aset19(context, k, v);
            else hash.op_aset(context, k, v);
        }
        return hash;
    }

    @JRubyMethod
    public IRubyObject prefix(ThreadContext context) {
        return stringOrNil(context.getRuntime(), reader.getPrefix());
    }

    @JRubyMethod
    public IRubyObject read(ThreadContext context) {
        Ruby ruby = context.getRuntime();
        try {
            if (reader.hasNext() == false) {
                return context.getRuntime().getNil();
            }

            reader.next();

            switch (reader.getEventType()) {
                case XMLStreamConstants.START_ELEMENT:
                    depth++;
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    depth--;
                    break;
            }
            return this;
        } catch (XMLStreamException e) {
            RubyArray errors = (RubyArray) this.getInstanceVariable("@errors");
            errors.append(ruby.newString(e.getMessage()));

            this.setInstanceVariable("@errors", errors);

            throw new RaiseException((XmlSyntaxError) new ReaderNode.ExceptionNode(ruby, e).toSyntaxError());
        }
    }

    @JRubyMethod
    public IRubyObject state(ThreadContext context) {
        return context.getRuntime().newFixnum(this.state);
    }

    @JRubyMethod
    public IRubyObject value(ThreadContext context) {
        return context.getRuntime().newString(new String(reader.getTextCharacters()));
    }

    @JRubyMethod(name = "value?")
    public IRubyObject value_p(ThreadContext context) {
        // maybe
        return context.getRuntime().newBoolean(reader.hasText());
    }

    @JRubyMethod
    public IRubyObject xml_version(ThreadContext context) {
        return context.getRuntime().newString(reader.getVersion());
    }

    protected XMLStreamReader createReader2(final Ruby ruby, InputStream stream) {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        BufferedInputStream bstream = new BufferedInputStream(stream);
        try {
            return factory.createXMLStreamReader(bstream);
        } catch (javax.xml.stream.XMLStreamException e) {
            throw RaiseException.createNativeRaiseException(ruby, e);
        }
    }

    protected XMLReader createReader(final Ruby ruby) {
        DefaultHandler2 handler = new DefaultHandler2() {

            Stack<String> langStack;
            int depth;
            Stack<String> xmlBaseStack;
            Stack<ReaderNode.ElementNode> elementStack;

            @Override
            public void characters(char[] chars, int start, int length) {
                ReaderNode.TextNode node = ReaderNode.createTextNode(ruby, new String(chars, start, length), depth, langStack, xmlBaseStack);
                nodeQueue.add(node);
            }
            
            @Override
            public void endDocument() throws SAXException {
                langStack = null;
                xmlBaseStack = null;
                elementStack = null;
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                depth--;     
                ReaderNode previous = nodeQueue.getLast();
                ElementNode startElementNode = elementStack.pop();
                if (previous instanceof ReaderNode.ElementNode && qName.equals(previous.name)) {
                    previous.hasChildren = false;
                } else {
                    ReaderNode node = ReaderNode.createClosingNode(ruby, uri, localName, qName, depth, langStack, xmlBaseStack);
                    if (startElementNode != null) {
                        node.attributeList = startElementNode.attributeList;
                        node.namespaces = startElementNode.namespaces;
                    }
                    nodeQueue.add(node);
                }
                if (!langStack.isEmpty()) langStack.pop();
                if (!xmlBaseStack.isEmpty()) xmlBaseStack.pop();
            }

            @Override
            public void error(SAXParseException ex) throws SAXParseException {
                nodeQueue.add(new ReaderNode.ExceptionNode(ruby, ex));
                throw ex;
            }

            @Override
            public void fatalError(SAXParseException ex) throws SAXParseException {
                nodeQueue.add(new ReaderNode.ExceptionNode(ruby, ex));
                throw ex;
            }

            @Override
            public void startDocument() {
                depth = 0;
                langStack = new Stack<String>();
                xmlBaseStack = new Stack<String>();
                elementStack = new Stack<ReaderNode.ElementNode>();
            }

            @Override
            public void startElement(String uri, String localName, String qName, Attributes attrs) {
                ReaderNode readerNode = ReaderNode.createElementNode(ruby, uri, localName, qName, attrs, depth, langStack, xmlBaseStack);
                nodeQueue.add(readerNode);
                depth++;
                if (readerNode.lang != null) langStack.push(readerNode.lang);
                if (readerNode.xmlBase != null) xmlBaseStack.push(readerNode.xmlBase);
                elementStack.push((ReaderNode.ElementNode)readerNode);
            }

            @Override
            public void warning(SAXParseException ex) throws SAXParseException {
                nodeQueue.add(new ReaderNode.ExceptionNode(ruby, ex));
                throw ex;
            }
        };
        try {
            XMLReader reader = XMLReaderFactory.createXMLReader();
            reader.setContentHandler(handler);
            reader.setDTDHandler(handler);
            reader.setErrorHandler(handler);
            reader.setFeature("http://xml.org/sax/features/xmlns-uris", true);
            reader.setFeature("http://xml.org/sax/features/namespace-prefixes", true);
            reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            return reader;
        } catch (SAXException saxe) {
            throw RaiseException.createNativeRaiseException(ruby, saxe);
        }
    }
}
