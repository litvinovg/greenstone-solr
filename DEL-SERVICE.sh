#!/bin/bash

gsdlsrcdir=src/java/org/greenstone/gsdl3

file_list=`cat java-src-file-list.txt | egrep -v '^#'`

for f in $file_list ; do
  if [ -e "../../$gsdlsrcdir/$f" ] ; then
    echo "Removing $gsdlsrcdir/$f from gsdl3 java code-base area '$gsdlsrcdir'"

    /bin/rm -f "../../$gsdlsrcdir/$f"
  fi
done

if [ -d backup ] ; then
  echo "Reverting GS2LuceneSearch to nolonger inherit from SharedSoleneGS2FieldSearch.java"
  /bin/rm ../../$gsdlsrcdir/service/SharedSoleneGS2FieldSearch.java 
  /bin/rm ../../$gsdlsrcdir/service/GS2LuceneSearch.java 
  /bin/mv backup/GS2LuceneSearch.java $gsdlsrcdir/service/.
  rmdir backup
fi


classesdir=web/WEB-INF/classes

prop_list=`cat prop-file-list.txt`

for f in $prop_list ; do
  echo "Removing $f from gsdl3 properties area '$classesdir'"

  /bin/rm -f "../../$classesdir/$f"
done

webextdir=ext/solr

if [ -d web ] ;
  # remove the content of the web folder copied in by ADD-SERVICE.sh
  /bin/rm -r ../../$webextdir
fi
