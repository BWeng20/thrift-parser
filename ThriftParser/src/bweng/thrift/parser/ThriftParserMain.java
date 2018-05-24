/* Copyright (c) 2015 Bernd Wengenroth
 * Licensed under the MIT License.
 * See LICENSE file for details.
 */
package bweng.thrift.parser;

import bweng.thrift.parser.model.ThriftDocument;
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
                ThriftModelGenerator gen = new ThriftModelGenerator(includeDirs);
                ThriftDocument doc = gen.loadDocument(gen.getPath(file) );
                gen.loadIncludes( doc );
                System.out.println(doc.toString() );
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
