###########################################################################
#
# solrbuildproc.pm -- perl wrapper for building index with Solr
# A component of the Greenstone digital library software
# from the New Zealand Digital Library Project at the 
# University of Waikato, New Zealand.
#
# Copyright (C) 1999 New Zealand Digital Library Project
#
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation; either version 2 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
#
###########################################################################

package solrbuildproc;

# This document processor outputs a document for solr to process

# Rather then use the XML structure developed for mgppbuilder/mgppbuildproc
# whose use was then extended to Lucene, Solr has its own XML syntax:
#
#  http://wiki.apache.org/solr/UpdateXmlMessages
#
# Using this means we don't need to write SolrWrapper.jar, as had to be
# done for Lucene, translating the XML syntax piped to it into appropriate
# calls to the Lucene API


use lucenebuildproc;
use ghtml;
use strict;
no strict 'refs'; # allow filehandles to be variables and viceversa


use IncrementalBuildUtils;

sub BEGIN {
    @solrbuildproc::ISA = ('lucenebuildproc');
}


sub new {
    my $class = shift @_;
    my $self = new lucenebuildproc (@_);

    return bless $self, $class;
}


#----

sub index_field_mapping_edit {
    my $self = shift (@_);
    my ($doc_obj,$file,$edit_mode) = @_;

    # Only add/update gets to here
    # Currently there is no need to distinguish between these edit modes

    my $outhandle = $self->{'outhandle'};

    # only study this document if it is one to be indexed
    return if ($doc_obj->get_doc_type() ne "indexed_doc");

    my $indexed_doc = $self->is_subcollection_doc($doc_obj);

    # get the parameters for the output
    # split on : just in case there is subcoll and lang stuff
    my ($fields) = split (/:/, $self->{'index'});

    my $doc_section = 0; # just for this document

    # get the text for this document
    my $section = $doc_obj->get_top_section();

    while (defined $section)
    {
	$doc_section++;

	# if we are doing subcollections, then some docs shouldn't be 
	# considered for indexing

	my $indexed_section 
	    = $doc_obj->get_metadata_element($section, "gsdldoctype") 
	      || "indexed_section";

	if (($indexed_doc == 0) 
	    || ($indexed_section ne "indexed_section" && $indexed_section ne "indexed_doc")) {
            $section = $doc_obj->get_next_section($section);
	    next;
          }

	# has the user added a 'metadata' index?
	my $all_metadata_specified = 0;

	# which fields have already been indexed?
	# (same as fields, but in a map)
	my $specified_fields = {};
	
	# do we have an allfields index??
	my $allfields_index = 0;

	# collect up all the text for it in here
	my $allfields_text = "";

	foreach my $field (split (/;/, $fields)) {
	    if ($field eq "allfields") {
		$allfields_index = 1;
	    } elsif ($field eq "metadata") {
		$all_metadata_specified = 1;
	    }
	}
	
	foreach my $field (split (/;/, $fields)) {
	    
	    # only deal with this field if it doesn't start with top or
	    # this is the first section
	    my $real_field = $field;
	    next if (($real_field =~ s/^top//) && ($doc_section != 1));
	    
	    # process these two later
	    next if ($real_field eq "allfields" || $real_field eq "metadata");
	    
	    # individual metadata and or text specified 
	    # -- could be a comma separated list
	    $specified_fields->{$real_field} = 1;

	    if (!defined $self->{'indexfieldmap'}->{$real_field}) {
		my $shortname = $self->create_shortname($real_field);
		$self->{'indexfieldmap'}->{$real_field} = $shortname;
		$self->{'indexfieldmap'}->{$shortname} = 1;
	    }	    
	} # foreach field


   	if ($all_metadata_specified) {
	    
	    my $new_text = "";
	    my $shortname = "";
	    my $metadata = $doc_obj->get_all_metadata ($section);

	    foreach my $pair (@$metadata) {
		my ($mfield, $mvalue) = (@$pair);

		# no value
		next unless defined $mvalue && $mvalue ne "";

		# we have already indexed this
		next if defined ($specified_fields->{$mfield});

		# check fields here, maybe others dont want - change to use dontindex!!
		next if ($mfield eq "Identifier" || $mfield eq "classifytype" || $mfield eq "assocfilepath");
		next if ($mfield =~ /^gsdl/);
		
		if (defined $self->{'indexfieldmap'}->{$mfield}) {
		    $shortname = $self->{'indexfieldmap'}->{$mfield};
		}
		else {
		    $shortname = $self->create_shortname($mfield);
		    $self->{'indexfieldmap'}->{$mfield} = $shortname;
		    $self->{'indexfieldmap'}->{$shortname} = 1;
		}	   

		if (!defined $self->{'indexfields'}->{$mfield}) {
		    $self->{'indexfields'}->{$mfield} = 1;
		}				    	    
	    }
	}

	if ($allfields_index) {
	    # add the index name mapping
	    $self->{'indexfieldmap'}->{"allfields"} = "ZZ";
	    $self->{'indexfieldmap'}->{"ZZ"} = 1;	    	    
	}
	    
        $section = $doc_obj->get_next_section($section);

    } # while defined section

    
}

sub index_field_mapping {
    my $self = shift (@_);
    my ($doc_obj,$file) = @_;

    $self->index_field_mapping_edit($doc_obj,$file,"add");
}

sub index_field_mappingreindex
{
    my $self = shift (@_);
    my ($doc_obj,$file) = @_;

    $self->index_field_mapping_edit($doc_obj,$file,"update");
}

sub index_field_mappingdelete
{
    my $self = shift (@_);
    my ($doc_obj,$file) = @_;

    return; # nothing to be done
}


#----

sub textedit {
    my $self = shift (@_);
    my ($doc_obj,$file,$edit_mode) = @_;


    if (!$self->get_indexing_text()) {
	# In text-compress mode:
	# => want document to be output in the simple <Doc>..</Doc> as is
	# done by its super-class 
	return $self->SUPER::textedit(@_); 
    }

    # "update" for $edit_mode near identical to "add" as we use Solr in its
    # default mode of replacing an existing document if the new document
    # has the same doc id.  Main area of difference between "add" and "update"
    # is that we do not update our 'stats' for number of documents or number
    # of bytes processed.  The latter is inaccurate, but considered better
    # than allowing the value to steadily climb.


    my $solrhandle = $self->{'output_handle'};
    my $outhandle = $self->{'outhandle'};

    # only output this document if it is one to be indexed
    return if ($doc_obj->get_doc_type() ne "indexed_doc");

    # skip this document if in "compress-text" mode and asked to delete it
    return if (!$self->get_indexing_text() && ($edit_mode eq "delete"));

    my $indexed_doc = $self->is_subcollection_doc($doc_obj);

    # this is another document
    if ($edit_mode eq "add") {
	$self->{'num_docs'} += 1;
    }
    elsif ($edit_mode eq "delete") {
	$self->{'num_docs'} -= 1;
    }

    # get the parameters for the output
    # split on : just in case there is subcoll and lang stuff
    my ($fields) = split (/:/, $self->{'index'});

    my $levels = $self->{'levels'};
    my $ldoc_level = $levels->{'document'};
    my $lsec_level = $levels->{'section'};

    my $gs2_docOID = $doc_obj->get_OID();

    my $start_doc;
    my $end_doc;

    if ($edit_mode eq "add") {
	$start_doc  = "  <add>\n";
	$start_doc .= "    <doc>\n";
	$start_doc .= "      <field name=\"docOID\">$gs2_docOID</field>\n";
	
	$end_doc    = "    </doc>\n";
	$end_doc   .= "  </add>\n"; 
    }
    else {
	$start_doc  = "  <delete>\n";
	$start_doc .= "    <id>$gs2_docOID</id>\n";

	$end_doc    = "  </delete>\n"; 
    }

    # add/update, delete

    my $sec_tag_name = "";
    if ($lsec_level)
    {
	$sec_tag_name = $mgppbuildproc::level_map{'section'};
    }

    my $doc_section = 0; # just for this document

    # only output if working with doc level
	# my $text = undef;
	
    my $text = ($sec_tag_name eq "") ? $start_doc : "";

#	  my $text = $start_doc if ($sec_tag_name eq "");
	  
    # get the text for this document
    my $section = $doc_obj->get_top_section();

    while (defined $section)
    {
	# update a few statistics
	$doc_section++;
	$self->{'num_sections'}++;

	my $sec_gs2_id = $self->{'num_sections'};
	my $sec_gs2_docOID = $gs2_docOID;
	$sec_gs2_docOID .= ".$section" if ($section ne "");
	
	my $start_sec;
	my $end_sec;

	if ($edit_mode eq "add") {
	    $start_sec  = "  <add>\n";
	    $start_sec .= "    <doc>\n";
	    $start_sec .= "      <field name=\"docOID\">$sec_gs2_docOID</field>\n";
	
	    $end_sec    = "    </doc>\n";
	    $end_sec   .= "  </add>\n"; 
	}
	else {
	    $start_sec  = "  <delete>\n";
	    $start_sec .= "    <id>$sec_gs2_docOID</id>\n";

	    $end_sec    = "  </delete>\n"; 
	}


	# if we are doing subcollections, then some docs shouldn't be indexed.
	# but we need to put the section tag placeholders in there so the
	# sections match up with database
	my $indexed_section = $doc_obj->get_metadata_element($section, "gsdldoctype") || "indexed_section";
	if (($indexed_doc == 0) || ($indexed_section ne "indexed_section" && $indexed_section ne "indexed_doc")) {
	    if ($sec_tag_name ne "") {
		$text .= $start_sec;
		$text .= $end_sec;
	    }
            $section = $doc_obj->get_next_section($section);
	    next;
          }

	# add in start section tag if indexing at the section level
	$text .= $start_sec if ($sec_tag_name ne "");

	if ($edit_mode eq "add") {
	    $self->{'num_bytes'} += $doc_obj->get_text_length ($section);
	}
	elsif ($edit_mode eq "delete") {
	    $self->{'num_bytes'} -= $doc_obj->get_text_length ($section);
	}


	# has the user added a 'metadata' index?
	my $all_metadata_specified = 0;
	# which fields have already been indexed? (same as fields, but in a map)
	my $specified_fields = {};
	
	# do we have an allfields index??
	my $allfields_index = 0;
	# collect up all the text for it in here
	my $allfields_text = "";
	foreach my $field (split (/;/, $fields)) {
	    if ($field eq "allfields") {
		$allfields_index = 1;
	    } elsif ($field eq "metadata") {
		$all_metadata_specified = 1;
	    }
	}
	
	foreach my $field (split (/;/, $fields)) {
	    
	    # only deal with this field if it doesn't start with top or
	    # this is the first section
	    my $real_field = $field;
	    next if (($real_field =~ s/^top//) && ($doc_section != 1));
	    
	    # process these two later
	    next if ($real_field eq "allfields" || $real_field eq "metadata");
	    
	    #individual metadata and or text specified - could be a comma separated list
	    $specified_fields->{$real_field} = 1;
	    my $shortname="";
	    my $new_field = 0; # have we found a new field name?
	    if (defined $self->{'indexfieldmap'}->{$real_field}) {
		$shortname = $self->{'indexfieldmap'}->{$real_field};
	    }
	    else {
		$shortname = $self->create_shortname($real_field);
		$new_field = 1;
	    }

	    my @metadata_list = (); # put any metadata values in here
	    my $section_text = ""; # put the text in here
	    foreach my $submeta (split /,/, $real_field) {
		if ($submeta eq "text") {
		    # no point in indexing text more than once
		    if ($section_text eq "") {
			$section_text = $doc_obj->get_text($section);
			if ($self->{'indexing_text'}) {
			    # we always strip html
			    $section_text = $self->preprocess_text($section_text, 1, "");
			}
			else { 
			    # leave html stuff in, but escape the tags
			    &ghtml::htmlsafe($section_text);
			}
		    }
		}
		else {
		    $submeta =~ s/^ex\.//; #strip off ex.

		    # its a metadata element
		    my @section_metadata = @{$doc_obj->get_metadata ($section, $submeta)};
		    if ($section ne $doc_obj->get_top_section() && $self->{'indexing_text'} && defined ($self->{'sections_index_document_metadata'})) {
			if ($self->{'sections_index_document_metadata'} eq "always" || ( scalar(@section_metadata) == 0 && $self->{'sections_index_document_metadata'} eq "unless_section_metadata_exists")) {
			    push (@section_metadata, @{$doc_obj->get_metadata ($doc_obj->get_top_section(), $submeta)});
			}
		    }
		    push (@metadata_list, @section_metadata);
		}
	    } # for each field in this one index
	    
	    # now we add the text and/or metadata into new_text
	    if ($section_text ne "" || scalar(@metadata_list)) {
		my $new_text = "";
		
		if ($section_text ne "") {
		    $new_text .= "$section_text ";
		}
		
		foreach my $item (@metadata_list) {
		    &ghtml::htmlsafe($item);
		    $new_text .= "$item ";
		}

		if ($allfields_index) {
		    $allfields_text .= $new_text;
		}

		# Remove any leading or trailing white space
		$new_text =~ s/\s+$//;
		$new_text =~ s/^\s+//;
	
		
		if ($self->{'indexing_text'}) {
		    # add the tag
		    $new_text = "<field name=\"$shortname\" >$new_text</field>\n";
		}
		# filter the text
		$new_text = $self->filter_text ($field, $new_text);

		if ($edit_mode eq "add") {
		    $self->{'num_processed_bytes'} += length ($new_text);
		    $text .= "$new_text";
		}
		elsif ($edit_mode eq "update") {
		    $text .= "$new_text";
		}
		elsif ($edit_mode eq "delete") {
		    $self->{'num_processed_bytes'} -= length ($new_text);
		}
		

		if ($self->{'indexing_text'} && $new_field) {
		    # we need to add to the list in indexfields
		    
		    $self->{'indexfieldmap'}->{$real_field} = $shortname;
		    $self->{'indexfieldmap'}->{$shortname} = 1;
		}
		
	    }
	    
	} # foreach field


   	if ($all_metadata_specified) {
	    
	    my $new_text = "";
	    my $shortname = "";
	    my $metadata = $doc_obj->get_all_metadata ($section);
	    foreach my $pair (@$metadata) {
		my ($mfield, $mvalue) = (@$pair);

		# no value
		next unless defined $mvalue && $mvalue ne "";

		# we have already indexed this
		next if defined ($specified_fields->{$mfield});

		# check fields here, maybe others dont want - change to use dontindex!!
		next if ($mfield eq "Identifier" || $mfield eq "classifytype" || $mfield eq "assocfilepath");
		next if ($mfield =~ /^gsdl/);
		
		&ghtml::htmlsafe($mvalue);
		
		if (defined $self->{'indexfieldmap'}->{$mfield}) {
		    $shortname = $self->{'indexfieldmap'}->{$mfield};
		}
		else {
		    $shortname = $self->create_shortname($mfield);
		    $self->{'indexfieldmap'}->{$mfield} = $shortname;
		    $self->{'indexfieldmap'}->{$shortname} = 1;
		}	   
		$new_text .= "<field name=\"$shortname\">$mvalue</field>\n";
		if ($allfields_index) {
		    $allfields_text .= "$mvalue ";
		}

		if (!defined $self->{'indexfields'}->{$mfield}) {
		    $self->{'indexfields'}->{$mfield} = 1;
		}				    
	    
	    }
	    # filter the text
	    $new_text = $self->filter_text ("metadata", $new_text);
	    
	    if ($edit_mode eq "add") {
		$self->{'num_processed_bytes'} += length ($new_text);
		$text .= "$new_text";
	    }
	    elsif ($edit_mode eq "update") {
		$text .= "$new_text";
	    }
	    elsif ($edit_mode eq "delete") {
		$self->{'num_processed_bytes'} -= length ($new_text);
	    }	    
	}

	if ($allfields_index) {
	    # add the index name mapping
	    $self->{'indexfieldmap'}->{"allfields"} = "ZZ";
	    $self->{'indexfieldmap'}->{"ZZ"} = 1;
	    
	    my $new_text = "<field name=\"ZZ\">$allfields_text</field>\n";
	    # filter the text
	    $new_text = $self->filter_text ("allfields", $new_text);
	    
	    if ($edit_mode eq "add") {
		$self->{'num_processed_bytes'} += length ($new_text);
		$text .= "$new_text";
	    }
	    elsif ($edit_mode eq "update") {
		$text .= "$new_text";
	    }
	    elsif ($edit_mode eq "delete") {
		$self->{'num_processed_bytes'} -= length ($new_text);
	    }
	}
	    
	# add in end tag if at top-level doc root, or indexing at the section level
	$text .= $end_sec if ($sec_tag_name ne "");

        $section = $doc_obj->get_next_section($section);
    } # while defined section

    
    # only output if working with doc level
    $text .= $end_doc if ($sec_tag_name eq "");

##    $text .= "<commit/>\n";

    print $solrhandle $text;

}




sub textreindex
{
    my $self = shift (@_);
    my ($doc_obj,$file) = @_;

    $self->textedit($doc_obj,$file,"update");
}


1;


