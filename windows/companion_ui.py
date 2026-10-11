"""Tk dashboard owned by the existing DropEdge main loop (no second Tk thread)."""
import tkinter as tk
from tkinter import ttk, filedialog
import time
import io
from PIL import Image, ImageTk
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
        ttk.Button(box,text='VER TELA DO CELULAR',style='Nexus.TButton',command=self.show_phone).pack(fill='x',pady=8)
        ttk.Button(box,text='PREPARAR ATUALIZAÇÃO DOS DOIS',style='Nexus.TButton',
                   command=self.prepare_update).pack(fill='x',pady=(4,4))
        self.update_info=ttk.Label(box,text='Escolha uma vez os novos instaladores. Depois confirme no celular.',
                                   style='Nexus.TLabel',wraplength=620)
        self.update_info.pack(fill='x',pady=(0,6))
        self.phone_window=None
        self.phone_photo=None
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
    def prepare_update(self):
        # The physical PC user must choose both sources; Android cannot stage arbitrary EXEs.
        apk = filedialog.askopenfilename(
            parent=self.window, title='NEXUS: selecione o APK novo',
            filetypes=[('Android APK','*.apk')])
        if not apk: return
        exe = filedialog.askopenfilename(
            parent=self.window, title='NEXUS: selecione o EXE Windows novo',
            filetypes=[('Windows EXE','*.exe')])
        if not exe: return
        try:
            manifest=self.api.UPDATER.prepare(apk,exe)
            self.update_info.configure(
                text='Atualização preparada por até 60 minutos. Abra Configurações > Atualizar NEXUS no celular conectado.\\n'
                     'APK: '+manifest['android']['sha256'][:12]+'…  •  EXE: '+manifest['windows']['sha256'][:12]+'…'
            )
        except Exception as error:
            self.update_info.configure(text='Não foi possível preparar: '+str(error))

    def show_phone(self):
        if self.phone_window is None or not self.phone_window.winfo_exists():
            self.phone_window=tk.Toplevel(self.window)
            self.phone_window.title('NEXUS • Tela do celular')
            self.phone_window.geometry('490x820')
            self.phone_window.configure(bg='#070e16')
            self.phone_label=tk.Label(self.phone_window,text='Inicie a transmissão na aba Tela celular do Android',
                                      bg='#070e16',fg='#cbdfee',font=('Segoe UI',11))
            self.phone_label.pack(fill='both',expand=True)
            self.phone_image_at=0
            self.poll_phone()
        else:
            self.phone_window.deiconify();self.phone_window.lift()

    def poll_phone(self):
        if self.phone_window is None or not self.phone_window.winfo_exists():
            return
        data, timestamp = self.api.phone_frame_snapshot()
        if timestamp and timestamp != self.phone_image_at and time.monotonic()-timestamp < 4:
            try:
                frame=Image.open(io.BytesIO(data))
                frame.thumbnail((920,1380),Image.Resampling.BILINEAR)
                self.phone_photo=ImageTk.PhotoImage(frame,master=self.phone_window)
                self.phone_label.configure(image=self.phone_photo,text='')
                self.phone_image_at=timestamp
            except Exception:
                pass
        elif time.monotonic()-timestamp > 4:
            self.phone_label.configure(image='',text='Aguardando transmissão autorizada no celular…')
            self.phone_photo=None
        self.phone_window.after(150,self.poll_phone)

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
