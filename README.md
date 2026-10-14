# NEXUS PC Remote 1.3 — Bibliotecas independentes e fila de downloads

Esta branch preserva as funções anteriores do NEXUS e corrige a arquitetura do Cinema e dos Jogos: **o catálogo pode ser usado no celular sem o PC ligado ou pareado**.

## Uso no celular, com PC desligado
- **Jogos > Steam:** busca diretamente pelo Android usando o catálogo público da Steam; é possível salvar favoritos e deixar um pedido de instalação na fila.
- **Jogos > SteamRIP:** pesquisa páginas públicas diretamente pelo Android e permite abrir o site no **navegador do celular**. Há opção separada de abrir a página no PC depois; esta fonte **não** inicia downloads ou executa cracks automaticamente. Se o site bloquear a busca automatizada, use o botão "Pesquisar SteamRIP no celular".
- **Cinema > Filmes:** busca informações, cartazes e links da iTunes Search API diretamente do Android.
- **Cinema > Séries:** busca informações do TVMaze diretamente do Android.
- **Favoritos:** ficam salvos na memória do telefone e podem ser abertos sem rede (capas dependem de estarem no cache do sistema).
- **Última biblioteca do PC:** jogos e vídeos detectados durante a última sincronização são salvos no Android, para consulta sem PC. O app não pode conhecer arquivos de um PC com o qual nunca se conectou.
- **Fila:** tarefas ficam armazenadas no Android mesmo quando o computador está desligado. A fila inclui pedidos de instalação via Steam, downloads autorizados de vídeo do Internet Archive e abertura de links/páginas no Windows. O celular não armazena os arquivos pesados que serão baixados pelo PC.
- Uma pesquisa nova exige Internet no celular, mas **não** exige conexão com o PC. Resultados de buscas feitas anteriormente podem ser recuperados da memória do app quando a Internet não está disponível.

## Sincronização com Windows
1. No primeiro pareamento, abra no Windows: bandeja do NEXUS → **Mostrar código NEXUS Central**. Copie ou digite uma única vez na configuração opcional de PC dentro de Cinema/Jogos.
2. Depois, você pode escolher jogos e vídeos mesmo com o PC desligado. O app tenta sincronizar enquanto essas telas estão abertas e, em segundo plano, o Android agenda verificações pelo WorkManager a cada ~15 minutos (a execução exata depende do sistema e suas restrições de bateria/rede).
3. Quando encontra o companion no endereço de rede previamente salvo, envia cada tarefa **uma vez**; o Windows mantém recibos de IDs da fila para evitar repetir instalações e comandos após reconexões.
4. Se o endereço IP do computador mudar, pode ser necessário abrir o NEXUS no celular para ele descobrir o PC novamente.
5. **Enviado ao PC** significa que o Windows aceitou a tarefa, não que download ou instalação terminaram. A Steam pode pedir login, licença ou confirmação. O computador precisa estar ligado e com o companion em execução para começar as operações.

## Limitações e segurança
- Não há servidor na nuvem, então um PC desligado não pode receber comandos na hora; a **fila fica no celular** até uma sincronização local posterior.
- A busca por filmes e séries retorna metadados, **não disponibiliza automaticamente vídeos protegidos por direitos autorais**. Downloads diretos permitidos ficam restritos a vídeos de `archive.org/download/`; verifique as permissões do item antes de baixar.
- A SteamRIP é um site de terceiros, sujeito a bloqueios de busca; não há instalação ou download automático dessa fonte. **Não desative o antivírus** para executáveis desconhecidos.
- A comunicação com o PC continua sendo HTTP na LAN e as funções legadas têm limitações de autenticação. **Use somente em redes locais confiáveis; não exponha as portas do NEXUS à Internet**.
- Os dados locais persistem enquanto o armazenamento do app não for limpo ou desinstalado. A sincronização não substitui backup.
- Builds automatizadas validam a compilação, mas são necessários testes em aparelhos reais.

## Arquivos
- Android: `app/src/main/java/com/pcremote/mobile/EntertainmentData.kt` (catálogos, cache, fila e WorkManager), `EntertainmentHub.kt` (interface).
- Windows: `windows/entertainment_hub.py` (dispatcher idempotente e downloads) e `windows/companion_v2.py` (rotas autenticadas).

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
