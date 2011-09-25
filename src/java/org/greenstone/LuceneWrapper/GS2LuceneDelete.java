/** @file GS2LuceneDelete.java
 *
 *  Provides a wrapper to the document deleting features of Lucene. 
 *
 *  This java application makes use of the existing Lucene class IndexModifier 
 *  to access and make changes to the information stored about documents in a
 *  Lucene database. This is an essential component of the IncrementalBuilder
 *  PERL module, and endevours to make editing the text and metadata of 
 *  documents without having to rebuild the entire collection a reality (in
 *  other words, true incremental/dynamic building).
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
import java.io.File;
//import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;

import org.apache.lucene.store.SimpleFSDirectory;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;


/** Contains methods for deleting a document that has previously been indexed
 *  into a Lucene database.
 *  @author John Thompson, DL Consulting Ltd. (unless stated otherwise)
 */
public class GS2LuceneDelete
{
    /** This is the main entry point to the deletor and is responsible for 
     *  parsing the arguments and creating an instance of the deletor class.
     *
     *  @param  args The arguments passed into the application as a string
     *               array
     *  @return An integer describing the exit state of the application
     *  @throws Exception on any fatal error state
     */
    static public void main (String args[])
        throws Exception
    {
        // Parse arguments
        String index_path = "";
        int node_id = -1;

        for (int i = 0; i < args.length; i += 2)
        {
	    if (args[i].equals("--index"))
	    {
		index_path = args[i + 1];
	    }
	    else if (args[i].equals("--nodeid"))
	    {
		node_id = Integer.parseInt(args[i + 1]);
	    }
	    else 
	    {
		System.out.println("Error! Unknown argument: " + args[i]);
		GS2LuceneDelete.printUsage();
		System.exit(0);
	    }
	}

        // Check arguments
        if (index_path.equals(""))
        {
	    System.out.println("Error! Missing index path");
	    GS2LuceneDelete.printUsage();
	    System.exit(0);
	}
        if (node_id == -1)
        {
	    System.out.println("Error! Missing or invalid Node ID");
	    GS2LuceneDelete.printUsage();
	    System.exit(0);
	}

        // Instantiate deletor, and perform the delete
        GS2LuceneDelete deletor = new GS2LuceneDelete(index_path);
        deletor.deleteDocument(node_id);
        deletor.destroy();
        deletor = null;
    }


    /** Display program usage message.
     */
    static public void printUsage()
    {
        System.out.println("usage: GS2LuceneDelete --index <path> --nodeid <int>");
        System.out.println("");
        System.out.println("where:");
        System.out.println("  index    - is the full path to the directory containing the directory");
        System.out.println("             to edit, including the level (ie didx, sidx)");
        System.out.println("  nodeid   - the unique identifier of the document to delete. This is the");
        System.out.println("             same as the docnum in the GDBM");
        System.out.println("");
    }


    /** **/
    private boolean debug = true;

    /** **/
    private IndexWriter index_writer = null;


    /** Constructor which takes the path to the Lucene index to be edited.
     *
     *  @param  index_path The full path to the index directory as a String
     */
    public GS2LuceneDelete(String index_path)
        throws IOException
    {
	SimpleFSDirectory index_path_dir = new SimpleFSDirectory(new File(index_path));
	index_writer = new IndexWriter(index_path_dir, new GS2Analyzer(), 
				       MaxFieldLength.UNLIMITED);
    }


    /** When called prints a debug message but only if debugging is enabled.
     */
    public void debug(String message)
    {
        if (debug)
        {
	    System.err.println(message);
	}
    }


    /** Destructor which unallocates connection to Lucene.
     */
    public void destroy()
        throws IOException
    {
	index_writer.close();
	index_writer = null;
    }


    /** Delete the indicated document from the Lucene index. This process is
     *  very similar to the initial step of index editing.
     *
     *  @param  node_id   The unique identifier of a Lucene document as an
     *                    integer
     */
    public void deleteDocument(int node_id)
        throws IOException
    {
        debug("GS2LuceneDelete.deleteDocument(" + node_id + ")");
        debug("- Initial number of documents in index: " + index_writer.numDocs());
	index_writer.deleteDocuments(new Term("nodeid", "" + node_id));
        debug("- Final number of documents in index: " + index_writer.numDocs());
    }
}
