# NEXUS PC Remote — Mega Update 2.0

Aplicativo Android + companion para Windows, com código-fonte neste repositório.

## Novidades implementadas
- Autenticação na rede local: o Windows mostra um **PIN de seis números** para parear; o Android salva o token do dispositivo.
- Reconexão, status do computador, erros visíveis.
- Mouse com movimento contínuo, clique esquerdo/direito, rolagem, teclas direcionais.
- Mídia, volume, teclado remoto Unicode, atalhos e energia com confirmação.
- Lançamento de programas selecionados pelo servidor Windows.
- Captura de tela JPEG periódica com qualidade ajustável.
- Upload/download de arquivos de até 512 MB, progresso, validação de nomes e prevenção de sobrescrita.
- Sons de clique, sucesso e erro com interruptor em Ajustes.
- Botões digitais XInput opcionais quando vgamepad e ViGEmBus estão disponíveis.
- Janela Windows mostrando IP, PIN, status e pasta de arquivos.

## Instalação no Windows
1. Baixe o artefato **NEXUS-PC-Remote-Windows-2.0** de uma execução bem-sucedida das [GitHub Actions](../../actions/workflows/build-apk.yml), descompacte e abra o EXE.
2. Alternativamente, com Python 3.11+, instale Pillow pelo comando: py -m pip install pillow. Depois, na pasta windows, execute py nexus_desktop.py.
3. Permita acesso do NEXUS a **redes privadas** no Firewall do Windows, quando solicitado.
4. Anote o IPv4 e PIN da janela. Mantenha o companion aberto.

## Instalação no Android
1. Baixe o artefato **NEXUS-PC-Remote-Android-2.0** na mesma página de Actions e instale o APK.
2. PC e celular precisam estar na mesma rede Wi-Fi.
3. Em **Ajustes**, digite o IPv4 do PC e o PIN de seis dígitos. Toque em **Parear e conectar**.
4. Use as abas **Início**, **Mouse**, **Controle**, **Apps**, **Tela** e **Arquivos**.

## Controle virtual para jogos
O controle de jogos usa XInput opcional. No Windows, instale o driver ViGEmBus de fonte confiável e, com Python, execute: py -m pip install vgamepad. Reinicie o NEXUS. A/B/X/Y, direcional, LB/RB e Start/Back são botões digitais. **Analógicos e gatilhos proporcionais ainda não foram implementados.** Sem o driver, a interface informa que o módulo está indisponível.

## Limites conhecidos
- Tela ao vivo usa HTTP/JPEG periódico (~1–2 quadros/segundo dependendo da rede), não transmite 60/120 FPS, áudio do PC nem substitui Moonlight/Sunshine.
- Mouse é relativo, ainda sem toque por coordenada absoluta na própria tela transmitida.
- Apps usa lista permitida; menu radial, descoberta de todos os programas, 10 temas completos, overlay invisível, analógicos e serviço minimizado na bandeja ainda não estão incluídos.
- O companion deve permanecer aberto, com sessão Windows desbloqueada, e o firewall deve autorizar a rede privada.
- HTTP é restrito por token mas não criptografado: não usar Wi-Fi não confiável e **não expor porta 8765 à Internet**.

## Desenvolvedores
- Pasta app/src/main/java/com/pcremote/mobile/: Kotlin + Jetpack Compose.
- windows/pc_remote_server.py: API autenticada na LAN.
- windows/nexus_desktop.py: interface Windows usando Tkinter.
- .github/workflows/build-apk.yml: testes, build APK e EXE.
- windows/tests/: regressões de autenticação, arquivos e validação de comandos.

Para rodar testes: python -m unittest discover -s windows/tests -v
