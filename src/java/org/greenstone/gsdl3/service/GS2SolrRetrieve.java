/*
 *    GS2SolrRetrieve.java
 *    Copyright (C) 2005 New Zealand Digital Library, http://www.nzdl.org
 *
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 2 of the License, or
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

import org.apache.log4j.Logger;
import org.greenstone.gsdl3.core.GSException;
import org.greenstone.gsdl3.util.GSXML;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

/** Does excatly the same as GS2LuceneRetrieve.  Wrap up in a bit of
 *  inheritance so logging messages are more appropriate */

// Consider changing GS2LuceneRetrieve to GS2DocXMLRetrieve and
// official have this service specified by perl builder/builderproc
// code

public class GS2SolrRetrieve
    extends GS2LuceneRetrieve
{
    
    static Logger logger = Logger.getLogger(org.greenstone.gsdl3.service.GS2SolrRetrieve.class.getName());
    
    public GS2SolrRetrieve() {
	super();
    }
    @Override
    protected Element getNodeContent(Document doc, String doc_id, String lang) throws GSException
   	{
     // IF we need only process our content by macro resolver
    	if (highlightedNode != null) 
    	{
    		String highlightedTextContent = highlightedNode.getTextContent();
    		
    		//Process text
    		highlightedTextContent = resolveTextMacros(highlightedTextContent, doc_id, lang);
    		//Create result element
    		Element highlighted_node = doc.createElement(GSXML.NODE_CONTENT_ELEM);
    		Text hlt = doc.createTextNode(highlightedTextContent);
       		highlighted_node.appendChild(hlt);
       		return highlighted_node;
    	}
    	return super.getNodeContent(doc, doc_id, lang);
 	
   	}
}

