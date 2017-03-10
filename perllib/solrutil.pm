###########################################################################
#
# solrutil.pm -- support module for Solr extension
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

package solrutil;

use strict; 

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


sub get_search_path
{
  my $search_path = [];

  push(@$search_path,$ENV{'GSDLCOLLECTDIR'}) if defined $ENV{'GSDLCOLLECTDIR'};
  push(@$search_path,$ENV{'GSDLHOME'})       if defined $ENV{'GSDLHOME'};
  push(@$search_path,$ENV{'GEXT_SOLR'})      if defined $ENV{'GEXT_SOLR'};

  return $search_path;
}

# The get-solr-servlet-url ant target can be run from anywhere by specifying the
# location of GS3's ant build.xml buildfile.
# GSDL3SRCHOME will be set for GS3 by gs3-setup.sh.
# Based on servercontrol::get_library_URL.
sub get_solr_servlet_url {
    # Set up fall backs, incl. old way of using solr host and port values that's already in the environment
    my $solr_url = "http://".$ENV{'SOLR_HOST'}.$ENV{'SOLR_PORT'}."/solr"; # fallback to default

    my $perl_command = "ant -buildfile \"$ENV{'GSDL3SRCHOME'}/build.xml\" get-solr-servlet-url";
    
    if (open(PIN, "$perl_command |")) {
	while (defined (my $perl_output_line = <PIN>)) {
	    if($perl_output_line =~ m@(https?):\/\/(\S*)@) { # grab all the non-whitespace chars
		$solr_url="$1://".$2; # preserve the http protocol
	    }
	}
	close(PIN);
	
	#print STDERR "XXXXXXXXXX SOLR URL: $solr_url\n";

    } else {
	print STDERR "*** ERROR IN solrutil::get_solr_servlet_url:\n";
	print STDERR "    Failed to run $perl_command to work out GS3's solr URL\n";
	print STDERR "    falling back to using original solr_URL: $solr_url\n";
    }

    return $solr_url;
}

# Given the solr base url (e.g. http://localhost:8383/solr by default), this function
# returns the url's parts: protocol, host, port, solr servlet
sub get_solr_url_parts {
    my $solr_url = shift (@_);

    # Set up fall backs, incl. old way of using solr host and port values that's already in the environment
    my ($protocol, $server_host, $server_port, $servlet_name)
	= ("http://", $ENV{'SOLR_HOST'}, $ENV{'SOLR_PORT'}, "solr");

    
    # http://stackoverflow.com/questions/8206135/storing-regex-result-in-a-new-variable
    if($solr_url =~ m@(https?)://([^:]*):([0-9]*)/(.*)$@) { # m@https?://([^:]*):([^/])/(.*)@) {
	
	($protocol, $server_host, $server_port, $servlet_name) = ($1, $2, $3, $4);
	
	#print STDERR "XXXXXXXXXX PROTOCOL: $protocol, SOLR_HOST: $server_host, SOLR_PORT: $server_port, servlet: $servlet_name\n";

    } else {
	print STDERR "*** WARNING: in solrutil::get_solr_url_parts(): solr servlet URL not in expected format\n";
    }

    return ($protocol, $server_host, $server_port, $servlet_name);
}


sub open_post_pipe
{
    my ($core, $solr_base_url) = @_;

    my $search_path = get_search_path();

    chdir($ENV{'GEXT_SOLR'});
    
    my $post_jar   = &util::filename_cat("lib","java","solr-post.jar");
    my $full_post_jar   = solrutil::locate_file($search_path,$post_jar);
    
    # Now run solr-post command
    my $post_props = "-Durl=$solr_base_url/$core/update"; # robustness of protocol is taken care of too

    $post_props .= " -Ddata=stdin";
    $post_props .= " -Dcommit=yes";
    
    my $post_java_cmd = "java -Xmx512M $post_props -jar \"$full_post_jar\"";
    
	##print STDERR "**** post cmd = $post_java_cmd\n";
    
    open (PIPEOUT, "| $post_java_cmd") 
	|| die "Error in solr_passes.pl: Failed to run $post_java_cmd\n!$\n";
    
}

sub print_to_post_pipe
{
    my ($line) = @_;

    print PIPEOUT $line;
}

sub close_post_pipe
{
    # closing the pipe has the effect of shutting down solr-post.jar
    close(PIPEOUT);
}

1;
