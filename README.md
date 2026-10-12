# NEXUS PC Remote — Entertainment Hub 1.2 (teste)

Esta branch continua `full-redesign` e acrescenta **Cinema** e **Jogos** sem remover o controle de mouse, teclado, arquivos, streaming ou a biblioteca existente.

## Como testar no Android + Windows
1. Instale o APK de teste e o novo companion EXE produzidos em **Actions** nesta branch (ou na pull request).
2. Abra o NEXUS Windows e conecte o celular à **mesma rede privada confiável**.
3. No ícone do NEXUS na bandeja do Windows, clique **Copiar código NEXUS Central**.
4. No Android, abra **Cinema** ou **Jogos**, cole o código e clique **Conectar Central**.

## Cinema
- **Filmes**: pesquisa metadados na iTunes Search API. Capas, sinopse e link informativo na Apple TV / iTunes. Não fornece o filme completo.
- **Séries**: pesquisa metadados no TVMaze. A página do TVMaze é informativa, não a reprodução de episódios.
- **Meus vídeos**: examina até 160 arquivos com extensões de vídeo na pasta Vídeos do Windows e na pasta `Vídeos/NEXUS`. O botão **Reproduzir no PC** abre o vídeo no player padrão do Windows.
- **Downloads**: aceita apenas URLs diretas HTTPS de vídeos com início `https://archive.org/download/`, destinadas a conteúdos que você está autorizado a baixar. O arquivo é baixado **pelo PC** para `Vídeos/NEXUS`. Fila de até 2 downloads simultâneos, progresso, cancelamento e limite de 10 GB por arquivo. Os resultados de pesquisas por filmes **não fornecem links de download**.

## Jogos
- **Pesquisar**: busca títulos públicos no catálogo da Steam e apresenta capa e nome.
- **Steam**: abre a página do jogo no cliente oficial.
- **Instalar**: envia `steam://install/<appid>` ao PC. A Steam decide se o usuário tem licença e conduz a instalação; não é download alternativo, não bypassa DRM.
- **Instalados**: lista jogos detectados pelo companion e permite iniciá-los no PC.

## Segurança e limites
- As novas rotas exigem chave aleatória persistida localmente no Windows, copiada voluntariamente pelo menu da bandeja; a chave é enviada em header pelo Android.
- A conexão ainda é HTTP local, sem criptografia TLS; use **apenas Wi-Fi/rede LAN confiável**. As outras rotas legadas do NEXUS continuam com as limitações de autenticação existentes.
- Downloads do Archive.org não garantem que toda obra tenha licença para redistribuição ou que esteja livre de direitos no Brasil. Verifique permissões antes de usar.
- Uma pesquisa por qualquer título pode mostrar metadados mesmo quando o conteúdo **não** estiver disponível para download.
- Search endpoints de Apple, TVMaze e Steam dependem de Internet e de serviços externos, que podem mudar ou limitar as requisições.
- O app não baixa jogos pirateados. SteamRIP não foi integrada. Fontes alternativas só devem ser adicionadas após avaliação de licenças e segurança.
- Compilação CI não substitui teste real no smartphone, PC e roteador.

## Versão anterior e estrutura
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
