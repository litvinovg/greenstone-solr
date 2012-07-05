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
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.SolrCore;
import org.greenstone.LuceneWrapper3.SharedSoleneQueryResult;
import org.greenstone.gsdl3.util.FacetWrapper;
import org.greenstone.gsdl3.util.GSFile;
import org.greenstone.gsdl3.util.GSXML;
import org.greenstone.gsdl3.util.SolrFacetWrapper;
import org.greenstone.gsdl3.util.SolrQueryResult;
import org.greenstone.gsdl3.util.SolrQueryWrapper;
import org.greenstone.util.GlobalProperties;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class GS2SolrSearch extends SharedSoleneGS2FieldSearch
{
	static Logger logger = Logger.getLogger(org.greenstone.gsdl3.service.GS2SolrSearch.class.getName());

	static protected CoreContainer all_solr_cores = null;

	protected HashMap solr_core_cache;
	protected SolrQueryWrapper solr_src = null;

	protected ArrayList<String> _facets = new ArrayList<String>();

	public GS2SolrSearch()
	{
		does_faceting = true;
		// Used to store the solr cores that match the required 'level' 
		// of search (e.g. either document-level=>didx, or 
		// section-level=>sidx.  The hashmap is filled out on demand
		// based on 'level' parameter passed in to 'setUpQueryer()'

		solr_core_cache = new HashMap();

		if (all_solr_cores == null)
		{
			// Share one CoreContainer across all sites/collections
			try
			{
				String gsdl3_home = GlobalProperties.getGSDL3Home();
				String solr_ext_name = GlobalProperties.getProperty("gsdlext.solr.dirname", "solr");

				String solr_home_str = GSFile.extHome(gsdl3_home, solr_ext_name);

				all_solr_cores = new CoreContainer(solr_home_str);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}

		this.solr_src = new SolrQueryWrapper();
	}

	/** configure this service */
	public boolean configure(Element info, Element extra_info)
	{
		boolean success = super.configure(info, extra_info);

		// 1. Make the CoreContainer reload solr.xml
		// This is particularly needed for when activate.pl is executed during
		// a running GS3 server. At that point, the solr collection is reactivated and 
		// we need to tell Greenstone that the solr index has changed. This requires
		// the CoreContainer to reload the solr.xml file, and it all works again.

		solr_core_cache.clear(); // clear the map of solr cores for this collection added to the map upon querying
	    
		// Reload the updated solr.xml into the CoreContainer
		// (Doing an all_solr_cores.shutdown() first doesn't seem to be required)
		try { 	
		    String solr_home_str = all_solr_cores.getSolrHome();
		    File solr_home = new File(solr_home_str);
		    File solr_xml = new File( solr_home,"solr.xml" );
		    
		    all_solr_cores.load(solr_home_str,solr_xml);

		} catch (Exception e) {
		    logger.error("Exception in GS2SolrSearch.configure(): " + e.getMessage());
		    e.printStackTrace();
		    return false;
		}
		
		if(!success) {
		    return false;
		}
		
		// 2. Setting up facets
		Element searchElem = (Element) GSXML.getChildByTagName(extra_info, GSXML.SEARCH_ELEM);
		NodeList configIndexElems = searchElem.getElementsByTagName(GSXML.INDEX_ELEM);

		ArrayList<String> chosenFacets = new ArrayList<String>();
		for (int i = 0; i < configIndexElems.getLength(); i++)
		{
			Element current = (Element) configIndexElems.item(i);
			if (current.getAttribute(GSXML.FACET_ATT).equals("true"))
			{
				chosenFacets.add(current.getAttribute(GSXML.NAME_ATT));
			}
		}

		Element indexListElem = (Element) GSXML.getChildByTagName(info, GSXML.INDEX_ELEM + GSXML.LIST_MODIFIER);
		NodeList buildIndexElems = indexListElem.getElementsByTagName(GSXML.INDEX_ELEM);

		for (int j = 0; j < buildIndexElems.getLength(); j++)
		{
			Element current = (Element) buildIndexElems.item(j);
			for (int i = 0; i < chosenFacets.size(); i++)
			{
				if (current.getAttribute(GSXML.NAME_ATT).equals(chosenFacets.get(i)))
				{
					_facets.add(current.getAttribute(GSXML.SHORTNAME_ATT));
				}
			}
		}

		return true;
	}

	public void cleanUp()
	{
		super.cleanUp();
		this.solr_src.cleanUp();

		// When cleaning up, not only do we need to empty the solr_core_cache map, but we also need to remove all
		// references to this collection's sorlcores in the CoreContainer object, which can be more SolrCores than
		// the EmbeddedSolrServers instantiated and added to the solr_core_cache, since the cache does lazy loading 
		// while the CoreContainer contains all the cores defined in solr.xml, which includes all *possible* cores
		// for this collection even if EmbeddedSolrServers for these were not added to the solr_core_cache_map.

		// 1. clear the map keeping track of the solrcores' EmbeddedSolrServers in this collection
		solr_core_cache.clear();

		// 2. Remove all SolrCores in the CoreContainer (all_solr_cores) that are specific to this collection
		String collection_core_name_prefix = getCollectionCoreNamePrefix();

		Collection<String> coreNames = all_solr_cores.getCoreNames();
		if(!coreNames.isEmpty()) {
		    Iterator<String> coreIterator = coreNames.iterator();
		    while(coreIterator.hasNext()) {

			String solrCoreName = coreIterator.next();		
			if(solrCoreName.startsWith(collection_core_name_prefix)) {

			    logger.error("**** Removing collection-specific core: " + solrCoreName + " from CoreContainer");

			    // CoreContainer.remove(String name): removes and returns registered core w/o decrementing it's reference count
			    // http://lucene.apache.org/solr/api/index.html?org/apache/solr/core/CoreContainer.html
			    SolrCore solr_core = all_solr_cores.remove(solrCoreName);
			    while(!solr_core.isClosed()) {
				logger.error("@@@@@@ " + solrCoreName + " was not closed. Closing....");
				solr_core.close(); // http://lucene.apache.org/solr/api/org/apache/solr/core/SolrCore.html
			    } 
			    if(solr_core.isClosed()) {
				logger.error("@@@@@@ " + solrCoreName + " is closed.");
			    }
			    solr_core = null;
			}
		    }
		}

		// 3. if there are no more solr cores in Greenstone, then all_solr_cores will be empty, null the CoreContainer
		// All going well, this will happen when we're ant stopping the Greenstone server and the last Solr collection
		// is being deactivated
		Collection<String> coreNamesRemaining = all_solr_cores.getCoreNames();
		if(coreNamesRemaining.isEmpty()) {
		    logger.error("**** CoreContainer contains 0 solrCores. Shutting down...");

		    all_solr_cores.shutdown(); // wouldn't do anything anyway for 0 cores I think
		    all_solr_cores = null;
		} 
		else { // else part is just for debugging
		    Iterator coreIterator = coreNamesRemaining.iterator();
		    while(coreIterator.hasNext()) {
			logger.error("**** Core: " + coreIterator.next() + " still exists in CoreContainer");
		    }
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
				if (value.equals(RANK_PARAM_RANK_VALUE))
				{
					value = null;
				}
				this.solr_src.setSortField(value);
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
		int start_results = 1;
		if (start_page != 1)
		{
			start_results = ((start_page - 1) * hits_per_page) + 1;
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

		EmbeddedSolrServer solr_core = null;

		if (!solr_core_cache.containsKey(core_name))
		{
			solr_core = new EmbeddedSolrServer(all_solr_cores, core_name);

			solr_core_cache.put(core_name, solr_core);
		}
		else
		{
			solr_core = (EmbeddedSolrServer) solr_core_cache.get(core_name);
		}

		this.solr_src.setSolrCore(solr_core);
		this.solr_src.initialise();
		return true;
	}

	/** do the query */
	protected Object runQuery(String query)
	{
		try
		{
			//SharedSoleneQueryResult sqr = this.solr_src.runQuery(query);
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
		String query_level = (String) params.get(LEVEL_PARAM); // the current query level

		Vector terms = ((SharedSoleneQueryResult) query_result).getTerms();
		for (int t = 0; t < terms.size(); t++)
		{
			SharedSoleneQueryResult.TermInfo term_info = (SharedSoleneQueryResult.TermInfo) terms.get(t);

			Element term_elem = this.doc.createElement(GSXML.TERM_ELEM);
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

			Element stopword_elem = this.doc.createElement(GSXML.STOPWORD_ELEM);
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
			newFacetList.add(new SolrFacetWrapper(facet));
		}

		return newFacetList;
	}


    protected String getCollectionCoreNamePrefix() {
	String site_name = this.router.getSiteName();
	String coll_name = this.cluster_name;
	String collection_core_name_prefix = site_name + "-" + coll_name;
	return collection_core_name_prefix;
    }
}
