/**********************************************************************
 *
 * Greenstone3SearchHandler.java 
 *
 * Copyright 2015 The New Zealand Digital Library Project
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

package org.greenstone.solrserver;

import org.apache.solr.handler.component.SearchHandler;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//import org.apache.log4j.Logger;

import org.apache.solr.client.solrj.SolrQuery; // subclass of ModifiableSolrParams, a subclass of SolrParams

import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.SolrCore;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;

import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.Query; // Query, TermQuery, BooleanQuery, BooleanClause and more


import org.apache.solr.search.QParser;
import org.apache.solr.search.SolrIndexSearcher;

import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * This class is a custom Solr RequestHandler that sits on the solr server side (in tomcat's solr webapp)
 * and when it receives a query request (sent to this SearchHandler), it will expand the query terms
 * by calling query.rewrite and then request the totaltermfreq and totalfreq for these individual terms.
 * This class was made necessary by the fact that solr/lucene index locking exceptions occurred when
 * this code used to be in ext/solr's SolrQueryWrapper.java::getTerms().
 *
 * With the customisations in this class, can search a Solr collection for: econom* cat
 * And the total and term frequencies will be returned for all expanded forms, depending on the analyzer.
 */


// Important page:
// https://wiki.apache.org/solr/SolrPlugins
public class Greenstone3SearchHandler extends SearchHandler
{
    // IMPORTANT NOTE: Logging doesn't work in this calss either with log4j or slf4j, 
    // but System.err goes to catalina.out.

    //protected static Logger log = LoggerFactory.getLogger(Greenstone3SearchHandler.class);
    //static Logger logger = LoggerFactory.getLogger(org.greenstone.solrserver.Greenstone3SearchHandler.class.getName());

    protected MultiTermQuery.RewriteMethod currentRewriteMethod 
	= MultiTermQuery.CONSTANT_SCORE_BOOLEAN_QUERY_REWRITE;
        // which is less CPU intensive than MultiTermQuery.SCORING_BOOLEAN_QUERY_REWRITE) 

    // This recursive method calls setRewriteMethod on any MultiTermQueries inside the given (boolean)query,
    // since by default PrefixQueries get rewritten to ConstantScoreQueries and don't get expanded.
    // Calling setRewriteMethod on each MultiTermQuery in query here is useful to later ensure that any 
    // MultiTermQueries like PrefixQueries and WildcareQueries can get expanded, 
    // including when embedded in BooleanQueries.
    protected Query getSimplified(Query query) 
    {

	// base case
	if(query instanceof MultiTermQuery) { // PrefixQuery or WildcardQuery
	    
	    // for some reason, when a PrefixQuery (e.g. econom*) gets rewritten to a ConstantScoreQuery
	    // it no longer rewrites the query to produce the expanded terms. Need to setRewriteMethod
	    // http://stackoverflow.com/questions/3060636/lucene-score-calculation-with-a-prefixquery
	    // See also http://trac.greenstone.org/ticket/845 and http://trac.greenstone.org/changeset/26157
	    
	    MultiTermQuery mtQuery = (MultiTermQuery)query;
	    mtQuery.setRewriteMethod(currentRewriteMethod);

	}

	else if(query instanceof BooleanQuery) {

	    BooleanQuery bQuery = (BooleanQuery)query;
	    Iterator<BooleanClause> clauses = bQuery.iterator();

	    while(clauses.hasNext()) {
		BooleanClause clause = clauses.next();
		Query clauseQuery = clause.getQuery();
		Query expandedClauseQuery = getSimplified(clauseQuery);
		clause.setQuery(expandedClauseQuery);
	    }
	}

	// another type of query, leave as-is
	return query;
    }

    protected Query expandQuery(SolrQueryRequest req, Query parsedQuery) throws Exception {

	// calls setRewriteMethod on any MultiTermQueries inside the given (boolean)query,
	// doing so ensures MultiTermQueries like PrefixQueries and WildcareQueries can get expanded
	parsedQuery = getSimplified(parsedQuery); // can throw exception
	
	// now finally rewrite the query to any expand Prefix- and WildCareQueries contained in here
	SolrIndexSearcher searcher = req.getSearcher();
	IndexReader indexReader = searcher.getIndexReader(); // returns a DirectoryReader
	parsedQuery = parsedQuery.rewrite(indexReader); // used to get rewritten to ConstantScoreQuery

	return parsedQuery;
    }

