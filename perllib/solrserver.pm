###########################################################################
#
# solrserver.pm -- class for starting and stopping the Solr/jetty server
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


package solrserver;

use strict; 

use solrutil;

sub new {
    my $class = shift(@_);

    my $self = { 'jetty_stop_key' => "greenstone-solr" };

    my $search_path = &solrutil::get_search_path();

    my $server_jar = &util::filename_cat("lib","java","solr-jetty-server.jar");
    my $full_server_jar = solrutil::locate_file($search_path,$server_jar);
    $self->{'full_server_jar'} = $full_server_jar;

    $self->{'jetty_explicitly_started'} = undef;

    return bless $self, $class;
}

sub start
{
    my $self = shift @_;

    my $solr_home         = $ENV{'GEXT_SOLR'};
    my $jetty_stop_port   = $ENV{'JETTY_STOP_PORT'};
    my $jetty_server_port = $ENV{'SOLR_JETTY_PORT'};

    chdir($solr_home);
    
    my $solr_etc = &util::filename_cat($solr_home,"etc");

    my $server_props = "-DSTOP.PORT=$jetty_stop_port";
    $server_props .= " -DSTOP.KEY=".$self->{'jetty_stop_key'};
    $server_props .= " -Dsolr.solr.home=$solr_etc";

    my $full_server_jar = $self->{'full_server_jar'};
    
    my $server_java_cmd = "java $server_props -jar \"$full_server_jar\"";

##    print STDERR "**** server cmd = $server_java_cmd\n";

    my $pid = open(STARTIN,"-|");

    if ($pid==0) {
	# child process that will start up jetty and monitor output

	setpgrp(0,0);
	
	exec "$server_java_cmd 2>&1" || die "Failed to execute $server_java_cmd\n$!\n";
	# everything stops here
    }

    my $server_status = "unknown";

    my $line;
    while (defined($line=<STARTIN>)) {
	# Scan through output until you see a line like:
	#   2011-08-22 .. :INFO::Started SocketConnector@0.0.0.0:8983
	# which signifies that the server has started up and is
	# "ready and listening"
	
##	print STDERR "**** $line";
		
	# skip annoying "not listening" message
	next if ($line =~ m/WARN:\s*Not listening on monitor port/);

	if (($line =~ m/^(WARN|ERROR|SEVERE):/)
	    || ($line =~ m/^[0-9 :-]*(WARN|ERROR|SEVERE)::/)) {
	    print "Jetty startup: $line";
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
	print "Jetty server ready and listening for connections on port $jetty_server_port\n";
	    
	# now we know the server is ready to accept connections, fork a
	# child process that continues to listen to the output and
	# prints out any lines that are not INFO lines

	if (fork()==0) {
	    # child process
	    
	    my $line;
	    while (defined ($line = <STARTIN>)) {

#		if (($line =~ m/^(WARN|ERROR|SEVERE):/)
#		    || ($line =~ m/^[0-9 :-]*(WARN|ERROR|SEVERE)::/)) {
#		    print "Jetty $line";
#		}


		# skip info lines
		next if ($line =~ m/^INFO:/);
		next if ($line =~ m/^[0-9 :-]*INFO::/);
		next if ($line =~ m/^\d{2}\/\d{2}\/\d{4}\s+/); 
		next if ($line =~ m/^\d{4}-\d{2}-\d{2}\s+/); 

##		next if ($line =~ m/^\s*\w+{.*}/);

		# if here, then some non-trival message has been logged
		print "Jetty/Solr processing: $line";
	    }
	    close(STARTIN);
		
	    # And now stop nicely
	    exit 0;
	}
	# otherwise let the parent continue on
    }
    elsif ($server_status eq "already-running") {
	print "Using existing server detected on port $jetty_server_port\n";
	$self->{'jetty_explicitly_started'} = 0;
	
	# Kill of the child process

	my $ks = kill(9,-$pid);

	# Consume any remaining (buffered) output (not interested in values)
	while (defined ($line = <STARTIN>)) { 
	    # skip info lines
	}
	close(STARTIN);
    }
    else {
	print STDERR "Failed to start Solr/Jetty web server on $jetty_server_port\n";
	exit -1;
    }


#    else {
#	print STDERR "Error: failed to start solr-jetty-server\n";
#	print STDERR "!$\n\n";
#	print STDERR "Command attempted was:\n";
#	print STDERR "  $server_java_cmd\n";
#	print STDERR "run from directory:\n";
#	print STDERR "  $solr_home\n";
#	print STDERR "----\n";

#	exit -1;
#    }

#    # If get to here then server started (and ready and listening)
#    # *and* we are the parent process of the fork()

}


sub explicitly_started
{
    my $self = shift @_;

    return $self->{'jetty_explicitly_started'};
}


sub stop
{    
    my $self = shift @_;
    my ($options) = @_;

    my $solr_home         = $ENV{'GEXT_SOLR'};

    chdir($solr_home);

    # defaults
    my $do_wait = 1; 
    my $output_verbosity = 1;

    if (defined $options) {
	if (defined $options->{'do_wait'}) {
	    $do_wait = $options->{'do_wait'};
	}
	if (defined $options->{'output_verbosity'}) {
	    $output_verbosity = $options->{'output_verbosity'};
	}
    }

    my $full_server_jar = $self->{'full_server_jar'};
    my $jetty_stop_port = $ENV{'JETTY_STOP_PORT'};
    
    my $server_props = "-DSTOP.PORT=$jetty_stop_port";
    $server_props   .= " -DSTOP.KEY=".$self->{'jetty_stop_key'};
    my $server_java_cmd = "java $server_props -jar \"$full_server_jar\" --stop";
    if (open(STOPIN,"$server_java_cmd 2>&1 |")) {

	my $line;
	while (defined($line=<STOPIN>)) {
	    print "Jetty shutdown: $line" if ($output_verbosity>1);
	}
	close(STOPIN);

	if ($do_wait) {
	    wait(); # let the child process finish
	}

	if ($output_verbosity>0) {
	    print "Jetty server shutdown\n";
	}
    }
    else {
	print STDERR "Error: failed to stop solr-jetty-server\n";
	print STDERR "!$\n";
	print STDERR "Command attempted was:\n";
	print STDERR "  $server_java_cmd\n";
	print STDERR "run from directory:\n";
	print STDERR "  $solr_home\n";
	print STDERR "----\n";

	exit -2;
    }
}



1;
