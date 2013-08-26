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
#no strict 'refs';

use solrutil;

sub new {
    my $class = shift(@_);
    my ($build_dir) = @_;

    my $self = { 'jetty_stop_key' => "greenstone-solr" };

    $self->{'build_dir'} = $build_dir;

    my $search_path = &solrutil::get_search_path();

    my $server_jar = &util::filename_cat("lib","java","solr-jetty-server.jar");
    my $full_server_jar = solrutil::locate_file($search_path,$server_jar);
    $self->{'full_server_jar'} = $full_server_jar;

    $self->{'jetty_explicitly_started'} = undef;

    my $jetty_server_port = $ENV{'SOLR_JETTY_PORT'};
    my $base_url = "http://localhost:$jetty_server_port/solr/";
    my $admin_url = "http://localhost:$jetty_server_port/solr/admin/cores";
    
    $self->{'base-url'} = $base_url;
    $self->{'admin-url'} = $admin_url;

    return bless $self, $class;
}

sub set_jetty_stop_key {
    my $self = shift (@_);
    my ($stop_key) = @_;

    $self->{'jetty_stop_key'} = $stop_key if defined $stop_key;
}

sub _wget_service
{
    my $self = shift (@_);
    my ($output_format,$url,$cgi_get_args) = @_;

    my $full_url = $url;

    $url .= "?$cgi_get_args" if (defined $cgi_get_args);
    
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

    my $running = !$have_error;

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

    my $gsdl3home = $ENV{'GSDL3HOME'};
    my $web_solr_ext_dir = &util::filename_cat($gsdl3home, "ext", "solr");
    my $web_solrxml_in = &util::filename_cat($web_solr_ext_dir, "solr.xml.in");
    my $web_solrxml = &util::filename_cat($web_solr_ext_dir, "solr.xml");

    my $gsdl3home_re = &util::filename_to_regex($gsdl3home);

    my $replacement_map = { qr/$gsdl3home_re/ => "\@gsdl3home\@" };

    $self->filtered_copy($web_solrxml,$web_solrxml_in,$replacement_map);
}


sub solr_xml_in_to_solr_xml
{
    my $self = shift @_;

    my $gsdl3home = $ENV{'GSDL3HOME'};
    my $web_solr_ext_dir = &util::filename_cat($gsdl3home, "ext", "solr");
    my $web_solrxml_in = &util::filename_cat($web_solr_ext_dir, "solr.xml.in");
    my $web_solrxml = &util::filename_cat($web_solr_ext_dir, "solr.xml");
    
    my $gsdl3home_re = &util::filename_to_regex($gsdl3home);

    my $replacement_map = { qr/\@gsdl3home\@/ => $gsdl3home_re };

    $self->filtered_copy($web_solrxml_in,$web_solrxml,$replacement_map);
}


# Some of the Solr CoreAdmin API calls available. 
# See http://wiki.apache.org/solr/CoreAdmin
sub admin_reload_core
{
    my $self = shift @_;
    my ($core) = @_;

    my $cgi_get_args = "action=RELOAD&core=$core";

    $self->_admin_service($cgi_get_args);

    $self->solr_xml_to_solr_xml_in();
}

sub admin_rename_core
{
    my $self = shift @_;
    my ($oldcore, $newcore) = @_;

    my $cgi_get_args = "action=RENAME&core=$oldcore&other=$newcore";

    $self->_admin_service($cgi_get_args);

    $self->solr_xml_to_solr_xml_in();
}

sub admin_swap_core
{
    my $self = shift @_;
    my ($oldcore, $newcore) = @_;

    my $cgi_get_args = "action=SWAP&core=$oldcore&other=$newcore";

    $self->_admin_service($cgi_get_args);

    $self->solr_xml_to_solr_xml_in();
}