    @Override
	public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception
    {

	// do getTerms() here:
	// getParams, modify solrparams (q is query_string)
	// if q exists, then do extractTerms and queryRewrite
	// then req.setSolrParams
	// and continue on as before: super.handleRequestBody(req, rsp);

	SolrQuery solrParams = new SolrQuery();
	solrParams.add(req.getParams());

	//String query_string = "TX:(farming)"; 
	String query_string = solrParams.get("q");


	if(query_string == null || query_string.equals("")) {
	    log.error("@@@@@@@@@ " + this.getClass() + " - QUERY STRING EMPTY");
	}
	else {
	    //System.err.println("@@@ Parsing query_string " + query_string);

	
	    QParser qParser = QParser.getParser(query_string, "lucene", req);
	    Query parsedQuery = qParser.getQuery();
	    
	    // For PrefixQuery or WildCardQuery (a subclass of AutomatonQuery, incl RegexpQ), 
	    // like ZZ:econom* and ZZ:*date/regex queries, Query.extractTerms() throws an Exception 
	    // because it has not done the Query.rewrite() step yet. So do that manually for them.
	    // This still doesn't provide us with the terms that econom* or *date break down into.
	    
	    //if(parsedQuery instanceof PrefixQuery || parsedQuery instanceof AutomatonQuery) { 
	    // Should we just check superclass MultiTermQuery?
	    // Can be a BooleanQuery containing PrefixQuery/WildCardQuery among its clauses, so
	    // just test for * in the query_string to determine if we need to do a rewrite() or not
	    if(query_string.contains("*")) { 
		
		//System.err.println("@@@@ query's class: " + parsedQuery.getClass().getName());


		// See also common-src/indexers/lucene-gs/src/org/greenstone/LuceneWrapper3/GS2LuceneQuery.java
		// Of http://trac.greenstone.org/changeset/26157 and http://trac.greenstone.org/ticket/845
		try {
		    parsedQuery = expandQuery(req, parsedQuery);

		} catch(BooleanQuery.TooManyClauses ex) { // hits this exception if searching solr coll for "a*"
		    System.err.println("@@@@ Encountered TooManyClauses Exception: " + ex.getMessage());
		    System.err.println("@@@@ Trying CustomRewriteMethod");
		    
		    MultiTermQuery.ConstantScoreAutoRewrite customRewriteMethod = new MultiTermQuery.ConstantScoreAutoRewrite();
		    customRewriteMethod.setDocCountPercent(100.0);
		    customRewriteMethod.setTermCountCutoff(350); // same as default
		    this.currentRewriteMethod = customRewriteMethod;

		    try {
			// try query.rewrite() again now
			parsedQuery = expandQuery(req, parsedQuery);
		    
		    } catch(BooleanQuery.TooManyClauses bex) { // still too many clauses
			System.err.println("@@@@ Encountered TooManyClauses Exception despite CustomRewriteMethod: " 
					   + bex.getMessage());
			System.err.println("@@@@ Using default Multiterm RewriteMethod");
			
			// do what the code originally did: use the default rewriteMethod which
			// uses a default docCountPercent=0.1 (%) and termCountCutoff=350
			currentRewriteMethod = MultiTermQuery.CONSTANT_SCORE_AUTO_REWRITE_DEFAULT;
			
			// this will succeed, but probably won't expand * in Prefix- and WildcardQueries
			parsedQuery = expandQuery(req, parsedQuery);
		    } 
		}
		//System.err.println("@@@@ rewritten query is now: " + parsedQuery);
	    }
	    

	    // extract the terms
	    Set<Term> extractedQueryTerms = new HashSet<Term>();
	    parsedQuery.extractTerms(extractedQueryTerms);

	    // need to sort the terms for presentation, since a Set is unsorted
	    List<Term> termsList = new ArrayList<Term>(extractedQueryTerms);
	    java.util.Collections.sort(termsList); // Term implements Comparable, terms sorted alphabetically
	    
	    

	    Iterator<Term> termsIterator = termsList.iterator();//extractedQueryTerms.iterator();
	    while(termsIterator.hasNext()) { 
		Term term = termsIterator.next(); 
		//System.err.println("#### Found query term: " + term);		

		String field = term.field();
		String queryTerm = term.text();

		// totaltermfreq(TI, 'farming') 
		// termfreq(TI, 'farming')		
		solrParams.addField("totaltermfreq(" + field + ",'" + queryTerm + "')");
		solrParams.addField("termfreq(" + field + ",'" + queryTerm + "')");
	    }
	}

	// set to modified SolrQuery SolrParams
	req.setParams(solrParams);
	// send off modified request
	super.handleRequestBody(req, rsp);
    }
}