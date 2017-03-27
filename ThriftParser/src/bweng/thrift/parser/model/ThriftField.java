/* Copyright (c) 2015 Bernd Wengenroth
 * Licensed under the MIT License.
 * See LICENSE file for details.
 */
package bweng.thrift.parser.model;

/**
 * Part of the data model, representing a Thrift Field (e.g. as parameter in a Function).
 */
public final class ThriftField extends ThriftObject
{
    public String name_;
    public int    id_;
    public ThriftType type_;

    @Override
    public String toString()
    {
        return ""+id_+":"+
               ((type_ != null)
                ? ((type_.name_!= null) ?type_.name_ : type_.toString())
                : "?" ) + ' ' + name_;
    }

}
