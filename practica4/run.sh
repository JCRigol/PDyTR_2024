#!/bin/bash

##### FUNCTIONS START #####
## SETUP ##
setup()
{
	local FILE="$BASE_DIR/config.env"
	
	if [ ! -e $FILE ] ; then
		touch $FILE
		setup_config "$FILE"
	fi

	source "$FILE"
	
	if [[ $COMPILE = true ]] ; then
		export COMPILE_LIST
		if [[ -z ${TARGET_DIR+x} ]] ; then
			for i in "${!DIR_STRUCT[@]}" ; do
            	detect_changes "${DIR_STRUCT[$i]}"
			done
		else
			detect_changes "$BASE_DIR/$TARGET_DIR"
		fi
	fi
	
	if [[ $EXECUTE = true ]] ; then
		if [[ -z ${TARGET_DIR+x} ]] ; then
			for i in "${!DIR_STRUCT[@]}" ; do
            	detect_exec_profile "${DIR_STRUCT[$i]}"
			done
		else
			detect_exec_profile "$BASE_DIR/$TARGET_DIR"
		fi
	fi
}

setup_config()
{
	echo "# Set JAVA_PATH to the bin location of your Java installation" >> $1
		
	read -ep "JAVA Compiler full path (/usr/lib/jvm/java-*-*/bin/javac) : " javac_path
	echo "JAVAC_PATH=$javac_path" >> $1
	
	read -ep "JAVA Executable full path (/usr/lib/jvm/java-*-*/bin/java) : " java_path
	echo "JAVA_PATH=$java_path" >> $1
	
	echo >> $1
	echo "# Path to JADE library" >> $1
	
	read -ep "JADE Binary full path (lib/jade.jar) : " jade_lib
	echo "JADE_LIB=$jade_lib" >> $1
	
	echo >> $1
	echo "# Output directory for compiled classes" >> $1
	
	read -ep "Output Directory for compilation: " outp_dir
	echo "OUTPUT_DIR=$BASE_DIR/$outp_dir" >> $1
	
	echo >> $1
	echo "# Path to each excercise" >> $1
	
	read -a arr -ep "Directorios para cada ejercicio (separados por espacio): "
	
	echo "DIR_STRUCT=( " >> $1
	for item in "${arr[@]}"; do
		echo "    \"$BASE_DIR/$item\"" >> $1
	done
	echo ")" >> $1
}

detect_changes()
{
	local FILE="$1/files.history"
	local md5sum_aux=""

	if [ ! -e $FILE ] ; then
		touch $FILE
	fi

	for file in $( find "$1" -type f -name '*.java' ) ; do
		md5sum_aux=$( md5sum "$file" | cut -d' ' -f1-2 )
		
		if $( grep -q "$file" $FILE ) ; then
			checksum=$( grep "$file" $FILE | cut -d':' -f2 )
			
			if [[ $md5sum_aux != $checksum ]] ; then
				COMPILE_LIST+=("$file")
			fi
		else
			COMPILE_LIST+=("$file")
		fi
	done
}

write_changes()
{
	local FILE="$1/files.history"
	local md5sum_aux=""

	for file in "${COMPILE_LIST[@]}" ; do
		md5sum_aux=$( md5sum "$file" | cut -d' ' -f1-2 )
		
		if $( grep -q "$file" $FILE ) ; then
			sed -i "s|^$file:.*|$file:$md5sum_aux|" "$FILE"
		else
			echo "$file:$md5sum_aux" >> $FILE
		fi
	done
}

