###########################################################################
#
# solrbuilder.pm -- perl wrapper for building index with Solr
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


package solrbuilder;

use strict; 
no strict 'refs';

use lucenebuilder;
use solrserver;
use Config; # for getting the perlpath in the recommended way

sub BEGIN {
    @solrbuilder::ISA = ('lucenebuilder');
}


sub new {
    my $class = shift(@_);
    my $self = new lucenebuilder (@_);
    $self = bless $self, $class;

    $self->{'buildtype'} = "solr";

    my $solr_passes_script = "solr_passes.pl";

    $self->{'solr_passes'} = "$solr_passes_script";
    # Tack perl on the beginning to ensure execution
    $self->{'solr_passes_exe'} = "\"$Config{perlpath}\" -S \"$solr_passes_script\"";
    return $self;
}


sub default_buildproc {
    my $self  = shift (@_);

    return "solrbuildproc";
}

# This writes a nice version of the text docs
#
# Essentially the same as the lucenebuilder.pm version, only using solr_passes
# => refactor and make better use of inheritence
#
sub compress_text
{
    my $self = shift (@_);
    # do nothing if we don't want compressed text
    return if $self->{'no_text'};

    my ($textindex) = @_;

    # workaround to avoid hard-coding "solr" check into buildcol.pl
    $textindex =~ s/^section://; 

    my $outhandle = $self->{'outhandle'};

    # the text directory
    my $text_dir = &FileUtils::filenameConcatenate($self->{'build_dir'}, "text");
    my $build_dir = &FileUtils::filenameConcatenate($self->{'build_dir'},"");
    &FileUtils::makeAllDirectories($text_dir);

    my $osextra = "";
    if ($ENV{'GSDLOS'} =~ /^windows$/i)
    {
	$text_dir =~ s@/@\\@g;
    }
    else
    {
	if ($outhandle ne "STDERR")
	{
	    # so solr_passes doesn't print to stderr if we redirect output
	    $osextra .= " 2>/dev/null";
	}
    }

    # Find the perl script to call to run solr
    my $solr_passes = $self->{'solr_passes'};
    my $solr_passes_exe = $self->{'solr_passes_exe'};

    my $solr_passes_sections = "Doc";

    my ($handle);

    if ($self->{'debug'})
    {
	$handle = *STDOUT;
    }
    else
    {
	my $site        = $self->{'site'};
	my $collect     = $self->{'collection'};	
	my $core_prefix = (defined $site) ? "$site-$collect" : $collect;
	my $core        = $core_prefix; # unused in this call to solr_passes

	$core = "building-".$core unless $self->{'incremental'}; # core points to building only for force_removeold

        print STDERR "Executable:    $solr_passes_exe\n";
        print STDERR "Sections:      $solr_passes_sections\n";
        print STDERR "Build Dir:     $build_dir\n";
        print STDERR "Cmd:           $solr_passes_exe $core text \"$build_dir\" \"dummy\"   $osextra\n";
	if (!open($handle, "| $solr_passes_exe $core text \"$build_dir\" \"dummy\"   $osextra"))
	{
	    print STDERR "<FatalError name='NoRunSolrPasses'/>\n</Stage>\n" if $self->{'gli'};
	    die "solrbuilder::build_index - couldn't run $solr_passes_exe\n$!\n";
	}
    }

    # stored text is always Doc and Sec levels    
    my $levels = { 'document' => 1, 'section' => 1 };
    # always do database at section level
    my $db_level = "section"; 

    # set up the document processr
    $self->{'buildproc'}->set_output_handle ($handle);
    $self->{'buildproc'}->set_mode ('text');
    $self->{'buildproc'}->set_index ($textindex);
    $self->{'buildproc'}->set_indexing_text (0);
    #$self->{'buildproc'}->set_indexfieldmap ($self->{'indexfieldmap'});
    $self->{'buildproc'}->set_levels ($levels);
    $self->{'buildproc'}->set_db_level ($db_level);
    $self->{'buildproc'}->reset();

    &plugin::begin($self->{'pluginfo'}, $self->{'source_dir'},
		   $self->{'buildproc'}, $self->{'maxdocs'});
    &plugin::read ($self->{'pluginfo'}, $self->{'source_dir'},
		   "", {}, {}, $self->{'buildproc'}, $self->{'maxdocs'}, 0, $self->{'gli'});
    &plugin::end($self->{'pluginfo'});

    close ($handle) unless $self->{'debug'};
    $self->print_stats();

    print STDERR "</Stage>\n" if $self->{'gli'};
}

