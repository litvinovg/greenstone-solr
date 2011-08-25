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



sub open_post_pipe
{
    my ($collect,$ds_idx) = @_;

    my $search_path = get_search_path();

    chdir($ENV{'GEXT_SOLR'});
    
    my $post_jar   = &util::filename_cat("lib","java","solr-post.jar");
    my $full_post_jar   = solrutil::locate_file($search_path,$post_jar);
    
    my $jetty_port = $ENV{'SOLR_JETTY_PORT'};
    
    # Now run solr-post command
    my $core = $collect."-".$ds_idx;
    my $post_props = "-Durl=http://localhost:$jetty_port/solr/$core/update";
    $post_props .= " -Ddata=stdin";
    $post_props .= " -Dcommit=yes";
    
    my $post_java_cmd = "java $post_props -jar \"$full_post_jar\"";
    
###  print STDERR "**** post cmd = $post_java_cmd\n";
    
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
