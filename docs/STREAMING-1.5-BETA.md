# NEXUS 1.5 beta — Streaming e resposta aos comandos

Atualização incremental em cima de `nexus-entertainment-hub-20261012`; mantém a interface, as abas e a biblioteca desse ramo. Não mesclar no `main` sem reconciliar as outras PRs.

## Mudanças efetivamente implementadas

- **Android**: transferência de frames e decodificação JPEG separadas; decodificação em `Dispatchers.Default`, publicação na UI na thread principal; canal conflated de capacidade efetiva 1 descarta quadros antigos se a CPU/GPU do celular não acompanhar a rede.
- **Android**: cancelamento fecha o HTTP stream, evitando sessões antigas após trocar FPS/qualidade; valida limite do tamanho de cada JPEG; métricas medidas no aparelho em vez de repetir o FPS escolhido.
- **Android**: toque absoluto passa a usar **aspect ratio real da imagem recebida**; relevante para monitores fora de 16:9 (ex.: 1440x900).
- **Android**: touchpad agrupa deslocamentos fracionários, envia movimentos em ordem num único fluxo e evita a explosão de requisições por evento.
- **Windows**: captura com MSS + Pillow tenta BGRX direto para reduzir conversões extras; monitora FPS de captura, tempo de captura+codificação, tamanho enviado e idade do último quadro.
- **Windows**: transmissor aguarda nova captura com `Condition`, usa `TCP_NODELAY` e envia sempre o frame disponível mais recente (não cria fila de frames antigos).
- **Windows**: `/stream/status` reporta `actual_capture_fps`, `encode_ms`, dimensões da imagem produzida, FPS solicitado, qualidade JPEG e idade do último frame.
- **Windows**: os caminhos avançados também fazem checagem de endereço de rede local e bloqueio de origem de navegador.
- Testes Windows de qualidade JPEG efetiva, dimensões e estatísticas. Builds automatizados disparados por PR.

## O que os controles de qualidade fazem (e o que não fazem)

- HD / Full HD / Full HD+ configuram limites de resolução na captura do Windows. A imagem respeita a proporção da tela e pode ter dimensões inferiores ao limite selecionado.
- Os valores JPEG 55/68/78 afetam a compressão no Windows. Não são apenas temas de interface.
- 30/60/120 FPS são **tetos solicitados**, não resultados garantidos. O JPEG por CPU, o Wi‑Fi, a resolução e o desempenho do Android podem baixar a taxa real. A UI mostra FPS recebido e Mbps reais, e o endpoint Windows mostra FPS de captura e tempo de processamento.
- O pipeline ainda é **JPEG por HTTP**, não H.264/HEVC com aceleração de hardware. 1080p/60, 1440p/120 e latência de jogos dependem de arquitetura futura de codec, medições reais e hardware; esta beta **não garante** esses resultados.

## Verificação antes do release

1. Atualizar **Android e companion Windows** a partir da mesma revisão; versões antigas não expõem as novas métricas e podem ter desempenho diferente.
2. Confirmar permissões de rede e que celular/PC estão na mesma rede, preferencialmente PC via Ethernet e celular via Wi‑Fi 5 GHz.
3. Medir a tela do NEXUS por 1 minuto em HD 30, HD 60, Full HD 60 e Full HD+ 120; registrar FPS real, Mbps, FPS de captura, tempo de codificação, travamentos e latência toque→cursor.
4. Desconectar/reconectar Wi‑Fi, alterar resolução/FPS durante a transmissão, sair da tela, girar o celular e voltar. Confirmar que sessão anterior é encerrada.
5. Confirmar que touchpad, clique, rolagem, controle virtual, filmes/séries e envio de arquivos continuam funcionais.
6. Só liberar versão de produção depois de builds Android/Windows passarem no CI e de testes manuais em hardware Windows/Android.

**Segurança importante**: este ramo conserva a API LAN legada sem autenticação criptográfica completa para todos os comandos remotos. Não expor a porta TCP 8765 à Internet. Não considerar pronto para Play Store nem uso em redes não confiáveis até integrar/validar o pareamento seguro (existente em outra branch) com a interface e o entretenimento deste ramo.

## Próximas melhorias necessárias

- H.264 por encoder de hardware no Windows + MediaCodec no Android, com codec fallback e sincronização/telemetria de ponta a ponta.
- Canal de entrada de baixa latência independente do vídeo, com autenticação de dispositivo e priorização de comandos.
- Testes físicos com medições de frame-age/RTT/perda, alternância de redes, benchmarks reais e profiling de memória/bateria.
