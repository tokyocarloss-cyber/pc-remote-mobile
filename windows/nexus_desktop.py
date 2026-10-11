"""Interface Windows do NEXUS PC Remote. Executável PyInstaller compatível."""
import os
import threading
import tkinter as tk
from tkinter import messagebox, ttk
from http.server import ThreadingHTTPServer

from pc_remote_server import GAMEPAD, Handler, PIN, PORT, ROOT, VERSION, local_ip


class NexusDesktop:
    def __init__(self):
        self.root = tk.Tk()
        self.root.title("NEXUS PC Remote")
        self.root.geometry("475x455")
        self.root.minsize(430, 415)
        self.root.configure(bg="#0b1020")
        self.root.protocol("WM_DELETE_WINDOW", self.close)
        self.server = None

        shell = tk.Frame(self.root, bg="#0b1020", padx=24, pady=20)
        shell.pack(fill="both", expand=True)
        tk.Label(shell, text="NEXUS", bg="#0b1020", fg="#48e2ff",
                 font=("Segoe UI", 29, "bold")).pack(anchor="w")
        tk.Label(shell, text="PC Remote  " + VERSION, bg="#0b1020", fg="#9baabc",
                 font=("Segoe UI", 10)).pack(anchor="w")

        card = tk.Frame(shell, bg="#171f30", padx=18, pady=14)
        card.pack(fill="x", pady=(24, 14))
        self.status = tk.StringVar(value="Iniciando serviço...")
        tk.Label(card, textvariable=self.status, bg="#171f30", fg="#8bf0bd",
                 font=("Segoe UI", 12, "bold")).pack(anchor="w", pady=(0, 10))
        tk.Label(card, text="IP do computador", bg="#171f30", fg="#98a9be").pack(anchor="w")
        self.ip = tk.StringVar(value=local_ip())
        tk.Label(card, textvariable=self.ip, bg="#171f30", fg="white",
                 font=("Consolas", 17, "bold")).pack(anchor="w", pady=(0, 8))
        tk.Label(card, text="PIN de pareamento", bg="#171f30", fg="#98a9be").pack(anchor="w")
        tk.Label(card, text=PIN, bg="#171f30", fg="#48e2ff",
                 font=("Consolas", 27, "bold")).pack(anchor="w")

        btn = tk.Frame(shell, bg="#0b1020")
        btn.pack(fill="x")
        ttk.Button(btn, text="Copiar IP", command=self.copy_ip).pack(side="left", padx=(0, 8))
        ttk.Button(btn, text="Abrir pasta de arquivos", command=self.open_files).pack(side="left")
        tk.Label(shell, text="Controle XInput: " + ("disponível" if GAMEPAD else "requer vgamepad + ViGEmBus"),
                 bg="#0b1020", fg="#b6c4d8").pack(anchor="w", pady=(18, 7))
        tk.Label(shell, text="Use o Android na mesma rede Wi-Fi.\nNão exponha a porta 8765 na Internet.",
                 bg="#0b1020", fg="#98a9be", justify="left").pack(anchor="w")

    def copy_ip(self):
        self.root.clipboard_clear()
        self.root.clipboard_append(self.ip.get())
        self.root.update()

    def open_files(self):
        os.startfile(str(ROOT))

    def start(self):
        try:
            self.server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
        except OSError as exc:
            self.status.set("Erro: não foi possível abrir a porta")
            messagebox.showerror("NEXUS", "A porta 8765 pode estar em uso.\n" + str(exc))
            return
        self.status.set("● Serviço ativo • porta " + str(PORT))
        threading.Thread(target=self.server.serve_forever, daemon=True).start()

    def close(self):
        if self.server is not None:
            self.server.shutdown()
            self.server.server_close()
        self.root.destroy()

    def run(self):
        self.root.after(100, self.start)
        self.root.mainloop()


if __name__ == "__main__":
    NexusDesktop().run()
