"""In-memory, opt-in Android companion controls.

A single authorized Android device polls commands from the paired PC.
No arbitrary shell commands, touch injection, remote app launching or
background capture are supported.
"""
import collections
import threading
import time

class PhoneControlBridge:
    def __init__(self):
        self.lock=threading.Lock()
        self.queue=collections.deque(maxlen=20)
        self.notices=collections.deque(maxlen=30)
        self.last_seen=0.0
        self.volume=None
        self.enabled=False
        self.muted=False

    def enqueue(self,kind,value):
        if kind=="volume":
            if isinstance(value,bool) or not isinstance(value,int) or not 0 <= value <= 100:
                raise ValueError("Volume must be integer 0..100")
        elif kind=="mute":
            if not isinstance(value,bool):
                raise ValueError("Mute must be boolean")
        else:
            raise ValueError("Unsupported command")
        with self.lock:
            if not self.enabled or time.monotonic()-self.last_seen>8:
                raise RuntimeError("Android control is not enabled or not connected")
            self.queue.append({"action":kind,"value":value})

    def next(self):
        with self.lock:
            self.last_seen=time.monotonic()
            if not self.queue:
                return {"action":"none"}
            return self.queue.popleft()

    def update_state(self,volume,muted,enabled):
        if isinstance(volume,bool) or not isinstance(volume,int) or not 0<=volume<=100:
            raise ValueError("Invalid Android volume")
        if not isinstance(muted,bool) or not isinstance(enabled,bool):
            raise ValueError("Invalid Android control state")
        with self.lock:
            self.last_seen=time.monotonic()
            self.volume=volume
            self.muted=muted
            self.enabled=enabled
            if not enabled:
                self.queue.clear()

    def add_notice(self,title,detail,app):
        def clean(s):
            return " ".join(str(s).split())[:160]
        record={"title":clean(title),"text":clean(detail),"app":clean(app),
                "time":int(time.time())}
        if not record["title"] and not record["app"]:
            return
        with self.lock:
            self.notices.appendleft(record)

    def status(self):
        with self.lock:
            return {"connected":self.enabled and time.monotonic()-self.last_seen<8,
                    "enabled":self.enabled,"volume":self.volume,"muted":self.muted,
                    "notifications":list(self.notices),"queued":len(self.queue)}

BRIDGE=PhoneControlBridge()
