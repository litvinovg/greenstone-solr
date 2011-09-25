/**********************************************************************
 *
 * GS2Analyzer.java 
 *
 * Copyright 2004 The New Zealand Digital Library Project
 *
 * A component of the Greenstone digital library software
 * from the New Zealand Digital Library Project at the
 * University of Waikato, New Zealand.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *********************************************************************/
package org.greenstone.LuceneWrapper;


import java.io.*;
import java.util.Set;

import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.standard.*;

import org.apache.lucene.analysis.ASCIIFoldingFilter;

import org.apache.lucene.util.Version;


class GS2Analyzer extends GS2StandardAnalyzer 
{
    
    static Version matchVersion = Version.LUCENE_24;


    public GS2Analyzer() 
    {
	super(matchVersion);
    }
    

    public GS2Analyzer(Set stopWords) 
    {
	super(matchVersion,stopWords);
    }


    public GS2Analyzer(String [] stopwords) 
    {
	super(matchVersion,StopFilter.makeStopSet(stopwords));
    }

  @Override
  protected TokenStreamComponents createComponents(final String fieldName, final Reader reader) {
    final StandardTokenizer src = new StandardTokenizer(matchVersion, reader);
    src.setMaxTokenLength(maxTokenLength);
    src.setReplaceInvalidAcronym(replaceInvalidAcronym);
    TokenStream tok = new StandardFilter(matchVersion, src);
    tok = new LowerCaseFilter(matchVersion, tok);
    tok = new StopFilter(matchVersion, tok, stopwords);

    // top it up with accent folding
    tok = new ASCIIFoldingFilter(tok);

    return new TokenStreamComponents(src, tok) {
      @Override
      protected boolean reset(final Reader reader) throws IOException {
        src.setMaxTokenLength(GS2Analyzer.this.maxTokenLength);
        return super.reset(reader);
      }
    };
  }

}


