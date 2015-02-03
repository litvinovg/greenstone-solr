package org.greenstone.gsdl3.service;


// Based on LuceneSearch, but not thoroughly tested

// Greenstone classes
import org.greenstone.gsdl3.util.*;
import org.greenstone.util.GlobalProperties;

// XML classes
import org.w3c.dom.Element; 
import org.w3c.dom.Document;
import org.w3c.dom.NodeList; 

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.servlet.SolrRequestParsers;


public class SolrSearch extends LuceneSearch {

    public static final String SOLR_SERVLET_SUFFIX = "/solr";
    static Logger logger = Logger.getLogger(org.greenstone.gsdl3.service.SolrSearch.class.getName());

    protected String solr_servlet_base_url;
    protected HashMap<String, SolrServer> solr_server;

    public SolrSearch()
    {
	solr_server = new HashMap<String, SolrServer>();	

	// Create the solr servlet url on GS3's tomcat. By default it's "http://localhost:8383/solr"
	// Don't do this in configure(), since the tomcat url will remain unchanged while tomcat is running
	try {
	    Properties globalProperties = new Properties();
	    globalProperties.load(Class.forName("org.greenstone.util.GlobalProperties").getClassLoader().getResourceAsStream("global.properties"));
	    String host = globalProperties.getProperty("tomcat.server", "localhost");
	    String port = globalProperties.getProperty("tomcat.port", "8383");
	    String protocol = globalProperties.getProperty("tomcat.protocol", "http");
	    
	    String portStr = port.equals("80") ? "" : ":"+port;
	    solr_servlet_base_url = protocol+"://"+host+portStr+SOLR_SERVLET_SUFFIX;
	} catch(Exception e) {
	    logger.error("Error reading greenstone's tomcat solr server properties from global.properties", e);
	}
    }
	

    // Overriding the cleanUp() method here, so as to parallel the structure of GS2SolrSearch 
    // which also calls shutdown() on its CoreContainer object in GS2SolrSearch.cleanUp().
    // However, this class has not yet been tested, so it's not certain that this method is 
    // required here.
	// Adjusted  to bring it up to speed with changes in GS2SolrSearch (for activate.pl) - not yet tested
    public void cleanUp() {
	super.cleanUp();
	
	// clear the map keeping track of the SolrServers in this collection
	solr_server.clear();
    }

	
	// adjusted configure to bring it up to speed with changes in GS2SolrSearch (for activate.pl) - not yet tested
    public boolean configure(Element info, Element extra_info) {
	boolean success = super.configure(info, extra_info);
	
	// clear the map of solr cores for this collection added to the map upon querying
	solr_server.clear();
	
	if(!success) {
	    return false;
	}

	// initialize required number of SolrCores based on values
	// in 'index_ids' that are set by LuceneSearch::configure()

	String core_name_prefix = getCollectionCoreNamePrefix();

	for (int i=0; i<index_ids.size(); i++) {

	    String idx_name = (String)index_ids.get(i);
	    String core_name = core_name_prefix + "-" + idx_name;

	    SolrServer solr_core = new HttpSolrServer(this.solr_servlet_base_url+"/"+core_name);
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
	    
	    // Use SolrQuery with HttpSolrServer instead of ModifiableSolrParams, 
	    // see http://stackoverflow.com/questions/13944567/querying-solr-server-using-solrj	    
	    SolrQuery solrParams = new SolrQuery(query_string); // initialised with q url-parameter
	    //solrparams.setRequestHandler("/select"); // default. Or try "select"

	    ///solrParams.set("start", start);
	    ///solrParams.set("rows", nbDocuments);


	    String core_name = getCollectionCoreNamePrefix() + "-" + index;

	    // http://stackoverflow.com/questions/17026530/accessing-a-cores-default-handler-through-solrj-using-setquerytype
	    // default request handler is /select, see http://wiki.apache.org/solr/CoreQueryParameters
	    SolrServer solr_core = solr_server.get(core_name);
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
