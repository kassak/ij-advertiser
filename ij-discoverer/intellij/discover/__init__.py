import json
import socket
from urllib.request import Request, urlopen

_instance_cache = {}


def create_instance(host, port):
    global _instance_cache
    key = (host, port)
    inst = _instance_cache.get(key)
    if inst is None:
        _instance_cache[key] = inst = Client(host, port)
    return inst


def any_instance():
    one = discover_running_instances(1)
    return one[0] if one else create_instance('127.0.0.1', 63343)


def discover_running_instances(limit=None):
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        sock.sendto(bytes('IJDISC', 'latin1'), ('127.255.255.255', 6666))

        sock.settimeout(0.5)
        res = []
        try:
            while True:
                packet = sock.recvfrom(7)
                inst = _extract_host(packet)
                res.append(create_instance(*inst))
                if limit is not None and len(res) >= limit:
                    break
        except socket.timeout:
            pass
    return res


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


class Client(object):
    def __init__(self, host, port):
        self.host = host
        self.port = port
        self._about = None
        self.noisy = False

    def request(self, path):
        url = 'http://{0}:{1}/api/{2}'.format(self.host, self.port, path)
        if self.noisy:
            print(url)
        r = Request(url)
        r.add_header("Origin", "http://localhost")
        return r

    def perform(self, r):
        if isinstance(r, str):
            r = self.request(r)
        with urlopen(r) as info:
            return info.read()

    def perform_json(self, r):
        return json.loads(self.perform(r))

    def about(self):
        if self._about is None:
            self._about = self.perform_json("about")
        return self._about

    def __repr__(self):
        return "<{0}:{1}>".format(self.host, self.port)


if __name__ == '__main__':
    for c in discover_running_instances():
        print(c.about())
