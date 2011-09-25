
// Adapted from
// http://wiki.constellio.com/index.php/Solrj_example


import java.net.MalformedURLException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
import org.apache.solr.common.util.NamedList;
import org.apache.solr.servlet.SolrRequestParsers;

import java.io.File;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.core.CoreContainer;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import org.xml.sax.SAXException;

import java.util.Collection;

/**
 * A simple main to illustrate how to execute a request using SolrJ
 * 
 */
public class QueryTest 
{
    protected static String solr_home_str = "C:\\cygwin\\home\\davidb\\research\\code\\greenstone3-svn-full\\web\\ext\\solr";

    private static final String myCore = "localsite-solr-jdbm-demo-didx";
    
    private static final String facet_field = "DS";
    
    // q=...
    //private static final String query = "TX:snails";
    private static final String query = "TX:farming";
    
    private static final int start = 0;
    private static final int nbDocuments = 11;
    
    public static void main(String[] args) throws IOException,
						  ParserConfigurationException,
						  SAXException,
						  SolrServerException {
	
	File solr_home = new File(solr_home_str);
	File solr_xml = new File( solr_home,"solr.xml" );

	try { 
	    CoreContainer cores = new CoreContainer(solr_home_str,solr_xml);
	    
	    Collection<String> core_names = cores.getCoreNames();
	    
	    for (String name : core_names) {
		System.err.println("(**** core name: " + name);
	    }
	    
	    EmbeddedSolrServer server = new EmbeddedSolrServer(cores,myCore);
	    
	    // Do the same query three times using three different method
	    System.out.println("====== 1st way to execute a query ======");
	    print(doFirstQuery(server));
	    System.out.println("====== 2nd way to execute a query ======");
	    print(doSecondQuery(server));
	    System.out.println("====== 3rd way to execute query ======");
	    print(doThirdQuery(server));
	    
	    /*
	      System.out.println("======= Using SpellChecker ======");
	      print(spellCheck(server, "farmng snals"));
	
	    */

	    cores.shutdown();
	}
	catch (Exception e) {
	    e.printStackTrace();
	}

    }
    
    /**
     * Do the query using a StringBuffer
     */
    public static QueryResponse doFirstQuery(SolrServer server)
	throws SolrServerException {
	StringBuffer request = new StringBuffer();
	//request.append("collectionName=" + myCore);
	request.append("facet=" + true);
	request.append("&facet.field=" + facet_field);
	request.append("&q=" + query);
	request.append("&start=" + start);
	request.append("&rows=" + nbDocuments);
	SolrParams solrParams = SolrRequestParsers.parseQueryString(request
								    .toString());

	System.err.println("*** URL request: " + solrParams);

	return server.query(solrParams);
    }
    
    /**
     * Do the query using a ModifiableSolrParams
     */
    public static QueryResponse doSecondQuery(SolrServer server)
	throws SolrServerException {
	ModifiableSolrParams solrParams = new ModifiableSolrParams();
	//solrParams.set("collectionName", myCore);
	solrParams.set("facet", "true");
	solrParams.set("facet.field", facet_field);
	solrParams.set("q", query);
	solrParams.set("start", start);
	solrParams.set("rows", nbDocuments);
	return server.query(solrParams);
    }
    
    /**
     * Do the query using a SolrQuery
     */
    public static QueryResponse doThirdQuery(SolrServer server)
	throws SolrServerException {
	SolrQuery solrQuery = new SolrQuery();
	solrQuery.setQuery(query);
	//solrQuery.set("collectionName", myCore);
	solrQuery.set("facet", "true");
	solrQuery.set("facet.field", facet_field);
	solrQuery.setStart(start);
	solrQuery.setRows(nbDocuments);
	return server.query(solrQuery);
    }
    
    /**
     * Do the query using a SolrQuery
     */
    public static QueryResponse spellCheck(SolrServer server, String badQuery)
	throws SolrServerException {
	SolrQuery solrQuery = new SolrQuery();
	solrQuery.setQuery(badQuery);
	solrQuery.set("collectionName", myCore);

	// qt=spellcheck || qt=spellchecker
	solrQuery.setQueryType("spellcheck");
	return server.query(solrQuery);
    }
    
    /**
     * Print documents and facets
     * 
     * @param response
     */
    @SuppressWarnings("unchecked")
	public static void print(QueryResponse response) {
	SolrDocumentList docs = response.getResults();
	if (docs != null) {
	    System.out.println(docs.getNumFound() + " documents found, "
			       + docs.size() + " returned : ");
	    for (int i = 0; i < docs.size(); i++) {
		SolrDocument doc = docs.get(i);
		System.out.println("\t" + doc.toString());
	    }
	}
	
	List<FacetField> fieldFacets = response.getFacetFields();
	if (fieldFacets != null && !fieldFacets.isEmpty()) {
	    System.out.println("\nField Facets : ");
	    for (FacetField fieldFacet : fieldFacets) {
		System.out.print("\t" + fieldFacet.getName() + " :\t");
		if (fieldFacet.getValueCount() > 0) {
		    for (Count count : fieldFacet.getValues()) {
			System.out.print(count.getName() + "["
					 + count.getCount() + "]\t");
		    }
		}
		System.out.println("");
	    }
	}
	
	Map<String, Integer> queryFacets = response.getFacetQuery();
	if (queryFacets != null && !queryFacets.isEmpty()) {
	    System.out.println("\nQuery facets : ");
	    for (String queryFacet : queryFacets.keySet()) {
		System.out.println("\t" + queryFacet + "\t["
				   + queryFacets.get(queryFacet) + "]");
	    }
	    System.out.println("");
	}
	
	NamedList<NamedList<Object>> spellCheckResponse = (NamedList<NamedList<Object>>) response
	    .getResponse().get("spellcheck");
	
	if (spellCheckResponse != null) {
	    Iterator<Entry<String, NamedList<Object>>> wordsIterator = spellCheckResponse
		.iterator();
	    
	    while (wordsIterator.hasNext()) {
		Entry<String, NamedList<Object>> entry = wordsIterator.next();
		String word = entry.getKey();
		NamedList<Object> spellCheckWordResponse = entry.getValue();
		boolean correct = spellCheckWordResponse.get("frequency")
		    .equals(1);
		System.out.println("Word: " + word + ",\tCorrect?: " + correct);
		NamedList<Integer> suggestions = (NamedList<Integer>) spellCheckWordResponse
		    .get("suggestions");
		if (suggestions != null && suggestions.size() > 0) {
		    System.out.println("Suggestions : ");
		    Iterator<Entry<String, Integer>> suggestionsIterator = suggestions
			.iterator();
		    while (suggestionsIterator.hasNext()) {
			System.out.println("\t"
					   + suggestionsIterator.next().getKey());
		    }
		    
		}
		System.out.println("");
	    }
	    
	}
	
    }

}
