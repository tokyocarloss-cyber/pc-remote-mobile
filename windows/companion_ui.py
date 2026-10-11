"""Tk dashboard owned by the existing DropEdge main loop (no second Tk thread)."""
import tkinter as tk
from tkinter import ttk
import time
from security import DEVICES, fingerprint

class Dashboard:
    def __init__(self, root, api):
        self.api=api
        self.window=tk.Toplevel(root)
        self.window.title('NEXUS PC Remote • 2.0 beta')
        self.window.geometry('720x640');self.window.minsize(580,540)
        self.window.configure(bg='#09121d')
        self.window.protocol('WM_DELETE_WINDOW',self.window.withdraw)
        style=ttk.Style(self.window);style.theme_use('clam')
        style.configure('Nexus.TFrame',background='#09121d')
        style.configure('Nexus.TLabel',background='#09121d',foreground='#dae8f6',font=('Segoe UI',11))
        style.configure('Title.TLabel',background='#09121d',foreground='#68dfff',font=('Segoe UI',26,'bold'))
        style.configure('Nexus.TButton',padding=10,font=('Segoe UI',10))
        box=ttk.Frame(self.window,style='Nexus.TFrame',padding=24);box.pack(fill='both',expand=True)
        ttk.Label(box,text='NEXUS',style='Title.TLabel').pack(anchor='w')
        ttk.Label(box,text='Seu Windows conectado ao celular',style='Nexus.TLabel').pack(anchor='w',pady=(0,18))
        self.status=ttk.Label(box,text='Iniciando…',style='Nexus.TLabel');self.status.pack(anchor='w')
        self.detail=ttk.Label(box,text='',style='Nexus.TLabel');self.detail.pack(anchor='w',pady=6)
        row=ttk.Frame(box,style='Nexus.TFrame');row.pack(fill='x',pady=10)
        ttk.Button(row,text='Parear celular',style='Nexus.TButton',command=self.pair).pack(side='left',padx=(0,8))
        ttk.Button(row,text='Downloads',style='Nexus.TButton',command=api.open_downloads).pack(side='left',padx=(0,8))
        ttk.Button(row,text='Firewall',style='Nexus.TButton',command=api.repair_firewall).pack(side='left')
        self.pair_info=ttk.Label(box,text='Pareamento fechado. Abra somente quando for conectar seu celular.',style='Nexus.TLabel',wraplength=640)
        self.pair_info.pack(fill='x',pady=12)
        ttk.Label(box,text='Dispositivos autorizados',style='Nexus.TLabel').pack(anchor='w',pady=(10,6))
        self.list=tk.Listbox(box,bg='#122235',fg='#e7f3ff',selectbackground='#236788',height=5,borderwidth=0,highlightthickness=0,font=('Segoe UI',11))
        self.list.pack(fill='x')
        self.keys=[]
        row=ttk.Frame(box,style='Nexus.TFrame');row.pack(fill='x',pady=10)
        ttk.Button(row,text='Revogar selecionado',style='Nexus.TButton',command=self.revoke).pack(side='left')
        ttk.Button(row,text='Iniciar com Windows: alternar',style='Nexus.TButton',command=api.toggle_autostart).pack(side='left',padx=8)
        ttk.Button(box,text='Minimizar para bandeja',style='Nexus.TButton',command=self.window.withdraw).pack(anchor='e',pady=10)
        self.update()
    def show(self):self.window.deiconify();self.window.lift()
    def pair(self):
        if not self.api.server:
            self.pair_info.configure(text='Servidor indisponível: '+self.api.server_error);return
        pin=DEVICES.open_pairing()
        self.pair_info.configure(text='PIN: '+pin+' • válido por 2 minutos, para um celular\n\nCompare o certificado SHA-256 no Android:\n'+' '.join(fingerprint()[i:i+8] for i in range(0,64,8)))
    def revoke(self):
        selected=self.list.curselection()
        if selected:
            with DEVICES.lock:
                DEVICES.devices.pop(self.keys[selected[0]],None);DEVICES.save()
            self.api.servermod.reset_input();self.refresh_devices()
    def refresh_devices(self):
        with DEVICES.lock:devices=list(DEVICES.devices.items())
        keys=[key for key,_ in devices]
        if keys!=self.keys:
            self.keys=keys;self.list.delete(0,tk.END)
            for _,device in devices:self.list.insert(tk.END,device['name'])
    def update(self):
        if not self.window.winfo_exists():return
        self.status.configure(text=('Servidor ativo • ' if self.api.server else 'Servidor indisponível • ')+self.api.local_ip()+':8765 • TLS')
        self.detail.configure(text=('Celular conectado' if self.api.phone_connected() else 'Aguardando celular')+'  |  '+('XInput pronto' if self.api.GAMEPAD else 'XInput: driver indisponível')+'  |  '+('Autostart ligado' if self.api.autostart_enabled() else 'Autostart desligado'))
        self.refresh_devices()
        if DEVICES.expires and time.monotonic()>=DEVICES.expires:
            DEVICES.expires=0;self.pair_info.configure(text='PIN expirou. Clique em Parear celular para gerar outro.')
        self.window.after(1500,self.update)
