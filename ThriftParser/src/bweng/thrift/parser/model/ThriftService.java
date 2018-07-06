/* Copyright (c) 2015-2018 Bernd Wengenroth
 * Licensed under the MIT License.
 * See LICENSE file for details.
 */
package bweng.thrift.parser.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Part of the data model, representing a Thrift Service.
 */
public class ThriftService extends ThriftObject
{
    public String name_;
    public String name_fully_qualified_;
    public List<ThriftFunction> functions_ = new ArrayList<>();

    /** Parent-Package or null [DAI Extension]. */
    public ThriftPackage package_;

    /** Optional: base service. */
    public ThriftServiceRef extended_service_;

    @Override
    public String toString()
    {
        return name_+functions_;
    }

    /**
     * Checks all definitions for validity.
     * @return true if all used types are valid.
     */
    @Override
    public boolean valid()
    {
        if ( extended_service_ != null &&
            (extended_service_.resolvedService_ == null || !extended_service_.resolvedService_.valid() ))
               return false;
        for ( ThriftFunction f : functions_ ) if (!f.valid()) return false;
        return true;
    }
}
