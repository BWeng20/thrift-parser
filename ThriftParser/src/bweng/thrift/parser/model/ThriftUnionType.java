/* Copyright (c) 2015-2017 Bernd Wengenroth
 * Licensed under the MIT License.
 * See LICENSE file for details.
 */
package bweng.thrift.parser.model;

import java.util.List;

/**
 * Part of the data model, representing a Thrift Union.
 * Unions are simliar to struct, but only one field can be set.
 */
public class ThriftUnionType extends ThriftType
{
    public List<ThriftField> fields_;

    @Override
    public String toString()
    {
        return "union " + name_fully_qualified_ + fields_.toString();
    }

}
