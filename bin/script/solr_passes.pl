#!/usr/bin/perl -w

###########################################################################
#
# solr_passes.pl -- perl wrapper, akin to mgpp_passes, for Solr
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

# Heavily based on lucene_passes.pl, but does not need a SolrWrapper.jar
# style solution as Solr has its own XML syntax:
#
#  http://wiki.apache.org/solr/UpdateXmlMessages
#
# This syntax is rather similar to what we already use, so the
# main task of monitor_xml() is to translate the XML syntax Greenstone uses
# into that needed by the solr server.  


BEGIN {
    die "GSDLHOME not set\n" unless defined $ENV{'GSDLHOME'};
    die "GSDLOS not set\n" unless defined $ENV{'GSDLOS'};
    unshift (@INC, "$ENV{'GSDLHOME'}/perllib");
    die "GEXT_SOLR not set\n" unless defined $ENV{'GEXT_SOLR'};

    my $solr_ext = $ENV{'GEXT_SOLR'};
    unshift (@INC, "$solr_ext/perllib");
}

use strict;
use util;
use solrutil;
use solrserver;

my $DOC_BATCH_SIZE = 20;

# Not quite OO, but close enough for now
#
my $self = { 'solr_server' => undef };

sub open_java_solr
{
  my ($core,$full_builddir,$indexdir) = @_;

  # If the Solr/Jetty server is not already running, the following starts
  # it up, and only returns when the server is "reading and listening"
  
  my $solr_server = new solrserver($full_builddir);
  $solr_server->start();
  $self->{'solr_server'} = $solr_server;

  # Now start up the solr-post command and store the open cmd that is returned
  $self->{'post_java_cmd'} = &solrutil::open_post_pipe($core, $solr_server->get_solr_base_url());
}

# To commit any stream of data to solr that's amassed so far on the pipe to SimplePostTool,
# close the pipe. Then reopen it to continue streaming data to it.
sub flush_and_reopen_java_solr
{
    &solrutil::close_post_pipe();
    &solrutil::reopen_post_pipe($self->{'post_java_cmd'});
}

sub close_java_solr
{
    &solrutil::close_post_pipe();
      
    my $solr_server = $self->{'solr_server'};
    if ($solr_server->explicitly_started()) {
	$solr_server->stop();
    }
}

#----

sub save_xml_doc
{
    # This is identical to the one in lucene_passes.pl, and should be
    # moved in to a package and shared ####

    my ($full_textdir,$output_filename,$doc_xml) = @_;

    my $dir_sep = &util::get_os_dirsep();

    my $full_output_filename = &util::filename_cat($full_textdir,$output_filename);
    my ($full_output_dir) = ($full_output_filename =~ m/^(.*$dir_sep)/x);
    &FileUtils::makeAllDirectories($full_output_dir);

    open(DOCOUT,">$full_output_filename")
	|| die "Unable to open $full_output_filename";

    print DOCOUT $doc_xml;
    close(DOCOUT);

    # What this the purpose of the following? ####
    my @secs =  ($doc_xml =~ m/<Sec\s+gs2:id="\d+"\s*>.*?<\/Sec>/sg);
}


sub compress_xml_doc
{
    # This is identical to the one in lucene_passes.pl, and should be
    # moved in to a package and shared ####

    my ($full_textdir,$output_filename) = @_;

    my $full_output_filename
	= &util::filename_cat($full_textdir,$output_filename);

    # Greenstone ships with gzip for Windows
    `gzip $full_output_filename`;
}


sub monitor_xml_stream
{
    # based on lucene's monitor_xml_stream, but simplified
    # as only now used when in "text" mode

    my ($full_textdir) = @_;

    my $doc_xml = "";
    my $output_filename = "";

    my $line;
    while (defined ($line = <STDIN>)) {

	$doc_xml .= $line;

	if ($line =~ m/^<Doc.+file=\"(.*?)\".*>$/) {
	    $output_filename = $1;	    
	}
	
	if ($line =~ m/^<\/Doc>$/) {
	    save_xml_doc($full_textdir,$output_filename,$doc_xml);

	    # Compress file
	    # 
	    # The compress option was taken out for efficiency
	    # reasons.  Consider putting it back in but making it a 
	    # switch so a collection builder can decide for themselves on a
	    # case by case basis if they want to save on diskspace, but have
	    # the overhead of uncompressing at runtime
	    
###	    compress_xml_doc($full_textdir,$output_filename);

	    $doc_xml = "";
	    $output_filename = "";
	}
    }
}


