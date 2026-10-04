import tkinter as tk
from tkinter import filedialog
import urllib.request, urllib.parse
from pathlib import Path

class DropEdge:
    def __init__(self, host_provider):
        self.host_provider=host_provider
        self.root=tk.Tk()
        self.root.overrideredirect(True)
        self.root.attributes("-topmost", True)
        self.root.attributes("-alpha", .08)
        sw=self.root.winfo_screenwidth(); sh=self.root.winfo_screenheight()
        self.root.geometry(f"150x150+0+{sh-150}")
        self.label=tk.Label(self.root,text="↗\nPC DROP",font=("Segoe UI",13,"bold"),bg="#111629",fg="#42D9FF")
        self.label.pack(fill="both",expand=True)
        self.root.bind("<Enter>",self.enter); self.root.bind("<Leave>",self.leave)
        self.root.bind("<Button-1>",self.pick)
    def enter(self,_=None):
        self.root.attributes("-alpha",.94); self.label.config(text="↗\nENVIAR AO CELULAR")
    def leave(self,_=None):
        self.root.attributes("-alpha",.08); self.label.config(text="↗\nPC DROP")
    def pick(self,_=None):
        p=filedialog.askopenfilename()
        if p:self.send(Path(p))
    def send(self,p):
        host=self.host_provider()
        if not host:return
        try:
            data=p.read_bytes()
            req=urllib.request.Request(f"http://{host}:8766/inbox/{urllib.parse.quote(p.name)}",data=data,method="POST")
            urllib.request.urlopen(req,timeout=8).read()
            self.label.config(text="✓\nENVIADO")
            self.root.after(900,self.leave)
        except Exception:
            self.label.config(text="!\nFALHOU")
    def run(self):self.root.mainloop()
