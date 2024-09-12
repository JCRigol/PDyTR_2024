#!/bin/bash

# Define output files
server_output="server_output.txt"
client_output="client_output.txt"

# Remove old output files if they exist
rm -f $server_output $client_output

# Explicitly create (or clear) the output files
touch $server_output
touch $client_output

# Compile the server
gcc server.c -o server -lm
if [ $? -ne 0 ]; then
  echo "Server compilation failed!"
  exit 1
fi

# Compile the client
gcc client.c -o client -lm
if [ $? -ne 0 ]; then
  echo "Client compilation failed!"
  exit 1
fi


# Define fixed parameters for the server and client
# Fixed server parameter, e.g., server running on port 1234
server_param="1234"

# Fixed client parameters, e.g., client connecting to 127.0.0.1 on port 1234
client_param1="127.0.0.1"
client_param2="1234"

# Run the server in the background and redirect its output to a file
./server "$server_param" > $server_output 2>&1 &
server_pid=$!

# Give the server some time to start
sleep 1

for i in {1..6}
do
  echo -e "\n==== Client $i ====" >> $client_output
  client_param3=$i  # Increment client parameter (argv[3])

  # Run the client and append the result to the output file
  ./client "$client_param1" "$client_param2" "$client_param3" >> $client_output 2>&1

  # Document which client sent which message
  echo "Client $i sent: 10^$client_param3 bytes" >> $client_output
done

echo "Server output saved to $server_output"
echo "Client output saved to $client_output"

# Display the contents of both files with clear formatting
echo -e "\n============================"
echo -e " SERVER OUTPUT"
echo -e "============================\n"
cat $server_output

echo -e "\n============================"
echo -e " CLIENT OUTPUT"
echo -e "============================\n"
cat $client_output

# Wait for the server to finish
wait $server_pid