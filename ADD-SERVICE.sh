#!/bin/bash

gsdlsrcdir=src/java/org/greenstone/gsdl3

file_list=`cat java-src-file-list.txt | egrep -v '^#'`

for f in $file_list ; do
  echo "Adding $gsdlsrcdir/$f to gsdl3 java code-base"

  /bin/cp "$gsdlsrcdir/$f" "../../$gsdlsrcdir/$f"
done


if [ ! -d backup ] ; then
  echo "Patching GS2LuceneSearch to inherit from SharedSoleneGS2FieldSearch.java"
  mkdir backup
  /bin/mv ../../$gsdlsrc/service/GS2LuceneSearch.java backup/.
  /bin/cp $gsdlsrc/service/GS2LuceneSearch.java ../../$gsdlsrc/service/.
  /bin/cp $gsdlsrc/service/SharedSoleneGS2FieldSearch.java ../../$gsdlsrc/service/.
fi

classesdir=web/WEB-INF/classes

prop_list=`cat prop-file-list.txt`

for f in $prop_list ; do
  echo "Adding properties/$f to gsdl3 properties area"

  /bin/cp "properties/$f" "../../$classesdir/$f"
done

webextdir=ext/solr

if [ ! -d ../../$webextdir ] ; then
  echo "Creating web extension direction: $webextdir"
  mkir ../../$webextdir
fi

# copy the content of the web folder (avoiding the top-level .svn directory)
/bin/cp -r web/* ../../$webextdir/.

