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
}

use strict;
use util;

# Not quite OO, but close enough for now
#
my $self = { 'full_server_jar' => undef,
	     'jetty_explicitly_started' => undef,
	     'jetty_stop_key' => "greenstone-solr"
	     };



sub locate_file
{
    my ($search_path,$suffix) = @_;
        
    foreach my $sp (@$search_path) {
	my $full_path = &util::filename_cat($sp,$suffix);
	
	if (-f $full_path) {
	    return $full_path;
	}
    }
    
    # if get to here, then failed to find match

    print STDERR "Error: Failed to find '$suffix'\n";
    print STDERR "  Looked in: ", join(", ", @$search_path), "\n";
    exit -1;
}

sub start_solr_server
{
    my ($search_path) = @_;

    my $solr_home         = $ENV{'GEXT_SOLR'};
    my $jetty_stop_port   = $ENV{'JETTY_STOP_PORT'};
    my $jetty_server_port = $ENV{'SOLR_JETTY_PORT'};

    chdir($solr_home);
    
    my $solr_etc = &util::filename_cat($solr_home,"etc");

    my $server_props = "-DSTOP.PORT=$jetty_stop_port";
    $server_props .= " -DSTOP.KEY=".$self->{'jetty_stop_key'};
    $server_props .= " -Dsolr.solr.home=$solr_etc";

    my $server_jar = &util::filename_cat("lib","java","solr-jetty-server.jar");
    my $full_server_jar = locate_file($search_path,$server_jar);
    $self->{'full_server_jar'} = $full_server_jar;
    
    my $server_java_cmd = "java $server_props -jar \"$full_server_jar\"";

##    print STDERR "**** server cmd = $server_java_cmd\n";

    if (open(SIN,"$server_java_cmd 2>&1 |")) {
	
	my $server_status = "unknown";

	my $line;
	while (defined($line=<SIN>)) {
	    # Scan through output until you see a line like:
	    #   2011-08-22 .. :INFO::Started SocketConnector@0.0.0.0:8983
	    # which signifies that the server has started up and is
	    # "ready and listening"

##	    print STDERR "**** $line";

	    if (($line =~ m/^(WARN|ERROR|SEVERE):/)
		|| ($line =~ m/^[0-9 :-]*(WARN|ERROR|SEVERE)::/)) {
		print $line;
	    }


	    if ($line =~ m/WARN::failed SocketConnector/) {
		if ($line =~ m/Address already in use/) {
		    $server_status = "already-running";
		}
		else {
		    $server_status = "failed-to-start";
		}
		last;
	    }
		
	    if ($line =~ m/INFO::Started SocketConnector/) {
		$server_status = "explicitly-started";
		last;
	    }
	}

	if ($server_status eq "explicitly-started") {
	    $self->{'jetty_explicitly_started'} = 1;
	    print STDERR "Jetty server ready and listening for connections\n";
	}
	elsif ($server_status eq "already-running") {
	    print STDERR "Using existing server detected on port $jetty_server_port\n";
	}
	else {
	    print STDERR "Failed to start Solr/Jetty web server on $jetty_server_port\n";
	    exit -1;
	}
	    
	# now we know the server is ready to accept connections, fork a
	# child process that continues to listen to the output and
	# prints out any lines that are not INFO lines

	if (fork()==0) {
	    # child process
	    
	    my $line;
	    while (defined ($line = <SIN>)) {
		next if ($line =~ m/^INFO:/);
		next if ($line =~ m/^[0-9 :-]*INFO::/);
		next if ($line =~ m/^\d{2}\/\d{2}\/\d{4}\s+/); 
	    }
	    close(SIN);
	    
	    # And now stop nicely
	    exit 0;
	}
    }
    else {
	print STDERR "Error: failed to start solr-jetty-server\n";
	print STDERR "!$\n\n";
	print STDERR "Command attempted was:\n";
	print STDERR "  $server_java_cmd\n";
	print STDERR "run from directory:\n";
	print STDERR "  $solr_home\n";
	print STDERR "----\n";

	exit -1;
    }

    # If get to here then server started (and ready and listening)
    # *and* we are the parent process of the fork()

}



