/* Copyright (c) 2015 Bernd Wengenroth
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
    public String path_;

    public Path ospath_;
    public ThriftDocument doc_;

}
