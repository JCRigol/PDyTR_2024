#!/bin/bash

echo -en "Choose which will be executed first [\e[4;31mA\e[0mgents/\e[4;31mC\e[0montainers]: "
# Read user input
read -p "" user_input

# Print the user input for verification (optional)
case $user_input in
	a|A|agents|Agents|AGENTS) echo "You selected AGENTS first" ;;
	c|C|containers|Containers|CONTAINERS) echo "You selected CONTAINERS first" ;;
esac

#JAVA_PATH="/usr/lib/jvm/java-8-openjdk-amd64/bin/java"
#JADE_LIB="lib/jade.jar"

## "$JAVA_PATH" -cp "$JADE_LIB" jade.Boot -gui > output.txt 2>&1 & 

# & ---> lo deja vivo en el puerto, mas alla de la terminacion del programa

# Redirecting stdout and stderr seems to work like the desired behaviour