sub stop_solr_server
{
    my $full_server_jar = $self->{'full_server_jar'};
    my $jetty_stop_port = $ENV{'JETTY_STOP_PORT'};
    
    my $server_props = "-DSTOP.PORT=$jetty_stop_port";
    $server_props   .= " -DSTOP.KEY=".$self->{'jetty_stop_key'};
    my $server_java_cmd = "java $server_props -jar \"$full_server_jar\" --stop";

    my $server_status = system($server_java_cmd);
    
    if ($server_status!=0) {
	print STDERR "Error: failed to stop solr-jetty-server\n";
	print STDERR "!$\n";
	exit -1;
    }
    else {
	wait(); # let the child process finish
	print STDERR "Jetty server shutdown\n";
    }
}


sub open_java_solr
{
  my ($collect, $doc_tag_level,$full_builddir,$indexdir,$removeold) = @_;


  # if removeold set, then delete the curring $full_builddir
  if ($removeold) {
      my $full_indexdir = &util::filename_cat($full_builddir,$indexdir);
      &util::rm_r($full_indexdir);
  }

  my $search_path = [];

  push(@$search_path,$ENV{'GSDLCOLLECTDIR'}) if defined $ENV{'GSDLCOLLECTDIR'};
  push(@$search_path,$ENV{'GSDLHOME'})       if defined $ENV{'GSDLHOME'};
  push(@$search_path,$ENV{'GEXT_SOLR'})      if defined $ENV{'GEXT_SOLR'};


  # The following returns once Jetty has generated its 
  # "reading and listening" line
  #
  start_solr_server($search_path);

  # Now run the solr-post command

  chdir($ENV{'GEXT_SOLR'});
  
  my $post_jar   = &util::filename_cat("lib","java","solr-post.jar");
  my $full_post_jar   = locate_file($search_path,$post_jar);

  my $jetty_server_port = $ENV{'SOLR_JETTY_PORT'};

  # Now run solr-post command
  my $post_props = "-Durl=http://localhost:$jetty_server_port/solr/$collect-$doc_tag_level/update";
  $post_props .= " -Ddata=stdin";
  $post_props .= " -Dcommit=yes";

  my $post_java_cmd = "java $post_props -jar \"$full_post_jar\"";

###  print STDERR "**** post cmd = $post_java_cmd\n";
  
  open (PIPEOUT, "| $post_java_cmd") 
      || die "Error in solr_passes.pl: Failed to run $post_java_cmd\n!$\n";
}



sub close_java_solr
{
    # closing the pipe has the effect of shutting down solr-post.jar
    close(PIPEOUT);
    
    if ($self->{'jetty_explicitly_started'}) {
	stop_solr_server();
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
    &util::mk_all_dir($full_output_dir);

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


sub pass_on_xml_stream
{
    my $line;
    while (defined ($line = <STDIN>)) {
	print PIPEOUT $line;
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

  my $removeold = 0;
  my @filtered_argv = ();

  my $i = 0;
  while ($i<$argc) {
    if ($argv[$i] =~ m/^\-(.*)$/) {

      my $option = $1;

      # -removeold causes the existing index to be overwritten
      if ($option eq "removeold") {
        print STDERR "\n-removeold set (new index will be created)\n";
        $removeold = 1;
      }
      # -verbosity <num>
      elsif ($option eq "verbosity") {
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

  if ($filtered_argc < 5) {
    print STDERR "Usage: solr_passes.pl [-removeold|-verbosity num] collect \"text\"|\"index\" doc-tag-level build-dir index-name\n";
    exit 1;
  }

  my $collect       = $filtered_argv[0];
  my $mode          = $filtered_argv[1];
  my $doc_tag_level = $filtered_argv[2];
  my $full_builddir = $filtered_argv[3];
  my $indexdir      = $filtered_argv[4];

  # We only need the Solr handle opened if we are indexing the
  # documents, not if we are just storing the text
  if ($mode eq "index") {
    open_java_solr($collect, $doc_tag_level, $full_builddir, $indexdir, $removeold);
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
