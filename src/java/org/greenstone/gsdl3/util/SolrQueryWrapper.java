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
import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.greenstone.LuceneWrapper3.SharedSoleneQuery;
import org.greenstone.LuceneWrapper3.SharedSoleneQueryResult;

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

		ModifiableSolrParams solrParams = new ModifiableSolrParams();
		solrParams.set("q", query_string);
		// sort param, like "score desc" or "byORG asc"
		solrParams.set("sort", this.sort_field+" "+this.sort_order);
		// which result to start from
		solrParams.set("start", start_results);
		// how many results per "page"
		solrParams.set("rows", (end_results - start_results) + 1);
		// which fields to return for each document
		solrParams.set("fl", "docOID score");
		// turn on the termsComponent
		solrParams.set("terms", true);
		// which field to get the terms from
		solrParams.set("terms.fl", "ZZ");

		if (_facets.size() > 0)
		{
		  // enable facet counts in the query response
			solrParams.set("facet", "true");
			for (int i = 0; i < _facets.size(); i++)
			{
			  // add this field as a facet
				solrParams.add("facet.field", _facets.get(i));
			}
		}

		try
		{
			QueryResponse solrResponse = solr_core.query(solrParams);
			SolrDocumentList hits = solrResponse.getResults();

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

				int sepIndex = query_string.indexOf(":");
				String field = query_string.substring(0, sepIndex);
				String query = query_string.substring(sepIndex + 2, query_string.length() - 1);

				solr_query_result.addTerm(query, field, (int) hits.getNumFound(), -1);

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

					solr_query_result.addDoc(docOID, score.floatValue(), doc_term_freq);
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