detect_exec_profile()
{
	local profiles=()
	
	for profile in $( find "$1" -type f -name '*.profile' ) ; do
		profiles+="$( basename "$profile" .profile )"
	done
	
	if [[ ${#profiles[@]} -le 0 ]] ; then
		export AGENTS
		create_exec_profile "$1"
	else
		echo "SELECCIONE EL PERFIL DE EJECUCIÓN A UTILIZAR:"
    	for i in "${!profiles[@]}" ; do
    		profile_desc=$( cat "$1"/${profiles[$i]}.profile )
      		echo "$((i + 1))) ${profiles[$i]} => $profile_desc"
    	done
    	read -p "Ingrese el identificador de perfil (1-${#profiles[@]}): " id

		while [[ $id -lt 1 ]] || [[ $id -gt ${#profiles[@]} ]] ; do
			echo "Opción inválida. Por favor, ingrese un número válido."
			read -p "Ingrese el identificador de perfil (1-${#profiles[@]}): " id
		done

		local FILE="$1/${profiles[$(( id - 1 ))]}.profile"
		source "$FILE"
	fi
}

create_exec_profile()
{	
	local classes_available=()
	
	echo "CREATING NEW EXECUTION PROFILE"
	
	echo -ne "PROFILE NAME: "
	read profile_name
	
	touch "$1/$profile_name.profile"
	
	for file in $( find "$1" -type f -name '*.java' -exec grep -l 'extends Agent' {} \+ ) ; do
		class_name=$( basename "$file" .java )
		pckg=$( grep 'package' $file | sed 's/package \([^;]*\);/\1/' )

		classes_available+=($pckg.$class_name)
	done
	
	while : ; do
		echo -ne "MAKE NEW AGENT [NAME/Fin]: "
		read agent_name
		
		if [[ $agent_name = "F" ]] || [[ $agent_name = "f" ]] ; then
			break
		fi
		
		echo "AGENT CLASS SELECTION"
		for i in "${!classes_available[@]}"; do
		    echo "$((i + 1))) $( echo ${classes_available[$i]} | sed 's/.*\.\([^\.]*\)$/\1/' )"
		done
		
		echo -ne "Ingrese el número de la clase (1-${#classes_available[@]}): "
		read class_num
		
		while [[ $class_num -lt 1 ]] || [[ $class_num -gt ${#classes_available[@]} ]] ; do
		    echo "Opción inválida. Por favor, ingrese el número de la clase (1-${#classes_available[@]}): "
		    read -p class_num
		done
		
		class_selected=${classes_available[$((class_num - 1))]}
		AGENTS+=("$agent_name:$class_selected")
	done
	
	echo "AGENTS=( " >> "$1/$profile_name.profile"
	for item in "${AGENTS[@]}"; do
		echo "    \"$item\"" >> "$1/$profile_name.profile"
	done
	echo ")" >> "$1/$profile_name.profile"
}


## CLEANUP ##
cleanup()
{
	local FILE="$BASE_DIR/config.env"
	local unamestr=$( uname )
	
	if [ "$unamestr" = 'Linux' ] ; then
		unset $(grep -v '^#' $FILE | xargs -d '\n')
	elif [ "$unamestr" = 'FreeBSD' ] || [ "$unamestr" = 'Darwin' ] ; then
		unset $(grep -v '^#' $FILE | xargs -0)
	fi
	
	if [[ $COMPILE = true ]] ; then
		unset COMPILE_LIST
	fi
	
	if [[ $EXECUTE = true ]] ; then
		unset AGENTS
	fi
}


## COMPILATION ##
compile()
{
	if [[ ! -d $OUTPUT_DIR ]] ; then
		mkdir "$OUTPUT_DIR"
	fi
	
	local DEPENDENCIES=$JADE_LIB
	
	# Placeholder fix
	if [[ -d $1/lib ]] ; then
		for lib in $( find "$1" -type f -name '*.jar' ) ; do
			$DEPENDENCIES="$DEPENDENCIES:$lib"
		done
	fi
	
	# Placeholder $OUTPUT_DIR in classpath (fixes package deps)
	"$JAVAC_PATH" -classpath "$DEPENDENCIES:$OUTPUT_DIR" -d "$OUTPUT_DIR" "${COMPILE_LIST[@]}"
	
	if [[ $? -eq 0 ]] ; then
		write_changes "$1"
	else
		exit 1
	fi
}


## EXECUTION ##
execute()
{
	local OUT_DIR="$1/logs"
	local JADE_PID
	local CONTS_PID
	local CONTS_AMNT_CURRENT=1 # Lying variable, it needs to be current + 1 to avoid issues
	local CONTS_AMNT_TARGET=0
	
	local DEPENDENCIES=$JADE_LIB
	
	# Placeholder fix
	if [[ -d $1/lib ]] ; then
		for lib in $( find "$1" -type f -name '*.jar' ) ; do
			$DEPENDENCIES="$DEPENDENCIES:$lib"
		done
	fi
	
	rm -rf "$OUT_DIR/agents" "$OUT_DIR/containers" "$OUT_DIR/platform" 2> /dev/null
	mkdir -p "$OUT_DIR/agents" "$OUT_DIR/containers" "$OUT_DIR/platform" 2> /dev/null
	
	echo "Running JADE platform"
	
	"$JAVA_PATH" -cp "$JADE_LIB" jade.Boot -gui > "$OUT_DIR/platform/log.txt" 2>&1 & # JADE Running
	JADE_PID="$!"
		
	local continue=true
	while $continue ; do
		echo -en "Select next entities to run [\e[4;31mA\e[0mgents/\e[4;31mC\e[0montainers/\e[4;31mD\e[0mone]: "
		read user_input
		
		if [[ $user_input = "A" ]] || [[ $user_input = "a" ]] ; then

			echo -en "Enter parent container name (if none creates new container for each agent): "
			read container_name
			: ${container_name:=""}
			
			echo -en "Enter host name (default localhost): "
			read host_name
			: ${host_name:=localhost}
			
			for i in "${!AGENTS[@]}" ; do
				"$JAVA_PATH" -cp "$DEPENDENCIES":"$OUTPUT_DIR" jade.Boot -container $container_name -host $host_name -agents "${AGENTS[$(( i - 1 ))]}" > "$OUT_DIR/agents/${AGENTS[$(( i - 1 ))]}_log.txt" 2>&1 &
				CONTS_PID+=("$!")
			done
						
		elif [[ $user_input = "C" ]] || [[ $user_input = "c" ]] ; then

			echo -en "Enter amount of containers (default one): "
			read amount
			: ${amount:=1}
			
			CONTS_AMNT_TARGET=$(( $CONTS_AMNT_TARGET + $amount )) # Again lying, should be current + amount but it generates issues, since current = target then target gets used as current proxy.
			
			echo -en "Enter host name (default localhost): "
			read host_name
			: ${host_name:=localhost}
			
			for i in $( seq $CONTS_AMNT_CURRENT $CONTS_AMNT_TARGET ) ; do
				CONT_NAME="Container-$i"
				"$JAVA_PATH" -cp "$JADE_LIB" jade.Boot -container "$CONT_NAME" -host $host_name > "$OUT_DIR/containers/${CONT_NAME}_log.txt" 2>&1 &
				CONTS_PID+=("$!")
			done
			
			CONTS_AMNT_CURRENT=$(( CONTS_AMNT_TARGET + 1 )) # Finale of lies trifecta, +1 to move pointer to empty space instead of last element
			
		elif [[ $user_input = "D" ]] || [[ $user_input = "d" ]] ; then
			echo "Killing Containers"
			for i in "${!CONTS_PID[@]}" ; do
				kill "${CONTS_PID[$i]}"
			done
			
			echo "Killing Platform"
			kill "$JADE_PID"
			
			continue=false
		else
			echo -en "Invalid input, please select next entities to run [\e[4;31mA\e[0mgents/\e[4;31mC\e[0montainers/\e[4;31mD\e[0mone]: "
			read user_input
		fi
	done
}


## UTILS ##
print_error()
{
	echo ""
	echo $1
	echo ""
}

print_help()
{
	echo "Usage: -h // -cx [DIR]?"
	echo ""
	echo "Realiza las operaciones especificadas sobre el DIR (directorio del script por defecto)."
	echo ""
	echo "OPTIONS:"
	echo "  -c,                      Set COMPILE=true"
	echo "  -x,                      Set EXECUTE=true"
	echo "  -h,                      Show help, incompatible with other flags"
	echo
	echo "ARGUMENTS:"
	echo "  DIR (optional)           Set TARGET_DIR=DIR"
}

##### END FUNCTIONS #####

##### SCRIPT START #####

COMPILE=false;
EXECUTE=false;
HELP=false
BASE_DIR=$( dirname $0 )

while getopts ":cxh" FLAG ; do
	case $FLAG in
		c)
			COMPILE=true
			;;
		x)
			EXECUTE=true
			;;
		h)
			HELP=true
			;;
		*)
			print_error "ERROR: OPTION NOT RECOGNIZED"
			print_help
			exit 1
			;;
	esac
done
shift "$(( OPTIND - 1 ))"

if [[ $HELP = true ]] ; then
	if [[ $COMPILE = true || $EXECUTE = true || $# -gt 0 ]] ; then
		print_error "ERROR: HELP OPTION (-h) DOESN'T ALLOW OTHER OPTIONS/ARGUMENTS "
		exit 1
	else
		print_help
		exit 0
	fi
fi

if [[ $COMPILE = false && $EXECUTE = false ]] ; then
  print_error "ERROR: OPTION SPECIFICATION REQUIRED"
  print_help
  exit 1
fi

if [[ $# -gt 1 ]] ; then
	print_error "ERROR: TOO MANY ARGUMENTS"
	print_help
	exit 1
elif [[ $# -eq 1 ]] ; then
	TARGET_DIR=$1
fi

setup

if [[ $COMPILE = true && "${#COMPILE_LIST[@]}" -ge 1 ]] ; then
	if [[ -z ${TARGET_DIR+x} ]] ; then
		for i in "${!DIR_STRUCT[@]}" ; do
        	compile "${DIR_STRUCT[$i]}"
		done
	else
		compile "$BASE_DIR/$TARGET_DIR"
	fi
fi

if [[ $EXECUTE = true ]] ; then
	if [[ -z ${TARGET_DIR+x} ]] ; then
		for i in "${!DIR_STRUCT[@]}" ; do
        	execute "${DIR_STRUCT[$i]}"
		done
	else
		execute "$BASE_DIR/$TARGET_DIR"
	fi
fi

cleanup

##### END SCRIPT #####
