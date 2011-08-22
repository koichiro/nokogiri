/**
 * (The MIT License)
 *
 * Copyright (c) 2011:
 *
 * * {Koichiro Ohba}[http://twitter.com/koichiroo]
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

package nokogiri.internals;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.util.StreamReaderDelegate;

/**
 *
 * @author Koichiro Ohba <koichiro@meadowy.org>
 */
public class NokogiriXmlStreamReader extends StreamReaderDelegate {
    
    private int depth;
    private String lang;
    private String xmlBase;

    public NokogiriXmlStreamReader(XMLStreamReader reader) {
        super(reader);
        depth = 0;
    }
    
    public int getDepth() {
        return depth;
    }
    
    public String getXMLBase() {
        return xmlBase;
    }
    
    public String getLang() {
        return lang;
    }

    private void resolveXMLLang() {
        String l = getParent().getAttributeValue("http://www.w3.org/XML/1998/namespace", "lang");
        if (l != null) lang = l;
    }

    private void resolveXMLBase() {
        // TODO: relative uri
        String b = getParent().getAttributeValue("http://www.w3.org/XML/1998/namespace", "base");
        if (b != null) xmlBase = b;
    }

    @Override
    public int next() throws XMLStreamException {
        switch (getParent().getEventType()) {
            case XMLStreamConstants.START_ELEMENT:
                depth++;
                break;
        }

        int result = getParent().next();

        switch (result) {
            case XMLStreamConstants.START_ELEMENT:
                resolveXMLLang();
                resolveXMLBase();
                break;
            case XMLStreamConstants.END_ELEMENT:
                depth--;
                break;
        }

        return result;
    }
}
