package org.greenstone.gsdl3.service;


// Based on LuceneSearch, but not thoroughly tested

// Greenstone classes
import org.greenstone.gsdl3.util.*;
import org.greenstone.util.GlobalProperties;

// XML classes
import org.w3c.dom.Element; 
import org.w3c.dom.Document;
import org.w3c.dom.NodeList; 

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;


import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.servlet.SolrRequestParsers;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.SolrCore;

import java.io.File;
import java.util.Collection;


import org.apache.log4j.*;


public class SolrSearch extends LuceneSearch {

    static Logger logger = Logger.getLogger(org.greenstone.gsdl3.service.SolrSearch.class.getName());

    static protected CoreContainer solr_cores = null;
    protected HashMap solr_server;

    public SolrSearch()
    {
	solr_server = new HashMap();

	if (solr_cores == null) {
	    // Share one CoreContainer across all sites/collections
	    try { 
		
		String gsdl3_home = GlobalProperties.getGSDL3Home();
		String solr_ext_name = GlobalProperties.getProperty("gsdlext.solr.dirname","solr");
		
		String solr_home_str = GSFile.extHome(gsdl3_home,solr_ext_name);

		solr_cores = new CoreContainer(solr_home_str);
	    }
	    catch (Exception e) {
		e.printStackTrace();
	    }
	}
    }
	

    // Overriding the cleanUp() method here, so as to parallel the structure of GS2SolrSearch 
    // which also calls shutdown() on its CoreContainer object in GS2SolrSearch.cleanUp().
    // However, this class has not yet been tested, so it's not certain that this method is 
    // required here.
	// Adjusted  to bring it up to speed with changes in GS2SolrSearch (for activate.pl) - not yet tested
    public void cleanUp() {
	super.cleanUp();
	
		// 1. clear the map keeping track of the solrcores' EmbeddedSolrServers in this collection
		solr_server.clear();

		// 2. Need GS3 server (tomcat) to release the cores, else a part of tomcat is still running in the background 
		// on ant stop, holding a lock on the cores. Doing shutdown() preserves core descriptions in solr.xml
		solr_cores.shutdown();
		solr_cores = null;
    }

	
	// adjusted configure to bring it up to speed with changes in GS2SolrSearch (for activate.pl) - not yet tested
    public boolean configure(Element info, Element extra_info) {
		boolean success = super.configure(info, extra_info);
	
		// 1. Make the CoreContainer reload solr.xml
		// This is particularly needed for when activate.pl is executed during
		// a running GS3 server. At that point, the solr collection is reactivated and 
		// we need to tell Greenstone that the solr index has changed. This requires
		// the CoreContainer to reload the solr.xml file, and it all works again.

		solr_server.clear(); // clear the map of solr cores for this collection added to the map upon querying
	    
		// Reload the updated solr.xml into the CoreContainer
		// (Doing a solr_cores.shutdown() first doesn't seem to be required)
		try { 	
		    String solr_home_str = solr_cores.getSolrHome();
		    File solr_home = new File(solr_home_str);
		    File solr_xml = new File( solr_home,"solr.xml" );
		    
		    //solr_cores.load(solr_home_str,solr_xml);
		    solr_cores.load();
		} catch (Exception e) {
		    logger.error("Exception in SolrSearch.configure(): " + e.getMessage());
		    e.printStackTrace();
		    return false;
		}

	// initialize required number of SolrCores based on values
	// in 'index_ids' that are set by LuceneSearch::configure()

	String core_name_prefix = getCollectionCoreNamePrefix();

	for (int i=0; i<index_ids.size(); i++) {

	    String idx_name = (String)index_ids.get(i);
	    String core_name = core_name_prefix + "-" + idx_name;

	    EmbeddedSolrServer solr_core
		= new EmbeddedSolrServer(solr_cores,core_name);	

	    solr_server.put(core_name,solr_core);
	}

	return success;
    }
    
    protected void getIndexData(ArrayList index_ids, ArrayList index_names, String lang) 
    {
    }

    /** Process a text query - implemented by concrete subclasses */
    protected Element processTextQuery(Element request) {

      Document result_doc = XMLConverter.newDOM();
	Element result = result_doc.createElement(GSXML.RESPONSE_ELEM);
	Element doc_node_list = result_doc.createElement(GSXML.DOC_NODE_ELEM+GSXML.LIST_MODIFIER);
	Element metadata_list = result_doc.createElement(GSXML.METADATA_ELEM+GSXML.LIST_MODIFIER);
	initResultElement(result,doc_node_list,metadata_list);

	if (!hasParamList(request,metadata_list)) {
	    return result;
	}

	Element param_list = (Element) GSXML.getChildByTagName(request, GSXML.PARAM_ELEM+GSXML.LIST_MODIFIER);
	if (!hasQueryString(param_list,metadata_list)) {
	    return result;
	}

	HashMap params = GSXML.extractParams(param_list, false);
	String query_string = (String) params.get(QUERY_PARAM);


	// Get the index
	String index = (String) params.get(INDEX_PARAM);
	if (index == null || index.equals("")) {
	    index = this.default_index; // assume the default
	}

        try {

	    ModifiableSolrParams solrParams = new ModifiableSolrParams();
	    solrParams.set("q", query_string);
	    //solrParams.set("start", start);
	    //solrParams.set("rows", nbDocuments);

	    String core_name = getCollectionCoreNamePrefix() + "-" + index;

	    EmbeddedSolrServer solr_core = (EmbeddedSolrServer)solr_server.get(core_name);

	    QueryResponse solrResponse = solr_core.query(solrParams);

	    SolrDocumentList hits = solrResponse.getResults();

	    if (hits != null) {
		// or should this be docs.getNumFound() ??
		GSXML.addMetadata(metadata_list, "numDocsMatched", ""+hits.size());

		System.err.println(hits.getNumFound() + " documents found, "
				   + hits.size() + " returned : ");

		for (int i = 0; i < hits.size(); i++) {
		    SolrDocument solr_doc = hits.get(i);

		    String node_id = (String)solr_doc.get("docOID");
		    Element node = result_doc.createElement(GSXML.DOC_NODE_ELEM);
		    node.setAttribute(GSXML.NODE_ID_ATT, node_id);
		    doc_node_list.appendChild(node);

		    System.out.println("\t" + solr_doc.toString());
		}
	    }
	} catch (Exception e) {
	    e.printStackTrace();
	}
	
	return result;
	    
    }

	protected String getCollectionCoreNamePrefix() {
		String site_name = this.router.getSiteName();
		String coll_name = this.cluster_name;
		String collection_core_name_prefix = site_name + "-" + coll_name;
		return collection_core_name_prefix;
    }
    
}
