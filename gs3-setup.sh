
extdesc="the Solr Extension"

full_setup=`pwd`/${BASH_SOURCE}
fulldir=${full_setup%/*}
fulldir=${fulldir%/.}

#--
# Edit the following two port values if they conflict with
# with existing services on your computer
#--

# If using tomcat, read the tomcat host and port from the toplevel GS3 build.properties
# http://en.kioskea.net/faq/1757-how-to-read-a-file-line-by-line
# The following sets the field separator IFS to the = sign, then reads the file line by 
# line, setting propname and propval (which are fields separated by '=') for each line read
SOLR_PORT=8983
SOLR_HOST=localhost
file=$GSDL3SRCHOME/build.properties
while IFS== read propname propval; do
    if [ "x$propname" = "xtomcat.server" ] ; then
	SOLR_HOST=$propval
    fi
    if [ "x$propname" = "xtomcat.port" ] ; then
	SOLR_PORT=$propval
    fi          
done < $file

echo "SOLR port: $SOLR_PORT"
echo "SOLR host: $SOLR_HOST"

# If using jetty:
# The port Jetty runs on:
SOLR_JETTY_PORT=8983

# The port Jetty listens on for a "stop" command
JETTY_STOP_PORT=8079


if test -z $GSDLOS ; then
  GSDLOS=`uname -s | tr '[A-Z]' '[a-z]'`
  # check for running bash under Cygwin
  if test "`echo $GSDLOS | sed 's/cygwin//'`" != "$GSDLOS" ;
  then
    GSDLOS=windows
  fi
  # check for running bash under MinGW/MSys
  if test "`echo $GSDLOS | sed 's/mingw//'`" != "$GSDLOS" ;
  then
    GSDLOS=windows
  fi
  echo "GSDLOS was not set.  Setting it to '$GSDLOS'"
  export GSDLOS
fi
	
first_time=0;

if [ "x$GEXT_SOLR" = "x" ] ; then
  export GEXT_SOLR=`pwd`

  if [ -d "$GEXT_SOLR/bin/script" ] ; then
    export PATH=$GEXT_SOLR/bin/script:$PATH
  fi

  if [ -d "$GEXT_SOLR/lib" ] ; then
    if [ "$GSDLOS" = "linux" ] ; then
      export LD_LIBRARY_PATH=$GEXT_SOLR/lib:$LD_LIBRARY_PATH
    elif [ "$GSDLOS" = "darwin" ] ; then
      export DYLD_LIBRARY_PATH=$GEXT_SOLR/lib:$DYLD_LIBRARY_PATH
    fi
  fi

  extdir=${GEXT_SOLR##*/}

  if [ "x$GSDL3EXTS" = "x" ] ; then
    export GSDL3EXTS=$extdir
  else 
    export GSDL3EXTS=$GSDL3EXTS:$extdir
  fi

  export SOLR_JETTY_PORT
  export JETTY_STOP_PORT
  export SOLR_PORT
  export SOLR_HOST
  first_time=1

  echo "+Your environment is now setup for $extdesc"
else
  echo "+Your environment is already setup for $extdesc"
fi

#echo "++Solr/Jetty server will run on port $SOLR_JETTY_PORT (+ port $JETTY_STOP_PORT for shutdown command)"

if [ "$first_time" = "1" ] ; then
  echo "++Solr will run off the tomcat server on port $SOLR_PORT. "
  echo "-- This port value can be changed by editing tomcat.port in build.properties"
  echo ""
fi
