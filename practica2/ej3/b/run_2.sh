#!/bin/bash

# Define the output files
client_output="client_output.txt"
server_output="server_output.txt"
time_output="comm_times.csv"

# Remove old output files if any exist
rm -rf $client_output
rm -rf $server_output
rm -rf $time_output

# Create the output files
touch $client_output
touch $server_output
touch $time_output

# Define the binaries
client_binary="client"
server_binary="server"

# Compile the server
gcc server.c -o server -lm -lz
if [ $? -ne 0 ]; then
  echo "Server compilation failed!"
  exit 1
fi

# Compile the client
gcc client.c -o client -lm -lz
if [ $? -ne 0 ]; then
  echo "Client compilation failed!"
  exit 1
fi

# Start the boxes
vagrant up --debug

# Reassign naming
BOX1="vm1"
SERVER="vm2"

# Retrieve IP addresses from boxes
SERVER_IP=$(vagrant ssh $SERVER -c "hostname -I | awk '{print \$2}'" | tr -d '\r')

# Transfer the binaries to the respective boxes
vagrant ssh $SERVER -c "mkdir -p ~/server"
vagrant ssh $SERVER -c "cp /vagrant/server /home/vagrant/server/"

# Execution start
# Run the server
echo "Server execution starting..."
vagrant ssh $SERVER -c "
	chmod +x ~/server/$server_binary &&
	nohup ~/server/$server_binary > ~/server/$server_output 2>&1 &
"

# Run the client locally
echo "Client execution starting..."
client_param1="$SERVER_IP"
client_param2="1234"
	
for i in {1..6}
do
	echo -e "\n==== Client $i ==== " >> $client_output
	client_param3=$i
		
	./$client_binary "$client_param1" "$client_param2" "$client_param3" >> $client_output 2>&1
	echo "Client $i sent: 10^$client_param3 bytes" >> $client_output
done

echo "Client execution completed"

# Retrieve the output files from server and client boxes
vagrant ssh $SERVER -c "cp /home/vagrant/server/$server_output /vagrant/"

# Cleanup
echo "Cleaning up..."
vagrant halt $BOX1 $SERVER
vagrant destroy -f $BOX1 $SERVER
echo "Vagrant boxes have been destroyed, cleanup complete"
