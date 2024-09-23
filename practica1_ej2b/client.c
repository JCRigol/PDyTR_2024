#include <stdio.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>
#include <string.h>  // Para strlen y otras funciones de cadenas
#include <unistd.h>  // Para read, write y otras funciones de E/S
#include <stdlib.h>  // Para exit y otras funciones estándar
#include <math.h>
#include <zlib.h> // For CRC32

void error(char *msg)
{
    perror(msg);
    exit(1);
}

int main(int argc, char *argv[])
{
    int sockfd, portno, n;
    struct sockaddr_in serv_addr;
    struct hostent *server;

    const uint16_t comm_size = 32733;

    if (argc < 4) {
       fprintf(stderr,"usage %s hostname port potencia\n", argv[0]);
       exit(1);
    }

    // TOMA EL NÚMERO DE PUERTO DE LOS ARGUMENTOS
    portno = atoi(argv[2]);
	
    // CREA EL FILE DESCRIPTOR DEL SOCKET PARA LA CONEXIÓN
    sockfd = socket(AF_INET, SOCK_STREAM, 0);
    // AF_INET - FAMILIA DEL PROTOCOLO - IPV4 PROTOCOLS INTERNET
    // SOCK_STREAM - TIPO DE SOCKET 
	
    if (sockfd < 0) 
        error("ERROR opening socket");
	
    // TOMA LA DIRECCIÓN DEL SERVIDOR DE LOS ARGUMENTOS
    server = gethostbyname(argv[1]);
    if (server == NULL) {
        fprintf(stderr,"ERROR, no such host\n");
        exit(0);
    }

    // LIMPIA LA ESTRUCTURA serv_addr
    bzero((char *) &serv_addr, sizeof(serv_addr));
    serv_addr.sin_family = AF_INET;
	
    // COPIA LA DIRECCIÓN IP Y EL PUERTO DEL SERVIDOR A LA ESTRUCTURA DEL SOCKET
    bcopy((char *)server->h_addr, 
         (char *)&serv_addr.sin_addr.s_addr,
         server->h_length);
    serv_addr.sin_port = htons(portno);
	
    // DESCRIPTOR - DIRECCIÓN - TAMAÑO DIRECCIÓN
    if (connect(sockfd, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) < 0) 
        error("ERROR connecting");

    // ---- MENSAJE A ENVIAR ----
    int potencia = atoi(argv[3]);
    int buff_size = pow(10,potencia);
    char buffer[buff_size];
    memset(buffer, 'A', buff_size);
    buffer[buff_size - 1] = '\0';  // Null-terminate the buffer for printf

    // ---- PACKAGE NECESSARY INFO INTO FIRST COMM ----

    // ---- CALCULATE N# OF COMMS NECESSARY ----
    uint16_t nr_of_comms = (buff_size + comm_size - 1) / comm_size;

    int fc_buff_size, cp_buff_size;
    if (buff_size < comm_size) {
        fc_buff_size = buff_size + 2 + 2; //2 bytes of nr_of_comms, 2 bytes of comm_size
        cp_buff_size = buff_size;
    } else {
        fc_buff_size = comm_size + 2 + 2; //2 bytes of nr_of_comms, 2 bytes of comm_size
        cp_buff_size = comm_size;
    }
    char first_comm[fc_buff_size + 4]; //4 bytes of checksum
    memcpy(first_comm + 4, buffer, cp_buff_size);

    // NEED TO REFLECT OPERATION ON BUFFER, TRUNCATE? DELETE? SOMETHING. SNAKE EATER.

    first_comm[0] = (nr_of_comms & 0xFF);             // Low byte of nr_of_comms
    first_comm[1] = (nr_of_comms >> 8) & 0xFF;       // High byte of nr_of_comms
    first_comm[2] = (comm_size & 0xFF);               // Low byte of comm_size
    first_comm[3] = (comm_size >> 8) & 0xFF;         // High byte of comm_size

    unsigned long checksum = crc32(0L, (const unsigned char *)first_comm, fc_buff_size);
    first_comm[fc_buff_size - 5] = checksum;

    // ---- DEBUGGING STEP ----
    uint16_t received_nr_of_comms = (first_comm[0] | (first_comm[1] << 8)); // Combine bytes
    printf("nr_of_comms: %d\n", received_nr_of_comms); // Cast to int for printing
    uint16_t received_comm_size = (first_comm[2] | (first_comm[3] << 8)); // Combine bytes
    printf("comm_size: %d\n", received_comm_size); // Cast to int for printing

    // ---- FIRST COMMUNICATION ----

    if (nr_of_comms == 1) {
        // ENVÍA UN MENSAJE AL SOCKET
        n = write(sockfd, first_comm, strlen(first_comm));
        if (n < 0) 
                error("ERROR writing to socket");

        // ESPERA RECIBIR UNA RESPUESTA
        n = read(sockfd, first_comm, fc_buff_size + 4);
        if (n < 0) 
                error("ERROR reading from socket");

        // ---- CIERRE COMUNICACION ----
        printf("%s\n", first_comm);

    } else {
        char checked_buffer[comm_size + 4];
        
        for (int i = 0; i < nr_of_comms - 1; i++) {
            if (i == (nr_of_comms - 1)) {
                memcpy(checked_buffer, buffer, buff_size); //buff size should just be the remaining last x bytes < 32737 in buffer. Fill remainder space with \0.
                memcpy(checked_buffer + buff_size, '\0', comm_size - buff_size);
            } else {
                memcpy(checked_buffer, buffer, comm_size); //shorten buffsize by comm size, delete from front of buffer by comm size, readjust.
            }
            
            checksum = crc32(0L, (const unsigned char *)checked_buffer, comm_size);
            checked_buffer[comm_size - 5] = checksum; // probably insert it by byte like sizes? but they are bugged so who knows
            
            // ENVÍA UN MENSAJE AL SOCKET
            n = write(sockfd, checked_buffer, strlen(checked_buffer));
            if (n < 0) 
                error("ERROR writing to socket");

            bzero(checked_buffer, comm_size + 4);

            // ESPERA RECIBIR UNA RESPUESTA
            n = read(sockfd, checked_buffer, comm_size + 4);
            if (n < 0) 
                error("ERROR reading from socket");

            // ---- CIERRE COMUNICACION ----
            printf("%s\n", checked_buffer);
        }
    }

    // CIERRA EL SOCKET
    close(sockfd);

    return 0;
}
