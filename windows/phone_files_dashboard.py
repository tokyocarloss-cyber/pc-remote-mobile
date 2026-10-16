"""Windows tree browser for the Android folder explicitly granted via SAF."""
import tkinter as tk
from tkinter import ttk
from phone_control_bridge import BRIDGE


class PhoneFilesDashboard:
    def __init__(self,root):
        self.root=root
        self.window=None
        self.view=None
        self.status=None
        self.version=-1
        self.current="root"
        self.parent=""

    def show(self):
        if self.window is not None and self.window.winfo_exists():
            self.window.deiconify()
            self.window.lift()
            return
        w=tk.Toplevel(self.root)
        self.window=w
        w.title("NEXUS | Arquivos do celular")
        w.geometry("680x570")
        self.status=tk.StringVar(value="Ative Controle do celular e escolha uma pasta na aba Arquivos do Android.")
        body=ttk.Frame(w,padding=12)
        body.pack(fill="both",expand=True)
        ttk.Label(body,text="Arquivos compartilhados do celular",
                  font=("Segoe UI",15,"bold")).pack(anchor="w")
        ttk.Label(body,text="Somente a pasta que você autorizou no Android. Para baixar, clique duas vezes no arquivo.",
                  wraplength=620).pack(anchor="w",pady=(4,9))
        buttons=ttk.Frame(body)
        buttons.pack(fill="x",pady=7)
        ttk.Button(buttons,text="Início",command=lambda:self.ask("root")).pack(side="left",padx=3)
        ttk.Button(buttons,text="Voltar",command=self.go_back).pack(side="left",padx=3)
        ttk.Button(buttons,text="Atualizar",command=lambda:self.ask(self.current)).pack(side="left",padx=3)
        self.view=ttk.Treeview(body,columns=("size","type"),show="tree headings")
        self.view.heading("#0",text="Nome")
        self.view.heading("size",text="Tamanho")
        self.view.heading("type",text="Tipo")
        self.view.column("#0",width=390)
        self.view.column("size",width=110)
        self.view.column("type",width=90)
        scrollbar=ttk.Scrollbar(body,orient="vertical",command=self.view.yview)
        self.view.configure(yscrollcommand=scrollbar.set)
        self.view.pack(side="left",fill="both",expand=True)
        scrollbar.pack(side="right",fill="y")
        self.view.bind("<Double-1>",self.open_selection)
        bottom=ttk.Label(w,textvariable=self.status,wraplength=650)
        bottom.pack(fill="x",padx=12,pady=10)
        w.protocol("WM_DELETE_WINDOW",w.withdraw)
        self.ask("root")
        self.refresh()

    def ask(self,ident):
        try:
            BRIDGE.enqueue("files_list",ident)
            self.status.set("Consultando pasta no Android...")
        except (RuntimeError,ValueError) as e:
            self.status.set(str(e))

    def go_back(self):
        self.ask(self.parent or "root")

    def open_selection(self,_=None):
        selected=self.view.focus()
        if not selected:
            return
        tags=self.view.item(selected,"tags")
        if tags and tags[0]=="folder":
            self.ask(selected)
        else:
            try:
                BRIDGE.enqueue("file_get",selected)
                self.status.set("Solicitando arquivo ao Android...")
            except (RuntimeError,ValueError) as e:
                self.status.set(str(e))

    def refresh(self):
        if self.window is None or not self.window.winfo_exists():
            return
        if self.window.state()!="withdrawn":
            state=BRIDGE.files_snapshot()
            if state["seq"]!=self.version:
                self.version=state["seq"]
                self.current=state["folder"] or "root"
                self.parent=state["parent"]
                for row in self.view.get_children():
                    self.view.delete(row)
                for item in state["items"]:
                    self.view.insert("", "end",iid=item["id"],
                        text=("📁 " if item["dir"] else "📄 ")+item["name"],
                        values=("" if item["dir"] else str(item["size"])+" bytes",
                                "Pasta" if item["dir"] else "Arquivo"),
                        tags=("folder",) if item["dir"] else ("file",))
                self.status.set(state["error"] or f"{len(state['items'])} itens nesta pasta")
            if BRIDGE.last_file:
                self.status.set("Arquivo recebido: "+BRIDGE.last_file)
                BRIDGE.last_file=""
        self.window.after(800,self.refresh)
