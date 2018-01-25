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
   PROPERTY_GET_SET,
   PROPERTY_GET_SET_CHANGED,
   PROPERTY_GET_CHANGED,
   NONE
}