#----



sub filter_in_out_file
{
    my ($in_filename,$out_filename,$replace_rules) = @_;

    if (open(SIN,"<$in_filename")) {

	if (open(SOUT,">$out_filename")) {

	    my $line;
	    while (defined ($line=<SIN>)) {
		chomp $line;

		my $done_insert = 0;
		foreach my $rule (@$replace_rules) {
		    my $line_re = $rule->{'regexp'};
		    my $insert  = $rule->{'insert'};

		    if ($line =~ m/$line_re/) {
			print SOUT $insert;
			$done_insert = 1;
			last;
		    }
		}
		if (!$done_insert) {
		    print SOUT "$line\n";;
		}
	    }

	    close(SOUT);
	}
	else {
	    print STDERR "Error: Failed to open $out_filename\n";
	    print STDERR "       $!\n";
	}

	close(SIN);
    }
    else {
	print STDERR "Error: Failed to open $in_filename\n";
	print STDERR "       $!\n";
    }

}

# We need to push the list of indexfield to shortname mappings through to the
# build_cfg as, unlike in MGPP, we need these mappings in advance to configure
# Lucene/Solr. Unfortunately the original function found in mgbuilder.pm makes
# a mess of this - it only output fields that have been processed (none have)
# and it has a hardcoded renaming for 'text' so it becomes 'TX' according to
# the schema but 'TE' according to XML sent to lucene_passes.pl/solr_passes.pl
# This version is dumber - just copy them all across verbatum - but works. We
# do still need to support the special case of 'allfields'
sub make_final_field_list
{
  my $self = shift (@_);
  $self->{'build_cfg'} = {};
  my @indexfieldmap = ();
  my @indexfields = ();

  # @todo support: $self->{'buildproc'}->{'extraindexfields'}
  foreach my $fields (@{$self->{'collect_cfg'}->{'indexes'}})
  {
    # remove subcoll stuff
    $fields =~ s/:.*$//;
    foreach my $field (split(';', $fields))
    {
      my $shortname = 'ERROR';
      if ($field eq 'allfields')
      {
        $shortname = 'ZZ';
      }
      elsif (defined $self->{'buildproc'}->{'indexfieldmap'}->{$field})
      {
        $shortname = $self->{'buildproc'}->{'indexfieldmap'}->{$field};
      }
      else
      {
        print STDERR 'Error! Couldn\'t find indexfieldmap for field: ' . $field . "\n";
      }
      push (@indexfieldmap, $field . '->' . $shortname);
      push (@indexfields, $field);
    }
  }

  if (scalar @indexfieldmap)
  {
    $self->{'build_cfg'}->{'indexfieldmap'} = \@indexfieldmap;
  }

  if (scalar @indexfields)
  {
    $self->{'build_cfg'}->{'indexfields'} = \@indexfields;
  }
}

# Generate solr schema.xml file based on indexmapfield and other associated
# config files 
#
# Unlike make_auxiliary_files(), this needs to be done up-front (rather
# than at the end) so the data-types in schema.xml are correctly set up
# prior to document content being pumped through solr_passes.pl


