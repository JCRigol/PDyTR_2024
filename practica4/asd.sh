#!/bin/bash

## Flags ##
compile=false;
execute=false;

while getopts cx flag
do
	case "${flag}" in
		c) compile=true ;;
		x) execute=true ;;
		
	esac
done

## Arguments ##
WORKDIR=$( dirname $0 )

if [ $# -ge 2 ] ; then
	## Output error in usage and exit with error code
	
    echo "Usage: script.sh [FLAGS] [OPTIONAL-ARGUMENTS]"
	echo ""
	echo "Description of FLAGS:"
	echo "  -c          Set COMPILE=true"
	echo "  -x          Set EXECUTE=true"
	echo ""
	echo "OPTIONAL-ARGUMENTS:"
	echo "  DIRNAME     Specifies the directory to use"
	echo "				Input must be a valid name of a subdirectory in this scripts location"
	echo "				If not present value defaults to the scripts location (path/to/script.sh)"

    exit 1
    
elif [ $# -eq 1 ] ; then
	## DIR specified
	
	DIRNAME=$1
	WORKDIR=$WORKDIR/$DIRNAME
	
fi

## Commands ##
#COMPILE="$( ${JAVA_PATH} -classpath $JADE_LIB -d $OUTPUT_DIR $WORKDIR/*.java )" ## TODO: Logic for avoiding recompiling unchanged files

#EXEC_AGENT="$( ${JAVA_PATH}java -cp $JADE_LIB:$OUTPUT_DIR jade.Boot -container -host localhost -agents )"
#EXEC_CONTAINER="$( ${JAVA_PATH}java -cp $JADE_LIB jade.Boot -container )"
#EXEC_SERVER="$( ${JAVA_PATH}java -cp $JADE_LIB jade.Boot -gui )"

## Enviroment Variables ##
# Courtesy of https://stackoverflow.com/a/20909045 #
unamestr=$( uname )
if [ "$unamestr" = 'Linux' ] ; then

	export $(grep -v '^#' $( ls | grep .env ) | xargs -d '\n')
	
	if [ $compile = true ] ; then
		eval ${JAVAC_PATH} "-classpath $JADE_LIB -d $OUTPUT_DIR $WORKDIR/*.java"
	fi
	
	if [ $execute = true ] ; then
		eval $EXEC_SERVER
		for AGENT_DEF in "${AGENTS[@]}" ; do
			eval "$EXEC_AGENT $AGENT_DEF"
		done
	fi

	unset $(grep -v '^#' $( ls | grep .env ) | xargs -d '\n')
  
elif [ "$unamestr" = 'FreeBSD' ] || [ "$unamestr" = 'Darwin' ] ; then

	export $(grep -v '^#' $( ls | grep .env ) | xargs -0)
  
	if [ $compile = true ] ; then
		eval $COMPILE
	fi

	if [ $execute = true ] ; then
		eval $EXEC_SERVER
		for AGENT_DEF in "${AGENTS[@]}" ; do
			eval "$EXEC_AGENT $AGENT_DEF"
		done
	fi
  
	unset $(grep -v '^#' $( ls | grep .env ) | xargs -0)

fi


# project/
#  |-- p4.sh
#  |-- config.env
#  |-- ej1/	
#  |-- ej2/
#  |-- ej3/
#       |-- run.sh
#       |-- *.java

### ./p4.sh [FLAGS] -c COMPILE=true -x EXECUTE=true [ARGUMENTS] DIR=(default)project/ ----> c? ./DIR then x? ./DIR/run.sh


## Using stat for file changes since granularity of OS isn't an issue here

