/**********************************************************************
 *
 * SharedSoleneQueryResult.java 
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

/** a QueryResult class for a lucene search
 *
 */
public class SharedSoleneQueryResult {
    
    public static final int NO_ERROR = 0;
    public static final int PARSE_ERROR = 1;
    public static final int TOO_MANY_CLAUSES_ERROR = 2;
    public static final int IO_ERROR = 3;
    public static final int SERVER_ERROR = 4;
    public static final int OTHER_ERROR = 5;
    
    /** the list of DocInfo */
    protected Vector docs_=null;
    /** the list of TermInfo */
    protected Vector terms_=null;
    /** the list of stopwords found in the query */
    protected Vector stopwords_ = null;
    /** the total number of docs found - not necessarily the size of docs_*/
    protected int total_num_docs_=0;
    /** the start result number if we are retrieving only a portion of the results */
    protected int start_results_ = 0;
    /** the end result number if we are retrieving only a portion of the results */
    protected int end_results_ = 0;
    /** whether an error has occurred and what kind it is*/
    protected int error_ = NO_ERROR;

    public SharedSoleneQueryResult() {
	docs_ = new Vector();
	terms_ = new Vector();
	stopwords_ = new Vector();
    }
    
    /** clear the info from the last query - should be called before setting any new docs/terms */
    public void clear() {
	total_num_docs_=0;
	docs_.clear();
	terms_.clear();
	stopwords_.clear();
	error_ = NO_ERROR;
    }

    /** returns the result as a String - useful for printing out results */
    public String toString() {
	
	String result = "";
	result += "docs (ranks): ";
	for (int i=0; i<docs_.size(); i++) {
	    result += ((DocInfo)docs_.elementAt(i)).toString()+", ";
	}
	result += "\nterms: ";
	for (int i=0; i<terms_.size(); i++) {
	    result += ((TermInfo)terms_.elementAt(i)).toString()+", ";
	}
	result += "\nactual number of docs found = "+total_num_docs_;
	
	return result;
    }
    /** a shorter representation - just terms and total docs - not the 
	individual docnums and ranks */
    public String toShortString() {
	String result = "";
	result += "\nterms: ";
	for (int i=0; i<terms_.size(); i++) {
	    result += ((TermInfo)terms_.elementAt(i)).toString()+", ";
	}
	result += "\nactual number of docs found = "+total_num_docs_;
	return result;
    }
    
    public void setTotalDocs(int num) {
	total_num_docs_=num;
    }
    
    public void setStartResults(int start) {
	start_results_ = start;
    }

    public void setEndResults(int end) {
	end_results_ = end;
    }

    public void addDoc(String id, float rank, int termfreq)
    {
	docs_.add(new DocInfo(id, rank, termfreq));
    }
    
    public void addTerm(String term, String field, int match, int freq) {
	TermInfo ti = new TermInfo();
	ti.term_=term;
	ti.field_=field;
	ti.match_docs_=match;
	ti.term_freq_=freq;
	terms_.add(ti);
    }
    public void addStopWord(String stopword) {
	stopwords_.add(stopword);
    }
    public Vector getDocs() {
	return docs_;
    }
    
    public int getError() {
	return error_;
    }
    
    public String getErrorString() {
	if (error_ == PARSE_ERROR) {
	    return "PARSE_EXCEPTION";
	}
	if (error_ == TOO_MANY_CLAUSES_ERROR) {
	    return "TOO_MANY_CLAUSES";
	}
	if (error_ == IO_ERROR) {
	    return "IO_ERROR";
	}
	if (error_ == NO_ERROR) {
	    return "NO_ERROR";
	}
	return "UNKNOWN";
    }

    public Vector getTerms() {
	return terms_;
    }
    
    public Vector getStopWords() {
	return stopwords_;
    }
    public int getTotalDocs() {
	return total_num_docs_;
    }
    
    public void setError(int error) {
	error_ = error;
    }
    
    public String getXMLString() {
	StringBuffer buffer = new StringBuffer();

	// terms
	buffer.append("<QueryTermsInfo num=\"" + terms_.size() + "\"/>\n");
	for (int i=0; i<terms_.size(); i++) {
	    buffer.append(((TermInfo)terms_.elementAt(i)).toXMLString()+"\n");
	}

	// stopwords
	for (int i=0; i<stopwords_.size(); i++) {
	    buffer.append("<StopWord value=\"" + (String)stopwords_.elementAt(i)+"\" />\n");
	}
	
	// results
	buffer.append("<MatchingDocsInfo num=\"" + total_num_docs_ + "\"/>\n");
	buffer.append("<StartResults num=\"" + start_results_ + "\"/>\n");
	buffer.append("<EndResults num=\"" + end_results_ + "\"/>\n");
	
	for (int i=0; i< docs_.size(); i++) {
	    buffer.append(((DocInfo)docs_.elementAt(i)).toXMLString()+"\n");
	}

	return buffer.toString();
    }

 
    public class TermInfo {
	
	/** the term itself */
	public String term_=null;
	/** the field for which this term was queried */
	public String field_=null;
	/** the number of documents containing this term */
	public int match_docs_=0;
	/** overall term freq for this term */
	public int term_freq_=0;
	
	public TermInfo() {
	}
	
	/** output the class as a string */
	public String toString() {
	    String result="";
	    result +="<"+field_+">\""+term_+" docs("+match_docs_;
	    result +=")freq("+term_freq_+")";
	    return result;
	}

	/** output as an XML element */
	public String toXMLString() {
	    return "<Term value=\"" + xmlSafe(term_) + "\" field=\"" + field_ + "\" freq=\"" + term_freq_ + "\" />";
	}
    }


    public class DocInfo
    {
	public String id_ = "";
	public float rank_ = 0;
	public int termfreq_ = 0;

	public DocInfo (String id, float rank, int termfreq)
	{
	    id_ = id;
	    rank_ = rank;
	    termfreq_ = termfreq;
	}

	public String toString()
	{
	    return "" + id_ + " (" + rank_ + ") (" + termfreq_ + ")";
	}

	public String toXMLString()
	{
	    return "<Match id=\"" + id_ + "\" rank=\"" + rank_ + "\" termfreq=\"" + termfreq_ + "\" />";
	}
    }


    // where should this go???
    public static String xmlSafe(String text) {
	text = text.replaceAll("&","&amp;amp;");
	text = text.replaceAll("<","&amp;lt;");
	text = text.replaceAll(">","&amp;gt;");
	text = text.replaceAll("'","&amp;#039;");
	text = text.replaceAll("\\\"","&amp;quot;");
	return text;
    }
 
}