sub premake_solr_auxiliary_files 
{
    my $self = shift (@_);

    # Replace the following marker: 
    #
    #   <!-- ##GREENSTONE-FIELDS## -->
    #
    # with lines of the form:
    #
    #   <field name="<field>" type="string" ... /> 
    #
    # for each <field> in 'indexfieldmap'

    my $schema_insert_xml = "";

    foreach my $ifm (@{$self->{'build_cfg'}->{'indexfieldmap'}}) {

		my ($field) = ($ifm =~ m/^.*->(.*)$/);

		$schema_insert_xml .= "    "; # indent
		$schema_insert_xml .= "<field name=\"$field\" ";

		if($field eq "LA" || $field eq "LO")
		{
			$schema_insert_xml .=   "type=\"location\" ";
		}
#		elsif ($field ne "ZZ" && $field ne "TX")
#		{
#			$schema_insert_xml .=   "type=\"string\" ";
#		}
		else
		{
			$schema_insert_xml .= "type=\"text_en_splitting\" ";
		}
		$schema_insert_xml .=  "indexed=\"true\" stored=\"false\" multiValued=\"true\" />\n";
                #$schema_insert_xml .=  "indexed=\"true\" stored=\"true\" multiValued=\"true\" />\n";
    }

    # just the one rule to date
    my $insert_rules 
	= [ { 'regexp' => "^\\s*<!--\\s*##GREENSTONE-FIELDS##\\s*-->\\s*\$",
	      'insert' => $schema_insert_xml } ];
        
    my $solr_home = $ENV{'GEXT_SOLR'};
##    my $in_dirname = &FileUtils::filenameConcatenate($solr_home,"etc","conf");
    my $in_dirname = &FileUtils::filenameConcatenate($solr_home,"conf");
    my $schema_in_filename = &FileUtils::filenameConcatenate($in_dirname,"schema.xml.in");

    my $collect_home = $ENV{'GSDLCOLLECTDIR'};
    my $out_dirname = &FileUtils::filenameConcatenate($collect_home,"etc","conf");
    my $schema_out_filename = &FileUtils::filenameConcatenate($out_dirname,"schema.xml");
    
    # make sure output conf directory exists
    if (!&FileUtils::directoryExists($out_dirname)) {
	&FileUtils::makeDirectory($out_dirname);
    }

    filter_in_out_file($schema_in_filename,$schema_out_filename,$insert_rules);

    # now do the same for solrconfig.xml, stopwords, ...
    # these are simpler, as they currently do not need any filtering

    my @in_file_list = ( "solrconfig.xml", "stopwords.txt", "stopwords_en.txt",
			 "synonyms.txt", "protwords.txt", "currency.xml", "elevate.xml" );
 
    foreach my $file ( @in_file_list ) {
	my $in_filename = &FileUtils::filenameConcatenate($in_dirname,$file.".in");
	my $out_filename = &FileUtils::filenameConcatenate($out_dirname,$file);

	if(&FileUtils::fileExists($in_filename)) {
	    filter_in_out_file($in_filename,$out_filename,[]);
	}
    }

    my @in_dir_list = ( "lang" );
    foreach my $dir ( @in_dir_list ) {
	
	my $full_subdir_name = &FileUtils::filenameConcatenate($in_dirname,$dir);

	if(&FileUtils::directoryExists($full_subdir_name)) {
	    &FileUtils::copyFilesRecursiveNoSVN($full_subdir_name, $out_dirname);
	}
    }
}


