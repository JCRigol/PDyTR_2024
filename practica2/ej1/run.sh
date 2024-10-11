#!/bin/bash

set -e
set -x

# Define the Vagrant boxes
BOXES=("hashicorp/bionic64" "ubuntu/jammy64")
OUTPUT_FILE="$PWD/ubuntu_info.txt"

# Clear the output file if it exists
> "$OUTPUT_FILE"

cleanup() {
    if [[ -d "$TEMP_DIR" ]]; then
        rm -rf "$TEMP_DIR"
    fi
}

# Iterate through each box
for BOX in "${BOXES[@]}"; do
    # Create a temporary directory for the Vagrant environment
    TEMP_DIR=$(mktemp -d)

    # Set trap to clean up temp directory on EXIT
    trap cleanup EXIT

    # Change to the temporary directory
    cd "$TEMP_DIR" || exit

    # Initialize Vagrant with the specified box
    vagrant init "$BOX" || { echo "Vagrant init failed for $BOX"; continue; }
    
    # Bring up the Vagrant box
    vagrant up || { echo "Vagrant up failed for $BOX"; continue; }
    
    # Retrieve the OS version and append it to the output file with identification discarding error
    OS_VERSION=$(vagrant ssh -c "cat /etc/os-release" 2>/dev/null)
    
    # Check if OS_VERSION is empty or contains an error message
    if [[ -z "$OS_VERSION" || "$OS_VERSION" == *"Error retrieving OS info"* ]]; then
        echo "Failed to retrieve OS information for $BOX"
        vagrant destroy -f
        continue
    fi
    
    echo "OS Information for box: $BOX" >> "$OUTPUT_FILE"
    echo "$OS_VERSION" >> "$OUTPUT_FILE"
    echo -e "-----------------------------------------\n" >> "$OUTPUT_FILE"
    
    # Destroy the Vagrant box to save space
    vagrant destroy -f
    
    # Remove the trap for cleanup since we're about to delete the temp directory manually
    trap - EXIT
    
    # Remove the temporary directory
    cd .. || exit
    rm -rf "$TEMP_DIR"
done

echo "OS information has been saved to $OUTPUT_FILE."

