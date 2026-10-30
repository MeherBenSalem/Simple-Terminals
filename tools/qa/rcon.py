#!/usr/bin/env python3
"""Minimal RCON client for driving dev servers in QA scenarios.

  python3 tools/qa/rcon.py "list"                   # one command
  python3 tools/qa/rcon.py --port 25575 --password qa "list" "time set day"
"""
from __future__ import annotations

import argparse
import socket
import struct
import time


class Rcon:
    def __init__(self, host='127.0.0.1', port=25575, password='qa', timeout=15.0):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.next_id = 1
        if self._request(3, password)[0] == -1:
            raise PermissionError('RCON authentication failed')

    def _packet(self, kind, body):
        rid = self.next_id
        self.next_id += 1
        data = struct.pack('<ii', rid, kind) + body.encode('utf-8') + b'\x00\x00'
        self.sock.sendall(struct.pack('<i', len(data)) + data)
        return rid

    def _read(self):
        def recv(n):
            buf = b''
            while len(buf) < n:
                chunk = self.sock.recv(n - len(buf))
                if not chunk:
                    raise ConnectionError('RCON connection closed')
                buf += chunk
            return buf
        length = struct.unpack('<i', recv(4))[0]
        payload = recv(length)
        rid, kind = struct.unpack('<ii', payload[:8])
        return rid, kind, payload[8:-2].decode('utf-8', 'replace')

    def _request(self, kind, body):
        self._packet(kind, body)
        rid, _, text = self._read()
        return rid, text

    def command(self, cmd):
        return self._request(2, cmd)[1]

    def close(self):
        self.sock.close()


def wait_for(port=25575, password='qa', seconds=600):
    deadline = time.time() + seconds
    while time.time() < deadline:
        try:
            return Rcon(port=port, password=password)
        except OSError:
            time.sleep(2)
    raise TimeoutError('RCON did not come up')


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--port', type=int, default=25575)
    ap.add_argument('--password', default='qa')
    ap.add_argument('commands', nargs='+')
    args = ap.parse_args()
    r = Rcon(port=args.port, password=args.password)
    for c in args.commands:
        print(f'> {c}\n{r.command(c)}')
    r.close()
