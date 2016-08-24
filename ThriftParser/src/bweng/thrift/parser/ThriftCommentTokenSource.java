/* Copyright (c) 2015 Bernd Wengenroth
 * Licensed under the MIT License.
 * See LICENSE file for details.
 */
package bweng.thrift.parser;

import java.util.ArrayList;
import org.antlr.runtime.CommonToken;
import org.antlr.runtime.Token;
import org.antlr.runtime.TokenSource;

/**
 * TokenSource that stores comments to add them later to Thrift elements.
 */
public class ThriftCommentTokenSource implements TokenSource 
{
  private final TokenSource source_;
  private final int tokenType_;
  private final int contentChannel_;
  private final StringBuilder collectedContent_ = new StringBuilder();
  private int currentline_ = -1;
 
  class CommentEntry
  {
    public int line;
    public String comment;
  };
  private final ArrayList<CommentEntry> comments_= new ArrayList<>(1000);
  
  public ThriftCommentTokenSource(TokenSource source, int contentChannel, int commmentTokenType ) 
  {
    this.source_ = source;
    this.contentChannel_ = contentChannel;
    this.tokenType_ = commmentTokenType;
  }
  
  private void addComment( )
  {
      String x = collectedContent_.toString();
     
      if ( x.length() > 0 )
      {
          collectedContent_.setLength(0);
          final int n = comments_.size();
          if ( n > 0 )
          {
              CommentEntry oe = comments_.get( n-1 );
              if ( oe.line == currentline_)
              {
                 oe.comment += ' ' + x;
                 return;
              }
          }
          CommentEntry ne = new CommentEntry();
          ne.comment = x;
          ne.line = currentline_;
          comments_.add( ne );
          
    // System.out.println( "+"+ne.line+":" + x);

      }
  }
 
  /**
   * Returns next token from the wrapped token source. Stores it
   * in a list if necessary.
   */
  @Override
  public Token nextToken() 
  {
    CommonToken nextToken = (CommonToken)source_.nextToken();
      
    if (nextToken.getType() == tokenType_ )
    {
        if ( currentline_ == nextToken.getLine() )
        {
            collectedContent_.append(' ');
        }
        else
        {
           addComment();
        }
        currentline_ = nextToken.getLine();
        collectedContent_.append(nextToken.getText());
    }
    else if ( contentChannel_ == nextToken.getChannel() )
    {
        addComment();
    }
    return nextToken;
  }

  public String collectComment(int line ) 
  {      
      String ct = "";
      CommentEntry oe= null;
      int bestIdx = -1;
      for (int i=0 ; i<comments_.size() ; ++i )
      {
          oe = comments_.get( i );
          if ( oe.line <= (line-2) )
          {
              bestIdx = i;
              ct = oe.comment;
          }
          else if (oe.line > line )
              break;
      }
      if ( bestIdx >= 0 )
          comments_.remove(bestIdx);
      return ct;
  }


  @Override
  public String getSourceName() {
    return "Collector " + source_.getSourceName();
  }
}
