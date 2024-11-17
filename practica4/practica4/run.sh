#!/bin/bash


##### Functions Block #####

## JADE Config ##

exec_user_interface()
{
	# Determine the agents to execute
	# Determine the n° of containers to execute
	# Determine which of containers or agents executes first
	
	# Execute JADE GUI -> confirm execution
	# Execute ag/cont (whichever first) -> confirm execution (of all)
	# Execute ag/cont (whichever first) -> confirm execution (of all)
	
	# When last agent dies -> Tear down JADE GUI
	
	echo "Welcome to the JADE Execution CLI"
    echo "1) Start JADE GUI"
    echo "2) Start Containers"
    echo "3) Start Agents"
    echo "4) Start Full Execution"
    echo "5) Exit"

    read -p "Choose which will be executed first [\e[4;31mA\e[0mgents/\e[4;31mC\e[0montainers]: " choice

    case $choice in
        1)
            execute_jade
            ;;
        2)
            read -p "Enter number of containers to start: " container_count
            execute_containers "$container_count"
            ;;
        3)
            read -p "Enter path to directory with agents: " agents_path
            execute_agents "$agents_path"
            ;;
        4)
            echo "Starting full execution..."
            execute_jade
            sleep 5 # Give GUI time to initialize
            read -p "Enter number of containers to start: " container_count
            execute_containers "$container_count"
            read -p "Enter path to directory with agents: " agents_path
            execute_agents "$agents_path"
            ;;
        5)
            echo "Exiting."
            exit 0
            ;;
        *)
            echo "Invalid choice. Please select a valid option."
            exec_user_interface
            ;;
    esac
}

