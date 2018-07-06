/* Copyright (c) 2015-2018 Bernd Wengenroth
 * Licensed under the MIT License.
 * See LICENSE file for details.
 */
package bweng.thrift.parser.model;

import java.util.Map;

/**
 * Part of the data model,
 * base class for Thrift components that possibly have version informations.
 */
public class ThriftObject extends ThriftParserInfo
{
    /** The full comment text. */
    public String  comment_;

    /**
     * Version from @version annotation if available.
     * Use regular expression to detect it:
     *  <pre>@version\s+([0-9\.]+)</pre><br>
     */
    public String  version_;

    /**
     * Text from comments starting with "@".<br>
     * Contains also "version" is available.
     * The key is the annotation without leading '@'.<br>
     * The value is the trimmed annotation text until the next newline.
     */
    public Map<String,String>  annotations_;

    // True if the object is marked with @deprecated
    public boolean deprecated_ = false;

    private ThriftDocument document_;

    public final ThriftDocument getDocument()
    {
       return document_;
    }

    public final void setDocument(ThriftDocument doc)
    {
       document_ = doc;
    }

    /**
     * Verify if this object is functional.
     * On this level no meaningful verification is possible,
     * but inheritances may implement checks based on their data.
     *
     * @return true - inheritances may return different values.
     */
    public boolean valid()
    {
        return true;
    }

}
