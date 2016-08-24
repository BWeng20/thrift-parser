/* Copyright (c) 2015 Bernd Wengenroth
 * Licensed under the MIT License.
 * See LICENSE file for details.
 */
package bweng.thrift.parser.model;

/**
 * Part of the data model, representing a Thrift Include.
 */
public class ThriftInclude extends ThriftParserInfo
{
    public String path_;

    public String ospath_;  
    public ThriftDocument doc_;
    
}
