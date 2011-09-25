/** @file GS2IndexModifier.java
 *
 *  Extends the standard IndexModifier so that a use can retrieve documents and
 *  the underlying IndexReader and IndexWriter. 
 *
 *  Addresses the problem that the Lucene standard IndexModifier can't actually
 *  return a specified Document object, nor does it expose the IndexReader and
 *  IndexWriter so the user can access their methods instead.
 *
 *  A component of the Greenstone digital library software from the New Zealand 
 *  Digital Library Project at the University of Waikato, New Zealand.
 *
 *  This program is free software; you can redistribute it and/or modify it 
 *  under the terms of the GNU General Public License as published by the Free 
 *  Software Foundation; either version 2 of the License, or (at your option) 
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for 
 *  more details.
 *
 *  You should have received a copy of the GNU General Public License along 
 *  with this program; if not, write to the Free Software Foundation, Inc., 675
 *  Mass Ave, Cambridge, MA 02139, USA.
 *
 *  Copyright (c) 2006 DL Consulting Ltd., New Zealand
 */

package org.greenstone.LuceneWrapper;

import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermPositions;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.IndexSearcher;

/**
 */
public class GS2IndexModifier
    extends IndexModifier
{
    /** **/
    private boolean debug = true;

    /**
     */
    public GS2IndexModifier (String dir_name, Analyzer analyzer)
        throws IOException
    {
        super(dir_name, analyzer, false);
    }
    /** GS2IndexModifier() **/

    /**
     */
    protected void debug(String message)
    {
        if(debug)
            {
                System.err.println(message);
            }
    }
    /** debug() **/

    /**
     */
    public Document document(int n)
        throws IOException
    {
        // Ensure the reader exists
        createIndexReader();
        return indexReader.document(n);
    }
    /** document() **/

    /**
     */
    public int getDocNumByNodeID(int node_id)
        throws IOException
    {
        debug("GS2IndexModifier.getDocument(" + node_id + ")");
        int doc_num = -1;
        // Create a new term to encapsulate this node id
	// was nodeID, now using docOID --kjdon
        Term term = new Term("docOID", String.valueOf(node_id));
        debug("Searching using term: " + term.toString());
        // Ensure the indexReader exists
        createIndexReader();
        // We can return an enumeration of docNums which contains this term
        TermPositions term_positions = indexReader.termPositions(term);
        // If the size of term_positions is not 1 then somethings gone wrong in
        // that the original terms did not uniquely identify a document.
        if (term_positions.next())
            {
                doc_num = term_positions.doc();
            }
        else
            {
                debug("Term doesn't exists in index");
            }
        term_positions.close();
        // To the garbage collection with thee
        term_positions = null;
        term = null;
        // Done
        return doc_num;
    }
    /** getDocumentByNodeID() **/

    /**
     */
    public IndexReader getIndexReader()
    {
        return indexReader;
    }
    /** getIndexReader() **/

    /**
     */
    public IndexWriter getIndexWriter()
    {
        return indexWriter;
    }
    /** getIndexWriter() **/
}
/** GS2IndexModifier **/
