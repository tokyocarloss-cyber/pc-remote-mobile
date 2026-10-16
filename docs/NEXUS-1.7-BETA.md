# NEXUS 1.7 beta — integração de arquivos, roda e áudio bidirecional

Base: NEXUS 1.6 (PR #9). Todas as alterações estão em branch independente,
sem sobrescrever uma versão que o usuário já utiliza.

## Alterações implementadas

### Android
- **Roda de apps:** corrigido posicionamento dos 8 itens. Box já alinha seus
  filhos ao centro; offsets de ícones eram calculados como relativos ao canto,
  deslocando toda a roda.
- **Arquivos:** nova área com 3 abas: **Celular**, **PC**, **Enviar**. Explorador
  Android real por Storage Access Framework (`OpenDocumentTree`) com navegação
  em subpastas, pesquisa, abertura de arquivos e envio ao PC por streaming
  (não carrega o arquivo inteiro na memória). O acesso é limitado à pasta
  selecionada e suas subpastas, conforme regras e permissões do Android.
  O explorador PC incorpora o NEXUS Flow existente, inclusive downloads.
- **Áudio Windows:** slider sincronizado com o volume real, silêncio/reativar e
  player de mídia; atualizações no arrasto agrupadas em ~80 ms.
- **Controle do celular:** novo menu opcional. O usuário informa o código
  NEXUS Central e ATIVA o serviço antes de permitir alterações de volume
  a partir do PC. Botão PARAR permanente na notificação.
- **Notificações Android:** acesso separado nas configurações do sistema,
  envio de novas notificações somente se o serviço de controle estiver ativo
  e o usuário ativar o compartilhamento no NEXUS.

### Windows
- Integração Core Audio (pycaw/comtypes): obter volume real, configurar
  percentual de 0 a 100 e silenciar sem atalhos de teclado.
- Painel local **Controle do celular (volume e notificações)** com slider,
  silêncio/ativar som e até 30 notificações em memória.
- Fila de ações permitidas (somente volume/mute), consumida pelo Android via
  conexão de saída autenticada, sem comando shell nem controle oculto.
- Testes Windows de volume e validação de comandos.

## O que continua pendente e por quê

- **Navegar arquivos do Android dentro da janela do PC:** o antigo botão usa
  um servidor `8767` opcional/inativo; não foi anunciado como consertado.
  O novo navegador Android funciona localmente e pode enviar arquivos para
  Windows; o Windows→Android ainda exige autorização de pasta e um protocolo
  autenticado para listar/transferir arquivos sem expor a memória do celular.
- **Toques no Android pelo PC:** a visualização em MediaProjection não equivale
  a injetar toques. Uma futura implementação teria de solicitar permissões
  adequadas de maneira explícita, oferecer controles de segurança e validar
  compatibilidade.
- **HTTPS/TLS:** esta branch herdou uma API local HTTP, inclusive o código
  Central transitando em HTTP. Usar apenas Wi-Fi privado de confiança.
  **Não expor portas à Internet nem publicar na Play Store ainda.**
- Áudio e gravador H.264 acelerado, redução de peso AVI, testes físicos de
  consumo, latência e compatibilidade estão pendentes.
- Os 10 temas, telas de Cinema/Jogos, streaming e gravação existentes na 1.6
  não foram removidos intencionalmente, mas exigem regressão física.
- O acesso SAF não permite automaticamente pastas protegidas pelo Android.
  Nenhuma permissão ampla de arquivos foi adicionada.

## Testes físicos necessários

1. Android: selecionar pasta `Documents`, entrar em 3 subpastas,
   navegar para trás, pesquisar, abrir e enviar arquivo 50+ MB.
2. Android: testar troca de abas PC/Celular/Enviar, navegação PC no Flow,
   download PC→celular e reconexão.
3. Biblioteca: abrir roda no A56 em retrato e paisagem, confirmar 8 itens
   uniformemente distribuídos ao redor do centro sem cortes, alternar páginas.
4. Áudio Windows: deslizar 0–100% repetidamente, validar volume real no mixer,
   silenciar e reativar; ausência de dispositivo deve ser informada.
5. Painel PC: ativar/desativar controle no Android, volume/mute, perda de Wi-Fi,
   verificar que não executa comandos depois de desativado.
6. Notificações: autorizar/desautorizar no Android, confirmar que não envia
   textos sem ambos os consentimentos ativos; verificar armazenamento só em
   memória e nenhum evento quando desligado.

## Build
- Android: `gradle assembleDebug`
- Windows: `python -m unittest discover -s tests -v` e workflow PyInstaller
- Leia também `docs/RECORDER-1.6-BETA.md` e `docs/STREAMING-1.5-BETA.md`.
