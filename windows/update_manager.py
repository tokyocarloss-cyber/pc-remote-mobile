"""Explicitly staged local updates shared only over the paired NEXUS TLS session.

No URL fetching, arbitrary upload-to-execute, or unattended remote EXE execution.
A person on the Windows machine selects both installers first.
"""
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import threading
import time
import zipfile

UPDATE_ROOT = Path(os.environ.get('LOCALAPPDATA', str(Path.home()))) / 'NEXUS' / 'updates'
MAX_INSTALLER_BYTES = 250 * 1024 * 1024


def sha256_file(path):
    digest = hashlib.sha256()
    with Path(path).open('rb') as inp:
        for chunk in iter(lambda: inp.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def _validated_source(path, kind):
    src = Path(path).expanduser().resolve(strict=True)
    if not src.is_file() or not (1024 <= src.stat().st_size <= MAX_INSTALLER_BYTES):
        raise ValueError('Arquivo de atualização ausente ou tamanho inválido')
    if src.suffix.lower() != ('.apk' if kind == 'android' else '.exe'):
        raise ValueError('Escolha um APK para Android e um EXE para Windows')
    if kind == 'android':
        if not zipfile.is_zipfile(src):
            raise ValueError('O arquivo APK está corrompido')
        with zipfile.ZipFile(src) as package:
            if 'AndroidManifest.xml' not in package.namelist():
                raise ValueError('APK não contém manifesto Android')
    else:
        with src.open('rb') as inp:
            if inp.read(2) != b'MZ':
                raise ValueError('O EXE não é um executável Windows válido')
    return src


# Fixed, app-controlled script. No command string interpolation from Android.
# A detached PowerShell process waits until the one-file EXE exits before replacing it.
INSTALL_SCRIPT = r'''
param(
    [Parameter(Mandatory=$true)][int]$ProcessToWait,
    [Parameter(Mandatory=$true)][string]$TargetExe,
    [Parameter(Mandatory=$true)][string]$StagedExe,
    [Parameter(Mandatory=$true)][string]$ExpectedSha,
    [Parameter(Mandatory=$true)][string]$LogFile
)
$ErrorActionPreference='Stop'
try {
    $deadline = (Get-Date).AddSeconds(65)
    while ((Get-Process -Id $ProcessToWait -ErrorAction SilentlyContinue) -and (Get-Date) -lt $deadline) {
        Start-Sleep -Milliseconds 250
    }
    if (Get-Process -Id $ProcessToWait -ErrorAction SilentlyContinue) {
        throw 'NEXUS não encerrou; instalação cancelada'
    }
    if ((Get-FileHash -LiteralPath $StagedExe -Algorithm SHA256).Hash -ine $ExpectedSha) {
        throw 'Hash da atualização Windows não confere'
    }
    $backup = $TargetExe + '.nexus-backup'
    Copy-Item -LiteralPath $TargetExe -Destination $backup -Force
    try {
        Copy-Item -LiteralPath $StagedExe -Destination $TargetExe -Force
        if ((Get-FileHash -LiteralPath $TargetExe -Algorithm SHA256).Hash -ine $ExpectedSha) {
            throw 'Validação do novo EXE falhou'
        }
        Start-Process -FilePath $TargetExe
        'OK: Windows companion atualizado' | Out-File -LiteralPath $LogFile -Encoding utf8
    } catch {
        Copy-Item -LiteralPath $backup -Destination $TargetExe -Force
        throw
    }
} catch {
    ('FALHA: ' + $_.Exception.Message) | Out-File -LiteralPath $LogFile -Encoding utf8
    exit 1
}
'''


class UpdateManager:
    def __init__(self, root=UPDATE_ROOT):
        self.root = Path(root)
        self.lock = threading.RLock()
        self.bundle = None

    def prepare(self, android_apk, windows_exe):
        apk = _validated_source(android_apk, 'android')
        exe = _validated_source(windows_exe, 'windows')
        with self.lock:
            self.root.mkdir(parents=True, exist_ok=True)
            token = secrets.token_urlsafe(24)
            directory = self.root / token
            directory.mkdir(parents=True, exist_ok=False)
            try:
                selected = {}
                for kind, src, name in (
                    ('android', apk, 'nexus-android.apk'),
                    ('windows', exe, 'nexus-windows.exe')
                ):
                    dst = directory / name
                    shutil.copyfile(src, dst)
                    selected[kind] = {
                        'sha256': sha256_file(dst), 'size': dst.stat().st_size,
                        'file': dst,
                    }
                self.bundle = {
                    'nonce': token, 'expires_at': time.monotonic() + 3600,
                    'android': selected['android'], 'windows': selected['windows'],
                }
            except Exception:
                shutil.rmtree(directory, ignore_errors=True)
                raise
            return self.info()

    def _valid(self):
        b = self.bundle
        return b is not None and b['expires_at'] > time.monotonic()

    def info(self):
        with self.lock:
            if not self._valid():
                return {'ready': False, 'detail': 'Selecione APK e EXE no painel do Windows'}
            b = self.bundle
            return {
                'ready': True, 'nonce': b['nonce'],
                'expires_in_seconds': max(0, int(b['expires_at'] - time.monotonic())),
                'android': {k: b['android'][k] for k in ('sha256','size')},
                'windows': {k: b['windows'][k] for k in ('sha256','size')},
                'note': 'Pacote escolhido manualmente no PC. APK requer assinatura compatível.'
            }

    def android_file(self):
        with self.lock:
            if not self._valid():
                return None
            return self.bundle['android']['file']

    def begin_windows_install(self, nonce, android_sha, windows_sha):
        """Called only by an authenticated phone after an actual APK upgrade."""
        with self.lock:
            if not self._valid():
                raise ValueError('Pacote expirou ou não foi preparado no PC')
            b = self.bundle
            if not (
                secrets.compare_digest(str(nonce), b['nonce']) and
                secrets.compare_digest(str(android_sha), b['android']['sha256']) and
                secrets.compare_digest(str(windows_sha), b['windows']['sha256'])
            ):
                raise ValueError('Identificador ou hash da atualização inválido')
            if not getattr(sys, 'frozen', False):
                raise RuntimeError('Atualização automática do Windows requer o EXE empacotado')
            target = Path(sys.executable).resolve()
            if not target.is_file() or target.suffix.lower() != '.exe':
                raise RuntimeError('NEXUS não foi iniciado por um executável instalado')
            staged = b['windows']['file']
            if sha256_file(staged) != b['windows']['sha256']:
                raise ValueError('EXE de atualização alterado')
            script = self.root / ('apply-' + b['nonce'] + '.ps1')
            log = self.root / 'last-update.txt'
            script.write_text(INSTALL_SCRIPT, encoding='utf-8')
            # Start detached helper before stopping this executable.
            subprocess.Popen([
                'powershell.exe','-NoProfile','-NonInteractive','-WindowStyle','Hidden',
                '-ExecutionPolicy','Bypass','-File',str(script),
                '-ProcessToWait',str(os.getpid()),'-TargetExe',str(target),
                '-StagedExe',str(staged),'-ExpectedSha',b['windows']['sha256'],
                '-LogFile',str(log)
            ], creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0),
               stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
               close_fds=True)
            self.bundle = None
            return True