# The ALIAS action is not supported in our version of solr (despite it
# being marked as experimental in the documentation for Core Admin)
sub admin_alias_core
{
    my $self = shift @_;
    my ($oldcore, $newcore) = @_;

    my $cgi_get_args = "action=ALIAS&core=$oldcore&other=$newcore";

    $self->_admin_service($cgi_get_args);

    $self->solr_xml_to_solr_xml_in();
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

    $self->solr_xml_to_solr_xml_in();
}

# removes (unloads) core from the ext/solr/sorl.xml config file
sub admin_unload_core
{
    my $self = shift @_;
    my ($core, $delete) = @_;

    my $cgi_get_args = "action=UNLOAD&core=$core"; # &deleteIndex=true from Solr3.3
    if(defined $delete && $delete == 1) {
	$cgi_get_args = $cgi_get_args."&deleteIndex=true";
    }

    $self->_admin_service($cgi_get_args);

    $self->solr_xml_to_solr_xml_in();
}

sub copy_solrxml_to_web
{
    my $self = shift @_;

    my $ext_solrxml = &util::filename_cat($ENV{'GEXT_SOLR'}, "solr.xml.in");
    my $web_solrxml = &util::filename_cat($ENV{'GSDL3HOME'}, "ext", "solr", "solr.xml.in");

    #print STDERR "@@@@ Copying $ext_solrxml to $web_solrxml...\n";

    &FileUtils::copyFiles($ext_solrxml, $web_solrxml);

    $self->solr_xml_in_to_solr_xml();
}

sub start
{
    my $self = shift @_;
    my ($verbosity) = @_;
    
    $verbosity = 1 unless defined $verbosity;

    my $solr_home         = $ENV{'GEXT_SOLR'};
    my $jetty_stop_port   = $ENV{'JETTY_STOP_PORT'};
    my $jetty_server_port = $ENV{'SOLR_JETTY_PORT'};

    chdir($solr_home);
    
##    my $solr_etc = &util::filename_cat($solr_home,"etc");

    my $server_props = "-DSTOP.PORT=$jetty_stop_port";
    $server_props .= " -DSTOP.KEY=".$self->{'jetty_stop_key'};
    $server_props .= " -Dsolr.solr.home=$solr_home";

    my $full_server_jar = $self->{'full_server_jar'};
    
    my $server_java_cmd = "java $server_props -jar \"$full_server_jar\"";


    my $server_status = "unknown";

    if ($self->server_running()) {
	$server_status = "already-running";
    }
    elsif (open(STARTIN,"$server_java_cmd 2>&1 |")) {

	print STDERR "**** starting up solr jetty server with cmd start =\n $server_java_cmd\n" if ($verbosity > 1);

	my $line;
	while (defined($line=<STARTIN>)) {
	    # Scan through output until you see a line like:
	    #   2011-08-22 .. :INFO::Started SocketConnector@0.0.0.0:8983
	    # which signifies that the server has started up and is
	    # "ready and listening"
	
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
    }
    else {
	print STDERR "Error: failed to start solr-jetty-server\n";
	print STDERR "$!\n";
	print STDERR "Command attempted was:\n";
	print STDERR "  $server_java_cmd\n";
	print STDERR "run from directory:\n";
	print STDERR "  $solr_home\n";
	print STDERR "----\n";

	exit -1;
    }

    if ($server_status eq "explicitly-started") {
	$self->{'jetty_explicitly_started'} = 1;
	print "Jetty server ready and listening for connections on port";
	print " $jetty_server_port\n";
	    
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
	print STDERR "Using existing server detected on port $jetty_server_port\n";
	$self->{'jetty_explicitly_started'} = 0;
    }
    elsif ($server_status eq "failed-to-start") {
	print STDERR "Started Solr/Jetty web server on port $jetty_server_port";
	print STDERR ", but encountered an initialization error\n";
	exit -1;
    }

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

    print STDERR "**** java server stop cmd:\n  $server_java_cmd\n" if ($output_verbosity>1);

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
