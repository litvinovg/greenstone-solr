#!/usr/bin/perl -w

###########################################################################
#
# run_solr_server.pl -- perl script that uses solrserver.pm to stop and
# start the jetty server included with the solr extension for Greenstone3.
# A component of the Greenstone digital library software
# from the New Zealand Digital Library Project at the
# University of Waikato, New Zealand.
#
# Copyright (C) 2012 New Zealand Digital Library Project
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

# For easily starting and stopping the jetty server (that is included with 
# the solr extension for Greenstone 3) from the command-line.
# First source gs3-setup.sh, then this script can be run as follows:
#     run_solr_server.pl start
# which runs the jetty server. 
# To stop the jetty server, re-run the script with the stop parameter:
#     run_solr_server.pl stop


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
#use solrutil;
use solrserver;

sub print_usage {
    print STDERR "\nUsage:\t$0 start\n\t$0 stop\n\n";
    exit 1;
}

sub main
{
  my (@argv) = @_;
  my $argc = scalar(@argv);

  # defaults and fall-backs
  my $verbosity = 2;
  my $command = "stop";
  #my $stopkey = "standalone-greenstone-solr";

  
  if ($argc < 1 || $argc > 2) {
      print_usage();
  }

  if($argv[0] ne "start" && $argv[0] ne "stop") {
      print_usage();  
  } else {
      $command = $argv[0]
  }

  #if($argc > 1) {
  #    $stopkey = $argv[1];
  #}
  
  my $solr_server = new solrserver();
  #$solr_server->set_jetty_stop_key($stopkey);

  if ($command eq "start") {
      $solr_server->start($verbosity);
  } elsif ($command eq "stop") {
      my $options = { 'output_verbosity' => $verbosity };
      $solr_server->stop($options);
  }
}


&main(@ARGV);
