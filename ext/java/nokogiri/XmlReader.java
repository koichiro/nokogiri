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
import java.util.Stack;

import javax.xml.namespace.QName;
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
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.ByteList;
import org.jruby.util.IOInputStream;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import org.jruby.RubyHash;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;

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
    
    private XMLStreamReader reader;
    private int state;
    private int depth;
    private String lang;
    private String xmlBase;
    private int nodeType;
    private Document document;
    
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
        depth = -1;
        lang = "";
        nodeType = 0;
    }

    private void parse(ThreadContext context, IRubyObject in) {
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
        reader = this.createReader(ruby, stream);
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
        if (document == null) {
            document = ((XmlDocument) NokogiriService.XML_DOCUMENT_ALLOCATOR.allocate(ruby, getNokogiriClass(ruby, "Nokogiri::XML::Document"))).getDocument();
        }
        RubyArray array = RubyArray.newArray(ruby);
        if (reader.getEventType() != XMLStreamConstants.START_ELEMENT && reader.getEventType() != XMLStreamConstants.ATTRIBUTE)
            return array;
        int size = reader.getAttributeCount();
        if (size == 0) return array;
        for (int i=0; i< size; i++) {
            Attr attr = document.createAttributeNS(reader.getAttributeNamespace(i), reader.getAttributeLocalName(i));
            attr.setValue(reader.getAttributeValue(i));
            XmlAttr xmlAttr = (XmlAttr) NokogiriService.XML_ATTR_ALLOCATOR.allocate(ruby, getNokogiriClass(ruby, "Nokogiri::XML::Attr"));
            xmlAttr.setNode(ruby.getCurrentContext(), attr);
            array.append(xmlAttr);
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
        return stringOrNil(context.getRuntime(), xmlBase);
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
        return context.getRuntime().newFixnum(nodeType);
    }

    @JRubyMethod
    public IRubyObject inner_xml(ThreadContext context) {
        // TODO
        return context.getRuntime().newString();
    }
    
    @JRubyMethod
    public IRubyObject outer_xml(ThreadContext context) {
        // TODO
        return context.getRuntime().newString();
    }

    @JRubyMethod
    public IRubyObject lang(ThreadContext context) {
        return context.getRuntime().newString(lang);
    }

    @JRubyMethod
    public IRubyObject local_name(ThreadContext context) {
        return stringOrNil(context.getRuntime(), reader.getLocalName());
    }

    @JRubyMethod
    public IRubyObject name(ThreadContext context) {
        Ruby ruby = context.getRuntime();
        if (reader.isCharacters()) {
            return ruby.newString("#text");
        } else {
            QName qn = reader.getName();
            return stringOrNil(ruby, qn.getPrefix() + ":" + qn.getLocalPart());
        }
    }

    @JRubyMethod
    public IRubyObject namespace_uri(ThreadContext context) {
        return stringOrNil(context.getRuntime(), reader.getNamespaceURI());
    }

    @JRubyMethod
    public IRubyObject namespaces(ThreadContext context) {
        Ruby ruby = context.getRuntime();
        if (reader.isCharacters()) {
            return RubyHash.newHash(ruby);
        }
        RubyHash hash = RubyHash.newHash(ruby);
        for (int i=0; i < reader.getNamespaceCount(); i++) {
            IRubyObject k = stringOrBlank(ruby, "xmlns:" + reader.getNamespacePrefix(i));
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
    
    private void getXMLLang() {
        String l = reader.getAttributeValue("http://www.w3.org/XML/1998/namespace", "lang");
        if (l != null) lang = l;
    }
    
    private void getXMLBase() {
        String b = reader.getAttributeValue("http://www.w3.org/XML/1998/namespace", "base");
        if (b != null) xmlBase = b;
    }

    @JRubyMethod
    public IRubyObject read(ThreadContext context) {
        Ruby ruby = context.getRuntime();
        try {
            if (reader.hasNext() == false) {
                return ruby.getNil();
            }

            reader.next();

            switch (reader.getEventType()) {
                case XMLStreamConstants.START_DOCUMENT:
                    break;
                case XMLStreamConstants.START_ELEMENT:
                    getXMLLang();
                    getXMLBase();
                    depth++;
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    depth--;
                    break;
            }

            // skip unsupported node
            ReaderNode.ReaderNodeType type = dispatchNodeType(reader.getEventType());
            while (type == null) {
                if (reader.hasNext() == false)
                    return ruby.getNil();
                reader.next();
            }
            if (reader.isWhiteSpace()) {
                nodeType = ReaderNode.ReaderNodeType.SIGNIFICANT_WHITESPACE.getValue();
            } else {
                nodeType = type.getValue();
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
        return context.getRuntime().newString(reader.getText());
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

    protected XMLStreamReader createReader(final Ruby ruby, InputStream stream) {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        BufferedInputStream bstream = new BufferedInputStream(stream);
        try {
            return factory.createXMLStreamReader(bstream);
        } catch (javax.xml.stream.XMLStreamException e) {
            throw RaiseException.createNativeRaiseException(ruby, e);
        }
    }
}
