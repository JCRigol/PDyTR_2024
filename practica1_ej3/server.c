/* A simple server in the internet domain using TCP
   The port number is passed as an argument */
#include <stdio.h>
#include <stdlib.h>   // Para exit, atoi y otras funciones estándar
#include <string.h>   // Para bzero y otras funciones de cadenas
#include <unistd.h>   // Para read, write y otras funciones de E/S
#include <sys/types.h> 
#include <sys/socket.h>
#include <netinet/in.h>
#include <math.h>
#include <zlib.h> // For CRC32
#include <stdbool.h>


void error(char *msg)
{
    perror(msg);
    exit(1);
}

int main(int argc, char *argv[])
{
    int sockfd, newsockfd, portno;
    socklen_t clilen;
    struct sockaddr_in serv_addr, cli_addr;
    int n;

    if (argc < 2) {
        fprintf(stderr,"ERROR, no port provided\n");
        exit(1);
    }
    
    // CREA EL FILE DESCRIPTOR DEL SOCKET PARA LA CONEXIÓN
    sockfd = socket(AF_INET, SOCK_STREAM, 0);
    // AF_INET - FAMILIA DEL PROTOCOLO - IPV4 PROTOCOLS INTERNET
    // SOCK_STREAM - TIPO DE SOCKET 
    
    if (sockfd < 0) 
    error("ERROR opening socket");

    // LIMPIA LA ESTRUCTURA serv_addr
    bzero((char *) &serv_addr, sizeof(serv_addr));
    
    // ASIGNA EL PUERTO PASADO POR ARGUMENTO
    // ASIGNA LA IP EN DONDE ESCUCHA (SU PROPIA IP)
    portno = atoi(argv[1]);
    serv_addr.sin_family = AF_INET;
    serv_addr.sin_addr.s_addr = INADDR_ANY;
    serv_addr.sin_port = htons(portno);

    // ENABLE SOCKET REUSE FOR SHELLSCRIPT
    int opt = 1;
    if (setsockopt(sockfd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt)) < 0) {
        error("ERROR on setsockopt");
    }

    // VINCULA EL FILE DESCRIPTOR CON LA DIRECCIÓN Y EL PUERTO
    if (bind(sockfd, (struct sockaddr *) &serv_addr,
            sizeof(serv_addr)) < 0) 
            error("ERROR on binding");
            
    // SETEA LA CANTIDAD QUE PUEDEN ESPERAR MIENTRAS SE MANEJA UNA CONEXIÓN
    listen(sockfd, 5);

    // SE BLOQUEA A ESPERAR UNA CONEXIÓN
    clilen = sizeof(cli_addr);

    // ---- CONNECTION LOOP ----
    const char *ack_msg = "I got your message";

    for (int i = 1; i < 7; i++) {
        newsockfd = accept(sockfd, 
                    (struct sockaddr *) &cli_addr, 
                    &clilen);
                    
        // DEVUELVE UN NUEVO DESCRIPTOR POR EL CUAL SE VAN A REALIZAR LAS COMUNICACIONES
        if (newsockfd < 0) 
            error("ERROR on accept");

        // ---- INITIAL CONFIG MESSAGE ----
        int bytes_torecv;
        unsigned long checksum, checksum_recvd;
        bool checksum_failed;

        // ---- 100 TIMES FOR TIME TESTING ----
        for (int j = 0; j < 100; j++) {
            n = read(newsockfd, &bytes_torecv, sizeof(int));
            if (n < 0)
                error("ERROR reading from socket");

            printf("Bytes to recieve: %d\n",bytes_torecv);
            char buffer[bytes_torecv];
            bzero(buffer, bytes_torecv);

            n = write(newsockfd, ack_msg, strlen(ack_msg) + 1);
            if (n < 0)
                error("ERROR writing to socket");

            do {
                // ---- COMM LOOP ----
                int bytes_received = 0, outside_read_loops = 0;

                do {
                    int inside_read_loops = 0;

                    // ---- COMMS READ LOOP ----
                    do {
                        n = read(newsockfd, buffer + bytes_received, bytes_torecv - bytes_received);
                        if (n < 0)
                            error("ERROR reading from socket");
                        else if (n == 0)
                            break;

                        bytes_received += n;

                        inside_read_loops++;
                        printf("Bytes received so far: %d in %d inside read loops\n", bytes_received, inside_read_loops);

                    } while (n > 0 && bytes_received < bytes_torecv);

                    n = write(newsockfd, ack_msg, strlen(ack_msg) + 1);
                    if (n < 0)
                        error("ERROR writing to socket");

                    outside_read_loops++;
                    printf("Bytes received so far: %d in %d inside read loops, %d outside ones\n", bytes_received, inside_read_loops, outside_read_loops);          

                } while (bytes_received < bytes_torecv);
                
                // ---- VERIFICATION ----
                n = read(newsockfd, &checksum_recvd, sizeof(unsigned long));
                if (n < 0)
                    error("ERROR reading from socket");

                checksum = crc32(0L, (const unsigned char *)buffer, bytes_received);
                printf("Checksum received: %ld\nChecksum calculated: %ld\n", checksum_recvd, checksum);

                checksum_failed = (checksum != checksum_recvd);

                if (checksum_failed)
                    bzero(buffer, bytes_torecv);

                n = write(newsockfd, &checksum_failed, sizeof(checksum_failed));
                if (n < 0)
                    error("ERROR writing to socket");

            } while(checksum_failed);
        }

        close(newsockfd);
    }
    
    close(sockfd);

    return 0; 
}
