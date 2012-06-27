BEGIN 
{
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

my $full_builddir = shift(@_);

my $solr_server = new solrserver($full_builddir);
$solr_server->start();