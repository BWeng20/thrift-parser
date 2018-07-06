/* Copyright (c) 2015-2018 Bernd Wengenroth
 * Licensed under the MIT License.
 * See LICENSE file for details.
 */
package bweng.thrift.parser.model;

import java.nio.file.Path;

/**
 * Part of the data model, representing a Thrift Include.
 */
public class ThriftInclude extends ThriftParserInfo
{
    /** The given path from thrift include directive. */
    public String path_;

    /** The resolved path, based on the path of the including document. */
    public Path ospath_;

    /** The document. */
    public ThriftDocument doc_;

}