# Called when mode = index,
# This is the function that passes the contents of the docs for ingesting into solr
# as one long stream.
# Since if we have many docs in a collection, there will be one long stream of bytes
# sent to the SimplePostTool/(solr-)post.jar, which remain uncommitted until
# the pipe is closed. And for a long bytestream, this will result in an out of heap memory
# https://stackoverflow.com/questions/2082057/outputstream-outofmemoryerror-when-sending-http
# So we need to close and then reopen solr post pipe to force commit after some $DOC_BATCH_SIZE
# We could still have the same issue if any one document is very large (long stream of bytes),
# but then we'd need to take care of the problem at the root, see the StackOverFlow page
# and SimplePostTool.java, as the problem lies in HttpURLConnection.
sub pass_on_xml_stream
{
    # the xml_stream sent to solr looks like:
    # <update>
    #   <add>
    #     <doc>
    #       ....
    #     </doc>
    #   </add>
    #     <doc>
    #       ....
    #     </doc>
    #   </add>
    #   ...
    # </update>    
    
    my $doc_count = 0;
    
    my $line;
    while (defined ($line = <STDIN>)) {
	# monitor for end of each document	    
	if ($line =~ m/^\s*<\/add>$/) {
	    $doc_count++;
	}
	else {
	    if ($doc_count == $DOC_BATCH_SIZE) {
		# evidence that there is still more documents to process
		# => but reached batch size, so flush and reopen
		# to force a commit of past docs to solr
		# before sending this current line on its way
		
		if ($line =~ m/^\s*<add>$/) {
		    &solrutil::print_to_post_pipe("</update>\n");
		    flush_and_reopen_java_solr();
		
		    # start next doc
		    &solrutil::print_to_post_pipe("<update>\n");
		    $doc_count = 0;
		}
	    }
	}
		    
	&solrutil::print_to_post_pipe($line);

    }
}


# /** This checks the arguments on the command line, filters the
#  *  unknown command line arguments and then calls the open_java_solr
#  *  function to begin processing.
#  */
sub main
{
  my (@argv) = @_;
  my $argc = scalar(@argv);

  my @filtered_argv = ();

  my $i = 0;
  while ($i<$argc) {
    if ($argv[$i] =~ m/^\-(.*)$/) {

      my $option = $1;

      # -verbosity <num>
      if ($option eq "verbosity") {
        $i++;
        if ($i<$argc)
	{
	  # solr indexing has no support for verbosity 
	  # => parse to be compatible with calling program, but supress it
	  #    for solr-post.jar
        }
      }
      else {
        print STDERR "Unrecognised minus option: -$option\n";
      }
    }
    else {
        push(@filtered_argv,$argv[$i]);
    }
    $i++;
  }

  my $filtered_argc = scalar(@filtered_argv);

  if ($filtered_argc < 4) {
    print STDERR "Usage: solr_passes.pl [-verbosity num] core \"text\"|\"index\" build-dir index-name\n";
    exit 1;
  }

  my $core          = $filtered_argv[0];
  my $mode          = $filtered_argv[1];
  my $full_builddir = $filtered_argv[2];
  my $indexdir      = $filtered_argv[3];

  # We only need the Solr handle opened if we are indexing the
  # documents, not if we are just storing the text
  if ($mode eq "index") {
    open_java_solr($core, $full_builddir, $indexdir);
  }

  if ($mode eq "text") {
      print STDERR "Monitoring for input!\n";
      my $full_textdir = &util::filename_cat($full_builddir,"text");
      monitor_xml_stream($full_textdir);
  }
  else {
      print STDERR "Streaming document input onto Solr server!\n";
      pass_on_xml_stream();
  }


  if ($mode eq "index") {
    close_java_solr();
  }
}


&main(@ARGV);
