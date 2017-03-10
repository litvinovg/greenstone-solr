###########################################################################
#
# solrserver.pm -- class for starting and stopping the Solr with the
# GS3 tomcat server.
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
#no strict 'refs';

use solrutil;

sub new {
    my $class = shift(@_);
    my ($build_dir) = @_;

    my $self = { 'build_dir' => $build_dir };

    my $search_path = &solrutil::get_search_path();

    $self->{'server_explicitly_started'} = undef;

    # set SOLR_HOST and SOLR_PORT env vars (tomcat host and port, if not using jetty) 
    # by calling ant get-default-solr-servlet if possible. Else fallback on whatever the existing env vars are.
    # tomcat host and port would have been set up in the env as SOLR_HOST and SOLR_PORT
    # In case someone changed the tomcat host/port, we want to update the solr server variables too
    my $solr_url = &solrutil::get_solr_servlet_url();    
    # get the url parts, though we won't be using most of them
    my ($protocol, $server_host, $server_port, $servlet_name) = &solrutil::get_solr_url_parts($solr_url);
    
    # set the solr server env vars to what was discovered, so that any other old perl code
    # dependent on these env vars will have any changes propagated. 
    # (All perl code referencing these env vars should already be updated, but still...)
    $ENV{'SOLR_HOST'} = $server_host;
    $ENV{'SOLR_PORT'} = $server_port;
    
    $self->{'base-url'} = $solr_url; # e.g. of the form http://localhost:8383/solr
    $self->{'admin-url'} = "$solr_url/admin/cores";

    return bless $self, $class;
}

sub get_solr_base_url {
    my $self = shift (@_);
    return $self->{'base-url'};
}

sub _wget_service
{
    my $self = shift (@_);
    my ($output_format,$url,$cgi_get_args) = @_;

    my $full_url = $url;

    $url .= "?$cgi_get_args" if (defined $cgi_get_args);

    print STDERR "\n\n**** _wget_service SOLR WEB URL: $url\n\n";
    
    # the wget binary is dependent on the gnomelib_env (particularly lib/libiconv2.dylib) being set, particularly on Mac Lion binaries (android too?)
    &util::set_gnomelib_env(); # this will set the gnomelib env once for each subshell launched, by first checking if GEXTGNOME is not already set

    my $cmd = "wget -O - \"$url\" 2>&1";

    my $preamble_output = "";    
    my $xml_output = "";
    my $error_output = undef;

    my $in_preamble = ($output_format eq "xml") ? 1 : 0;
    
##    print STDERR "**** wgetcmd = \n $cmd\n";

    if (open(WIN,"$cmd |")) {

	my $line;
	while (defined ($line=<WIN>)) {

	    if ($line =~ m/ERROR \d+:/) {
		chomp $line;
		$error_output = $line;
		last;
	    }
	    elsif ($line =~ m/failed: Connection refused/) {
		chomp $line;
		$error_output = $line;
		last;
	    }
	    elsif ($in_preamble) {
		if ($line =~ m/<.*>/) {
		    $in_preamble = 0;
		}
		else {
		    $preamble_output .= $line;
		}
	    }

	    if (! $in_preamble) {
		$xml_output .= $line;
	    }
	}
	close(WIN);

    }
    else {
	$error_output = "Error: failed to run $cmd\n";
	$error_output .= "  $!\n";
    }

    if(defined $error_output) {
	print STDERR "\n\n**** WGET_SERVICE got an error: $error_output\n\n";
    }

    my $output = { 'url'      => $full_url,
		   'preamble' => $preamble_output,
		   'output'   => $xml_output,
		   'error'    => $error_output };

    return $output;
}


sub _base_service
{
    my $self = shift (@_);
    my ($cgi_get_args) = @_;

    my $base_url = $self->{'base-url'};

    return $self->_wget_service("html",$base_url,$cgi_get_args);
}
 
sub _admin_service
{
    my $self = shift (@_);
    my ($cgi_get_args) = @_;

    my $admin_url = $self->{'admin-url'};

    return $self->_wget_service("xml",$admin_url,$cgi_get_args);
}


sub server_running
{
    my $self = shift @_;

    my $output = $self->_base_service();

    my $have_error = defined $output->{'error'};

    my $running = ($have_error) ? 0 : 1;

    return $running;
}


sub admin_ping_core
{
    my $self = shift @_;
    my ($core) = @_;

    my $cgi_get_args = "action=STATUS&core=$core";

    my $ping_status = 1;

    my $output = $self->_admin_service($cgi_get_args);

    if (defined $output->{'error'}) {
	# severe error, such as failing to connect to the server
	$ping_status = 0;

	my $url      = $output->{'url'};
	my $preamble = $output->{'preamble'};
	my $error    = $output->{'error'};
	
	print STDERR "----\n";
	print STDERR "Error: Failed to get XML response from:\n";
	print STDERR "         $url\n";
	print STDERR "Output was:\n";
	print STDERR $preamble if ($preamble ne "");
	print STDERR "$error\n";
	print STDERR "----\n";
    }
    else {
	
	# If the collection doesn't exist yet, then there will be
	# an empty element of the form:
	#   <lst name="collect-doc"/>
	# where 'collect' is the actual name of the collection, 
	# such as demo

	my $xml_output = $output->{'output'};
	
	my $empty_element="<lst\\s+name=\"$core\"\\s*\\/>";
	
	$ping_status = !($xml_output =~ m/$empty_element/s);
    }

    return $ping_status;
}

