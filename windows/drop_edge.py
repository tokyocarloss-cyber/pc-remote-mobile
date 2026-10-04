import tkinter as tk
from tkinter import filedialog
from pathlib import Path
import shutil

OUTBOX=Path.home()/"Downloads"/"PC Remote"/"To Phone"
OUTBOX.mkdir(parents=True,exist_ok=True)

class DropEdge:
    def __init__(self):
        self.root=tk.Tk();self.root.overrideredirect(True);self.root.attributes("-topmost",True);self.root.attributes("-alpha",.08)
        sh=self.root.winfo_screenheight();self.root.geometry(f"150x150+0+{sh-150}")
        self.label=tk.Label(self.root,text="↗\nPC DROP",font=("Segoe UI",13,"bold"),bg="#111629",fg="#42D9FF");self.label.pack(fill="both",expand=True)
        self.root.bind("<Enter>",self.enter);self.root.bind("<Leave>",self.leave);self.root.bind("<Button-1>",self.pick)
    def enter(self,_=None):self.root.attributes("-alpha",.94);self.label.config(text="↗\nSOLTE/CLIQUE PARA ENVIAR")
    def leave(self,_=None):self.root.attributes("-alpha",.08);self.label.config(text="↗\nPC DROP")
    def pick(self,_=None):
        p=filedialog.askopenfilename()
        if p:self.queue(Path(p))
    def queue(self,p):
        try:
            target=OUTBOX/p.name
            if target.exists():
                target=OUTBOX/f"{p.stem}_{int(p.stat().st_mtime)}{p.suffix}"
            shutil.copy2(p,target);self.label.config(text="✓\nPRONTO PRO CELULAR");self.root.after(900,self.leave)
        except Exception:self.label.config(text="!\nFALHOU")
    def run(self):self.root.mainloop()

if __name__=="__main__":DropEdge().run()
