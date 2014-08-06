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

import java.lang.reflect.Type;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery; // subclass of ModifiableSolrParams
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.TermsResponse;

import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.SolrCore;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.greenstone.LuceneWrapper4.SharedSoleneQuery;
import org.greenstone.LuceneWrapper4.SharedSoleneQueryResult;

import org.apache.lucene.search.Query; // Query, TermQuery, BooleanQuery, BooleanClause and more
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.solr.search.QParser;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.request.LocalSolrQueryRequest;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class SolrQueryWrapper extends SharedSoleneQuery
{
  public static String SORT_ASCENDING = "asc";
  public static String SORT_DESCENDING = "desc";
  public static String SORT_BY_RANK = "score";
  public static String SORT_BY_INDEX_ORDER = "_docid_";

	static Logger logger = Logger.getLogger(org.greenstone.gsdl3.util.SolrQueryWrapper.class.getName());
	protected int max_docs = 100;
	protected String sort_order = SORT_DESCENDING;
  protected String sort_field = SORT_BY_RANK; // don't want null default for solr
	protected ArrayList<String> _facets = new ArrayList<String>();
	protected ArrayList<String> _facetQueries = new ArrayList<String>();
	SolrServer solr_core = null;

	public SolrQueryWrapper()
	{
		super();
		start_results = 0;
	}

	public void setMaxDocs(int max_docs)
	{
		this.max_docs = max_docs;
	}

	public void setSolrCore(SolrServer solr_core)
	{
		this.solr_core = solr_core;
	}
  // make sure its not null.
  public void setSortField(String sort_field) {
    if (sort_field != null) {
      this.sort_field = sort_field;
    }
  }

  public void setSortOrder(String order)
  {
    this.sort_order = order;
  }
	public void addFacet(String facet)
	{
		if (!_facets.contains(facet))
		{
			_facets.add(facet);
		}
	}

	public void clearFacets()
	{
		_facets.clear();
	}

	public void addFacetQuery(String facetQuery)
	{
		if (!_facetQueries.contains(facetQuery))
		{
			_facetQueries.add(facetQuery);
		}
	}

	public void clearFacetQueries()
	{
		_facetQueries.clear();
	}

	public boolean initialise()
	{
		if (solr_core == null)
		{
			utf8out.println("Solr Core not loaded in ");
			utf8out.flush();
			return false;
		}
		return true;
	}


    /** Extracts the query terms from the query string. The query string can be a boolean 
     * combination of the various search fields with their search terms or phrases
     */
    public Term[] getTerms(SolrQuery solrQuery, String query_string) 
    {
	Term terms[] = null;
	
	if(solr_core instanceof EmbeddedSolrServer) {
	    EmbeddedSolrServer solrServer = (EmbeddedSolrServer)solr_core;
	    
	    CoreContainer coreContainer = solrServer.getCoreContainer();
	    
	    Collection<SolrCore> solrCores = coreContainer.getCores();
	    if(!solrCores.isEmpty()) {
		Iterator<SolrCore> coreIterator = solrCores.iterator();

		// Just use the first core, since the term frequency of any term is the same regardless of core
		if(coreIterator.hasNext()) {
		    SolrCore solrCore = coreIterator.next();
		    
		    
		    LocalSolrQueryRequest solrQueryRequest = new LocalSolrQueryRequest(solrCore, solrQuery);
		    Query parsedQuery = null;

		    try {
			
			// get the qparser, default is LuceneQParserPlugin, which is called "lucene" see http://wiki.apache.org/solr/QueryParser
			QParser qParser = QParser.getParser(query_string, "lucene", solrQueryRequest);
			parsedQuery = qParser.getQuery();

			// For PrefixQuery or WildCardQuery (a subclass of AutomatonQuery, incl RegexpQ), 
			// like ZZ:econom* and ZZ:*date/regex queries, Query.extractTerms() throws an Exception 
			// because it has not done the Query.rewrite() step yet. So do that manually for them.
			// This still doesn't provide us with the terms that econom* or *date break down into.

			//if(parsedQuery instanceof PrefixQuery || parsedQuery instanceof AutomatonQuery) { 
			    			// Should we just check superclass MultiTermQuery?
			// Can be a BooleanQuery containing PrefixQuery/WildCardQuery among its clauses, so
			// just test for * in the query_string to determine if we need to do a rewrite() or not
			if(query_string.contains("*")) { 
			    SolrIndexSearcher searcher = solrQueryRequest.getSearcher();
			    IndexReader indexReader = searcher.getIndexReader(); // returns a DirectoryReader
			    parsedQuery = parsedQuery.rewrite(indexReader); // gets rewritten to ConstantScoreQuery
			}

			//System.err.println("#### Query type was: " + parsedQuery.getClass());
			//logger.error("#### Query type was: " + parsedQuery.getClass());
			
			// extract the terms
			Set<Term> extractedQueryTerms = new HashSet<Term>();
			parsedQuery.extractTerms(extractedQueryTerms);

			terms = new Term[extractedQueryTerms.size()];
			
			Iterator<Term> termsIterator = extractedQueryTerms.iterator();
			for(int i = 0; termsIterator.hasNext(); i++) {
			    Term term = termsIterator.next(); 
			    ///System.err.println("#### Found query term: " + term);
			    ///logger.error("#### Found query term: " + term);

			    terms[i] = term; //(term.field(), term.text());
			}
			
		    } catch(Exception queryParseException) {
			queryParseException.printStackTrace();
			System.err.println("Exception when parsing query: " + queryParseException.getMessage());
			System.err.println("#### Query type was: " + parsedQuery.getClass());
			logger.error("#### Query type was: " + parsedQuery.getClass());
		    }
		}
		
	    } else {
		System.err.println("#### Not an EmbeddedSolrServer. This shouldn't happen.");
		logger.error("#### Not an EmbeddedSolrServer. This shouldn't happen.");
	    }
	}
	
	return terms;
    }

	public SharedSoleneQueryResult runQuery(String query_string)
	{
		if (query_string == null || query_string.equals(""))
		{
			utf8out.println("The query word is not indicated ");
			utf8out.flush();
			return null;
		}

		SolrQueryResult solr_query_result = new SolrQueryResult();
		solr_query_result.clear();

		if (_facetQueries.size() > 0)
		{
			HashMap<String, ArrayList<String>> grouping = new HashMap<String, ArrayList<String>>();
			for (String currentQuery : _facetQueries)
			{
				//Facet queries are stored in JSON, so we have to decode it
				Gson gson = new Gson();
				Type type = new TypeToken<List<String>>()
				{
				}.getType();
				List<String> queryElems = gson.fromJson(currentQuery, type);

				//Group each query segment by the index it uses
				for (String currentQueryElement : queryElems)
				{
					String decodedQueryElement = null;
					try
					{
						decodedQueryElement = URLDecoder.decode(currentQueryElement, "UTF-8");
					}
					catch (Exception ex)
					{
						continue;
					}

					int colonIndex = currentQueryElement.indexOf(":");
					String indexShortName = currentQueryElement.substring(0, colonIndex);

					if (grouping.get(indexShortName) == null)
					{
						grouping.put(indexShortName, new ArrayList<String>());
					}
					grouping.get(indexShortName).add(decodedQueryElement);
				}
			}

			//Construct the facet query string to add to the regular query string
			StringBuilder facetQueryString = new StringBuilder();
			int keysetCounter = 0;
			for (String key : grouping.keySet())
			{
				StringBuilder currentFacetString = new StringBuilder("(");
				int groupCounter = 0;
				for (String queryElem : grouping.get(key))
				{
					currentFacetString.append(queryElem);

					groupCounter++;
					if (groupCounter < grouping.get(key).size())
					{
						currentFacetString.append(" OR ");
					}
				}
				currentFacetString.append(")");

				facetQueryString.append(currentFacetString);

				keysetCounter++;
				if (keysetCounter < grouping.keySet().size())
				{
					facetQueryString.append(" AND ");
				}
			}

			if (facetQueryString.length() > 0)
			{
				query_string += " AND " + facetQueryString;
			}
		}


		SolrQuery solrQuery = new SolrQuery(query_string);
		solrQuery.addSort(this.sort_field, SolrQuery.ORDER.valueOf(this.sort_order)); // sort param, like "score desc" or "byORG asc"
		solrQuery.setStart(start_results); // which result to start from
		solrQuery.setRows((end_results - start_results) + 1); // how many results per "page"

		// http://lucene.472066.n3.nabble.com/get-term-frequency-just-only-keywords-search-td4084510.html
		// WORKS (search didx core):
		//TI:farming
		//docOID,score,termfreq(TI,'farming'),totaltermfreq(TI,'farming')


		// which fields to return for each document, we'll add the request for totaltermfreq later
		// fl=docOID score termfreq(TI,'farming') totaltermfreq(TI,'farming')
		solrQuery.setFields("docOID", "score"); //solrParams.set("fl", "docOID score totaltermfreq(field,'queryterm')"); 
		
		//solrQuery.setTerms(true); // turn on the termsComponent		
		//solrQuery.set("terms.fl", "ZZ"); // which field to get the terms from. ModifiableSolrParams method
		
		// http://wiki.apache.org/solr/TermVectorComponent and https://cwiki.apache.org/confluence/display/solr/The+Term+Vector+Component
		// http://lucene.472066.n3.nabble.com/get-term-frequency-just-only-keywords-search-td4084510.html
		// http://stackoverflow.com/questions/13031534/word-frequency-in-solr
		// http://wiki.apache.org/solr/FunctionQuery#tf and #termfreq and #totaltermfreq
		// https://wiki.apache.org/solr/TermsComponent

		//solrParams.set("tv.tf", true);// turn on the terms vector Component
		//solrParams.set("tv.fl", "ZZ");// which field to get the terms from /// ZZ


		if (_facets.size() > 0)
		{
		  // enable facet counts in the query response
			solrQuery.setFacet(true); //solrParams.set("facet", "true");
			for (int i = 0; i < _facets.size(); i++)
			{
			  // add this field as a facet
			  solrQuery.addFacetField(_facets.get(i)); // solrParams.add("facet.field", _facets.get(i));
			}
		}

		// get the individual terms that make up the query, then request solr to return the totaltermfreq for each term
		Term[] terms = getTerms(solrQuery, query_string);
		if(terms != null) {
		    for(int i = 0; i < terms.length; i++) {
			Term term = terms[i];
			String field = term.field();
			String queryTerm = term.text();
			// totaltermfreq(TI, 'farming') termfreq(TI, 'farming')
			
			solrQuery.addField("totaltermfreq(" + field + ",'" + queryTerm + "')");
			solrQuery.addField("termfreq(" + field + ",'" + queryTerm + "')");
		    }
		}

		// do the query
		try
		{
			QueryResponse solrResponse = solr_core.query(solrQuery); //solr_core.query(solrParams);
			SolrDocumentList hits = solrResponse.getResults();
			//TermsResponse termResponse = solrResponse.getTermsResponse(); // null unless termvectors=true in schema.xml

			if (hits != null)
			{
				logger.info("*** hits size = " + hits.size());
				logger.info("*** num docs found = " + hits.getNumFound());

				logger.info("*** start results = " + start_results);
				logger.info("*** end results = " + end_results);
				logger.info("*** max docs = " + max_docs);

				// numDocsFound is the total number of matching docs in the collection
				// as opposed to the number of documents returned in the hits list

				solr_query_result.setTotalDocs((int) hits.getNumFound());

				solr_query_result.setStartResults(start_results);
				solr_query_result.setEndResults(start_results + hits.size());

				
				// get the first field we're searching in, this will be the fallback field
				int sepIndex = query_string.indexOf(":");
				String defaultField = query_string.substring(0, sepIndex);
				//String query = query_string.substring(sepIndex + 2, query_string.length() - 1); // Replaced by call to getTerms()

				//solr_query_result.addTerm(query, field, (int) hits.getNumFound(), -1);

				// Output the matching documents
				for (int i = 0; i < hits.size(); i++)
				{
					SolrDocument doc = hits.get(i);
					
					// Need to think about how to support document term frequency.  Make zero for now 
					int doc_term_freq = 0;
					String docOID = (String) doc.get("docOID");
					Float score = (Float) doc.get("score");

					logger.info("**** docOID = " + docOID);
					logger.info("**** score = " + score);
										
					
					// solr returns each term's totaltermfreq, ttf, at the document level, even though 
					// the ttf is the same for each document. So extract this information just for the first document
					if(i == 0) { // first document
					    
					    if(terms != null) {
						for(int j = 0; j < terms.length; j++) {
						    Term term = terms[j];
						    String field = term.field();
						    String queryTerm = term.text();

						    // totaltermfreq(TI, 'farming') termfreq(TI, 'farming')
						    Long totaltermfreq = (Long)doc.get("totaltermfreq("+field+",'"+queryTerm+"')");
						    Integer termfreq = (Integer)doc.get("termfreq("+field+",'"+queryTerm+"')");

						    //System.err.println("**** ttf = " + totaltermfreq); 
						    //System.err.println("**** tf = " + termfreq);
						    //logger.info("**** ttf = " + totaltermfreq); 
						    //logger.info("**** tf = " + termfreq);
						
						    solr_query_result.addTerm(queryTerm, field, (int) hits.getNumFound(), totaltermfreq.intValue()); // long totaltermfreq to int
						}
					    } else { // no terms extracted from query_string
						solr_query_result.addTerm(query_string, defaultField, (int) hits.getNumFound(), -1); // no terms
					    }
					}

					solr_query_result.addDoc(docOID, score.floatValue(), doc_term_freq); // doc_termfreq for which term????
				}
			}
			else
			{
				solr_query_result.setTotalDocs(0);

				solr_query_result.setStartResults(0);
				solr_query_result.setEndResults(0);
			}

			solr_query_result.setFacetResults(solrResponse.getFacetFields());
		}
		catch (SolrServerException server_exception)
		{
			server_exception.printStackTrace();
			solr_query_result.setError(SolrQueryResult.SERVER_ERROR);
		}

		return solr_query_result;
	}

	//Greenstone universe operates with a base of 1 for "start_results"
	//But Solr operates from 0
	public void setStartResults(int start_results)
	{
		if (start_results < 0)
		{
			start_results = 0;
		}
		this.start_results = start_results - 1;
	}

	public void cleanUp()
	{
		super.cleanUp();
	}

}
