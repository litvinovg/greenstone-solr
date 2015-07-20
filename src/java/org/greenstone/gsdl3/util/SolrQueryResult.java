/**********************************************************************
 *
 * SolrQueryResult.java 
 *
 * Copyright 2007 The New Zealand Digital Library Project
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

import java.util.List;
import java.util.Map;

import org.apache.solr.client.solrj.response.FacetField;
import org.greenstone.LuceneWrapper4.SharedSoleneQueryResult;

/**
 * Opportunity to fine tune QueryResult for solr search, such as facets,
 * spelling corrections, etc.
 * 
 */

public class SolrQueryResult extends SharedSoleneQueryResult
{
	protected List<FacetField> _facetResults = null;
	protected Map<String,Map<String,List<String>>> _highlightResults = null;
	SolrQueryResult()
	{
		super();
	}
	public void setFacetResults(List<FacetField> facetResults)
	{
		_facetResults = facetResults;
	}
	
	public List<FacetField> getFacetResults()
	{
		return _facetResults;
	}
	//Save highlighting snippets
	public void setHighlightResults(Map<String,Map<String,List<String>>> highlightResults){
		_highlightResults = highlightResults;
	}
	//Extract highlighting snippets
	public Map<String,Map<String,List<String>>> getHighlightResults(){
		return _highlightResults;
	}
	
}
