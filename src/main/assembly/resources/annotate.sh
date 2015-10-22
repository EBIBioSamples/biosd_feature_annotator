#!/bin/sh

# This is the entry point to invoke the annotator line command.
# 

# Do you use a proxy?
if [ "$http_proxy" != '' ]; then
  OPTS="$OPTS -DproxySet=true -DproxyHost=wwwcache.ebi.ac.uk -DproxyPort=3128 -DnonProxyHosts='*.ebi.ac.uk|localhost'"
fi

# These are passed to the JVM. they're appended, so that you can predefine it from the shell
# My Laptop OPTS="$OPTS -Xms2G -Xmx4G -XX:PermSize=128m -XX:MaxPermSize=256m"
OPTS="$OPTS -Xms10G -Xmx20G -XX:PermSize=512m -XX:MaxPermSize=1G"

# We always work with universal text encoding.
OPTS="$OPTS -Dfile.encoding=UTF-8"

# Monitoring with jconsole or jvisualvm. We keep this open, cause sometimes processes look stuck
# and it's useful to inspect the JVM to see what's going on
#OPTS="$OPTS 
# -Dcom.sun.management.jmxremote.port=5010
# -Dcom.sun.management.jmxremote.authenticate=false
# -Dcom.sun.management.jmxremote.ssl=false"
       
# Used for invoking a command in debug mode (end user doesn't usually need this)
#OPTS="$OPTS -Xdebug -Xnoagent"
#OPTS="$OPTS -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"

# You shouldn't need to change the rest
#
###

# Sometimes it hangs on external web services, this will make it to timeout (they are -1 = oo by default)
OPTS="$OPTS -Dsun.net.client.defaultConnectTimeout=30000 -Dsun.net.client.defaultReadTimeout=120000"


cd "$(dirname $0)"
MYDIR="$(pwd)"

# This includes the core and the db module, plus the H2 JDBC driver. If you want to use other databases 
# you need to download the .jar files you need and set up the classpath here
# (see http://kevinboone.net/classpath.html for details)  
export CLASSPATH="$CLASSPATH:$MYDIR:$MYDIR/lib/*"

# See here for an explaination about ${1+"$@"} :
# http://stackoverflow.com/questions/743454/space-in-java-command-line-arguments 

java \
  $OPTS uk.ac.ebi.fg.biosd.annotator.cli.AnnotateCmd ${1+"$@"}

EXCODE=$?

echo Java Finished. Quitting the Shell Too. >&2
exit $EXCODE
