#!/bin/bash

# Define the output files
client_output="client_output.txt"
server_output="server_output.txt"
time_output="comm_times.csv"

# Remove old output files if any exist
rm -f $client_output
rm -f $server_output
rm -f $time_output

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
vagrant up

# Reassign naming
CLIENT="vm1"
SERVER="vm2"

# Retrieve IP addresses from boxes
CLIENT_IP=$(vagrant ssh $CLIENT -c "hostname -I | awk '{print \$2}'" | tr -d '\r')
SERVER_IP=$(vagrant ssh $SERVER -c "hostname -I | awk '{print \$2}'" | tr -d '\r')

# Transfer the binaries to the respective boxes
vagrant ssh $CLIENT -c "mkdir -p ~/client"
vagrant ssh $CLIENT -c "cp /vagrant/client /home/vagrant/client/"

vagrant ssh $SERVER -c "mkdir -p ~/server"
vagrant ssh $SERVER -c "cp /vagrant/server /home/vagrant/server/"

# Debug
vagrant ssh $SERVER -c "ls -la ~/server/"

# Execution start
# Run the server
echo "Server execution starting..."
vagrant ssh $SERVER -c "
	server_param1=\"1234\"
	
	cd server/ &&
	chmod +x $server_binary &&
	nohup ./$server_binary \"\$server_param1\" > $server_output 2>&1 & sleep 1
	sleep 10
"

# Debug
vagrant ssh $CLIENT -c "ls -la ~/client/"

# Run the client
echo "Client execution starting..."
vagrant ssh $CLIENT -c "
	cd client/ &&
	chmod +x $client_binary
	client_param1=\"$SERVER_IP\"
	client_param2=\"1234\"
	client_output=\"$client_output\"
	
	for i in {1..6}
	do
		echo -e \"\n==== Client \$i ==== \" >> \$client_output
		client_param3=\$i
		
		./$client_binary \"\$client_param1\" \"\$client_param2\" \"\$client_param3\" >> \$client_output 2>&1
		echo \"Client \$i sent: 10^\$client_param3 bytes\" >> \$client_output
	done
"

echo "Client execution completed"

# Retrieve the output files from server and client boxes
vagrant ssh $CLIENT -c "cp /home/vagrant/client/$client_output /vagrant/"
vagrant ssh $CLIENT -c "cp /home/vagrant/client/$time_output /vagrant/"
vagrant ssh $SERVER -c "cp /home/vagrant/server/$server_output /vagrant/"

# Cleanup
echo "Cleaning up..."
vagrant halt $CLIENT $SERVER
vagrant destroy -f $CLIENT $SERVER
echo "Vagrant boxes have been destroyed, cleanup complete"
