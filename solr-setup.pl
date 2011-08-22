
BEGIN {
    die "GSDLHOME not set\n" unless defined $ENV{'GSDLHOME'};
    die "GSDLOS not set\n" unless defined $ENV{'GSDLOS'};
    unshift (@INC, "$ENV{'GSDLHOME'}/perllib");
    unshift (@INC, "$ENV{'GSDLHOME'}/perllib/cpan");
}

use Cwd;
require util;    

my $env_ext  = "GEXT_SOLR";
my $ext_desc = "the SOLR Extension";

if (!defined $ENV{$env_ext}) {
    
    my $ext_home = cwd();
    $ENV{$env_ext} = $ext_home;

    my $ext_bin_script = &util::filename_cat($ENV{$env_ext},"bin","script");
    my $ext_lib = &util::filename_cat($ENV{$env_ext},"lib");

    if (-d $ext_bin_script) {
	&util::envvar_prepend("PATH",$ext_bin_script);
    }
   
    if (-d $ext_lib) {
	if ($ENV{'GSDLOS'} =~ m/windows/i) {
	    &util::envvar_prepend("PATH",$ext_lib);
	} 
	elsif ($ENV{'GSDLOS'} =~ m/darwin/i) {
	    &util::envvar_prepend("DYLD_LIBRARY_PATH",$ext_lib);
	}
	else { # linux
	    &util::envvar_prepend("LD_LIBRARY_PATH",$ext_lib);
	}
    }

    if ($ENV{'GSDLOS'} =~ m/windows/i) {
	$ENV{'GS_CP_SET'} = "yes";
    }

    my ($ext_dir,$_prefix_dir) = File::Basename::fileparse($ext_home);
    # GSDLEXTS always uses : as a separator
    if (defined $ENV{'GSDLEXTS'}) {
	$ENV{'GSDLEXTS'} .= ":$ext_dir";
    }
    else {
	$ENV{'GSDLEXTS'} = $ext_dir;
    }
    
    print STDERR "+Your environment is now setup for $ext_desc\n";
}
else {    
    print STDERR "+Your environment is already setup for $ext_desc\n";
}

if (scalar(@ARGV>0)) {

    print STDERR "\n";

    my $cmd = join(" ",map {$_ = "\"$_\""} @ARGV);

    if (system($cmd)!=0) {
	print STDERR "\nError: Failed to run '$cmd'\n";
	print STDERR "$!\n";
    }
}

1;