agents()
{
	trap ' break ' SIGINT
	
	local dir=$1
	local -n ag_arr_output=$2
	declare -A agents_arr
	classes_available=()
	
	for file in $( find "$dir" -type f -name '*.java' -exec grep -l 'extends Agent' {} \+ ) ; do
		class_name=$( basename "$file" .java )
		pckg=$( grep 'package' $file | sed 's/package \([^;]*\);/\1/' )

		classes_available+=($pckg.$class_name)
	done
	
	while : ; do
		read -p "INGRESE EL NOMBRE DEL AGENTE: " agent_name
		
		echo "SELECCIONE SU CLASE:"
		for i in "${!classes_available[@]}"; do
		    echo "$((i + 1))) $( echo ${classes_available[$i]} | sed 's/.*\.\([^\.]*\)$/\1/' )"
		done
		
		read -p "Ingrese el número de la clase (1-${#classes_available[@]}): " class_num
		
		while [[ $class_num -lt 1 ]] || [[ $class_num -gt ${#classes_available[@]} ]] ; do
		    echo "Opción inválida. Por favor, ingrese un número válido."
		    read -p "Ingrese el número de la clase (1-${#classes_available[@]}): " class_num
		done
		
		class_selected=${classes_available[$((class_num - 1))]}
		agents_arr["$agent_name"]=$class_selected
	done
	
	if [[ ${#agents_arr[@]} -le 0 ]] ; then
		return 1
	else
		echo "Agentes definidos"
		for agent in "${!agents_arr[@]}" ; do
			echo "    $agent:${agents_arr[$agent]}"
			ag_arr_output+=$agent:${agents_arr[$agent]}
		done
	fi
}



## Arguments Handling ##

has_argument()
{
	[[ ("$1" == *=* && -n ${1#*=}) || ( ! -z "$2" && "$2" != -*)  ]]
}

extract_argument()
{
	echo "${2:-${1#*=}}"
}


## Config ##

setup()
{
	local b_DIR=$1
	local t_DIR=$2
	
	detect_base_config "$b_DIR"
	
	if [[ $t_DIR != $b_DIR ]] ; then
		detect_sub_config "$t_DIR"
	else
		for i in {1 .. $CANT_EJS} ; do
			detect_sub_config "$EJ_${i}"
		done
	fi
}

detect_base_config()
{
	local PATH=$1
	local FILE=$PATH/config.env

	if [ ! -e $FILE ] ; then
		touch $FILE
		
		echo "# Set JAVA_PATH to the bin location of your Java installation" >> $FILE
		
		read -ep "JAVA Compiler full path (/usr/lib/jvm/java-*-*/bin/javac) : " javac_path
		echo "JAVAC_PATH=$javac_path" >> $FILE
		
		read -ep "JAVA Executable full path (/usr/lib/jvm/java-*-*/bin/java) : " java_path
		echo "JAVA_PATH=$java_path" >> $FILE
		
		echo >> $FILE
		echo "# Path to JADE library" >> $FILE
		
		read -ep "JADE Binary full path (lib/jade.jar) : " jade_lib
		echo "JADE_LIB=$jade_lib" >> $FILE
		
		echo >> $FILE
		echo "# Path to each excercise" >> $FILE
		
		read -a arr -ep "Directorios para cada ejercicio (separados por espacio): "
		echo "CANT_EJS=${#arr[@]}" >> $FILE
		for i in "${!arr[@]}" ; do
			echo "EJ_$((i + 1))=$PATH/${arr[$i]}" >> $FILE
		done
	fi
	
	set_env_variables "$PATH"
}

detect_sub_config()
{
	local PATH=$1
	local FILE=$PATH/config.env

	if [ ! -e $FILE ] ; then	
		echo "# Output directory for compiled classes" >> $FILE
		
		read -ep "Output Directory for compilation: " outp_dir
		echo "OUTPUT_DIR=$PATH/$outp_dir" >> $FILE
		
		echo >> $FILE
		echo "# Paths to files to compile" >> $FILE
		
		read -a fls -ep "Paths to the files to compile (separados por espacio): "
		flss="("
		for f in "${!fls[@]}" ; do
			$flss="${flss} \"$PATH/${arr[$i]}\""
		done
		$flss"${flss} )"
		echo "COMPILE_LIST=$flss" >> $FILE
	fi
	
	set_env_variables "$PATH"
}


## Compile ##

compile()
{
	if [ ! -d $OUTPUT_DIR ] ; then
		mkdir $OUTPUT_DIR
	fi
	
	$JAVAC_PATH -classpath $JADE_LIB -d $OUTPUT_DIR ${COMPILE_LIST[@]}
}

## Execute ##

execute_jade()
{
	$JAVA_PATH -cp $JADE_LIB jade.Boot -gui
}

execute_containers()
{
	local amount=$1
	
	for i in {1 .. $amount} ; do
		$JAVA_PATH -cp $JADE_LIB jade.Boot -container
	done
}

execute_agents()
{
	local PATH=$1
	cd $PATH

	# Declare agents to run
	local arr=()
	agents "$TARGET_DIR" arr # if return = 1 explode, else continue
	declare -p arr
	
	$JAVA_PATH -cp $JADE_LIB:$OUTPUT_DIR jade.Boot -container -host localhost -agents ${arr[@]}

}

## Print Utils ##

print_error()
{
	echo ""
	echo "INVALID COMMAND OPTION"
	echo ""
}

print_help()
{
	echo "Usage: $0 [-cx] [-d DIR]"
	echo ""
	echo "Realiza las operaciones especificadas sobre el DIR (directorio del script por defecto)."
	echo ""
	echo "Description of FLAGS:"
	echo "  -c, --compile               Set COMPILE=true"
	echo "  -x, --execute               Set EXECUTE=true"
	echo "  -d, --directory=DIR         Specifies DIR as the base directory"
}

# Courtesy of https://stackoverflow.com/a/20909045 #
set_env_variables(){
	local script_dir=$1
	local unamestr=$( uname )
	
	if [ "$unamestr" = 'Linux' ] ; then
		export $(grep -v '^#' $( ls $script_dir | grep .env ) | xargs -d '\n') 
	elif [ "$unamestr" = 'FreeBSD' ] || [ "$unamestr" = 'Darwin' ] ; then
		export $(grep -v '^#' $( ls $dir | grep .env ) | xargs -0)
	fi
}

unset_env_variables(){
	local script_dir=$1
	local unamestr=$(uname)
	
	if [ "$unamestr" = 'Linux' ] ; then
		unset $(grep -v '^#' $( ls $script_dir | grep .env ) | xargs -d '\n')
	elif [ "$unamestr" = 'FreeBSD' ] || [ "$unamestr" = 'Darwin' ] ; then
		unset $(grep -v '^#' $( ls $script_dir | grep .env ) | xargs -0)
	fi
}

perform_actions(){
	local compile_action_is_requested=$1
	local execute_action_is_requested=$2
	
	if [ $compile_action_is_requested = true ] ; then
		eval $JAVAC_PATH "-classpath $JADE_LIB -d $OUTPUT_DIR $WORKDIR/*.java"
	fi
	
	if [ $execute_action_is_requested = true ] ; then
		eval $EXEC_SERVER
		for AGENT_DEF in "${AGENTS[@]}" ; do
			eval "$EXEC_AGENT $AGENT_DEF"
		done
	fi
}


## Flags ##
parse_flags()
{
	COMPILE=false;
	EXECUTE=false;
	SCRIPT_DIR=$( dirname $0 )
	TARGET_DIR=$SCRIPT_DIR;

	while [ $# -gt 0 ] ; do
		case $1 in
		
			-c | --compile)
				COMPILE=true
				;;
			
			-x | --execute)
				EXECUTE=true
				;;
			
			-d | --directory*)
				if has_argument $@ ; then
					TARGET_DIR=$SCRIPT_DIR/$( extract_argument $@ )
					shift
				fi
				echo $TARGET_DIR
				;;
				
			-h | --help)
				print_help
				exit 0
				;;
				
			*)
				print_error
				print_help
				exit 1
				;;
		esac
		
		shift
	done
}

parse_flags "$@"
setup "$SCRIPT_DIR" "$TARGET_DIR"

if [[ $COMPILE = true ]] ; then
	compile
fi

if [[ $EXECUTE = true ]] ; then
	exec_user_interface
fi

arr=()
agents "$TARGET_DIR" arr
declare -p arr

## Commands ##
#COMPILE="$( ${JAVA_PATH} -classpath $JADE_LIB -d $OUTPUT_DIR $WORKDIR/*.java )" ## TODO: Logic for avoiding recompiling unchanged files

#EXEC_AGENT="$( ${JAVA_PATH}java -cp $JADE_LIB:$OUTPUT_DIR jade.Boot -container -host localhost -agents )"
#EXEC_CONTAINER="$( ${JAVA_PATH}java -cp $JADE_LIB jade.Boot -container )"
#EXEC_SERVER="$( ${JAVA_PATH}java -cp $JADE_LIB jade.Boot -gui )"



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

