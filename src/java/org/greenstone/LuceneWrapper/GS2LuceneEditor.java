/** @file GS2LuceneEditor.java
 *
 *  Provides a wrapper to the index/document editing features of Lucene. 
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
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Vector;

import org.apache.lucene.analysis.Analyzer;
//import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;

import org.apache.lucene.store.SimpleFSDirectory;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;


/** Contains methods for modifying a document that has previously been indexed
 *  into a Lucene database.
 *  @author John Thompson, DL Consulting Ltd.
 */
public class GS2LuceneEditor
{
    /** This is the main entry point to the editor and is responsible for 
     *  parsing the arguments and creating an instance of the editor class.
     *
     *  @param  args The arguments passed into the application as a string
     *               array
     *  @return An integer describing the exit state of the application
     *  @throws Exception on any fatal error state
     *
     *  @author John Thompson, DL Consulting Ltd.
     */
    static public void main (String args[])
        throws Exception
    {
        // Parse arguments
        int node_id = -1;
        String field = "";
        String index_path = "";
        String new_value = "";
        String old_value = "";

        for (int i = 0; i < args.length; i += 2)
            {
                if (args[i].equals("--index"))
                    {
                        index_path = args[i + 1];
                    }
                else if (args[i].equals("--nodeid"))
                    {
                        String temp = args[i + 1];
                        node_id = Integer.parseInt(temp);
                        temp = null; // Off to the gc with you!
                    }
                else if (args[i].equals("--field"))
                    {
                        field = args[i + 1];
                    }
                else if (args[i].equals("--oldvalue"))
                    {
                        old_value = args[i + 1];
                    }
                else if (args[i].equals("--newvalue"))
                    {
                        new_value = args[i + 1];
                    }
                else 
                    {
                        System.out.println("Error! Unknown argument: " + args[i]);
                        GS2LuceneEditor.printUsage();
                    }
            }

        // Check arguments
        if(index_path.equals(""))
            {
                System.out.println("Error! Missing index path");
                GS2LuceneEditor.printUsage();
            }
        if(field.equals(""))
            {
                System.out.println("Error! Missing field");
                GS2LuceneEditor.printUsage();
            }
        if(node_id == -1)
            {
                System.out.println("Error! Missing or invalid Node ID");
                GS2LuceneEditor.printUsage();
            }
        if(old_value.equals("") && new_value.equals(""))
            {
                System.out.println("Error! No modification requested");
                GS2LuceneEditor.printUsage(); 
            }


        // Instantiate editor, and perform the edit
        GS2LuceneEditor editor = new GS2LuceneEditor(index_path);
        editor.editIndex(node_id, field, old_value, new_value);
        editor.destroy();
        editor = null;
    }
    /** main() **/

    /** **/
    private boolean debug = true;

    /** **/
    private GS2IndexModifier index_modifier;

    /** Constructor which takes the path to the Lucene index to be edited.
     *
     *  @param  index_path The full path to the index directory as a String
     *
     *  @author John Thompson, DL Consulting Ltd.
     */
    public GS2LuceneEditor(String index_path)
        throws IOException
    {
        Analyzer analyzer = new GS2Analyzer();
        // create an index in /tmp/index, overwriting an existing one:
        index_modifier = new GS2IndexModifier(index_path, analyzer);
    }
    /** GS2LuceneEditor **/

    /**
     */
    public void debug(String message)
    {
        if(debug)
            {
                System.err.println(message);
            }
    }
    /** debug() **/

    /** Destructor which unallocates connection to Lucene.
     */
    public void destroy()
        throws IOException
    {
        index_modifier.close();
        index_modifier = null;
    }

    /** Make an edit to a Lucene index.
     *
     *  @param  oid       The unique identifier of a Lucene document as an
     *                    integer
     *  @param  field     The field to be modified as a String
     *  @param  old_value The existing value to be changed or removed as a 
     *                    String
     *  @param  old_value The replacement value to be changed or added as a 
     *                    String
     *
     *  @author John Thompson, DL Consulting Ltd.
     */
    public void editIndex(int node_id, String field, String old_value, String new_value)
        throws IOException
    {
        debug("GS2LuceneEditor.editIndex(" + node_id + ",'" + field + "','" + old_value + "','" + new_value + "')");
        debug("- Initial number of documents in index: " + index_modifier.numDocs());
        // Retrieve the document requested
        int doc_num = index_modifier.getDocNumByNodeID(node_id);
        if (doc_num != -1)
            {
                debug("* Found document #" + doc_num);
                // Retrieve the actual document
                Document document = index_modifier.document(doc_num);
                // Remove the document from the index before modifying
                index_modifier.deleteDocument(doc_num);
                debug("* Removed document from index prior to editing");
                // Retrieve the requested fields values, and turn it into a 
                // vector
                debug("* Modifying the value of the field: " + field);
                doEdit(document, field, old_value, new_value);

                // We have to do a similar modification to the ZZ field
                // too
                debug("* Modifying the value of the field: ZZ");
                doEdit(document, "ZZ", old_value, new_value);

                // Re-index document
                index_modifier.addDocument(document);
                debug("* Reindexing modified document");
            }
        else
            {
                debug("- No such document!");
                Document document = new Document();

                // Retrieve the requested fields values, and turn it into a 
                // vector
                debug("* Adding the value to the field: " + field);
                doEdit(document, field, old_value, new_value);

                // We have to do a similar modification to the ZZ field
                // too
                debug("* Adding the value to the field: ZZ");
                doEdit(document, "ZZ", old_value, new_value);

                // We also have to initialize the nodeId value
		// changed to use docOID --kjdon
                document.add(new Field("docOID", String.valueOf(node_id), Field.Store.YES, Field.Index.ANALYZED));

                // Re-index document
                index_modifier.addDocument(document);
                debug("* Indexing new document");
            }


    }
    /** editIndex() **/

