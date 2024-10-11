#!/bin/bash

# Define the output file for the client
client_output="client_output.txt"

# Remove old output file if it exists
rm -f $client_output

# Explicitly create (or clear) the output file
touch $client_output

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

