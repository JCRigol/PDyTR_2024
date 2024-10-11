#!/bin/bash

# Define the output file for the client
client_output="client_output.txt"

# Remove old output file if it exists
rm -f $client_output

# Explicitly create (or clear) the output file
touch $client_output

# Function to get system specifications
get_system_specs() {
    echo "System Specifications:" 
    echo "CPU: $(awk -F: '/model name/ {print $2}' /proc/cpuinfo | head -n 1 | xargs)"
    
    # Get memory details (size, speed, provider) for all memory sticks
    stick_number=1
    sudo dmidecode --type memory | awk -F: -v stick_num="$stick_number" '
    /Size/ {size=$2}
    /Speed/ {speed=$2}
    /Manufacturer/ {if (size != "") {
        printf "Memory Stick %d: Size: %s, Speed: %s, Manufacturer: %s\n", stick_num, size, speed, $2; 
        size=""; speed=""; stick_num++
    }}'
    
    # Get OS information
    os_info=$(grep '^ID=' /etc/os-release | cut -d'=' -f2 | tr -d '\"') 
    os_version=$(grep '^VERSION=' /etc/os-release | cut -d'=' -f2 | tr -d '\"')
    echo "OS: $os_info $os_version"
    echo "===================================="
}

# Save system specs to the output file
get_system_specs >> $client_output

# Compile the client
gcc client.c -o client -lm -lz
if [ $? -ne 0 ]; then
  echo "Client compilation failed!"
  exit 1
fi

# Prompt the user for the server IP
read -p "Enter the server IP: " server_ip

# Define fixed client parameters
client_param1="$server_ip"
client_param2="1234"

for i in {1..6}
do
  echo -e "\n==== Client $i ====" >> $client_output
  client_param3=$i  # Increment client parameter (argv[3])

  # Run the client and append the result to the output file
  ./client "$client_param1" "$client_param2" "$client_param3" >> $client_output 2>&1

  # Document which client sent which message
  echo "Client $i sent: 10^$client_param3 bytes" >> $client_output
done

echo "Client output saved to $client_output"

# Display the contents of the client output file with clear formatting
echo -e "\n============================"
echo -e " CLIENT OUTPUT"
echo -e "============================\n"
cat $client_output

