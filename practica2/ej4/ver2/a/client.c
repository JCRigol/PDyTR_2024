#include <stdio.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <netinet/in.h>
#include <netdb.h>
#include <string.h>  // Para strlen y otras funciones de cadenas
#include <unistd.h>  // Para read, write y otras funciones de E/S
#include <stdlib.h>  // Para exit y otras funciones estándar
#include <math.h>
#include <zlib.h> // For CRC32
#include <stdbool.h>

void error(char *msg)
{
    perror(msg);
    exit(1);
}

double calculate_time(struct timeval start, struct timeval end, int repetitions)
{
    return ((end.tv_sec - start.tv_sec) + (end.tv_usec - start.tv_usec) / 1e6) / repetitions;
}

void timelog(int run_number, int buffer_size, double comm_time)
{
    FILE *fp;
    fp = fopen("comm_times.csv", "a");
    if (fp == NULL)
        error("Failed to open csv file");

    fprintf(fp, "%d,%d,%.6f\n", run_number, buffer_size, comm_time);
    fclose(fp);
}

int main(int argc, char *argv[])
{
    int sockfd, portno, n;
    struct sockaddr_in serv_addr;
    struct hostent *server;

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

    // ---- DATA TO SEND ----
    int potencia = atoi(argv[3]);
    int buff_size = pow(10,potencia);
    char buffer[buff_size];
    memset(buffer, 'A', buff_size);
    buffer[buff_size - 1] = '\0';  // Null-terminate the buffer for printf

    // ---- UTILITIES ----
    int nr_bytes_sent;
    char ack_buffer[19];
    unsigned long checksum;
    bool corrupted_comm;
    struct timeval start, end;
    int amount_of_msgs_needed;

    // ---- COMM START ----
    for (int i = 0; i < 100; i++){
        nr_bytes_sent = 0;
        bzero(ack_buffer, 19);
        checksum = crc32(0L, (const unsigned char *)buffer, buff_size);
        amount_of_msgs_needed = 0;

        // ---- START COMM TIME ----
        gettimeofday(&start, NULL);

        // ---- FIRST COMMUNICATION (SEND TOTAL BUFFER SIZE, AWAIT ACK) ----
        n = write(sockfd, &buff_size, sizeof(buff_size));
        if (n < 0)
            error("ERROR writing to socket");

        n = read(sockfd, ack_buffer, 19);
        if (n < 0)
            error("ERROR reading from socket");

        amount_of_msgs_needed++;
        printf("%s\n", ack_buffer);

        do {
            // ---- BUFFER COMM LOOP ----
            do {
                n = write (sockfd, buffer + nr_bytes_sent, buff_size - nr_bytes_sent);
                if (n < 0)
                    error("ERROR writing to socket");

                nr_bytes_sent += n;

                n = read(sockfd, ack_buffer, 19);
                if (n < 0)
                    error("ERROR writing to socket");

                amount_of_msgs_needed++;
                printf("%s\n", ack_buffer);
                bzero(ack_buffer, 18);
            } while (nr_bytes_sent < buff_size);

            // ---- VERIFICATION PHASE ----
            n = write(sockfd, &checksum, sizeof(checksum));
            if (n < 0)
                error("ERROR writing to socket");

            n = read(sockfd, &corrupted_comm, sizeof(bool));
            if (n < 0)
                error("ERROR reading from socket");

            amount_of_msgs_needed++;

            if (corrupted_comm)
                nr_bytes_sent = 0;

        } while (corrupted_comm);

        // ---- END COMM TIME
        gettimeofday(&end, NULL);

        // ---- COMM END ----
        timelog(i + 1, buff_size, calculate_time(start, end, amount_of_msgs_needed * 2));
    } 

    // CIERRA EL SOCKET
    close(sockfd);

    return 0;
}
