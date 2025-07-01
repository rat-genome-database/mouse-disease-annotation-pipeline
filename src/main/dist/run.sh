#!/usr/bin/env bash
#
# Mouse Disease pipeline
#
. /etc/profile
APPNAME=mouse-disease-annotation-pipeline
APPDIR=/home/rgddata/pipelines/$APPNAME
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`

EMAIL_LIST=mtutaj@mcw.edu
if [ "$SERVER" == "REED" ]; then
  EMAIL_LIST="mtutaj@mcw.edu jrsmith@mcw.edu slaulederkind@mcw.edu"
fi


cd $APPDIR
java -Dspring.config=$APPDIR/../properties/default_db2.xml \
    -Dlog4j.configurationFile=file://$APPDIR/properties/log4j2.xml \
    -jar lib/${APPNAME}.jar "$@" 2>&1 | tee run.log

mailx -s "[$SERVER] Mouse DO Annotation pipeline OK" $EMAIL_LIST < $APPDIR/logs/summary.log
