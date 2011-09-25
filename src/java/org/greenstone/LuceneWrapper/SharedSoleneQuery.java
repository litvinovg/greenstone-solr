/**********************************************************************
 *
 * SharedSoleneQuery.java 
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
package org.greenstone.LuceneWrapper;


import java.io.*;
import java.util.*;
import java.util.regex.*;



abstract public class SharedSoleneQuery
{
    static protected String TEXTFIELD = "TX";

    protected String default_conjunction_operator = "OR";
    protected String fuzziness = null;
    protected String sort_field = null;
    protected String filter_string = null;
    protected int start_results=1;
    protected int end_results=Integer.MAX_VALUE;

    static protected PrintWriter utf8out = null;

    static
    {
	try {
	    OutputStreamWriter osw = new OutputStreamWriter(System.out, "UTF-8");
	    utf8out = new PrintWriter(osw, true);
	}
        catch (UnsupportedEncodingException e) { 
	    System.out.println(e); 
	}
    }

    
    public SharedSoleneQuery() {
	// nothing currently to do in shared base class to Solr/Lucene
    }
    
    
    public boolean initialise() {
	return true;
    }
    
    abstract public SharedSoleneQueryResult runQuery(String query_string);
    

    public void setDefaultConjunctionOperator(String default_conjunction_operator) {
	this.default_conjunction_operator = default_conjunction_operator.toUpperCase();
    }
    
    public String getDefaultConjunctionOperator() {
	return this.default_conjunction_operator;
    }
    
    public void setEndResults(int end_results) {
	this.end_results = end_results;
    }
    public int getEndResults() {
	return this.end_results;
    }
        
    public void setFilterString(String filter_string) {
	this.filter_string = filter_string;
    }
    public String getFilterString() {
	return this.filter_string ;
    }
        
    public void setFuzziness(String fuzziness) {
	this.fuzziness = fuzziness;
    }
    public String getFuzziness() {
	return this.fuzziness;
    }
    
    public void setSortField(String sort_field) {
	this.sort_field = sort_field;
    }
    public String getSortField() {
	return this.sort_field;
    }
        
    public void setStartResults(int start_results) {
	if (start_results < 1) {
	    start_results = 1;
	}
	this.start_results = start_results;
    }
    public int getStartResults() {
	return this.start_results;
    }
        
    public void cleanUp() {
	// nothing currently to do in shared Solr/Lucene base class
    }

    protected void finalize() throws Throwable 
    {
	try {
	    utf8out.flush(); 
	} finally {
	    super.finalize();
	}
    }


    
}


