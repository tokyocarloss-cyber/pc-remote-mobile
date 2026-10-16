"""Windows recorder hub: local Tk window, no browser and no web authentication."""
import io
import tkinter as tk
from tkinter import ttk, messagebox
from PIL import Image, ImageTk
from recording_engine import PC_RECORDER, PHONE_RECORDER, encode_sample, unique_output


class RecorderDashboard:
    def __init__(self, root, pc_capture, phone_frame, phone_live, set_phone_profile):
        self.root = root
        self.pc_capture = pc_capture
        self.phone_frame = phone_frame
        self.phone_live = phone_live
        self.set_phone_profile = set_phone_profile
        self.window = None
        self.photo = None
        self.controls = {}
        self.statuses = {}
        self.preview = None
        self.last_preview_frame = None

    def show(self):
        if self.window and self.window.winfo_exists():
            self.window.deiconify()
            self.window.lift()
            return
        w = tk.Toplevel(self.root)
        w.title("NEXUS | Central de gravação e tela do celular")
        w.geometry("640x640")
        w.minsize(540, 520)
        self.window = w
        w.protocol("WM_DELETE_WINDOW", w.withdraw)
        tabs = ttk.Notebook(w)
        tabs.pack(fill="both", expand=True, padx=14, pady=14)
        for kind, title in (("pc", "Gravar PC"), ("phone", "Tela do celular")):
            tab = ttk.Frame(tabs, padding=12)
            tabs.add(tab, text=title)
            ttk.Label(tab, text=("Gravação da tela do Windows" if kind == "pc" else
                      "Compartilhamento iniciado pelo Android (permissão obrigatória)"),
                      font=("Segoe UI", 12, "bold")).pack(anchor="w", pady=(0, 6))
            if kind == "phone":
                ttk.Label(tab, text="O PC não inicia a captura escondida. Ative 'Tela do celular' no Android.\n"
                          "Som: não incluído nesta versão. FPS efetivo depende do envio do celular.",
                          wraplength=520).pack(anchor="w", pady=3)
            form = ttk.Frame(tab)
            form.pack(fill="x", pady=10)
            resolutions = (("720p", "1280x720"), ("1080p", "1920x1080"),
                           ("1440p", "2560x1440")) if kind == "pc" else (
                           ("540p", "960x540"), ("720p", "1280x720"), ("1080p", "1920x1080"))
            res_values = [name for name, _ in resolutions]
            res = tk.StringVar(value="720p")
            fps = tk.StringVar(value="30" if kind == "pc" else "10")
            quality = tk.StringVar(value="75")
            for col, (label, var, values) in enumerate((
                    ("Resolução", res, res_values),
                    ("FPS", fps, ["15", "24", "30", "60"] if kind == "pc" else ["5", "10", "15", "24"]),
                    ("Qualidade JPEG", quality, ["55", "75", "90"]))):
                ttk.Label(form, text=label).grid(row=0, column=col, sticky="w")
                select = ttk.Combobox(form, width=15, state="readonly", textvariable=var, values=values)
                select.grid(row=1, column=col, sticky="ew", padx=(0, 10), pady=6)
                form.columnconfigure(col, weight=1)
            self.controls[kind] = (res, fps, quality, dict(resolutions))
            buttons = ttk.Frame(tab)
            buttons.pack(fill="x", pady=6)
            ttk.Button(buttons, text="● Iniciar gravação", command=lambda k=kind: self.start(k)).pack(side="left", padx=3)
            ttk.Button(buttons, text="■ Parar", command=lambda k=kind: self.stop(k)).pack(side="left", padx=3)
            ttk.Button(buttons, text="▣ Tirar print", command=lambda k=kind: self.snapshot(k)).pack(side="left", padx=3)
            if kind == "phone":
                ttk.Button(buttons, text="Aplicar qualidade ao celular",
                           command=lambda: self.apply_phone_profile()).pack(anchor="w", pady=9)
                self.preview = ttk.Label(tab, text="Aguardando imagem autorizada do Android...", anchor="center")
                self.preview.pack(fill="both", expand=True)
            status = tk.StringVar(value="Aguardando")
            self.statuses[kind] = status
            ttk.Label(tab, textvariable=status, wraplength=550).pack(anchor="w", pady=8)
            ttk.Label(tab, text="Vídeos AVI MJPEG e imagens PNG/JPG em Vídeos\\NEXUS PC Remote.\n"
                      "Gravação sem áudio, com duração e FPS indicados no arquivo.",
                      wraplength=520).pack(anchor="w", pady=5)
        self.refresh()

    def options(self, kind):
        r, fps, q, mapping = self.controls[kind]
        width, height = (int(v) for v in mapping[r.get()].split("x"))
        return width, height, int(fps.get()), int(q.get())

    def apply_phone_profile(self):
        width, height, fps, quality = self.options("phone")
        self.set_phone_profile(width, height, fps, quality)
        self.statuses["phone"].set(
            "Perfil solicitado. O celular aplica FPS/qualidade na próxima consulta; "
            "a resolução real nunca supera a captura autorizada.")

    def start(self, kind):
        try:
            width, height, fps, quality = self.options(kind)
            if kind == "phone":
                self.apply_phone_profile()
            manager = PC_RECORDER if kind == "pc" else PHONE_RECORDER
            source = self.pc_capture if kind == "pc" else self.phone_frame
            path = manager.start(source, kind, width, height, fps, quality)
            self.statuses[kind].set("Gravando em " + path)
        except Exception as exc:
            messagebox.showerror("Gravação NEXUS", str(exc), parent=self.window)

    def stop(self, kind):
        manager = PC_RECORDER if kind == "pc" else PHONE_RECORDER
        state = manager.stop()
        self.statuses[kind].set(("Arquivo: " + state["path"]) if state["frames"] else
                               ("Nada gravado: " + state["error"]))

    def snapshot(self, kind):
        try:
            source = self.pc_capture() if kind == "pc" else self.phone_frame()
            if source is None:
                raise RuntimeError("Sem imagens recebidas: ative o espelhamento no celular")
            if isinstance(source, bytes):
                with Image.open(io.BytesIO(source)) as image:
                    picture = image.convert("RGB")
            else:
                picture = source.convert("RGB")
            target = unique_output(kind + "-print", "png")
            picture.save(target, format="PNG")
            self.statuses[kind].set("Print salvo: " + str(target))
        except Exception as exc:
            messagebox.showerror("Print NEXUS", str(exc), parent=self.window)

    def refresh(self):
        if not self.window or not self.window.winfo_exists():
            return
        if self.window.state() != "withdrawn":
            for kind, manager in (("pc", PC_RECORDER), ("phone", PHONE_RECORDER)):
                state = manager.status()
                if state["active"]:
                    self.statuses[kind].set("REC ●  " + str(state["frames"]) +
                        " quadros gravados | " + state["path"])
                elif state["error"]:
                    self.statuses[kind].set("Erro na gravação: " + state["error"])
            if self.preview:
                try:
                    frame = self.phone_frame()
                    if frame and frame != self.last_preview_frame:
                        with Image.open(io.BytesIO(frame)) as source:
                            photo = source.convert("RGB")
                        photo.thumbnail((460, 340))
                        self.photo = ImageTk.PhotoImage(photo)
                        self.preview.configure(image=self.photo, text="")
                        self.last_preview_frame = frame
                except Exception:
                    pass
        self.window.after(180, self.refresh)
