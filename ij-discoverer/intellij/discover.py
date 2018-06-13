import json
import socket
from urllib.request import Request, urlopen


def discover_running_instances():
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        sock.sendto(bytes('IJDISC', 'latin1'), ('127.255.255.255', 6666))

        packet = sock.recvfrom(7)
        inst = _extract_host(packet)
        print(inst)
        print(_about(*inst))


def _extract_host(packet):
    d = packet[0]
    if (len(d) == 7
            and d[0] == ord('I')
            and d[1] == ord('J')
            and d[2] == ord('A')
            and d[3] == ord('D')
            and d[4] == ord('V')):
        return packet[1][0], (d[5] << 8) | d[6]
    else:
        return None


def _about(host, port):
    r = Request('http://{0}:{1}/api/about'.format(host, port))
    r.add_header("Origin", "http://localhost")
    with urlopen(r) as info:
        return json.loads(info.read())


if __name__ == '__main__':
    discover_running_instances()