sub pre_build_indexes
{
    my $self = shift (@_);
    my ($indexname) = @_;
    my $outhandle = $self->{'outhandle'};

    # If the Solr/Jetty server is not already running, the following starts
    # it up, and only returns when the server is "reading and listening"
  
    my $solr_server = new solrserver($self->{'build_dir'});
    $solr_server->start();
    $self->{'solr_server'} = $solr_server;

    my $indexes = [];
    if (defined $indexname && $indexname =~ /\w/) {
	push @$indexes, $indexname;
    } else {
	$indexes = $self->{'collect_cfg'}->{'indexes'};
    }

    # skip para-level check, as this is done in the main 'build_indexes' 
    # routine

    my $all_metadata_specified = 0; # has the user added a 'metadata' index?
    my $allfields_index = 0;        # do we have an allfields index?

    # Using a hashmap here would avoid duplications, but while more space
    # efficient, it's not entirely clear it would be more computationally
    # efficient
    my @all_fields = ();

    foreach my $index (@$indexes) {
	if ($self->want_built($index)) {

	    # get the parameters for the output
	    # split on : just in case there is subcoll and lang stuff
	    my ($fields) = split (/:/, $index);

	    foreach my $field (split (/;/, $fields)) {
		if ($field eq "metadata") {
		    $all_metadata_specified = 1;
		}
		else {
		    push(@all_fields,$field);
		}
	    }
	}
    }

    if ($all_metadata_specified) {

	# (Unforunately) we need to process all the documents in the collection
	# to figure out what the metadata_field_mapping is	    

	# set up the document processr
	$self->{'buildproc'}->set_output_handle (undef);
	$self->{'buildproc'}->set_mode ('index_field_mapping');
	$self->{'buildproc'}->reset();
	
	&plugin::begin($self->{'pluginfo'}, $self->{'source_dir'},
		       $self->{'buildproc'}, $self->{'maxdocs'});
	&plugin::read ($self->{'pluginfo'}, $self->{'source_dir'},
		       "", {}, {}, $self->{'buildproc'}, $self->{'maxdocs'}, 0, $self->{'gli'});
	&plugin::end($self->{'pluginfo'});
	
    }

    else {
	# Field mapping solely dependent of entries in 'indexes'

	# No need to explicitly handle "allfields" as create_shortname()
	# will get a fix on it through it's static_indexfield_map

	my $buildproc = $self->{'buildproc'};
	
      foreach my $field (@all_fields)
      {
        if (!defined $buildproc->{'indexfieldmap'}->{$field})
        {
          my $shortname = '';
          if (defined $buildproc->{'fieldnamemap'}->{$field})
          {
            $shortname = $buildproc->{'fieldnamemap'}->{$field};
          }
          else
          {
            $shortname = $buildproc->create_shortname($field);
          }
          $buildproc->{'indexfieldmap'}->{$field} = $shortname;
          $buildproc->{'indexfieldmap'}->{$shortname} = 1;
        }
      }
    }

    # Write out solr 'schema.xml' (and related) file 
    #
    $self->make_final_field_list();
    $self->premake_solr_auxiliary_files();

    # Now update the solr-core information in solr.xml
    # => at most two cores <colname>-Doc and <colname>-Sec

    my $site        = $self->{'site'};
    my $collect     = $self->{'collection'};
    my $core_prefix = (defined $site) ? "$site-$collect" : $collect;

    # my $idx = $self->{'index_mapping'}->{$index};
    my $idx = "idx";

    my $build_dir = $self->{'build_dir'};

    foreach my $level (keys %{$self->{'levels'}}) {
	
	my ($pindex) = $level =~ /^(.)/;

	my $index_dir = $pindex.$idx;
	my $core = "$core_prefix-$index_dir";

	# force_removeold == opposite of being run in 'incremental' mode
	my $force_removeold = ($self->{'incremental'}) ? 0 : 1;

	if ($force_removeold) {
	    print $outhandle "\n-removeold set (new index will be created)\n";

	    # create cores under temporary core names, corresponding to building directory
	    $core = "building-".$core; 

	    my $full_index_dir = &FileUtils::filenameConcatenate($build_dir,$index_dir);
	    &FileUtils::removeFilesRecursive($full_index_dir);
	    &FileUtils::makeDirectory($full_index_dir);

	    my $full_tlog_dir = &FileUtils::filenameConcatenate($full_index_dir, "tlog");
	    &FileUtils::makeDirectory($full_tlog_dir);

	    # Solr then wants an "index" folder within this general index area!
#	    my $full_index_index_dir = &FileUtils::filenameConcatenate($full_index_dir,"index");
#	    &FileUtils::makeDirectory($full_index_index_dir);


	    # now go on and create new index
	    print $outhandle "Creating Solr core: $core\n";
	    $solr_server->admin_create_core($core);

	}
	else {
	    # if collect==core already in solr.xml (check with STATUS)
	    # => use RELOAD call to refresh fields now expressed in schema.xml
	    #
	    # else 
	    # => use CREATE API to add to solr.xml
		
	    my $check_core_exists = $solr_server->admin_ping_core($core);
       
	    if ($check_core_exists) {	    
		print $outhandle "Reloading Solr core: $core\n";
		$solr_server->admin_reload_core($core);
	    }
	    else {
		print $outhandle "Creating Solr core: $core\n";
		$solr_server->admin_create_core($core);
	    }
	}
    }

}

