# NEXUS 1.6 beta — gravações e capturas restauradas

Base: `nexus-streaming-low-latency-20261016`. Preserva Cinema, Jogos, streaming de PC,
mouse, mídia, Flow, gamepad, temas e a navegação principal da branch 1.5. Este
patch **não reúne automaticamente** todas as branches independentes anteriores:
funções de arquivos avançados, pareamento seguro e atualizador exigem integração
e validação posteriores.

## Adicionado nesta beta

- Tela `Celular → PC` no Android. O usuário inicia MediaProjection com o diálogo de
  consentimento do Android. Serviço de primeiro plano com aviso e botão PARAR.
- Windows: menu de bandeja `Gravar PC / Celular e tirar prints` abre painel local,
  com duas abas, preview da tela autorizada do Android, opções de resolução, FPS,
  qualidade, iniciar/parar gravação e print de ambas as fontes.
- `windows/recording_engine.py`: gravação AVI/MJPEG local (sem dependência de
  Game Bar, FFmpeg ou codecs extras). Gravação do PC usa captura própria independente
  do streaming da tela; gravação do celular usa quadros enviados explicitamente
  pelo Android; arquivo respeita orientação vertical quando aplicável.
- Gravações em `Vídeos/NEXUS PC Remote/NEXUS-pc-*.avi` e `NEXUS-phone-*.avi`.
  Prints de ambas as fontes em PNG na mesma pasta.
- O Android na tela do PC oferece botão GRAVAR/PARAR, PRINT e opções separadas
  de 720p,1080p,1440p, 15/30/60 FPS e JPEG 55/75/90.
- O PC envia configuração 540p/720p/1080p e 5/10/15/24 FPS ao serviço de
  espelhamento Android, que aplica alvo de dimensão máxima respeitando a
  proporção da tela e JPEG de qualidade configurável.
- Os atalhos de print anteriores (Recorte de tela do Windows) foram **preservados**,
  com adição de print PNG direto e REC. `/record-toggle` continua compatível,
  porém agora usa o gravador interno em vez da Game Bar.

## Limitações importantes

- **Beta não pronta para Play Store** e não garantida sem testes nos dois aparelhos.
- AVI/MJPEG sem áudio, com peso maior que H.264; 60 FPS e gravação 1080p dependem
  da CPU/disco/rede. FPS pedido é alvo; quadros podem ser repetidos para manter
  a duração do vídeo quando o encoder é lento. Não afirmar 60 FPS únicos reais.
- O streaming Android→Windows ainda é JPEG experimental, não oferece controle
  dos toques pelo PC e depende de MediaProjection autorizado. Para controlar
  efetivamente a tela do Android (toques, apps, volume, notificações), implementar
  permissões específicas com UI/consentimento e canais autenticados.
- Esta branch **ainda usa comunicação LAN legada sem token obrigatório para todas
  as rotas**. Não expor TCP 8765 na Internet ou em redes não confiáveis.
- Arquivos e biblioteca gamer deste ramo continuam na condição da branch base;
  não marcar funcionalidades de outra branch como integradas.
- Build automatizado não substitui benchmark físico e teste com 10+ minutos,
  orientações e rotações, mudança de perfil, captura em segundo plano,
  gravação longa e uso com Android 14/15.

## Autorização dos novos comandos

No PC use o menu de bandeja **Mostrar código NEXUS Central**. No Android,
insira esse código na tela `Celular → PC` e/ou no diálogo das opções de
gravação da tela do PC. O servidor exige `X-Nexus-Central-Key` nas rotas de
gravação, print remoto, envio de quadros e consulta do perfil. O painel local
do PC opera diretamente na aplicação e não requer a digitação do código.
O código trafega por HTTP local sem TLS neste ramo legado; **não** use redes
não confiáveis nem encaminhamento de portas à Internet.

## Roteiro de teste

1. Atualize **ambos** os binários 1.6, encerre o companion velho no tray.
2. Abra Windows > menu NEXUS > `Gravar PC / Celular e tirar prints`.
3. Grave PC em 720p/15 e 1080p/30 por 15s; faça print; verifique arquivos
   com player compatível AVI MJPEG e tempo correto.
4. Android > `Celular → PC` > conceder gravação de tela; esperar preview no Windows.
5. Alterar qualidade para 55/90 e FPS para 5/15/24 no Windows; medir quadros reais,
   latência e tráfego. Gravar 15s, tirar print, verificar imagem vertical no AVI.
6. Interromper espelhamento Android e confirmar que o painel deixa de exibir
   novos quadros; não tentar gravar celular sem consentimento.
7. Trocar orientação do Android e voltar, testar recursos antigos (bibliotecas,
   atalhos, mídia, volume, jogos, arquivos) antes de mesclar.
