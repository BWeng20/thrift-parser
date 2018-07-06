/* Copyright (c) 2015-2018 Bernd Wengenroth
 * Licensed under the MIT License.
 * See LICENSE file for details.
 */
package bweng.thrift.parser;

import bweng.thrift.parser.model.ThriftDocument;
import bweng.thrift.parser.model.ThriftFunction;
import bweng.thrift.parser.model.ThriftPackage;
import bweng.thrift.parser.model.ThriftService;
import java.io.IOException;
import java.util.ArrayList;
import org.antlr.runtime.RecognitionException;

/**
 * Test tool to call the parser from command line.
 */
public class ThriftParserMain {

    public static void help()
    {
        System.err.println("Missing or wrong arguments\nThriftParser.jar [-i includepath] FILE");
        System.exit(-1);
    }

    public static void main(String[] args) throws RecognitionException
    {
        long time = System.currentTimeMillis();
        try
        {
            String file = null;
            ArrayList<String> includeDirs = new ArrayList<>();

            for (int i = 0 ; i<args.length ; ++i )
            {
                String a = args[i];
                if( a.equalsIgnoreCase("-i") )
                {
                     ++i;
                     if ( i< args.length )
                        includeDirs.add( args[i]);
                     else
                        help();

                }
                else
                {
                    file = a;
                }
            }
            if ( file != null )
            {
                String fupper = file.toUpperCase();
                System.out.println( "Loading " + file );
                ThriftModelGenerator gen = new ThriftModelGenerator(includeDirs);
                if ( fupper.endsWith(".ZIP") || fupper.endsWith(".JAR") )
                {
                   ThriftDocument doc = gen.loadZipArchive(gen.getPath(file) );
                   if ( doc != null )
                   {
                        System.out.println("Finished. Time needed "+(System.currentTimeMillis()-time)+"ms"  );
                        System.out.println("  Name: "+doc.name_ );
                        System.out.println("  Complete Name: "+doc.name_fully_qualified_ );
                        System.out.println("  Validity: "+doc.valid()  );
                        System.out.println("  services "+ doc.all_services_.size()+"\n  types "+doc.all_types_.size() );
                        System.out.println("  unresolved types: "+doc.unresolved_types_.size() );
                        System.out.println("  unresolved services: "+doc.unresolved_services_.size() );

                        if ( doc.all_packages_ != null && !doc.all_packages_.isEmpty() )
                        {
                            System.out.println("== Leave Packages ===========");
                            for ( ThriftPackage p : doc.all_packages_ )
                            {
                                if ( p.subpackages_ == null || p.subpackages_.isEmpty() )
                                    System.out.println(" "+p.name_fully_qualified_+(p.valid()?"": " Not valid"));
                            }
                        }

                        if ( !doc.all_services_.isEmpty() )
                        {
                            System.out.println("== Services ===========");
                            for ( ThriftService s : doc.all_services_ )
                            {
                                boolean v = s.valid();
                                System.out.println(" "+s.name_fully_qualified_+" "+s.functions_.size()+" Methods "+(v?"": " [Not valid]"));
                                if ( !v )
                                {
                                    for ( ThriftFunction f : s.functions_ )
                                    {
                                        System.out.println("  > "+f.name_+(v?"": " [Not valid]"));
                                    }
                                }
                            }
                        }
                   }
                }
                else
                {
                   ThriftDocument doc = gen.loadDocument(ThriftModelGenerator.getPath(file) );
                   gen.loadIncludes( doc, true );
                   System.out.println(doc.toString() );
                }
            }
            else
            {
                help();
            }

        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
    }
}