# Essentially the same as the lucenebuilder.pm version, only using solr_passes
# => refactor and make better use of inheritence

sub build_index {
    my $self = shift (@_);
    my ($index,$llevel) = @_;
    my $outhandle = $self->{'outhandle'};
    my $build_dir = $self->{'build_dir'};

    # get the full index directory path and make sure it exists
    my $indexdir = $self->{'index_mapping'}->{$index};
    &FileUtils::makeAllDirectories(&FileUtils::filenameConcatenate($build_dir, $indexdir));

    # Find the perl script to call to run solr
    my $solr_passes = $self->{'solr_passes'};
    my $solr_passes_exe = $self->{'solr_passes_exe'};

    # define the section names for solrpasses
    # define the section names and possibly the doc name for solrpasses
    my $solr_passes_sections = $llevel;

    my $osextra = "";
    if ($ENV{'GSDLOS'} =~ /^windows$/i) {
	$build_dir =~ s@/@\\@g;
    } else {
	if ($outhandle ne "STDERR") {
	    # so solr_passes doesn't print to stderr if we redirect output
	    $osextra .= " 2>/dev/null";
	}
    }

    # get the index expression if this index belongs
    # to a subcollection
    my $indexexparr = [];
    my $langarr = [];

    # there may be subcollection info, and language info.
    my ($fields, $subcollection, $language) = split (":", $index);
    my @subcollections = ();
    @subcollections = split /,/, $subcollection if (defined $subcollection);

    foreach $subcollection (@subcollections) {
	if (defined ($self->{'collect_cfg'}->{'subcollection'}->{$subcollection})) {
	    push (@$indexexparr, $self->{'collect_cfg'}->{'subcollection'}->{$subcollection});
	}
    }

    # add expressions for languages if this index belongs to
    # a language subcollection - only put languages expressions for the
    # ones we want in the index
    my @languages = ();
    my $languagemetadata = "Language";
    if (defined ($self->{'collect_cfg'}->{'languagemetadata'})) {
	$languagemetadata = $self->{'collect_cfg'}->{'languagemetadata'};
    }
    @languages = split /,/, $language if (defined $language);
    foreach my $language (@languages) {
	my $not=0;
	if ($language =~ s/^\!//) {
	    $not = 1;
	}
	if($not) {
	    push (@$langarr, "!$language");
	} else {
	    push (@$langarr, "$language");
	}
    }

    # Build index dictionary. Uses verbatim stem method
    print $outhandle "\n    creating index dictionary (solr_passes -I1)\n"  if ($self->{'verbosity'} >= 1);
    print STDERR "<Phase name='CreatingIndexDic'/>\n" if $self->{'gli'};
    my ($handle);

    if ($self->{'debug'}) {
	$handle = *STDOUT;
    } else {
	my $site        = $self->{'site'};
	my $collect     = $self->{'collection'};
	my $core_prefix = (defined $site) ? "$site-$collect" : $collect;
	my $ds_idx      = $self->{'index_mapping'}->{$index};
	my $core        = "$core_prefix-$ds_idx";

	$core = "building-".$core unless $self->{'incremental'}; # core points to building only for force_removeold

	print STDERR "Cmd: $solr_passes_exe $core index \"$build_dir\" \"$indexdir\"   $osextra\n";
	if (!open($handle, "| $solr_passes_exe $core index \"$build_dir\" \"$indexdir\"   $osextra")) {
	    print STDERR "<FatalError name='NoRunSolrPasses'/>\n</Stage>\n" if $self->{'gli'};
	    die "solrbuilder::build_index - couldn't run $solr_passes_exe\n!$\n";
	}
    }

    my $store_levels = $self->{'levels'};
    my $db_level = "section"; #always
    my $dom_level = "";
    foreach my $key (keys %$store_levels) {
	if ($mgppbuilder::level_map{$key} eq $llevel) {
	    $dom_level = $key;
	}
    }
    if ($dom_level eq "") {
	print STDERR "Warning: unrecognized tag level $llevel\n";
	$dom_level = "document";
    }

    my $local_levels = { $dom_level => 1 }; # work on one level at a time

    # set up the document processr
    $self->{'buildproc'}->set_output_handle ($handle);
    $self->{'buildproc'}->set_mode ('text');
    $self->{'buildproc'}->set_index ($index, $indexexparr);
    $self->{'buildproc'}->set_index_languages ($languagemetadata, $langarr) if (defined $language);
    $self->{'buildproc'}->set_indexing_text (1);
    #$self->{'buildproc'}->set_indexfieldmap ($self->{'indexfieldmap'});
    $self->{'buildproc'}->set_levels ($local_levels);
    if (defined $self->{'collect_cfg'}->{'sortfields'}) {
	$self->{'buildproc'}->set_sortfields ($self->{'collect_cfg'}->{'sortfields'});
    }
    if (defined $self->{'collect_cfg'}->{'facetfields'}) {
	$self->{'buildproc'}->set_facetfields ($self->{'collect_cfg'}->{'facetfields'});
    }
    $self->{'buildproc'}->set_db_level($db_level);
    $self->{'buildproc'}->reset();

    print $handle "<update>\n";

    &plugin::read ($self->{'pluginfo'}, $self->{'source_dir'},
		   "", {}, {}, $self->{'buildproc'}, $self->{'maxdocs'}, 0, $self->{'gli'});
	
    print $handle "</update>\n";

    close ($handle) unless $self->{'debug'};

    $self->print_stats();

    $self->{'buildproc'}->set_levels ($store_levels);
    print STDERR "</Stage>\n" if $self->{'gli'};

}


