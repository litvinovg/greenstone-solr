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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.FacetField;
import org.greenstone.LuceneWrapper4.SharedSoleneQueryResult;
import org.greenstone.gsdl3.util.FacetWrapper;
import org.greenstone.gsdl3.util.GSFile;
import org.greenstone.gsdl3.util.GSXML;
import org.greenstone.gsdl3.util.SolrFacetWrapper;
import org.greenstone.gsdl3.util.SolrQueryResult;
import org.greenstone.gsdl3.util.SolrQueryWrapper;
import org.greenstone.util.GlobalProperties;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class GS2SolrSearch extends SharedSoleneGS2FieldSearch
{

  public static final String SOLR_SERVLET_SUFFIX = "/solr";
  protected static final String SORT_ORDER_PARAM = "sortOrder";
  protected static final String SORT_ORDER_DESCENDING = "1";
  protected static final String SORT_ORDER_ASCENDING = "0";

	static Logger logger = Logger.getLogger(org.greenstone.gsdl3.service.GS2SolrSearch.class.getName());

        protected String solr_servlet_base_url;
	protected HashMap<String, SolrServer> solr_core_cache;
	protected SolrQueryWrapper solr_src = null;

	protected ArrayList<String> _facets = new ArrayList<String>();

	public GS2SolrSearch()
	{
                paramDefaults.put(SORT_ORDER_PARAM, SORT_ORDER_DESCENDING);
		does_faceting = true;
		does_highlight_snippets = true;
		does_full_field_highlighting = true;
		// Used to store the solr cores that match the required 'level' 
		// of search (e.g. either document-level=>didx, or 
		// section-level=>sidx.  The hashmap is filled out on demand
		// based on 'level' parameter passed in to 'setUpQueryer()'

		solr_core_cache = new HashMap<String, SolrServer>();

		this.solr_src = new SolrQueryWrapper();		

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

	/** configure this service */
	public boolean configure(Element info, Element extra_info)
	{
		boolean success = super.configure(info, extra_info);

		// clear the map of solr cores for this collection added to the map upon querying
		solr_core_cache.clear(); 

		if(!success) {
		    return false;
		}
		
		// Setting up facets
		// TODO - get these from build config, in case some haven't built
		Element searchElem = (Element) GSXML.getChildByTagName(extra_info, GSXML.SEARCH_ELEM);
		NodeList facet_list = info.getElementsByTagName("facet");
		for (int i=0; i<facet_list.getLength(); i++) {
		  _facets.add(((Element)facet_list.item(i)).getAttribute(GSXML.SHORTNAME_ATT));
		}
		// NodeList configIndexElems = searchElem.getElementsByTagName(GSXML.INDEX_ELEM);

		// ArrayList<String> chosenFacets = new ArrayList<String>();
		// for (int i = 0; i < configIndexElems.getLength(); i++)
		// {
		// 	Element current = (Element) configIndexElems.item(i);
		// 	if (current.getAttribute(GSXML.FACET_ATT).equals("true"))
		// 	{
		// 		chosenFacets.add(current.getAttribute(GSXML.NAME_ATT));
		// 	}
		// }

		// Element indexListElem = (Element) GSXML.getChildByTagName(info, GSXML.INDEX_ELEM + GSXML.LIST_MODIFIER);
		// NodeList buildIndexElems = indexListElem.getElementsByTagName(GSXML.INDEX_ELEM);

		// for (int j = 0; j < buildIndexElems.getLength(); j++)
		// {
		// 	Element current = (Element) buildIndexElems.item(j);
		// 	for (int i = 0; i < chosenFacets.size(); i++)
		// 	{
		// 		if (current.getAttribute(GSXML.NAME_ATT).equals(chosenFacets.get(i)))
		// 		{
		// 			_facets.add(current.getAttribute(GSXML.SHORTNAME_ATT));
		// 		}
		// 	}
		// }

		return true;
	}

	public void cleanUp()
	{
		super.cleanUp();
		this.solr_src.cleanUp();

		// clear the map keeping track of the SolrServers in this collection
		solr_core_cache.clear();
	}

	/** add in the SOLR specific params to TextQuery */
	protected void addCustomQueryParams(Element param_list, String lang)
	{
		super.addCustomQueryParams(param_list, lang);
		/** Add in the sort order asc/desc param */
		createParameter(SORT_ORDER_PARAM, param_list, lang);
	}
  /** add in SOLR specific params for AdvancedFieldQuery */
  protected void addCustomQueryParamsAdvField(Element param_list, String lang)
	{
		super.addCustomQueryParamsAdvField(param_list, lang);
		createParameter(SORT_ORDER_PARAM, param_list, lang);
		
	}
	/** create a param and add to the list */
  protected void createParameter(String name, Element param_list, String lang)
  {
    Document doc = param_list.getOwnerDocument();
    Element param = null;
    String param_default = paramDefaults.get(name);
    if (name.equals(SORT_ORDER_PARAM)) {
        String[] vals = { SORT_ORDER_ASCENDING, SORT_ORDER_DESCENDING }; 
	String[] vals_texts = { getTextString("param." + SORT_ORDER_PARAM + "." + SORT_ORDER_ASCENDING, lang), getTextString("param." + SORT_ORDER_PARAM + "." + SORT_ORDER_DESCENDING, lang) }; 	    
	
	param = GSXML.createParameterDescription(doc, SORT_ORDER_PARAM, getTextString("param." + SORT_ORDER_PARAM, lang), GSXML.PARAM_TYPE_ENUM_SINGLE, param_default, vals, vals_texts);
    }

    if (param != null)
      {
	param_list.appendChild(param);
      }
    else
      {
	super.createParameter(name, param_list, lang);
      }
    
  }
  
	/** methods to handle actually doing the query */

	/** do any initialisation of the query object */
	protected boolean setUpQueryer(HashMap params)
	{
		this.solr_src.clearFacets();
		this.solr_src.clearFacetQueries();

		for (int i = 0; i < _facets.size(); i++)
		{
			this.solr_src.addFacet(_facets.get(i));
		}

		String index = "didx";
		if (this.default_level.toUpperCase().equals("SEC")) {
		  index = "sidx";
		}
		String physical_index_language_name = null;
		String physical_sub_index_name = null;
		int maxdocs = 100;
		int hits_per_page = 20;
		int start_page = 1;
		// set up the query params
		Set entries = params.entrySet();
		Iterator i = entries.iterator();
		while (i.hasNext())
		{
			Map.Entry m = (Map.Entry) i.next();
			String name = (String) m.getKey();
			String value = (String) m.getValue();

			///System.err.println("### GS2SolrSearch.java: name " + name + " - value " + value);

			if (name.equals(MAXDOCS_PARAM) && !value.equals(""))
			{
				maxdocs = Integer.parseInt(value);
			}
			else if (name.equals(HITS_PER_PAGE_PARAM))
			{
				hits_per_page = Integer.parseInt(value);
			}
			else if (name.equals(START_PAGE_PARAM))
			{
				start_page = Integer.parseInt(value);
			}
			else if (name.equals(MATCH_PARAM))
			{
				if (value.equals(MATCH_PARAM_ALL))
				{
					this.solr_src.setDefaultConjunctionOperator("AND");
				}
				else
				{
					this.solr_src.setDefaultConjunctionOperator("OR");
				}
			}
			else if (name.equals(RANK_PARAM))
			{
				if (value.equals(RANK_PARAM_RANK))
				{
				  value = SolrQueryWrapper.SORT_BY_RANK;
				} else if (value.equals(RANK_PARAM_NONE)) {
				    value = SolrQueryWrapper.SORT_BY_INDEX_ORDER;
				  }
		       
				this.solr_src.setSortField(value);
			}
			else if (name.equals(SORT_ORDER_PARAM)) {
			    if (value.equals(SORT_ORDER_DESCENDING)) {
			      this.solr_src.setSortOrder(SolrQueryWrapper.SORT_DESCENDING);
			    } else {
			      this.solr_src.setSortOrder(SolrQueryWrapper.SORT_ASCENDING);
			    }
			  }
			else if (name.equals(LEVEL_PARAM))
			{
				if (value.toUpperCase().equals("SEC"))
				{
					index = "sidx";
				}
				else
				{
					index = "didx";
				}
			}
			// Would facets ever come in through params???
			else if (name.equals("facets") && value.length() > 0)
			{
				String[] facets = value.split(",");

				for (String facet : facets)
				{
					this.solr_src.addFacet(facet);
				}
			}
			else if (name.equals("facetQueries") && value.length() > 0)
			{
				this.solr_src.addFacetQuery(value);
			}
			else if (name.equals(INDEX_SUBCOLLECTION_PARAM))
			{
				physical_sub_index_name = value;
			}
			else if (name.equals(INDEX_LANGUAGE_PARAM))
			{
				physical_index_language_name = value;
			} // ignore any others
		}
		// set up start and end results if necessary
		int start_results = 0;
		if (start_page != 1)
		{
			start_results = ((start_page - 1) * hits_per_page) ;
		}
		int end_results = hits_per_page * start_page;
		this.solr_src.setStartResults(start_results);
		this.solr_src.setEndResults(end_results);
		this.solr_src.setMaxDocs(maxdocs);

		if (index.equals("sidx") || index.equals("didx"))
		{
			if (physical_sub_index_name != null)
			{
				index += physical_sub_index_name;
			}
			if (physical_index_language_name != null)
			{
				index += physical_index_language_name;
			}
		}

		// now we know the index level, we can dig out the required
		// solr-core, (caching the result in 'solr_core_cache')
		String core_name = getCollectionCoreNamePrefix() + "-" + index;
		
		SolrServer solr_core = null;

		if (!solr_core_cache.containsKey(core_name))
		{		    
		    solr_core = new HttpSolrServer(this.solr_servlet_base_url+"/"+core_name);
		    solr_core_cache.put(core_name, solr_core);		    
		}
		else
		{
		    solr_core = solr_core_cache.get(core_name);
		}

		this.solr_src.setSolrCore(solr_core);
		this.solr_src.setCollectionCoreNamePrefix(getCollectionCoreNamePrefix());
		this.solr_src.initialise();
		return true;
	}

	/** do the query */
	protected Object runQuery(String query)
	{
		try
		{
			//if it is a Highlighting Query - execute it
			this.solr_src.setHighlightField(indexField);
			if(hldocOID != null)
			{
				String rslt = this.solr_src.runHighlightingQuery(query,hldocOID);
				// Check result
				if (rslt != null)
				{
				return rslt;
				}
				//Highlighting request failed. Do standard request.
				hldocOID = null;
			}
			SharedSoleneQueryResult sqr = this.solr_src.runQuery(query);

			return sqr;
		}
		catch (Exception e)
		{
			logger.error("Exception happened in run query: ", e);
		}

		return null;
	}
	
	
	/** get the total number of docs that match */
	protected long numDocsMatched(Object query_result)
	{
		return ((SharedSoleneQueryResult) query_result).getTotalDocs();

	}

	/** get the list of doc ids */
	protected String[] getDocIDs(Object query_result)
	{
		Vector docs = ((SharedSoleneQueryResult) query_result).getDocs();
		String[] doc_nums = new String[docs.size()];
		for (int d = 0; d < docs.size(); d++)
		{
			String doc_num = ((SharedSoleneQueryResult.DocInfo) docs.elementAt(d)).id_;
			doc_nums[d] = doc_num;
		}
		return doc_nums;
	}

	/** get the list of doc ranks */
	protected String[] getDocRanks(Object query_result)
	{
		Vector docs = ((SharedSoleneQueryResult) query_result).getDocs();
		String[] doc_ranks = new String[docs.size()];
		for (int d = 0; d < docs.size(); d++)
		{
			doc_ranks[d] = Float.toString(((SharedSoleneQueryResult.DocInfo) docs.elementAt(d)).rank_);
		}
		return doc_ranks;
	}

	/** add in term info if available */
	protected boolean addTermInfo(Element term_list, HashMap params, Object query_result)
	{
	  Document doc = term_list.getOwnerDocument();
		String query_level = (String) params.get(LEVEL_PARAM); // the current query level

		Vector terms = ((SharedSoleneQueryResult) query_result).getTerms();
		for (int t = 0; t < terms.size(); t++)
		{
			SharedSoleneQueryResult.TermInfo term_info = (SharedSoleneQueryResult.TermInfo) terms.get(t);

			Element term_elem = doc.createElement(GSXML.TERM_ELEM);
			term_elem.setAttribute(GSXML.NAME_ATT, term_info.term_);
			term_elem.setAttribute(FREQ_ATT, "" + term_info.term_freq_);
			term_elem.setAttribute(NUM_DOCS_MATCH_ATT, "" + term_info.match_docs_);
			term_elem.setAttribute(FIELD_ATT, term_info.field_);
			term_list.appendChild(term_elem);
		}

		Vector stopwords = ((SharedSoleneQueryResult) query_result).getStopWords();
		for (int t = 0; t < stopwords.size(); t++)
		{
			String stopword = (String) stopwords.get(t);

			Element stopword_elem = doc.createElement(GSXML.STOPWORD_ELEM);
			stopword_elem.setAttribute(GSXML.NAME_ATT, stopword);
			term_list.appendChild(stopword_elem);
		}

		return true;
	}

	protected ArrayList<FacetWrapper> getFacets(Object query_result)
	{
		if (!(query_result instanceof SolrQueryResult))
		{
			return null;
		}

		SolrQueryResult result = (SolrQueryResult) query_result;
		List<FacetField> facets = result.getFacetResults();

		if (facets == null)
		{
			return null;
		}

		ArrayList<FacetWrapper> newFacetList = new ArrayList<FacetWrapper>();

		for (FacetField facet : facets)
		{
		  SolrFacetWrapper wrap = new SolrFacetWrapper(facet);
		  // String name = wrap.getName();
		  // String display_name = "Poo";
		  // wrap.setDisplayName(display_name);
		    
		  newFacetList.add(wrap);
		}

		return newFacetList;
	}
	@Override
	protected Map<String, Map<String, List<String>>> getHighlightSnippets(Object query_result)
	{
		if (!(query_result instanceof SolrQueryResult))
		{
			return null;
		}

		SolrQueryResult result = (SolrQueryResult) query_result;
		
		return result.getHighlightResults();
	}


    protected String getCollectionCoreNamePrefix() {
	String site_name = this.router.getSiteName();
	String coll_name = this.cluster_name;
	String collection_core_name_prefix = site_name + "-" + coll_name;
	return collection_core_name_prefix;
    }
}
