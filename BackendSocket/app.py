import socket
import struct
import os
import threading

HOST = '0.0.0.0'
TCP_PORT = 12345
SAVE_DIR = './analysis_data'

DISCOVERY_REQUEST  = b'DISC'
DISCOVERY_RESPONSE = b'OK\n'


def recv_all(conn, length):
    data = b''
    while len(data) < length:
        packet = conn.recv(length - len(data))
        if not packet:
            return None
        data += packet
    return data


def handle_client(conn, addr):
    print(f"[*] Conexão de {addr[0]}:{addr[1]}")
    try:
        with conn:
            first_bytes = recv_all(conn, 4)
            if not first_bytes:
                return

            # Handshake de descoberta TCP
            if first_bytes == DISCOVERY_REQUEST:
                conn.sendall(DISCOVERY_RESPONSE)
                print(f"  [+] Descoberta respondida para {addr[0]}")
                return

            # Protocolo de log normal: primeiro 4 bytes = tamanho
            log_len = struct.unpack('>I', first_bytes)[0]

            log_data_bytes = recv_all(conn, log_len)
            if not log_data_bytes:
                raise ConnectionError("Conexão encerrada durante recepção do log.")

            log_data = log_data_bytes.decode('utf-8')
            print(f"  [+] Log recebido ({len(log_data_bytes)} bytes).")

            os.makedirs(SAVE_DIR, exist_ok=True)

            i = 1
            while True:
                log_filepath = os.path.join(SAVE_DIR, f"log_{i}.txt")
                if not os.path.exists(log_filepath):
                    break
                i += 1

            with open(log_filepath, 'w', encoding='utf-8') as f:
                f.write(log_data)
            print(f"  [+] Log salvo em: {log_filepath}")

    except (ConnectionError, struct.error, ValueError) as e:
        print(f"[!] Erro de protocolo com {addr}: {e}")
    except Exception as e:
        print(f"[!] Erro inesperado com {addr}: {e}")
    finally:
        print(f"[*] Conexão com {addr} encerrada.")


def start_server():
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind((HOST, TCP_PORT))
        s.listen()
        print(f"[*] Servidor escutando na porta {TCP_PORT}")

        while True:
            conn, addr = s.accept()
            t = threading.Thread(target=handle_client, args=(conn, addr))
            t.start()


if __name__ == "__main__":
    os.makedirs(SAVE_DIR, exist_ok=True)
    start_server()
