#!/bin/bash

# Define output files
SERVER_OUTPUT="server_output.txt"
CLIENT_OUTPUT="client_output.txt"

# Set the server port
PORT=12345

# Remove old output files if they exist
rm -f $SERVER_OUTPUT $CLIENT_OUTPUT

# Explicitly create (or clear) the output files
touch $SERVER_OUTPUT
touch $CLIENT_OUTPUT

# Define the CSV file name
CSV_FILE="times.csv"

# Delete the CSV file if it exists
if [ -f "$CSV_FILE" ]; then
    echo "Deleting existing CSV file: $CSV_FILE"
    rm "$CSV_FILE"
fi

# Function to stop any process listening on the specified port
stop_process() {
    echo "Stopping any process listening on port $PORT..."
    PID=$(sudo lsof -t -i:$PORT)
    if [ -n "$PID" ]; then
        sudo kill -9 $PID
        echo "Stopped process with PID $PID."
    else
        echo "No process found listening on port $PORT."
    fi
}

# Stop any existing server process
stop_process

# Remove old class files if they exist
echo "Removing old class files..."
rm -f Client.class Server.class

# Compile Java files
echo "Compiling Java files..."
javac Client.java Server.java

# Start the server in the background
echo "Starting the server..."
java Server "$PORT" > "$SERVER_OUTPUT" 2>&1 &
SERVER_PID=$!

# Sleep for a moment to allow the server to start
sleep 1

# Loop over buffer sizes: 10^1 to 10^6
for i in {1..6}; do
    BUFFER_SIZE=$((10 ** i))
    echo "Starting clients for buffer size: $BUFFER_SIZE bytes"

    java Client localhost "$PORT" "$BUFFER_SIZE" >> "$CLIENT_OUTPUT" 2>&1
done

# Kill the server after clients are done
kill "$SERVER_PID"

# Print outputs
echo "Server output:"
cat "$SERVER_OUTPUT"
echo
echo "Client output:"
cat "$CLIENT_OUTPUT"
