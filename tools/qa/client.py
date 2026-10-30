#!/usr/bin/env python3
"""Drives a running QA client (on an X display) together with its server (over RCON).

Used for the client checks and for the marketing screenshots: commands go to the server console, keys and mouse go
to the game window with xdotool, and captures are the X display's pixels (ImageMagick `import`).

  from client import Session
  s = Session(display=':1')
  s.cmd('time set noon'); s.hud(False); s.shot('out.png')
"""
from __future__ import annotations

import math
import os
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from rcon import wait_for  # noqa: E402

PLAYER = 'Builder'


class Session:
    def __init__(self, display=':1', port=25575):
        self.env = dict(os.environ, DISPLAY=display)
        self.rcon = wait_for(port)
        self.hud_hidden = False

    # Server side
    def cmd(self, *commands):
        out = ''
        for c in commands:
            out = self.rcon.command(c)
        return out

    def tp(self, x, y, z, yaw=None, pitch=None, facing=None, eye=1.62):
        if facing:
            # `tp ... facing` does not stick on 26.3 clients, so aim with explicit angles.
            dx, dy, dz = facing[0] - x, facing[1] - (y + eye), facing[2] - z
            yaw = math.degrees(math.atan2(-dx, dz))
            pitch = -math.degrees(math.atan2(dy, math.hypot(dx, dz)))
        return self.cmd(f'tp {PLAYER} {x} {y} {z} {yaw or 0:.2f} {pitch or 0:.2f}')

    # Client side
    def xdo(self, *args):
        subprocess.run(['xdotool', *args], env=self.env, check=False)

    def window(self):
        out = subprocess.run(['xdotool', 'search', '--name', 'Minecraft'], env=self.env, capture_output=True, text=True).stdout.split()
        return out[-1] if out else None

    def focus(self):
        w = self.window()
        if w:
            # Xvfb has no window manager, so focus the window directly (no _NET_ACTIVE_WINDOW).
            self.xdo('windowfocus', '--sync', w)
        return w

    def key(self, *keys, delay=0.15):
        self.focus()
        for k in keys:
            self.xdo('key', '--delay', '60', k)
            time.sleep(delay)

    def type(self, text):
        self.focus()
        self.xdo('type', '--delay', '40', text)

    def chat(self, text):
        """Runs a client chat command (e.g. a /command as the player)."""
        self.key('t')
        time.sleep(0.4)
        self.type(text)
        time.sleep(0.2)
        self.key('Return')

    def hold(self, button, seconds, sneak=False):
        self.focus()
        if sneak:
            self.xdo('keydown', 'shift')
        self.xdo('mousedown', str(button))
        time.sleep(seconds)
        self.xdo('mouseup', str(button))
        if sneak:
            self.xdo('keyup', 'shift')

    def click(self, button=1, x=None, y=None):
        self.focus()
        if x is not None:
            self.xdo('mousemove', str(x), str(y))
            time.sleep(0.1)
        self.xdo('click', str(button))

    def move(self, x, y):
        self.focus()
        self.xdo('mousemove', str(x), str(y))

    def hud(self, visible):
        if visible == self.hud_hidden:
            self.key('F1')
            self.hud_hidden = not visible

    def shot(self, path, crop=None):
        path = Path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(['import', '-window', 'root', str(path)], env=self.env, check=True)
        if crop:
            from PIL import Image
            Image.open(path).crop(crop).save(path)
        return path

    @staticmethod
    def wait(seconds):
        time.sleep(seconds)
