/* Copyright (c) 2015 Bernd Wengenroth
 * Licensed under the MIT License.
 * See LICENSE file for details.
 */

package bweng.thrift.parser.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Part of the data model, representing a Scope (thrift file or package).
 */
public class ThriftScope extends ThriftObject 
{
    // Name of this scope (package or file)
    public String name_;
    // Full qualified name
    public String name_fully_qualified_;
    
    // Services that belong to this scope only.
    public List<ThriftService> services_ = new ArrayList<>();
    
    // Tyes that belong to this scope only.
    public List<ThriftType> types_ = new ArrayList<>();

}
