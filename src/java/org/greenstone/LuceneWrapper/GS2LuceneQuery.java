/**********************************************************************
 *
 * GS2LuceneQuery.java 
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
import java.util.*;
import java.util.regex.*;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.BooleanQuery.TooManyClauses;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Hit;
import org.apache.lucene.search.Hits;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermRangeFilter;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopFieldDocs;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

public class GS2LuceneQuery extends SharedSoleneQuery
{
    protected String full_indexdir="";

    protected Sort sorter=new Sort();
    protected Filter filter = null;

    protected static Version matchVersion = Version.LUCENE_24;

    protected QueryParser query_parser = null;
    protected QueryParser query_parser_no_stop_words = null;
    protected Searcher searcher = null;
    protected IndexReader reader = null;

    public GS2LuceneQuery() {
	super();

	// Create one query parser with the standard set of stop words, and one with none

	query_parser = new QueryParser(matchVersion, TEXTFIELD, new GS2Analyzer()); // uses built-in stop_words_set
    	query_parser_no_stop_words = new QueryParser(matchVersion, TEXTFIELD, new GS2Analyzer(new String[] { }));
    }
    
    
    public boolean initialise() {

	if (!super.initialise()) {
	    return false;
	}


    	if (full_indexdir==null || full_indexdir.length()==-1){
	    utf8out.println("Index directory is not indicated ");
	    utf8out.flush();
	    return false;
    	}

        try {
	    Directory full_indexdir_dir = FSDirectory.open(new File(full_indexdir));
    	    searcher = new IndexSearcher(full_indexdir_dir,true);
    	    reader = ((IndexSearcher) searcher).getIndexReader();
	    
	}
	catch (IOException exception) {
    	    exception.printStackTrace();
	    return false;
    	}
	return true;

    }

    public void setIndexDir(String full_indexdir) {
	this.full_indexdir = full_indexdir;
    }

    public void setSortField(String sort_field) {
	super.setSortField(sort_field);

	if (sort_field == null) {
	    this.sorter = new Sort();
	} else {
	    this.sorter = new Sort(new SortField(sort_field,SortField.STRING)); // **** can do better than this?!?
	}
    }

    public void setFilterString(String filter_string) {
	super.setFilterString(filter_string);
	this.filter = parseFilterString(filter_string);
    }

    public Filter getFilter() {
	return this.filter;
    }

    
    public LuceneQueryResult runQuery(String query_string) {
	
	if (query_string == null || query_string.equals("")) {
	    utf8out.println("The query word is not indicated ");
	    utf8out.flush();
	    return null;
	}

 	LuceneQueryResult lucene_query_result=new LuceneQueryResult();
	lucene_query_result.clear();
	    
	try {    	        
	    Query query_including_stop_words = query_parser_no_stop_words.parse(query_string);
	    query_including_stop_words = query_including_stop_words.rewrite(reader);
		
	    // System.err.println("********* query_string " + query_string + "****");

	    Query query = parseQuery(reader, query_parser, query_string, fuzziness);
	    query = query.rewrite(reader);
		
	    // Get the list of expanded query terms and their frequencies 
	    // num docs matching, and total frequency	    
	    HashSet terms = new HashSet();
	    query.extractTerms(terms);

	    HashMap doc_term_freq_map = new HashMap();
	    
	    Iterator iter = terms.iterator();
	    while (iter.hasNext()) {
		    
		Term term = (Term) iter.next();
		
		// Get the term frequency over all the documents
		TermDocs term_docs = reader.termDocs(term);
		int term_freq = 0;
		int match_docs = 0;
		while (term_docs.next())
		{
		    if (term_docs.freq() != 0)
		    {
			term_freq += term_docs.freq();
			match_docs++;

			// Calculate the document-level term frequency as well
			Integer lucene_doc_num_obj = new Integer(term_docs.doc());
			int doc_term_freq = 0;
                        if (doc_term_freq_map.containsKey(lucene_doc_num_obj))
			{
			    doc_term_freq = ((Integer) doc_term_freq_map.get(lucene_doc_num_obj)).intValue();
			}
			doc_term_freq += term_docs.freq();

			doc_term_freq_map.put(lucene_doc_num_obj, new Integer(doc_term_freq));
		    }
		}

		// Create a term 
		lucene_query_result.addTerm(term.text(), term.field(), match_docs, term_freq);
	    }
	
	    // Get the list of stop words removed from the query
	    HashSet terms_including_stop_words = new HashSet();
	    query_including_stop_words.extractTerms(terms_including_stop_words);
	    Iterator terms_including_stop_words_iter = terms_including_stop_words.iterator();
	    while (terms_including_stop_words_iter.hasNext()) {
		Term term = (Term) terms_including_stop_words_iter.next();
		if (!terms.contains(term)) {
		    lucene_query_result.addStopWord(term.text());
		}
	    }
	    
	    // do the query
	    // Simple case for getting all the matching documents
	    if (end_results == Integer.MAX_VALUE) {
		// Perform the query (filter and sorter may be null)
		TopFieldDocs hits = searcher.search(query, filter, end_results, sorter);
		lucene_query_result.setTotalDocs(hits.totalHits);

		// Output the matching documents
		lucene_query_result.setStartResults(start_results);
		lucene_query_result.setEndResults(hits.totalHits);

		for (int i = start_results; i <= hits.totalHits; i++) {
		    int lucene_doc_num = hits.scoreDocs[i - 1].doc;
		    Document doc = reader.document(lucene_doc_num);
		    int doc_term_freq = 0;
		    Integer doc_term_freq_object = (Integer) doc_term_freq_map.get(new Integer(lucene_doc_num));
		    if (doc_term_freq_object != null)
		    {
			doc_term_freq = doc_term_freq_object.intValue();
		    }
		    lucene_query_result.addDoc(doc.get("docOID").trim(), hits.scoreDocs[i-1].score, doc_term_freq);
		}
	    }

	    // Slightly more complicated case for returning a subset of the matching documents
	    else {
		// Perform the query (filter may be null)
		TopFieldDocs hits = searcher.search(query, filter, end_results, sorter);
		lucene_query_result.setTotalDocs(hits.totalHits);
		
		lucene_query_result.setStartResults(start_results);
		lucene_query_result.setEndResults(end_results < hits.scoreDocs.length ? end_results: hits.scoreDocs.length);

		// Output the matching documents
		for (int i = start_results; (i <= hits.scoreDocs.length && i <= end_results); i++) {
		    int lucene_doc_num = hits.scoreDocs[i - 1].doc;
		    Document doc = reader.document(lucene_doc_num);
		    int doc_term_freq = 0;
		    Integer doc_term_freq_object = (Integer) doc_term_freq_map.get(new Integer(lucene_doc_num));
		    if (doc_term_freq_object != null)
		    {
			doc_term_freq = doc_term_freq_object.intValue();
		    }
		    lucene_query_result.addDoc(doc.get("docOID").trim(), hits.scoreDocs[i-1].score, doc_term_freq);
		}
	    }
	}
	
	catch (ParseException parse_exception) {
	    lucene_query_result.setError(LuceneQueryResult.PARSE_ERROR);
	}
	catch (TooManyClauses too_many_clauses_exception) {
	    lucene_query_result.setError(LuceneQueryResult.TOO_MANY_CLAUSES_ERROR);
	}
	catch (IOException exception) {
	    lucene_query_result.setError(LuceneQueryResult.IO_ERROR);
	    exception.printStackTrace();
	}
	catch (Exception exception) {
	    lucene_query_result.setError(LuceneQueryResult.OTHER_ERROR);
	    exception.printStackTrace();
	}
	return lucene_query_result;
    }

    public void setDefaultConjunctionOperator(String default_conjunction_operator) {
	super.setDefaultConjunctionOperator(default_conjunction_operator);

	if (default_conjunction_operator.equals("AND")) {
	    query_parser.setDefaultOperator(query_parser.AND_OPERATOR);
	    query_parser_no_stop_words.setDefaultOperator(query_parser.AND_OPERATOR);
	} else { // default is OR
	    query_parser.setDefaultOperator(query_parser.OR_OPERATOR);
	    query_parser_no_stop_words.setDefaultOperator(query_parser.OR_OPERATOR);
	}
    }
     
       
    public void cleanUp() {
	super.cleanUp();
	try {
	    if (searcher != null) {
		searcher.close();
	    }
	} catch (IOException exception) {
	    exception.printStackTrace();
	}
    }


    protected Query parseQuery(IndexReader reader, QueryParser query_parser, String query_string, String fuzziness)
	throws java.io.IOException, org.apache.lucene.queryParser.ParseException
    {
	// Split query string into the search terms and the filter terms
	// * The first +(...) term contains the search terms so count
	//   up '(' and stop when we finish matching ')'
	int offset = 0;
	int paren_count = 0;
	boolean seen_paren = false;
	while (offset < query_string.length() && (!seen_paren || paren_count > 0)) {
	    if (query_string.charAt(offset) == '(') {
		paren_count++;
		seen_paren = true;
	    }
	    if (query_string.charAt(offset) == ')') {
		paren_count--;
	    }
	    offset++;
	}
	String query_prefix = query_string.substring(0, offset);
	String query_suffix = query_string.substring(offset);
	
	///ystem.err.println("Prefix: " + query_prefix);
	///ystem.err.println("Suffix: " + query_suffix);
	
	Query query = query_parser.parse(query_prefix);
	query = query.rewrite(reader);
	
	// If this is a fuzzy search, then we need to add the fuzzy
	// flag to each of the query terms
	if (fuzziness != null && query.toString().length() > 0) {
	    
	    // Revert the query to a string
	    System.err.println("Rewritten query: " + query.toString());
	    // Search through the string for TX:<term> query terms
	    // and append the ~ operator. Note that this search will
	    // not change phrase searches (TX:"<term> <term>") as
	    // fuzzy searching is not possible for these entries.
	    // Yahoo! Time for a state machine!
	    StringBuffer mutable_query_string = new StringBuffer(query.toString());
	    int o = 0; // Offset
	    // 0 = BASE, 1 = SEEN_T, 2 = SEEN_TX, 3 = SEEN_TX:
	    int s = 0; // State
	    while(o < mutable_query_string.length()) {
		char c = mutable_query_string.charAt(o);
		if (s == 0 && c == TEXTFIELD.charAt(0)) {
		    ///ystem.err.println("Found T!");
		    s = 1;
		}
		else if (s == 1) {
		    if (c == TEXTFIELD.charAt(1)) {
			///ystem.err.println("Found X!");
			s = 2;
		    }
		    else {
			s = 0; // Reset
		    }
		}
		else if (s == 2) {
		    if (c == ':') {
			///ystem.err.println("Found TX:!");
			s = 3;
		    }
		    else {
			s = 0; // Reset
		    }
		}
		else if (s == 3) {
		    // Don't process phrases
		    if (c == '"') {
			///ystem.err.println("Stupid phrase...");
			s = 0; // Reset
		    }
		    // Found the end of the term... add the
		    // fuzzy search indicator
		    // Nor outside the scope of parentheses
		    else if (Character.isWhitespace(c) || c == ')') {
			///ystem.err.println("Yahoo! Found fuzzy term.");
			mutable_query_string.insert(o, '~' + fuzziness);
			o++;
			s = 0; // Reset
		    }
		}
		o++;
	    }
	    // If we were in the state of looking for the end of a
	    // term - then we just found it!
	    if (s == 3) {
		    
		mutable_query_string.append('~' + fuzziness);
	    }
	    // Reparse the query
	    ///ystem.err.println("Fuzzy query: " + mutable_query_string.toString() + query_suffix);
	    query = query_parser.parse(mutable_query_string.toString() + query_suffix);
	}
	else {
	    query = query_parser.parse(query_prefix + query_suffix);
	}
	
	return query;
    }

    protected Filter parseFilterString(String filter_string)
    {
	Filter result = null;
	Pattern pattern = Pattern.compile("\\s*\\+(\\w+)\\:([\\{\\[])(\\d+)\\s+TO\\s+(\\d+)([\\}\\]])\\s*");
	Matcher matcher = pattern.matcher(filter_string);
	if (matcher.matches()) {
	    String field_name = matcher.group(1);
	    boolean include_lower = matcher.group(2).equals("[");
	    String lower_term = matcher.group(3);
	    String upper_term = matcher.group(4);
	    boolean include_upper = matcher.group(5).equals("]");
	    result = new TermRangeFilter(field_name, lower_term, upper_term, include_lower, include_upper);
	}
	else {
	    System.err.println("Error: Could not understand filter string \"" + filter_string + "\"");
	}
	return result;
    }
    

    /** command line program and auxiliary methods */

    // Fairly self-explanatory I should hope
    static protected boolean query_result_caching_enabled = false;


    static public void main (String args[])
    {
	if (args.length == 0) {
	    System.out.println("Usage: GS2LuceneQuery <index directory> [-fuzziness value] [-filter filter_string] [-sort sort_field] [-dco AND|OR] [-startresults number -endresults number] [query]");
	    return;
	}

	try {
	    String index_directory = args[0];
	    
	    GS2LuceneQuery queryer = new GS2LuceneQuery();
	    queryer.setIndexDir(index_directory);

	    // Prepare the index cache directory, if query result caching is enabled
	    if (query_result_caching_enabled) {
		// Make the index cache directory if it doesn't already exist
		File index_cache_directory = new File(index_directory, "cache");
		if (!index_cache_directory.exists()) {
		    index_cache_directory.mkdir();
		}

		// Disable caching if the index cache directory isn't available
		if (!index_cache_directory.exists() || !index_cache_directory.isDirectory()) {
		    query_result_caching_enabled = false;
		}
	    }

	    String query_string = null;

	    // Parse the command-line arguments
            for (int i = 1; i < args.length; i++) {
		if (args[i].equals("-sort")) {
		    i++;
		    queryer.setSortField(args[i]);
		}
		else if (args[i].equals("-filter")) {
		    i++;
		    queryer.setFilterString(args[i]);
		}
		else if (args[i].equals("-dco")) {
		    i++;
		    queryer.setDefaultConjunctionOperator(args[i]);
		}
		else if (args[i].equals("-fuzziness")) {
		    i++;
		    queryer.setFuzziness(args[i]);
		}
		else if (args[i].equals("-startresults")) {
		    i++;
		    if (args[i].matches("\\d+")) {
			queryer.setStartResults(Integer.parseInt(args[i]));
		    }
		}
		else if (args[i].equals("-endresults")) {
		    i++;
		    if (args[i].matches("\\d+")) {
			queryer.setEndResults(Integer.parseInt(args[i]));
		    }
		}
		else {
		    query_string = args[i];
		}
	    }
	    
	    if (!queryer.initialise()) {
		return;
	    }
	    
	    // The query string has been specified as a command-line argument
	    if (query_string != null) {
		runQueryCaching(index_directory, queryer, query_string);
	    }

	    // Read queries from STDIN
	    else {
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
		while (true) {
		    // Read the query from STDIN
		    query_string = in.readLine();
		    if (query_string == null || query_string.length() == -1) {
			break;
		    }

		    runQueryCaching(index_directory, queryer, query_string);
		    
		}
	    }
	    queryer.cleanUp();
	}
 	catch (IOException exception) {
 	    exception.printStackTrace();
 	}
    }

    protected static void runQueryCaching(String index_directory, GS2LuceneQuery queryer, String query_string) 
	throws IOException
    {
	StringBuffer query_results_xml = new StringBuffer();

	// Check if this query result has been cached from a previous search (if it's enabled)
	File query_result_cache_file = null;
	if (query_result_caching_enabled) {
	    // Generate the cache file name from the query options
	    String query_result_cache_file_name = query_string + "-";
	    String fuzziness = queryer.getFuzziness();
	    query_result_cache_file_name += ((fuzziness != null) ? fuzziness : "") + "-";
	    String filter_string = queryer.getFilterString();
	    query_result_cache_file_name += ((filter_string != null) ? filter_string : "") + "-";
	    String sort_string = queryer.getSortField();
	    query_result_cache_file_name += ((sort_string != null) ? sort_string : "") + "-";
	    String default_conjunction_operator = queryer.getDefaultConjunctionOperator();
	    query_result_cache_file_name += default_conjunction_operator + "-";
	    int start_results = queryer.getStartResults();
	    int end_results = queryer.getEndResults();
	    query_result_cache_file_name += start_results + "-" + end_results;
	    query_result_cache_file_name = fileSafe(query_result_cache_file_name);

	    // If the query result cache file exists, just return its contents and we're done
	    File index_cache_directory = new File(index_directory, "cache");
	    query_result_cache_file = new File(index_cache_directory, query_result_cache_file_name);
	    if (query_result_cache_file.exists() && query_result_cache_file.isFile()) {
		FileInputStream fis = new FileInputStream(query_result_cache_file);
		InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
		BufferedReader buffered_reader = new BufferedReader(isr);
		String line = "";
		while ((line = buffered_reader.readLine()) != null) {
		    query_results_xml.append(line + "\n");
		}
		String query_results_xml_string = query_results_xml.toString();
		query_results_xml_string = query_results_xml_string.replaceFirst("cached=\"false\"", "cached=\"true\"");

		utf8out.print(query_results_xml_string);
		utf8out.flush();

		return;
	    }
	}
	
	// not cached
	query_results_xml.append("<ResultSet cached=\"false\">\n");
	query_results_xml.append("<QueryString>" + LuceneQueryResult.xmlSafe(query_string) + "</QueryString>\n");
	Filter filter = queryer.getFilter();
	if (filter != null) {
	    query_results_xml.append("<FilterString>" + filter.toString() + "</FilterString>\n");
	}
	
	LuceneQueryResult query_result = queryer.runQuery(query_string);
	if (query_result == null) {
	    System.err.println("Couldn't run the query");
	    return;
	}
	
	if (query_result.getError() != LuceneQueryResult.NO_ERROR) {
	    query_results_xml.append("<Error type=\""+query_result.getErrorString()+"\" />\n");
	} else {
	    query_results_xml.append(query_result.getXMLString());
	}
	query_results_xml.append("</ResultSet>\n");

	utf8out.print(query_results_xml);
	utf8out.flush();

	// Cache this query result, if desired
	if (query_result_caching_enabled) {
	    // Catch any exceptions thrown trying to write the query result cache file and warn about them, but don't
	    //   bother with the full stack trace. It won't affect the functionality if we can't write some cache
	    //   files, it will just affect the speed of subsequent requests.
	    // Example exceptions are "permission denied" errors, or "filename too long" errors (the filter string
	    //   can get very long in some collections)
	    try
	    {
		FileWriter query_result_cache_file_writer = new FileWriter(query_result_cache_file);
		query_result_cache_file_writer.write(query_results_xml.toString());
		query_result_cache_file_writer.close();
	    }
	    catch (Exception exception)
	    {
		System.err.println("Warning: Exception occurred trying to write query result cache file (" + exception + ")");
	    }
	}
    }
    
    protected static String fileSafe(String text)
    {
	StringBuffer file_safe_text = new StringBuffer();
	for (int i = 0; i < text.length(); i++) {
	    char character = text.charAt(i);
	    if ((character >= 'A' && character <= 'Z') || (character >= 'a' && character <= 'z') || (character >= '0' && character <= '9') || character == '-') {
		file_safe_text.append(character);
	    }
	    else {
		file_safe_text.append('%');
		file_safe_text.append((int) character);
	    }
	}
	return file_safe_text.toString();
    }

    
}