sub post_build_indexes {
    my $self = shift(@_);

    # deliberately override to prevent the mgpp post_build_index() calling
    #  $self->make_final_field_list()
    # as this has been done in our pre_build_indexes() phase for solr


    # Also need to stop the Solr/jetty server if it was explicitly started
    # in pre_build_indexes()
    
    my $solr_server = $self->{'solr_server'};

    if ($solr_server->explicitly_started()) {
	$solr_server->stop();
    }

    $self->{'solr_server'} = undef;

}    

sub build_cfg_extra {
    my $self = shift (@_);
    my ($build_cfg) = @_;

    $self->lucenebuilder::build_cfg_extra($build_cfg);

    # need to add in facet stuff
    my @facetfields = ();
    my @facetfieldmap = ();

    foreach my $sf (@{$self->{'buildproc'}->{'facetfields'}}) {
	if ($sf eq "rank") {
	    push(@facetfields, $sf);
	} elsif ($self->{'buildproc'}->{'actualsortfields'}->{$sf}) {
	    my $shortname = $self->{'buildproc'}->{'sortfieldnamemap'}->{$sf};
	    push(@facetfields, $shortname);
	    push (@facetfieldmap, "$sf\-\>$shortname");
	}
	
    }
    $build_cfg->{'indexfacetfields'} = \@facetfields;
    $build_cfg->{'indexfacetfieldmap'} = \@facetfieldmap;
}  
1;