    /**
     */
    protected void doEdit(Document document, String field, String old_value, String new_value)
    {
        if (debug)
            {
                debug("GS2LuceneEditor.doEdit(Document, \"" + field + "\", \"" + old_value + "\", \"" + new_value + "\")");
            }

        String values_raw[] = document.getValues(field);
        if(values_raw != null)
            {
                Vector values = new Vector(Arrays.asList(values_raw));
                // Remove all the values for this field (no other safe way to
                // do this
                document.removeFields(field);
                // DEBUG
                if (debug)
                    {
                        debug("- Before modification:");
                        for(int i = 0; i < values.size(); i++)
                            {
                                debug("\t" + field + "[" + i + "]: " + values.get(i));
                            }
                    }
                // If old_value is set, remove it from the values array
                if(!old_value.equals(""))
                    {
                        // Remove all occurances of this metadata - this means
                        // it becomes a bit dangerous to have multiple pieces
                        // of metadata with exactly the same metadata - but
                        // this is only for indexing purposes so its not so
                        // bad.
                        while(values.contains(old_value))
                            {
                                values.remove(old_value);
                            }
                    }                
                // If new_value is set, add it to the values array
                if(!new_value.equals("") && !values.contains(new_value))
                    {
                        values.add(new_value);
                    }
                // DEBUG
                if(debug)
                    {
                        debug("- After modification:");
                        for(int i = 0; i < values.size(); i++)
                            {
                                debug("\t" + field + "[" + i + "]: " + values.get(i));
                            }
                    }
                // Add all the values for this field
                for(int i = 0; i < values.size(); i++)
                    {
                        document.add(new Field(field, (String)values.get(i), Field.Store.YES, Field.Index.ANALYZED));
                    }
                values.clear();
                values = null;
            }
        // We may be adding a value to a field that current has no values
        else if (!new_value.equals(""))
            {
                Vector values = new Vector();
                values.add(new_value);
                // DEBUG
                if(debug)
                    {
                        debug("- Brand spanking new values:");
                        for(int i = 0; i < values.size(); i++)
                            {
                                debug("\t" + field + "[" + i + "]: " + values.get(i));
                            }
                    }
                // Add all the values for this field
                for(int i = 0; i < values.size(); i++)
                    {
                        document.add(new Field(field, (String)values.get(i), Field.Store.YES, Field.Index.ANALYZED));
                    }
                values.clear();
                values = null;
            }
        // Can't do a removal unless something exists
        else
            {
                debug("- No such field for this document: " + field);
            }
        values_raw = null;
    }
    /** doEdit() **/

    /**
     */
    static public void printUsage()
    {
        System.out.println("usage: GS2LuceneEditor --index <path> --nodeid <int> --field <string>");
        System.out.println("                      [--oldvalue <string>] [--newvalue <string>]");
        System.out.println("");
        System.out.println("where:");
        System.out.println("  index    - is the full path to the directory containing the directory");
        System.out.println("             to edit, including the level (ie didx, sidx)");
        System.out.println("  nodeid   - the unique identifier of the document to change. This is the");
        System.out.println("             same as the docnum in the GDBM");
        System.out.println("  field    - the two letter code of the metadata field to edit. These can");
        System.out.println("             found in the build.cfg file. ZZ is not a valid field as it");
        System.out.println("             is handled as a special case");
        System.out.println("  oldvalue - the current value of the metadata field if it is to be");
        System.out.println("             replaced or removed");
        System.out.println("  newvalue - the new value for the metadata field if it is to be replaced");
        System.out.println("             or added");
        System.out.println("");
        System.exit(0);
    }
    /** printUsage() **/

}
/** class GS2LuceneEditor **/
