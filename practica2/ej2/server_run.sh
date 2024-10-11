#!/bin/bash

# Define the output file for the server
server_output="server_output.txt"

# Remove old output file if it exists
rm -f $server_output

# Explicitly create (or clear) the output file
touch $server_output

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

# Run the server in the background and redirect its output to a file
./server "$server_param" > $server_output 2>&1 &
server_pid=$!

# Wait for the server to finish
wait $server_pid

echo "Server output saved to $server_output"

