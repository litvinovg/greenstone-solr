
jars="apache-solr-core-3.3.0.jar \
  apache-solr-solrj-3.3.0.jar \
  log4j-over-slf4j-1.6.1.jar \
  slf4j-jdk14-1.6.1.jar \
  slf4j-api-1.6.1.jar \
  commons-io-1.4.jar \
  commons-fileupload-1.2.1.jar \
  lucene-analyzers-3.3.0.jar \
  lucene-core-3.3.0.jar \
  lucene-grouping-3.3.0.jar \
  lucene-highlighter-3.3.0.jar \
  lucene-memory-3.3.0.jar \
  lucene-misc-3.3.0.jar \
  lucene-queries-3.3.0.jar \
  lucene-spatial-3.3.0.jar \
  lucene-spellchecker-3.3.0.jar \
  velocity-1.6.1.jar"

classpath="."

for j in $jars ; do
  classpath="$classpath\;lib\\java\\$j"
done


classpath="$classpath\;lib\\servlet-api-2.5-20081211.jar"

java -Djava.util.logging.config.file=logging.properties \
     -cp $classpath \
    QueryTest

