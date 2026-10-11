# NEXUS PC Remote — Atualização de estabilidade 1.1

Esta é a continuação da versão **full-redesign**, preservando as dez skins, biblioteca de jogos, Flow, controle avançado, tela remota, transferência de arquivos e companion Windows.

## O que mudou
- Sons: um gerador de áudio reutilizado substitui a criação de uma thread e dispositivo de som a cada toque. Isso diminui overhead e reduz sons truncados sob toques rápidos.
- Sons: cliques rápidos são limitados para evitar sobreposição excessiva; agora há controle de volume em Configurações > Skins completas > Volume dos efeitos.
- Som de confirmação quando o Android conecta ao PC.
- Servidor Windows: bloqueio de pedidos originados fora de redes locais privadas e de requisições com Origin (evita chamadas diretas de sites).
- Servidor: validação de teclas e botões do mouse, limites para deslocamento e rolagem, verificação do tamanho da requisição.
- Arquivos: downloads e outbox são transmitidos em blocos, em vez de carregar o arquivo todo na memória.
- Atualização da versão Android para 1.1.0, mantendo compatibilidade com a instalação antiga.

## Download para testes
Abra a seção [Actions do repositório](../../actions). Procure uma execução verde de **Build Android APK** e **Build Windows Companion** na branch nexus-v2-stability-sound-20261011. Os arquivos estão em **Artifacts**.

Execute o aplicativo Windows e conecte o Android ao mesmo roteador (Wi-Fi ou cabo + Wi-Fi). Para abrir a versão nova no Android, instale o APK mais recente.

## Limitações e cuidados
- Este projeto requer teste no seu aparelho e PC reais. Uma compilação bem-sucedida não garante que todas as funcionalidades funcionem em todos os drivers ou redes.
- A descoberta e o pareamento da versão 1.1 ainda não oferecem autenticação criptográfica por dispositivo. Use somente em rede local confiável e nunca encaminhe as portas 8765/8766 para a Internet.
- Acesso a controle XInput depende de driver e ambiente Windows adequados.
- Taxas altas de streaming dependem do hardware, rede e modo de captura. O aplicativo não garante 120 FPS em todas as configurações.

## Estrutura
- Android Kotlin/Compose na pasta app.
- Companion PyInstaller em windows/companion_v2.py.
- Servidor HTTP em windows/pc_remote_server.py.
- Efeitos dos dez temas em app/src/main/java/com/pcremote/mobile/NexusThemeEffects.kt.