sub filtered_copy
{
    my $self = shift @_;

    my $src_file = shift @_;
    my $dst_file = shift @_;
    my $re_substitutions = shift @_;

    # $re_substitutions is a hashmap of the form: [re_key] => subst_str
    
    my $content = "";

    if (open(FIN,'<:utf8',$src_file)) {

	my $line;
	while (defined($line=<FIN>)) {
	    $content .= $line;
	}
    }

    close(FIN);

    # perform RE string substitutions
    foreach my $re_key (keys %$re_substitutions) {

	my $subst_str = $re_substitutions->{$re_key};

	# Perform substitution of the form:
	#  $content =~ s/$re_key/$subst_str/g;
	# but allow allow separator char (default '/') 
	# and flags (default 'g') to be parameterized

	$content =~ s/$re_key/$subst_str/g;
    }
    
    if (open(FOUT, '>:utf8', $dst_file)) {
	print FOUT $content;
	close(FOUT);
    }
    else {
	print STDERR "Error: Failed to open file '$dst_file' for writing.\n$!\n";
    }   
}

sub solr_xml_to_solr_xml_in
{
    my $self = shift @_;
    my ($solr_xml_dir) = @_;
    
    my $gsdl3home = $ENV{'GSDL3HOME'};
    
    if (!defined $solr_xml_dir || !-d $solr_xml_dir) {
	# if not passed in, use stored solr_live_home
	$solr_xml_dir = $self->{'solr_live_home'};
    }

    my $solrxml_in = &util::filename_cat($solr_xml_dir, "solr.xml.in");
    my $solrxml = &util::filename_cat($solr_xml_dir, "solr.xml");

    my $gsdl3home_re = &util::filename_to_regex($gsdl3home);

    my $replacement_map = { qr/$gsdl3home_re/ => "\@gsdl3home\@" };

    $self->filtered_copy($solrxml,$solrxml_in,$replacement_map);
}


sub solr_xml_in_to_solr_xml
{
    my $self = shift @_;
    my ($solr_xml_dir) = @_;

    my $gsdl3home = $ENV{'GSDL3HOME'};
    if (!defined $solr_xml_dir || !-d $solr_xml_dir) {
	# if not passed in, use stored solr home
	$solr_xml_dir = $self->{'solr_live_home'};
    }
    my $solrxml_in = &util::filename_cat($solr_xml_dir, "solr.xml.in");
    my $solrxml = &util::filename_cat($solr_xml_dir, "solr.xml");
    
    my $gsdl3home_re = &util::filename_to_regex($gsdl3home);

    my $replacement_map = { qr/\@gsdl3home\@/ => $gsdl3home_re };

    $self->filtered_copy($solrxml_in,$solrxml,$replacement_map);
}


# Some of the Solr CoreAdmin API calls available. 
# See http://wiki.apache.org/solr/CoreAdmin
sub admin_reload_core
{
    my $self = shift @_;
    my ($core) = @_;

    my $cgi_get_args = "action=RELOAD&core=$core";

    $self->_admin_service($cgi_get_args);

}

sub admin_rename_core
{
    my $self = shift @_;
    my ($oldcore, $newcore) = @_;

    my $cgi_get_args = "action=RENAME&core=$oldcore&other=$newcore";

    $self->_admin_service($cgi_get_args);

}

sub admin_swap_core
{
    my $self = shift @_;
    my ($oldcore, $newcore) = @_;

    my $cgi_get_args = "action=SWAP&core=$oldcore&other=$newcore";

    $self->_admin_service($cgi_get_args);

}

# The ALIAS action is not supported in our version of solr (despite it
# being marked as experimental in the documentation for Core Admin)
sub admin_alias_core
{
    my $self = shift @_;
    my ($oldcore, $newcore) = @_;

    my $cgi_get_args = "action=ALIAS&core=$oldcore&other=$newcore";

    $self->_admin_service($cgi_get_args);

}

sub admin_create_core
{
    my $self = shift @_;
    my ($core, $data_parent_dir) = @_; # data_parent_dir is optional, can be index_dir. Defaults to builddir if not provided

    my ($ds_idx) = ($core =~ m/^.*-(.*?)$/);

    my $cgi_get_args = "action=CREATE&name=$core";

    my $collect_home = $ENV{'GSDLCOLLECTDIR'};
    my $etc_dirname = &util::filename_cat($collect_home,"etc");

    if(!defined $data_parent_dir) {
	$data_parent_dir = $self->{'build_dir'};
    } 
    
    my $idx_dirname = &util::filename_cat($data_parent_dir,$ds_idx); # "dataDir"  
	    
    $cgi_get_args .= "&instanceDir=$etc_dirname";
    $cgi_get_args .= "&dataDir=$idx_dirname";

    $self->_admin_service($cgi_get_args);

}

