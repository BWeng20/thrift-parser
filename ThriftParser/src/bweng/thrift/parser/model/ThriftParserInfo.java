/* Copyright (c) 2015-2018 Bernd Wengenroth
 * Licensed under the MIT License.
 * See LICENSE file for details.
 */
package bweng.thrift.parser.model;

/**
 * Part of the data model, base class to store parser information.
 */
public class ThriftParserInfo
{
   // 0-based line of declaration.
   public int line_;
   public int column_;
}
