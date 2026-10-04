from pathlib import Path
import shutil
try:
 from tkinterdnd2 import TkinterDnD,DND_FILES
except ImportError:
 TkinterDnD=None
import tkinter as tk
from tkinter import filedialog

OUTBOX=Path.home()/"Downloads"/"PC Remote"/"To Phone";OUTBOX.mkdir(parents=True,exist_ok=True)

class DropEdge:
 def __init__(self):
  self.root=(TkinterDnD.Tk() if TkinterDnD else tk.Tk());self.root.overrideredirect(True);self.root.attributes("-topmost",True);self.root.attributes("-alpha",.08)
  sh=self.root.winfo_screenheight();self.root.geometry(f"160x160+0+{sh-160}")
  self.label=tk.Label(self.root,text="↗\nPC DROP",font=("Segoe UI",13,"bold"),bg="#111629",fg="#42D9FF");self.label.pack(fill="both",expand=True)
  self.root.bind("<Enter>",self.enter);self.root.bind("<Leave>",self.leave);self.root.bind("<Button-1>",self.pick)
  if TkinterDnD:
   self.root.drop_target_register(DND_FILES);self.root.dnd_bind("<<DropEnter>>",self.drag_enter);self.root.dnd_bind("<<DropLeave>>",self.drag_leave);self.root.dnd_bind("<<Drop>>",self.drop)
 def enter(self,_=None):self.root.attributes("-alpha",.94);self.label.config(text="↗\nSOLTE PARA ENVIAR")
 def leave(self,_=None):self.root.attributes("-alpha",.08);self.label.config(text="↗\nPC DROP")
 def drag_enter(self,e):self.enter();return e.action
 def drag_leave(self,e):self.leave();return e.action
 def drop(self,e):
  paths=[Path(x) for x in self.root.tk.splitlist(e.data)]
  ok=sum(1 for p in paths if p.is_file() and self.queue(p,False))
  self.label.config(text=f"✓\n{ok} ARQUIVO(S)" if ok else "!\nNENHUM ARQUIVO");self.root.after(1100,self.leave);return e.action
 def pick(self,_=None):
  paths=filedialog.askopenfilenames()
  for p in paths:self.queue(Path(p),False)
  if paths:self.label.config(text=f"✓\n{len(paths)} ARQUIVO(S)");self.root.after(900,self.leave)
 def queue(self,p,feedback=True):
  try:
   target=OUTBOX/p.name;n=1
   while target.exists():target=OUTBOX/f"{p.stem}_{n}{p.suffix}";n+=1
   shutil.copy2(p,target)
   if feedback:self.label.config(text="✓\nPRONTO PRO CELULAR")
   return True
  except Exception:
   if feedback:self.label.config(text="!\nFALHOU")
   return False
 def run(self):self.root.mainloop()

if __name__=="__main__":DropEdge().run()