# removes (unloads) core from the ext/solr/sorl.xml config file
sub admin_unload_core
{
    my $self = shift @_;
    my ($core, $delete) = @_;

    my $cgi_get_args = "action=UNLOAD&core=$core"; 
    # &deleteIndex=true available from Solr3.3, see https://wiki.apache.org/solr/CoreAdmin. 
    # Also available since later Solr versions: deleteDataDir and deleteInstanceDir
    if(defined $delete && $delete == 1) {
	$cgi_get_args = $cgi_get_args."&deleteIndex=true";
    }

    $self->_admin_service($cgi_get_args);

}

sub start
{
    my $self = shift @_;
    my ($verbosity) = @_;
    
    $verbosity = 1 unless defined $verbosity;

    my $solr_live_home    = &util::filename_cat($ENV{'GSDL3HOME'}, "ext", "solr");
    $self->{'solr_live_home'} = $solr_live_home; # will be used later to generate solr.xml.in from solr.xml and vice-versa
    my $server_port = $ENV{'SOLR_PORT'};
    my $server_host = $ENV{'SOLR_HOST'};

    chdir($ENV{'GSDL3SRCHOME'});

    my $server_java_cmd = "ant start";

    my $server_status = "unknown";

    if ($self->server_running()) {
	$server_status = "already-running";
	print STDERR "@@@@ server already running\n\n";
    }
    elsif (open(STARTIN,"$server_java_cmd 2>&1 |")) {

	print STDERR "@@@@ need to start tomcat\n\n";
	print STDERR "**** starting up tomcat server with cmd start =\n $server_java_cmd\n" if ($verbosity > 1);

	my $line;
	while (defined($line=<STARTIN>)) {	   
	
	    #if ($line =~ m/^(BUILD FAILED)/) {
	    print "Tomcat startup: $line";
	    #}
		if ($line =~ m/^BUILD SUCCESSFUL/) {
			last;
		}
	}

	close(STARTIN);
	
	if ($self->server_running()) {
	    $server_status = "explicitly-started";
	    #print STDERR "\n*** Tomcat server has started up now.\n\n";
	} else {
	    $server_status = "failed-to-start"; # no need to set this, will be exiting below anyway

	    print STDERR "Error: failed to start greenstone tomcat server\n";
	    print STDERR "$!\n";
	    print STDERR "Command attempted was:\n";
	    print STDERR "  $server_java_cmd\n";
	    print STDERR "run from directory:\n";
	    print STDERR "  $ENV{'GSDL3SRCHOME'}\n";
	    print STDERR "----\n";
	    
	    exit -1;
	}
    }
    else {
	print STDERR "@@@@ failed to start tomcat\n\n";
	$server_status = "failed-to-start"; # no need to set this, will be exiting below anyway

	print STDERR "Error: unable to start greenstone tomcat server\n";
	print STDERR "$!\n";
	print STDERR "Command attempted was:\n";
	print STDERR "  $server_java_cmd\n";
	print STDERR "run from directory:\n";
	print STDERR "  $ENV{'GSDL3SRCHOME'}\n";
	print STDERR "----\n";

	exit -1;
    }

    if ($server_status eq "explicitly-started") {
	$self->{'server_explicitly_started'} = 1;
	print "Tomcat server ready and listening for connections at ";
	print " $server_host:$server_port\n";
	    
	# now we know the server is ready to accept connections
    }
    elsif ($server_status eq "already-running") {
	print STDERR "Using existing tomcat server detected at $server_host:$server_port\n";
	$self->{'server_explicitly_started'} = 0;
    }
    elsif ($server_status eq "failed-to-start") {
	print STDERR "Started Solr/Tomcat web server at $server_host:$server_port";
	print STDERR ", but encountered an initialization error\n";
	exit -1;
    }

}

sub explicitly_started
{
    my $self = shift @_;

    return $self->{'server_explicitly_started'};
}

sub stop
{    
    my $self = shift @_;
    my ($options) = @_;

    my $solr_home         = $ENV{'GEXT_SOLR'};

    chdir($ENV{'GSDL3SRCHOME'});

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

    my $server_java_cmd = "ant stop";

    print STDERR "**** java server stop cmd:\n  $server_java_cmd\n" if ($output_verbosity>1);

    if (open(STOPIN,"$server_java_cmd 2>&1 |")) {

	my $line;
	while (defined($line=<STOPIN>)) {
	    print "@@@@ Tomcat shutdown: $line"; #if ($output_verbosity>1);
	}
	close(STOPIN);

	if ($do_wait) {
	    wait(); # let the child process finish
	}

	if ($output_verbosity>0) {
	    print "@@@@@ Tomcat server shutdown\n";
	}
    }
    else {
	print STDERR "Error: failed to stop tomcat-server\n";
	print STDERR "$!\n";
	print STDERR "Command attempted was:\n";
	print STDERR "  $server_java_cmd\n";
	print STDERR "run from directory:\n";
	print STDERR "  $solr_home\n";
	print STDERR "----\n";

	exit -2;
    }
}



1;
