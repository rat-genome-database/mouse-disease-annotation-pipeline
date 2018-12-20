#!/usr/bin/env bash
#
# Mouse Disease pipeline
#
. /etc/profile
APPNAME=MouseDisease
APPDIR=/home/rgddata/pipelines/$APPNAME
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`

EMAIL_LIST=mtutaj@mcw.edu
if [ "$SERVER" == "REED" ]; then
  EMAIL_LIST=mtutaj@mcw.edu,jrsmith@mcw.edu,slaulederkind@mcw.edu
fi


cd $APPDIR
java -Dspring.config=$APPDIR/../properties/default_db.xml \
    -Dlog4j.configuration=file://$APPDIR/properties/log4j.properties \
    -jar lib/${APPNAME}.lib "$@" 2>&1 | tee run.log

mailx -s "[$SERVER] Mouse DO Annotation pipeline OK" $EMAIL_LIST < $APPDIR/logs/status.log
