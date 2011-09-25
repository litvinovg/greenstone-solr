
Rudamentary program to test Solr built indexes.  Includes three
different ways to build a query, and how to access facetted
information.


1. Edit QueryTest.java to suit your installation/needs, in particular the static fields:
     'solr_home_str', 'myCore' and 'facet_field', and 'query'

2. Ensure 'javac' is in your path, then compile with:
     COMPILE-TEST.sh (or COMPILE-TEST.bat)

3. Assuming successful compilation, run with:

     RUN-TEST.sh (or RUN-TEST.bat)
