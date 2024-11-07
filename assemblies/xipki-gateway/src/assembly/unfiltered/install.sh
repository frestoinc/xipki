#!/bin/bash

set -e

helpFunction()
{
   echo ""
   echo "Usage: $0 -t <dir of destination tomcat>"
   exit 1 # Exit script after printing help
}

#while getopts "a:b:" opt
while getopts "t:" opt
do
   case "$opt" in
      t ) tomcatDir="$OPTARG" ;;
      ? ) helpFunction ;; # Print helpFunction in case parameter is non-existent
   esac
done

# Print helpFunction in case parameters are empty
#if [ -z "$parameterA" ] || [ -z "$parameterB" ]
if [ -z "$tomcatDir" ]
then
   echo "Some or all of the parameters are empty";
   helpFunction
fi

## workding dir
#WDIR="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"
WDIR=`dirname $0`

## check the pre-conditions
if [ ! -d ${WDIR}/tomcat/xipki/keycerts ]; then
   echo "Generate the key and certificate via ${WDIR}/../setup/generate-keycerts.sh first."
   exit 1
fi

echo "Tomcat: $tomcatDir"

## make sure the tomcat is only for Gateway
if [ -f ${tomcatDir}/webapps/ca.war ]; then
   echo "CA is running in $tomcatDir, please use other tomcat instance."
   exit 1
fi

if [ -f ${tomcatDir}/webapps/hp.war ]; then
   echo "HSM proxy is running in $tomcatDir, please use other tomcat instance."
   exit 1
fi

if [ -f ${tomcatDir}/webapps/ocsp.war ]; then
   echo "OCSP responder is running in $tomcatDir, please use other tomcat instance."
   exit 1
fi

## detect the major version of tomcat
TOMCAT_VERSION=`${tomcatDir}/bin/version.sh | grep "Server number"`
echo "Tomcat ${TOMCAT_VERSION}"

TOMCAT_VERSION=`cut -d ":" -f2- <<< "${TOMCAT_VERSION}"`
TOMCAT_VERSION=`cut -d "." -f1  <<< "${TOMCAT_VERSION}"`
## Remove leading and trailing spaces and tabs
TOMCAT_VERSION=`awk '{$1=$1};1'  <<< "${TOMCAT_VERSION}"`

if [ "$TOMCAT_VERSION" -lt "8" ]; then
  echo "Unsupported tomcat major version ${TOMCAT_VERSION}"
  exit 1
fi

## Backup the current files
BDIR=$tomcatDir/backup-`date '+%Y%m%dT%H%M%S'`
mkdir ${BDIR}
mkdir ${BDIR}/bin
mkdir ${BDIR}/lib
mkdir ${BDIR}/conf
mkdir ${BDIR}/webapps
echo "backup dir: $BDIR"

SRC="${tomcatDir}/xipki"
[ -d $SRC ] && cp -r $SRC ${BDIR}

SRC="${tomcatDir}/conf/catalina.properties"
[ -f $SRC ] && mv $SRC ${BDIR}/conf

SRC="${tomcatDir}/conf/server.xml"
[ -f $SRC ] && mv $SRC ${BDIR}/conf

# mv if file exists
# [ -f old ] && mv old nu

SRC="${tomcatDir}/bin/setenv.*"
for X in $SRC; do [[ -e $X ]] && mv "$X" ${BDIR}/bin; done

SRC="${tomcatDir}/lib/password-*.jar"
for X in $SRC; do [[ -e $X ]] && mv "$X" ${BDIR}/lib; done

SRC="${tomcatDir}/lib/xipki-tomcat-password-*.jar"
for X in $SRC; do [[ -e $X ]] && mv "$X" ${BDIR}/lib; done

SRC="${tomcatDir}/lib/*pkcs11wrapper-*.jar"
for X in $SRC; do [[ -e $X ]] && mv "$X" ${BDIR}/lib; done

SRC="${tomcatDir}/lib/bc*-jdk*.jar"
for X in $SRC; do [[ -e $X ]] && mv "$X" ${BDIR}/lib; done

SRC="${tomcatDir}/lib/h2-*.jar"
for X in $SRC; do [[ -e $X ]] && mv "$X" ${BDIR}/lib; done

SRC="${tomcatDir}/lib/mariadb-java-*.jar"
for X in $SRC; do [[ -e $X ]] && mv "$X" ${BDIR}/lib; done

SRC="${tomcatDir}/lib/mariadb-java-*.jar"
for X in $SRC; do [[ -e $X ]] && mv "$X" ${BDIR}/lib; done

if [ "$TOMCAT_VERSION" -lt "10" ]; then
  _DIR=tomcat8on
else
  _DIR=tomcat10on
fi

cp -r ${WDIR}/tomcat/* ${tomcatDir}
cp -r ${WDIR}/${_DIR}/conf ${tomcatDir}/

wars=(
    gw
    acme
    cmp
    est
    rest
    scep
)

for i in "${wars[@]}"; do
  war="${tomcatDir}/webapps/${i}"
  [ -f ${war}.war ] && mv ${war}.war ${BDIR}/webapps
  rm -rf "${war}"
done

echo "Copying gw.war"
cp ${WDIR}/${_DIR}/webapps/gw.war ${tomcatDir}/webapps
