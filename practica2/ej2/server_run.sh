#!/bin/bash

# Define the output file for the server
server_output="server_output.txt"

# Remove old output file if it exists
rm -f $server_output

# Explicitly create (or clear) the output file
touch $server_output

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
get_system_specs >> $server_output

# Compile the server
gcc server.c -o server -lm -lz
if [ $? -ne 0 ]; then
  echo "Server compilation failed!"
  exit 1
fi

# Get the server's IP address (change the interface name as needed, e.g., eth0, enp0s3, etc.)
server_ip=$(hostname -I | awk '{print $1}')
echo "Server IP: $server_ip"

# Define the server parameter (e.g., running on port 1234)
server_param="1234"

# Sanitize port if needed
if lsof -i :$server_param; then
    pid=$(lsof -t -i :$server_param)
    kill -9 $pid
fi

# Run the server in the background and redirect its output to a file
./server "$server_param" >> $server_output 2>&1 &
server_pid=$!

# Wait for the server to finish
wait $server_pid

echo "Server output saved to $server_output"

