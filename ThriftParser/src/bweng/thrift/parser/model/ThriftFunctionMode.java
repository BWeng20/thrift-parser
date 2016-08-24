/* Copyright (c) 2015 Bernd Wengenroth
 * Licensed under the MIT License.
 * See LICENSE file for details.
 */
package bweng.thrift.parser.model;

/**
 * Describes mode of functions.
 */
public enum ThriftFunctionMode 
{
   EVENT,
   ONEWAY,
   ASYNC,
   DEFERRED,
   NONE
}
