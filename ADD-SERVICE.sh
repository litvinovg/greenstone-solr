#!/bin/bash

gsdlsrcdir=src/java/org/greenstone/gsdl3

file_list=`cat java-src-file-list.txt | egrep -v '^#'`

for f in $file_list ; do
  echo "Adding $gsdlsrcdir/$f to gsdl3 java code-base"

  /bin/cp "$gsdlsrcdir/$f" "../../$gsdlsrcdir/$f"
done


classesdir=web/WEB-INF/classes

prop_list=`cat prop-file-list.txt`

for f in $prop_list ; do
  echo "Adding properties/$f to gsdl3 properties area"

  /bin/cp "properties/$f" "../../$classesdir/$f"
done


jarwebdir=web/WEB-INF/lib

file_list=`cat jar-file-list.txt | egrep -v '^#'`

for f in $file_list ; do
  echo "Adding $gsdlsrcdir/$f to gsdl3 web jar lib directory"

  /bin/cp "lib/java/$f" "../../$jarwebdir/$f"
done



webextdir=ext/solr

if [ ! -d ../../$webextdir ] ; then
  echo "Creating web extension direction: $webextdir"
  mkir ../../$webextdir
fi

if [ -d web ] ; then
  # copy the content of the web folder (avoiding the top-level .svn directory)
  /bin/cp -r web/* ../../$webextdir/.
fi

