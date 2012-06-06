/*
*    GS2SolrSearch.java
*    Copyright (C) 2006 New Zealand Digital Library, http://www.nzdl.org
*
*    This program is free software; you can redistribute it and/or modify
*   the Free Software Foundation; either version 2 of the License, or
*    (at your option) any later version.
*
*    This program is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*    GNU General Public License for more details.
*
*    You should have received a copy of the GNU General Public License
*    along with this program; if not, write to the Free Software
*    Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
*/

package org.greenstone.gsdl3.service;

// Greenstone classes
import org.greenstone.gsdl3.util.*;
import org.greenstone.util.GlobalProperties;

// XML classes
import org.w3c.dom.Element; 
import org.w3c.dom.NodeList;
import org.w3c.dom.Document; 
// java classes
import java.util.ArrayList;
import java.util.HashMap;
import java.io.File;
import java.util.Iterator;
import java.util.Set;
import java.util.Map;
import java.util.Vector;

// Logging
import org.apache.log4j.Logger;

//import org.greenstone.SolrWrapper.GS2SolrQuery;
//import org.greenstone.SolrWrapper.SolrQueryResult;
import org.greenstone.LuceneWrapper3.SharedSoleneQueryResult;

import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.core.CoreContainer;

import java.net.MalformedURLException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;



public class GS2SolrSearch extends SharedSoleneGS2FieldSearch
{
    static Logger logger = Logger.getLogger(org.greenstone.gsdl3.service.GS2SolrSearch.class.getName());

    static protected CoreContainer all_solr_cores = null;

    protected HashMap solr_core_cache;
    protected SolrQueryWrapper solr_src=null;
	
    public GS2SolrSearch()
    {
	// Used to store the solr cores that match the required 'level' 
	// of search (e.g. either document-level=>didx, or 
	// section-level=>sidx.  The hashmap is filled out on demand
	// based on 'level' parameter passed in to 'setUpQueryer()'

	solr_core_cache = new HashMap(); 

	if (all_solr_cores == null) {
	    // Share one CoreContainer across all sties/collections
	    try { 
		
		String gsdl3_home = GlobalProperties.getGSDL3Home();
		String solr_ext_name = GlobalProperties.getProperty("gsdlext.solr.dirname","solr");
		
		String solr_home_str = GSFile.extHome(gsdl3_home,solr_ext_name);
		File solr_home = new File(solr_home_str);
		File solr_xml = new File( solr_home,"solr.xml" );

		all_solr_cores = new CoreContainer(solr_home_str,solr_xml);	    	    
	    }
	    catch (Exception e) {
		e.printStackTrace();
	    }
	}

	this.solr_src = new SolrQueryWrapper();
    }
	

    public void cleanUp() {
	super.cleanUp();
	this.solr_src.cleanUp();
	all_solr_cores.shutdown();
    }
	    
    /** methods to handle actually doing the query */
    
    /** do any initialisation of the query object */
    protected boolean setUpQueryer(HashMap params) {
	String indexdir = GSFile.collectionBaseDir(this.site_home, this.cluster_name) + File.separatorChar + "index"+File.separatorChar;
		
	String index = "didx";
	String physical_index_language_name=null;
	String physical_sub_index_name=null;
	int maxdocs = 100;
	int hits_per_page = 20;
	int start_page = 1;
	// set up the query params
	Set entries = params.entrySet();
	Iterator i = entries.iterator();
	while (i.hasNext()) {
	    Map.Entry m = (Map.Entry)i.next();
	    String name = (String)m.getKey();
	    String value = (String)m.getValue();
	    
	    if (name.equals(MAXDOCS_PARAM)&& !value.equals("")) {
		maxdocs = Integer.parseInt(value);
	    } else if (name.equals(HITS_PER_PAGE_PARAM)) {
		hits_per_page = Integer.parseInt(value);
	    } else if (name.equals(START_PAGE_PARAM)) {
		start_page = Integer.parseInt(value);
		
	    } else if (name.equals(MATCH_PARAM)) {
		if (value.equals(MATCH_PARAM_ALL)) {
		    this.solr_src.setDefaultConjunctionOperator("AND");
		} else{
		    this.solr_src.setDefaultConjunctionOperator("OR");
		}
	    } else if (name.equals(RANK_PARAM)) {
		if (value.equals(RANK_PARAM_RANK_VALUE)) {
		    value = null;
		}
		this.solr_src.setSortField(value);
	    } else if (name.equals(LEVEL_PARAM)) {
		if (value.toUpperCase().equals("SEC")){
		    index = "sidx";
		}
		else {
		    index = "didx";
		}
	    } else if (name.equals(INDEX_SUBCOLLECTION_PARAM)) {
		physical_sub_index_name=value;
	    } else if (name.equals(INDEX_LANGUAGE_PARAM)){
		physical_index_language_name=value;
	    }  // ignore any others
	}
	// set up start and end results if necessary
	int start_results = 1;
	if (start_page != 1) {
	    start_results = ((start_page-1) * hits_per_page) + 1;
	}
	int end_results = hits_per_page * start_page;
	this.solr_src.setStartResults(start_results);
	this.solr_src.setEndResults(end_results);
	this.solr_src.setMaxDocs(maxdocs);

	if (index.equals("sidx") || index.equals("didx")){
	    if (physical_sub_index_name!=null) {
		index+=physical_sub_index_name;
	    }   
	    if (physical_index_language_name!=null){
		index+=physical_index_language_name;
	    }
	}


	// now we know the index level, we can dig out the required
	// solr-core, (caching the result in 'solr_core_cache')

	String site_name = this.router.getSiteName();
	String coll_name = this.cluster_name;

	String core_name = site_name + "-" + coll_name + "-" + index;

	EmbeddedSolrServer solr_core = null;

	if (!solr_core_cache.containsKey(core_name)) {
	    solr_core = new EmbeddedSolrServer(all_solr_cores,core_name);	
	    
	    solr_core_cache.put(core_name,solr_core);
	}
	else {
	    solr_core = (EmbeddedSolrServer)solr_core_cache.get(core_name);
	}
	
	this.solr_src.setSolrCore(solr_core);
	this.solr_src.initialise();
	return true;
    }

