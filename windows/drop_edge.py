from pathlib import Path
import shutil
try:
 from tkinterdnd2 import TkinterDnD,DND_FILES
except ImportError:
 TkinterDnD=None
import tkinter as tk

OUTBOX=Path.home()/"Downloads"/"PC Remote"/"To Phone";OUTBOX.mkdir(parents=True,exist_ok=True)

class DropEdge:
 def __init__(self):
  self.root=(TkinterDnD.Tk() if TkinterDnD else tk.Tk())
  self.root.overrideredirect(True);self.root.attributes("-topmost",True)
  self.w=220;self.h=136;self.hot_h=24
  self.label=tk.Label(self.root,text="",font=("Segoe UI",12,"bold"),bg="#000000",fg="#60E6FF")
  self.label.pack(fill="both",expand=True)
  self.hide_target()
  if TkinterDnD:
   self.root.drop_target_register(DND_FILES)
   self.root.dnd_bind("<<DropEnter>>",self.drag_enter)
   self.root.dnd_bind("<<DropLeave>>",self.drag_leave)
   self.root.dnd_bind("<<Drop>>",self.drop)
  else:
   self.root.withdraw()
 def place(self,height):
  sh=self.root.winfo_screenheight();self.root.geometry(f"{self.w}x{height}+0+{max(0,sh-height)}")
 def hide_target(self,_=None):
  self.place(self.hot_h);self.root.attributes("-alpha",.01);self.label.config(text="",bg="#000000")
 def show_target(self):
  self.place(self.h);self.root.attributes("-alpha",.97);self.label.config(text="SOLTE AQUI\nPARA ENVIAR AO CELULAR",bg="#0B1119",fg="#60E6FF")
 def drag_enter(self,e):self.show_target();return e.action
 def drag_leave(self,e):self.hide_target();return e.action
 def drop(self,e):
  paths=[Path(x) for x in self.root.tk.splitlist(e.data)]
  ok=sum(1 for p in paths if p.is_file() and self.queue(p))
  self.label.config(text=f"✓\n{ok} ARQUIVO(S)" if ok else "!\nNENHUM ARQUIVO",fg="#62E69A" if ok else "#FF7A7A")
  self.root.after(650,self.hide_target);return e.action
 def queue(self,p):
  try:
   target=OUTBOX/p.name;n=1
   while target.exists():target=OUTBOX/f"{p.stem}_{n}{p.suffix}";n+=1
   shutil.copy2(p,target);return True
  except Exception:return False
 def run(self):self.root.mainloop()

if __name__=="__main__":DropEdge().run()
