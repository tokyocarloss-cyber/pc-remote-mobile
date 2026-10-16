"""Local Windows panel for user-consented Android volume/notification controls."""
import tkinter as tk
from tkinter import ttk
from phone_control_bridge import BRIDGE


class PhoneControlDashboard:
    def __init__(self,root,open_files):
        self.root=root
        self.open_files=open_files
        self.window=None
        self.info=None
        self.volume=None
        self.notices=None
        self.is_adjusting=False
        self.last_displayed=None

    def show(self):
        if self.window is not None and self.window.winfo_exists():
            self.window.deiconify()
            self.window.lift()
            return
        w=tk.Toplevel(self.root)
        self.window=w
        w.title("NEXUS | Controle do celular")
        w.geometry("490x550")
        w.minsize(400,430)
        w.protocol("WM_DELETE_WINDOW",w.withdraw)
        body=ttk.Frame(w,padding=14)
        body.pack(fill="both",expand=True)
        ttk.Label(body,text="Controle do celular",font=("Segoe UI",16,"bold")).pack(anchor="w")
        ttk.Label(body,text="Ative no Android: Mais → Controle do celular.\n"
                  "Ações e notificações exigem autorização explícita.",
                  wraplength=440).pack(anchor="w",pady=(2,10))
        self.info=tk.StringVar(value="Aguardando autorização no Android")
        ttk.Label(body,textvariable=self.info).pack(anchor="w",pady=5)
        ttk.Label(body,text="Volume de mídia Android",font=("Segoe UI",10,"bold")).pack(anchor="w",pady=(12,2))
        self.volume=tk.DoubleVar(value=50)
        bar=ttk.Scale(body,from_=0,to=100,orient="horizontal",variable=self.volume)
        bar.pack(fill="x")
        bar.bind("<ButtonPress-1>",lambda e:self.set_adjusting(True))
        bar.bind("<ButtonRelease-1>",self.volume_committed)
        buttons=ttk.Frame(body)
        buttons.pack(fill="x",pady=9)
        ttk.Button(buttons,text="Silenciar",command=lambda:self.command("mute",True)).pack(side="left",padx=3)
        ttk.Button(buttons,text="Ativar som",command=lambda:self.command("mute",False)).pack(side="left",padx=3)
        ttk.Button(buttons,text="Abrir arquivos",command=self.open_files).pack(side="left",padx=3)
        ttk.Label(body,text="Notificações compartilhadas voluntariamente",
                  font=("Segoe UI",10,"bold")).pack(anchor="w",pady=(10,4))
        self.notices=tk.Text(body,height=14,wrap="word",state="disabled")
        self.notices.pack(fill="both",expand=True)
        self.refresh()

    def set_adjusting(self,value):
        self.is_adjusting=value

    def volume_committed(self,_=None):
        self.is_adjusting=False
        self.command("volume",round(self.volume.get()))

    def command(self,kind,value):
        try:
            BRIDGE.enqueue(kind,value)
        except (RuntimeError,ValueError) as exc:
            self.info.set(str(exc))

    def refresh(self):
        if self.window is None or not self.window.winfo_exists():
            return
        if self.window.state()!="withdrawn":
            state=BRIDGE.status()
            self.info.set("Celular autorizado e conectado" if state["connected"] else
                          "Celular desconectado ou controle não ativado")
            if state["connected"] and state["volume"] is not None and not self.is_adjusting:
                self.volume.set(state["volume"])
            notices=tuple((n["app"],n["title"],n["text"],n["time"])
                          for n in state["notifications"])
            if notices!=self.last_displayed and self.notices:
                self.notices.configure(state="normal")
                self.notices.delete("1.0","end")
                for app,title,content,timestamp in notices:
                    self.notices.insert("end",f"{app} — {title}\n{content}\n\n")
                self.notices.configure(state="disabled")
                self.last_displayed=notices
        self.window.after(700,self.refresh)
