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

import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.greenstone.LuceneWrapper3.SharedSoleneQuery;
import org.greenstone.LuceneWrapper3.SharedSoleneQueryResult;

public class SolrQueryWrapper extends SharedSoleneQuery
{

	static Logger logger = Logger.getLogger(org.greenstone.gsdl3.util.SolrQueryWrapper.class.getName());

	protected int max_docs = 100;

	SolrServer solr_core = null;

	public SolrQueryWrapper()
	{
		super();
	}

	public void setMaxDocs(int max_docs)
	{
		this.max_docs = max_docs;
	}

	public void setSolrCore(SolrServer solr_core)
	{
		this.solr_core = solr_core;
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

		ModifiableSolrParams solrParams = new ModifiableSolrParams();
		solrParams.set("q", query_string);
		solrParams.set("start", start_results);
		solrParams.set("rows", (end_results - start_results) + 1);
		solrParams.set("fl", "docOID score");

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

				// numDocsFound is the total number of mactching docs in the collection
				// as opposed to the number of documents returned in the hits list

				solr_query_result.setTotalDocs((int) hits.getNumFound());

				solr_query_result.setStartResults(start_results);
				solr_query_result.setEndResults(start_results + hits.size());

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
		}
		catch (SolrServerException server_exception)
		{
			solr_query_result.setError(SolrQueryResult.SERVER_ERROR);
		}

		return solr_query_result;
	}

	public void cleanUp()
	{
		super.cleanUp();
	}
}
