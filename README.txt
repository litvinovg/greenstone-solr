README file for Greenstone 3's Solr extension

CONTENTS OF THIS README
- Acquiring and setting up the Solr extension
- Importing and (re-)building a collection with the Solr extension
- Running the Solr extension's Jetty server on its own
- Some handy manual commands
- Adding new Java classes into the Solr extension


ACQUIRING AND SETTING UP THE SOLR EXTENSION

1. SVN checkout the solr extension's src folder into the top-level ext directory of a Greenstone 3 installation, under the name "solr":

$gs3> cd ext
$gs3> svn co http://svn.greenstone.org/gs3-extensions/solr/trunk/src solr


2. Next run the add-service ant target in the solr extension just checked out, which will set up solr (the del-service will undo this setup if ever required):

$gs3/ext> cd solr
$gs3/ext/solr> ant add-service

3. The solr-jdbm-demo collection is copied into localsite's collect folder as part of the install.

4. Alternatively, edit an existing collection's etc/collectionConfig.xml, setting the search type to be solr. You may want to set up sort fields and facet fields, see solr-jdbm-demo collectionConfig.xml for examples.

5. Run the GS3 tomcat server:
$gs3> ant start

6. View your SOLR collections.

IMPORTING AND (RE-)BUILDING A COLLECTION WITH SOLR

1. Set up the environment for Greenstone 3 first to have access to the build-scripts:

$gs3> gs3-setup.bat / source gs3-setup.sh


2a. For a full rebuild (without -removeold it defaults to -removeold anyway), with or without the Greenstone3 server already running, type the following commands in succession:

$gs3> perl -S import.pl [-removeold] -site localsite <colname>
$gs3> perl -S buildcol.pl [-removeold] -site localsite <colname>
$gs3> perl -S activate.pl [-removeold] -site localsite <colname>

OR:
$gs3> perl -S import.pl [-removeold] -site localsite <colname>
$gs3> perl -S buildcol.pl [-removeold] -activate -site localsite <colname>

OR:
$gs3> perl -S full-rebuild.pl -site localsite <colname>


2b. For an incremental rebuild, with or without the Greenstone3 server already running:

$gs3> perl -S incremental-import.pl -site localsite <colname>
$gs3> perl -S incremental-buildcol.pl -site localsite <colname>
$gs3> perl -S activate.pl -incremental -site localsite <colname>


3. Preview the collection. If the Greenstone3 server wasn't already running, start it up first:
$gs3> ant start


RUNNING THE SOLR EXTENSION'S JETTY SERVER ON ITS OWN

1. Ensure the environment for Greenstone 3 is set up, if you haven't already done so:

$gs3> gs3-setup.bat / source gs3-setup.sh


2. Run the solr extension's jetty server in standalone mode:
$gs3> perl -S run_solr_server.pl start <optional stopkey>

If no stopkey is provided, it will use "standalone-greenstone-solr" as the stopkey.


3. Visit the running Solr Admin page for an index of your solr collection, which tends to be run on port 8983:
	http://localhost:8983/solr/localsite-<colname>-<indexlevel>/admin

E.g. http://localhost:8983/solr/localsite-solr-jdbm-demo-didx/admin
	 http://localhost:8983/solr/localsite-solr-jdbm-demo-sidx/admin

Search each index on the default search pattern (*:*) to see if documents have been properly indexed by solr.
If that's the case, an XML listing the number of responses and some metadata for each should be returned.


4. To stop a running solr extension jetty server that has been run in standalone mode. Pass in the same stopkey if you provided any during start:

$gs3> perl -S run_solr_server.pl stop <optional stopkey>


SOME HANDY MANUAL COMMANDS
The commands expressed below are for Linux, adjust them for Windows

1. Manually running the solr extension's jetty server:

$gs3/ext/solr> java -Dsolr.solr.home=`pwd` -jar lib/java/solr-jetty-server.jar


2. Manually getting this running solr server to index a greenstone collection's documents:

$gs3/ext/solr> java -Dsolr.solr.home=`pwd` -Durl=http://localhost:8983/solr/localsite-solr-jdbm-demo-didx/update -jar lib/java/solr-post.jar

The above posts the solr-jdbm-demo collection's didx (document-level) index folder contents to Solr to be ingested.


ADDING NEW JAVA CLASSES INTO THE SOLR EXTENSION

1. Create the Java classes and place them into their package within the ext/solr/src/java location. They can then be compiled by running 'ant compile' in the ext/solr folder.



