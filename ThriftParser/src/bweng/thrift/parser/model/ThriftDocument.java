/* Copyright (c) 2015 Bernd Wengenroth
 * Licensed under the MIT License.
 * See LICENSE file for details.
 */
package bweng.thrift.parser.model;

import java.util.List;
import java.util.Map;

/**
 * Part of the data model, representing a Thrift Document.
 */
public class ThriftDocument extends ThriftScope
{   
    // Operating system dependend path where the document was loaded from or null.
    public String ospath_;
       
    public List<ThriftInclude> includes_;
       
    // All packages (also all sub-packages) [DAI Extension].
    public List<ThriftPackage> all_packages_;   

    // All servives (also from all sub-packages)
    public List<ThriftService> all_services_;
    
    // All servives (also from all sub-packages) by fully qualified name
    public Map<String,ThriftService> all_services_byname_;
    
    // All types defined in this document
    public Map<String, ThriftType> all_types_;    

    // All yet unresolved types in this document
    public Map<String, ThriftTypeRef> unresolved_types_;    

    // All yet unresolved services in this document
    public Map<String, ThriftServiceRef> unresolved_services_;    

    @Override
    public String toString()
    {
        return ""+(0<all_packages_.size()?all_packages_:all_services_);
    }
    
}
