/**********************************************************************
 *
 * SolrQueryWrapper.java 
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
package org.greenstone.gsdl3.util;


import java.io.*;
import java.util.*;
import java.util.regex.*;

import org.apache.log4j.Logger;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.QueryResponse;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;

import org.greenstone.LuceneWrapper.SharedSoleneQuery;
import org.greenstone.LuceneWrapper.SharedSoleneQueryResult;


public class SolrQueryWrapper  extends SharedSoleneQuery
{

    static Logger logger = Logger.getLogger(org.greenstone.gsdl3.util.SolrQueryWrapper.class.getName());

    /*
    // Use the standard set of English stop words by default
    static private String[] stop_words = GS2Analyzer.STOP_WORDS;

    private String full_indexdir="";
    
    private String default_conjunction_operator = "OR";
    private String fuzziness = null;
    private String sort_field = null;
    private Sort sorter=new Sort();
    private String filter_string = null;
    private Filter filter = null;

    private QueryParser query_parser = null;
    private QueryParser query_parser_no_stop_words = null;
    */

    protected int max_docs = 100;

    SolrServer solr_core = null;


    public SolrQueryWrapper() {
	super();
    }
    /*
	// Create one query parser with the standard set of stop words, and one with none

	query_parser = new QueryParser(TEXTFIELD, new GS2Analyzer(stop_words));
    	query_parser_no_stop_words = new QueryParser(TEXTFIELD, new GS2Analyzer(new String[] { }));
    }
    */

    public void setMaxDocs(int max_docs)
    {
	this.max_docs = max_docs;
    }

    public void setSolrCore(SolrServer solr_core) 
    {
	this.solr_core = solr_core;
    }

    
    public boolean initialise() {

    	if (solr_core==null) {
	    utf8out.println("Solr Core not loaded in ");
	    utf8out.flush();
	    return false;
    	}
	return true;

    }

    public SharedSoleneQueryResult runQuery(String query_string) {
	
	if (query_string == null || query_string.equals("")) {
	    utf8out.println("The query word is not indicated ");
	    utf8out.flush();
	    return null;
	}
	
 	SolrQueryResult solr_query_result=new SolrQueryResult();
	solr_query_result.clear();
	
	ModifiableSolrParams solrParams = new ModifiableSolrParams();
	solrParams.set("q", query_string);
	solrParams.set("start", start_results);
        solrParams.set("rows", (end_results - start_results) +1);
	solrParams.set("fl","docOID score");

	/*
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
	    
*/

	try {
	    QueryResponse solrResponse = solr_core.query(solrParams);

	    SolrDocumentList hits = solrResponse.getResults();

	    if (hits != null) {

		logger.info("*** hits size = " + hits.size());
		logger.info("*** num docs found = " + hits.getNumFound());

		logger.info("*** start results = " + start_results);
		logger.info("*** end results = " + end_results);
		logger.info("*** max docs = " + max_docs);

		// numDocsFound is the total number of mactching docs in the collection
		// as opposed to the number of documents returned in the hits list

		solr_query_result.setTotalDocs((int)hits.getNumFound());
		
		solr_query_result.setStartResults(start_results);
		solr_query_result.setEndResults(start_results + hits.size());
	    
		// Output the matching documents
		for (int i = 0; i < hits.size(); i++) {
		    SolrDocument doc = hits.get(i);
		    
		    // Need to think about how to support document term frequency.  Make zero for now 
		    int doc_term_freq = 0;
		    String docOID = (String)doc.get("docOID");
		    Float score = (Float)doc.get("score");

		    logger.info("**** docOID = " + docOID);
		    logger.info("**** score = " + score);

		    solr_query_result.addDoc(docOID, score.floatValue(), doc_term_freq);
		}
	    }
	    else {
		solr_query_result.setTotalDocs(0);
		
		solr_query_result.setStartResults(0);
		solr_query_result.setEndResults(0);
	    }
	}

	catch (SolrServerException server_exception) {
	    solr_query_result.setError(SolrQueryResult.SERVER_ERROR);
	}


	/*
	    
	    // do the query
	    // Simple case for getting all the matching documents
	    if (end_results == Integer.MAX_VALUE) {
		// Perform the query (filter and sorter may be null)
		Hits hits = searcher.search(query, filter, sorter);
		lucene_query_result.setTotalDocs(hits.length());

		// Output the matching documents
		lucene_query_result.setStartResults(start_results);
		lucene_query_result.setEndResults(hits.length());

		for (int i = start_results; i <= hits.length(); i++) {
		    int lucene_doc_num = hits.id(i - 1);
		    Document doc = hits.doc(i - 1);
		    int doc_term_freq = 0;
		    Integer doc_term_freq_object = (Integer) doc_term_freq_map.get(new Integer(lucene_doc_num));
		    if (doc_term_freq_object != null)
		    {
			doc_term_freq = doc_term_freq_object.intValue();
		    }
		    lucene_query_result.addDoc(doc.get("docOID").trim(), hits.score(i-1), doc_term_freq);
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
	*/

	return solr_query_result;
	}
    /*

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
	this.default_conjunction_operator = default_conjunction_operator.toUpperCase();
	if (default_conjunction_operator.equals("AND")) {
	    query_parser.setDefaultOperator(query_parser.AND_OPERATOR);
	    query_parser_no_stop_words.setDefaultOperator(query_parser.AND_OPERATOR);
	} else { // default is OR
	    query_parser.setDefaultOperator(query_parser.OR_OPERATOR);
	    query_parser_no_stop_words.setDefaultOperator(query_parser.OR_OPERATOR);
	}
	

    }
    */
    
    public void cleanUp() {
	super.cleanUp();
    }

}