    /** do the query */
    protected Object runQuery(String query) {
	
	/*
	  ModifiableSolrParams solrParams = new ModifiableSolrParams();
	  solrParams.set("collectionName", myCollection);
		solrParams.set("username", "admin");
		solrParams.set("password", "password");
		solrParams.set("facet", facet);
		solrParams.set("q", query);
		solrParams.set("start", start);
		solrParams.set("rows", nbDocuments);
		return server.query(solrParams);
	    */

	/*
	SolrQuery solrQuery = new SolrQuery();
	solrQuery.setQuery(query);
	//solrQuery.set("collectionName", myCollection);
	solrQuery.set("username", "admin");
	solrQuery.set("password", "password");
	solrQuery.set("facet", facet);
	solrQuery.setStart(start);
	solrQuery.setRows(nbDocuments);
	//return server.query(solrQuery);
	*/
	
	try {
	    SharedSoleneQueryResult sqr=this.solr_src.runQuery(query);
	    return sqr;
	} catch (Exception e) {
	    logger.error ("Exception happened in run query: ", e);
	}
	
	return null;
    }

    /** get the total number of docs that match */
    protected long numDocsMatched(Object query_result) {
	return ((SharedSoleneQueryResult)query_result).getTotalDocs();
	
    }

    /** get the list of doc ids */
    protected String [] getDocIDs(Object query_result) {
	Vector docs = ((SharedSoleneQueryResult)query_result).getDocs();
	String [] doc_nums = new String [docs.size()];
	for (int d = 0; d < docs.size(); d++) {
	    String doc_num = ((SharedSoleneQueryResult.DocInfo) docs.elementAt(d)).id_;
	    doc_nums[d] = doc_num;
	}
	return doc_nums;
    }

    /** get the list of doc ranks */
    protected String [] getDocRanks(Object query_result) {
	Vector docs = ((SharedSoleneQueryResult)query_result).getDocs();
	String [] doc_ranks = new String [docs.size()];
	for (int d = 0; d < docs.size(); d++) {
	    doc_ranks[d] = Float.toString(((SharedSoleneQueryResult.DocInfo) docs.elementAt(d)).rank_);
	}
	return doc_ranks;
    }

    /** add in term info if available */
    protected boolean addTermInfo(Element term_list, HashMap params,
				  Object query_result) {
	String query_level = (String)params.get(LEVEL_PARAM); // the current query level
	
	Vector terms = ((SharedSoleneQueryResult)query_result).getTerms();
	for (int t = 0; t < terms.size(); t++) {
	    SharedSoleneQueryResult.TermInfo term_info = (SharedSoleneQueryResult.TermInfo) terms.get(t);
	    
	    Element term_elem = this.doc.createElement(GSXML.TERM_ELEM);
	    term_elem.setAttribute(GSXML.NAME_ATT, term_info.term_);
	    term_elem.setAttribute(FREQ_ATT, "" + term_info.term_freq_);
	    term_elem.setAttribute(NUM_DOCS_MATCH_ATT, "" + term_info.match_docs_);
	    term_elem.setAttribute(FIELD_ATT, term_info.field_);
	    term_list.appendChild(term_elem);
	}
	
	Vector stopwords = ((SharedSoleneQueryResult)query_result).getStopWords();
	for (int t = 0; t < stopwords.size(); t++) {
	    String stopword = (String) stopwords.get(t);
	    
	    Element stopword_elem = this.doc.createElement(GSXML.STOPWORD_ELEM);
	    stopword_elem.setAttribute(GSXML.NAME_ATT, stopword);
	    term_list.appendChild(stopword_elem);
	}
	
	return true;
    }
    

}
