/**********************************************************************
 *
 * FacetWrapper.java 
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

import java.util.HashMap;

import org.apache.solr.client.solrj.response.FacetField;

public class SolrFacetWrapper extends FacetWrapper
{
	protected String _name = null;
	protected HashMap<String, Long> _counts = new HashMap<String, Long>();
	
	public SolrFacetWrapper(FacetField facet)
	{
		_name = facet.getName();
		
		for(FacetField.Count count : facet.getValues())
		{
			_counts.put(count.getName(), count.getCount());
		}
	}
	
	public String getName()
	{
		return _name;
	}
	
	public HashMap<String, Long> getCounts()
	{
		return _counts;
	}
}
