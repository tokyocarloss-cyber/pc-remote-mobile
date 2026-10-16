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
        self.file_state={'folder':'root','parent':'','items':[],
                         'error':'Escolha a pasta no Android', 'seq':0}
        self.last_file=''

    def enqueue(self,kind,value):
        if kind=="volume":
            if isinstance(value,bool) or not isinstance(value,int) or not 0 <= value <= 100:
                raise ValueError("Volume must be integer 0..100")
        elif kind=="mute":
            if not isinstance(value,bool):
                raise ValueError("Mute must be boolean")
        elif kind in ("files_list","file_get"):
            if not isinstance(value,str) or not 1<=len(value)<=96:
                raise ValueError("Invalid file identifier")
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

    def update_files(self,payload):
        if not isinstance(payload,dict) or not isinstance(payload.get('items'),list):
            raise ValueError('Invalid folder listing')
        items=[]
        for item in payload['items'][:300]:
            if not isinstance(item,dict):
                continue
            ident=str(item.get('id',''))[:96]
            if not ident:
                continue
            items.append({'id':ident,'name':str(item.get('name',''))[:180],
                          'dir':bool(item.get('dir',False)),
                          'size':max(0,int(item.get('size',0)))})
        with self.lock:
            self.file_state={'folder':str(payload.get('folder','root'))[:96],
                             'parent':str(payload.get('parent',''))[:96],
                             'items':items,'error':str(payload.get('error',''))[:150],
                             'seq':self.file_state['seq']+1}

    def files_snapshot(self):
        with self.lock:
            return dict(self.file_state)

    def file_received(self,path):
        with self.lock:
            self.last_file=str(path)

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
