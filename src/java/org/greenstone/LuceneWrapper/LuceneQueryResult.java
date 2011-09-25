/**********************************************************************
 *
 * LuceneQueryResult.java 
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

package org.greenstone.LuceneWrapper;

import java.util.Vector;

/** Opportunity to fine tune QueryResult for lucene search
 *
 */

public class LuceneQueryResult extends SharedSoleneQueryResult {
    
    // Currently no fine tuning -- rely on underlying shared Solr/Lucene base class
    LuceneQueryResult() {
	super();
    } 
}
