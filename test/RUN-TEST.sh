
jars="solr-core-4.7.2.jar \
  solr-solrj-4.7.2.jar \
  log4j-over-slf4j-1.6.6.jar \
  slf4j-jdk14-1.6.6.jar \
  slf4j-api-1.6.6.jar \
  commons-io-2.1.jar \
  commons-fileupload-1.2.1.jar \
  lucene-analyzers-common-4.7.2.jar \
  lucene-analyzers-kuromoji-4.7.2.jar \
  lucene-core-4.7.2.jar \
  lucene-grouping-4.7.2.jar \
  lucene-highlighter-4.7.2.jar \
  lucene-memory-4.7.2.jar \
  lucene-misc-4.7.2.jar \
  lucene-queries-4.7.2.jar \
  lucene-spatial-4.7.2.jar \
  lucene-suggest-4.7.2.jar \
  velocity-1.6.1.jar"

classpath="."

for j in $jars ; do
  classpath="$classpath\;lib\\java\\$j"
done


classpath="$classpath\;lib\\servlet-api-2.5-20081211.jar"

java -Djava.util.logging.config.file=logging.properties \
     -cp $classpath \
    QueryTest

