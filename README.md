# NEXUS PC Remote 1.3 — Bibliotecas independentes e fila de downloads

Esta branch preserva as funções anteriores do NEXUS e corrige a arquitetura do Cinema e dos Jogos: **o catálogo pode ser usado no celular sem o PC ligado ou pareado**.

## NEXUS 1.3.2 — Navegador integrado e downloads direto no PC

- **Cinema e Jogos** possuem um botão **Navegador NEXUS · baixar no PC**, sem abrir o Chrome. As páginas de catálogo, SteamRIP e fontes comunitárias agora também abrem nesse navegador interno.
- Ao tocar num arquivo de download HTTPS compatível dentro do navegador, o Android registra uma tarefa `web_download` na **fila local** sem gravar o arquivo no armazenamento do telefone.
- Depois de um primeiro pareamento, quando o Windows estiver ligado e o companion disponível, o PC busca o arquivo do **servidor de origem** e grava em `Downloads/NEXUS`. O celular mostra o progresso disponível em **Cinema/Jogos > Fila** (enquanto conectado) e permite cancelar.
- Formatos aceitos: vídeos, compactados ZIP/RAR/7z/tar/gz/ISO, PDF e áudio. Não há execução automática, extração de arquivos ou desativação de antivírus.
- **Steam oficial**: jogos instalados pelo cliente Steam no PC e pela conta com licença, não por URL genérica.
- Nem todo site permite transferência direta: links sujeitos a login, cookies, CAPTCHA, páginas intermediárias, arquivos `blob:`, magnet links e downloads protegidos por JavaScript podem falhar. O app relata falha ao invés de fingir que concluiu.
- A fila aguarda o PC estar ligado, mas a transferência precisa da conexão Internet do próprio PC. O download web nesta versão não oferece retomada/resume após reiniciar o companion e a tela só consulta progresso dos jobs atuais.
- Downloads são arquivos de terceiros, não verificados ou seguros por definição. Não execute cracks ou programas desconhecidos. Instale somente arquivos cuja origem e licença você conhece.
- O dispatcher do Windows valida HTTPS público e bloqueia IPs locais/loopback e redirecionamentos privados para reduzir riscos de acesso indevido à rede interna. Não transforme o companion em serviço exposto à Internet.

## NEXUS Cinema 1.3.1 — Fontes oficiais + comunidade

A aba **Cinema > Fontes** reúne os dois tipos de serviços lado a lado, sem exigir pareamento:

| Categoria | Fonte | Função |
|---|---|---|
| Oficial | JustWatch Brasil | Descobrir onde assistir legalmente |
| Oficial | Pluto TV | Streaming gratuito com anúncios |
| Oficial | Apple TV | Streaming e loja oficial |
| Oficial | TVmaze | Informações sobre séries |
| Acervo | Internet Archive | Localizar conteúdo de arquivo; conferir direitos antes de baixar |
| Comunidade | Megathread Pirataria | Diretório de sites da comunidade lusófona |
| Comunidade | Torrent dos Filmes | Pesquisa pelo nome; vários domínios usam essa marca e **não foi confirmado um domínio oficial** |
| Comunidade | BTDig | Índice público DHT com busca por nome |
| Comunidade | EXT Torrents | Índice pesquisável por título |
| Comunidade | Hattered | Diretório comunitário de sites e ferramentas |

As fontes são apresentadas claramente como **links externos** em vez de resultados supostamente disponíveis. Você pode pesquisar ou abrir pelo navegador **do celular**, adicionar diretórios aos favoritos locais e agendar **No PC depois**. As opções `PT-BR dublado`, `Dual áudio`, `720p`, `1080p` e `4K` adicionam termos de pesquisa aos índices de terceiros, **não verificam nem garantem** idiomas, direitos, qualidade ou disponibilidade.

O app **não baixa automaticamente torrents, não transfere magnets ou arquivos protegidos para o PC e não desativa antivírus**. A fila somente solicita ao PC que abra as páginas permitidas no navegador. Os downloads automáticos já existentes continuam limitados a vídeos autorizados com URL direta do Internet Archive.

Os endereços de BTDig, EXT, Megathread e Hattered foram checados antes da integração. No caso de **Torrent dos Filmes**, sites com nomes parecidos aparecem na web; preferimos abrir uma pesquisa pelo nome sem afirmar que um espelho específico é oficial.

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
