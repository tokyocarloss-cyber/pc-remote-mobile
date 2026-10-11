# Auditoria e recuperação — 2.0 beta 1

## Base preservada

Base: `full-redesign`, commit `8e6bc33`, com a PR #2 de estabilidade e sons 1.1.0. A branch de trabalho é `nexus-2-preserve-redesign`. `main`, `full-redesign`, `nexus-v2-stability-sound-20261011` e `nexus-mega-update-2-0-20261011` foram comparadas, junto com históricos, PRs e workflows disponíveis. Não havia release publicada na recuperação.

A tentativa anterior 2.0 removia mais de 6.500 linhas e substituía recursos avançados por uma implementação menor. Não foi mesclada. A recuperação mantém a navegação Compose, temas, biblioteca, radial, controles, Flow, Drop Edge e companion avançado. Os commits desta branch são incrementais e recuperáveis.

## Inventário e estado

| Área | Implementação preservada e alterações | Limites |
| --- | --- | --- |
| Navegação/temas | Dez temas, efeitos e transições existentes; sons curtos originais por tema | Identidade visual anterior preservada, sem redesenho completo de todas as telas |
| Mouse/teclado | Gestos, rolagem, atalhos, texto; movimentos fracionários agrupados | Precisão/latência física requerem teste |
| Biblioteca/radial | Atalhos, Steam, pasta extra, busca, favoritos, ícones, roda | Integrações completas Epic/Xbox/EA/Ubisoft não implementadas |
| Gamepad | Layouts e perfis existentes, fila serial, reset e watchdog; estado de driver | ViGEmBus externo; gatilhos continuam digitais; sem retorno de vibração |
| Tela | JPEG com proporção real, decode fora da UI, captura ociosa, alvos 30/60 FPS | Sem H.264/áudio, adaptação automática ou garantia de 60 FPS |
| Arquivos | Blocos, progresso, cancelamento, múltiplos envios, proteção de caminhos e arquivos parciais | Sem retomada; upload prepara cópia temporária; máximo 4 GB |
| Flow | Ações e recursos existentes; suspensão com confirmação | Bridge HTTP legado opcional, desativado por padrão |
| Companion | Painel, bandeja, autostart, logs e revogação por dispositivo | EXE sem Authenticode; hardware/driver exigem validação local |
| Autorização | TLS, pin de certificado confirmado, PIN temporário, tokens por dispositivo, DPAPI/Keystore | Não destinado a exposição na Internet |

## Defeitos corrigidos

- Rotas avançadas ignoravam a verificação de autorização do handler base. Todas agora verificam credenciais antes de arquivos, comandos e streaming.
- Uploads consumiam memória proporcional ao arquivo e podiam deixar resultados incompletos. Agora gravam blocos em temporários exclusivos, com tamanho verificado e publicação atômica.
- A navegação de arquivos podia alcançar áreas de credenciais. Raízes permitidas são resolvidas e verificadas, incluindo symlinks.
- Gamepad podia manter estado após sair/perder contato. Reset ao sair/cancelar e watchdog de dois segundos limitam estados presos.
- Touchpad perdia movimentos pequenos e criava trabalho concorrente por evento. Acumulador preserva frações e agrupa pacotes.
- Mapeamento de toque assumia proporção fixa. Passou a usar dimensões do frame.
- Captura continuava consumindo recursos sem espectador. Agora entra em espera após inatividade.
- Sons eram suscetíveis a sobreposição. SoundPool com uma voz, debounce e silenciamento configurável substitui geração concorrente.
- Erros de envio de texto e comandos podiam apresentar sucesso aparente. Resultado da requisição é propagado.
- Entrada de inicialização Windows tinha caminhos/aspas problemáticos; instalador vgamepad bloqueava CI esperando confirmação do driver. Corrigidos comando de startup e instalação não interativa da biblioteca, sem instalar driver silenciosamente.
- Builds Android tinham referências Gradle e casts de Activity rejeitados por lint. Corrigidos e compilados.

## Duplicação e legado

`NexusV2Activity` é a entrada atual. Activities e componentes antigos permanecem para compatibilidade, sem serem exportados como entradas externas. Não foi feita exclusão em massa de telas antigas. Os entrypoints Windows antigos delegam ao companion avançado para não iniciar um servidor HTTP antigo. O bridge HTTP do celular é uma opção de compatibilidade separada, com aviso explícito; não oferece a proteção do canal principal.

## Evidência de testes

- Windows: 14 testes HTTP/segurança/TLS/DPAPI passaram no runner Windows. Cobrem PIN, revogação, autenticação das rotas, Origin, limites de caminhos, nomes, concorrência e interrupção de upload. Entrada nativa usa mocks e não comprova mouse/XInput físicos.
- Linux: 13 testes passam, um teste DPAPI é ignorado explicitamente.
- Android: quatro testes JVM de pin e acumulador; compilação debug e lint passaram. Avisos de lint herdados ainda existem.
- EXE: PyInstaller compilou; executável empacotado executou `--self-test`, incluindo criação de TLS, imports e recursos básicos. Isso não comprova todos os fluxos gráficos.
- Emulador API 35: teste automatizado de temas, navegação, orientação e pareamento TLS foi adicionado. Consulte o resultado da execução correspondente ao commit: uma tentativa encontrou ANR do Pixel Launcher do emulador, documentado nas capturas. Não confundir teste criado com teste aprovado.

Os relatórios e capturas ficam nos artefatos de Actions. O APK disponível sem secrets de release é assinado para debug. Não houve teste físico no celular/PC do proprietário; siga [TESTE-NO-APARELHO.md](TESTE-NO-APARELHO.md). Esta entrega é beta, não conclusão de todas as funcionalidades solicitadas.
