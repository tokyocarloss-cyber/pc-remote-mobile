# NEXUS PC Remote — 2.0 beta 1 (desenvolvimento incremental)

Continuação da **full-redesign / 1.1.0**, sem substituir as telas avançadas pela tentativa simplificada da 2.0. Android Kotlin/Compose + companion Windows Python/PyInstaller.

Esta é uma versão beta para validação no aparelho. Compilação e testes de protocolo não comprovam latência de jogos, drivers ou captura no seu PC.

## Instalação

1. Em [Actions](../../actions), abra as execuções da branch `nexus-2-preserve-redesign`. Baixe `NEXUS-PC-Remote-Android-TEST` e `NEXUS-PC-Remote-Windows` em **Artifacts** e extraia os ZIPs.
2. Instale o APK no Android 8 ou superior. O APK de testes usa assinatura debug; caso a instalação anterior use outra assinatura, anote seus ajustes antes de desinstalar. A assinatura de release só é produzida se as quatro secrets de assinatura já estiverem configuradas.
3. Feche o companion antigo e abra `NEXUS PC Remote.exe`. A janela mostra IP, conexão e dispositivos. Fechar a janela mantém o app na bandeja; **Sair** encerra o servidor.
4. Celular e PC devem estar na mesma rede. No Windows, use perfil de rede **Privado**. O botão Firewall solicita elevação e libera somente o perfil privado e a sub-rede local.
5. Clique em **Parear celular** no PC. No Android, compare o certificado completo exibido nos dois aparelhos, marque a confirmação e digite o PIN. Ele expira em dois minutos, aceita cinco tentativas e autoriza um celular.
6. Se a descoberta falhar, abra Configurações no Android e digite o IPv4 mostrado no PC. Não encaminhe as portas do roteador.

Depois do pareamento, a credencial é persistida no Android Keystore e a conexão verifica o certificado do PC. Para remover acesso, use **Revogar selecionado** no painel ou **Dispositivos autorizados / revogar** na bandeja. Para trocar um PC/certificado, use **Redefinir pareamento** no Android e repita a autorização.

## O que mudou

- TLS com certificado vinculado ao PC; chave privada e cadastro de dispositivos protegidos com DPAPI no Windows. Tokens são aleatórios por dispositivo, armazenados como hashes no servidor. Redirecionamentos de rede não recebem credenciais.
- Todas as rotas do companion avançado passam pela autorização, incluindo arquivos, tela e comandos. O navegador não pode usar essas APIs via Origin.
- Painel Windows com estado, PIN, certificados, dispositivos autorizados, Downloads, Firewall e início opcional com Windows. Logs rotativos em `%LOCALAPPDATA%\NEXUS\nexus.log`.
- Uploads por blocos, seleção múltipla, progresso dos bytes enviados, cancelamento, arquivos temporários e proteção contra nomes duplicados. Downloads sem carregar arquivos inteiros na memória. Arquivos incompletos não são publicados.
- Touchpad agrupa movimentos e preserva deslocamentos fracionários; gamepad serializa comandos e libera estados ao sair, cancelar gesto ou perder contato.
- Tela remota usa a proporção do frame recebido para mapear toques. JPEG é decodificado fora da thread principal. Captura fica ociosa quando não há visualização. Opções de FPS limitadas a alvos de 30/60, com contador real.
- Dez temas preservados. Setenta efeitos curtos originais, gerados no build, com SoundPool, volume, pré-escuta, perfis Tema/Suave/Retrô e silêncio em jogos/mídia.
- Confirmação de suspensão no Flow; erros de comandos aparecem no app; envio de texto usa o resultado real da requisição.
- Compilação com lint, testes JVM, testes HTTP/TLS em Windows e testes de navegação/pareamento em emulador.

## Gamepad

A biblioteca vgamepad é incluída no EXE. O driver ViGEmBus compatível deve estar instalado separadamente; o NEXUS não o instala silenciosamente. Use a origem oficial [nefarius/ViGEmBus](https://github.com/nefarius/ViGEmBus/releases), nunca sites com nome parecido. Reinicie o companion após instalar o driver. A ausência do driver é mostrada no PC e no Android. A emulação XInput e a vibração precisam de teste físico; retorno de vibração do jogo ainda não está implementado.

## Arquivos e permissões

- Recebidos no PC: Downloads. No Android 10+: Downloads/NEXUS. No Android 8/9: diretório de Downloads privado do aplicativo.
- Navegação remota no PC limitada a Desktop, Downloads, Documents, Pictures, Videos e Music. Não inclui AppData, credenciais ou pastas fora dessas raízes.
- Upload máximo de 4 GB; o Android prepara uma cópia temporária em disco para obter tamanho confiável, então precisa de espaço livre. Cancelamento durante bloqueio de rede pode aguardar o timeout. Retomada de arquivos interrompidos não implementada.
- O **Bridge legado por navegador** foi preservado como opção desativada por padrão. Ele usa HTTP e controle por IP, não a segurança TLS do canal principal. Ative somente em rede confiável e compartilhe apenas a pasta escolhida no Flow. Não é necessário para as transferências normais.

## Desenvolvimento e limites

Veja [AUDIT-2.0.md](docs/AUDIT-2.0.md) e [TESTE-NO-APARELHO.md](docs/TESTE-NO-APARELHO.md). Streaming continua JPEG, sem áudio do PC, codec H.264, 120 FPS garantidos ou adaptação automática de bitrate. A biblioteca detecta atalhos, Steam e a pasta extra existente; não há scanner completo para todos os launchers. Não existe assinatura Authenticode do EXE.

Build Android: JDK 17, Android SDK 35 e Gradle 8.9; execute `gradle testDebugUnitTest lintDebug assembleDebug`. Os efeitos WAV são gerados automaticamente.

Build Windows: Python 3.12 e `windows/build_companion.bat`. O workflow também executa o EXE empacotado em modo de diagnóstico. Para testes de API: `cd windows` e `python -m unittest discover -s tests -v`. Em Linux, o teste de DPAPI é explicitamente ignorado; os testes não simulam sucesso de entrada nativa.

## Experimentos de interface — PR #5 (somente branch de trabalho)

Estas funções **exigem o Android e o companion Windows desta mesma branch**. Não estão incluídas na beta anterior nem são consideradas concluídas até os testes no celular/PC.

- **Teclado ao vivo:** abra um campo no Windows (posicione o cursor), então digite em NEXUS > Teclado. Inserções, caracteres Unicode e apagamentos são transmitidos sem um botão Enviar. O campo local é um histórico de edição, não sincroniza a posição do cursor/seleção do Windows; use Limpar campo para começar outro trecho. Atualizações IME complexas e múltiplas telas precisam de teste físico.
- **Arquivos:** use NEXUS > Arquivos > Celular. Para mostrar as pastas de memória interna compartilhada diretamente no NEXUS, conceda manualmente a permissão especial de gerenciar arquivos nas Configurações do Android. É uma permissão ampla, opcional e revogável; não elimina restrições do Android para dados privados. Se não conceder, use o seletor do sistema para enviar arquivos pontuais. A aba PC permite navegar e baixar dos diretórios compartilhados do Windows; Enviar usa o fluxo de transferências antigo.
- **Espelhamento Android para PC:** em NEXUS > Mais > Tela celular, toque Iniciar, aceite o diálogo de gravação de tela do Android e abra NEXUS no Windows > Ver tela do celular. A transmissão passa pela conexão TLS pareada existente; só inicie em redes confiáveis. Para parar, use o botão no NEXUS Android ou PARAR na notificação do serviço. A captura pode mostrar dados sensíveis, portanto evite compartilhá-los. Proteções do Android podem bloquear imagens de apps restritos.
- **Limites do espelhamento:** protótipo JPEG na rede local a cerca de 6 quadros/s, sem áudio e sem controle reverso do Android. Não equiparar a scrcpy nem a streaming de jogos. Girar o aparelho durante a sessão pode exigir interromper e iniciar a projeção novamente.
- **Atalhos de transmissão:** na tela do PC transmitida ao Android, toque duas vezes no botão 2 TOQUES no canto superior direito para mostrar/ocultar atalhos.
- **Navegação:** barra inferior reduzida a Início, Roda, Arquivos, Teclado e Mais. Mouse, Controle, Apps, Tela PC, Tela celular, Mídia e Configurações ficam em Mais ou nos atalhos da tela inicial.

**Antes da distribuição:** validar compilação Android/Windows, permissões MediaProjection no Android 14+, configuração multi-toque, pausa/encerramento do espelhamento, transferência de arquivos grandes, e limite real de latência no aparelho. O pedido de `MANAGE_EXTERNAL_STORAGE` deve passar por revisão da política da loja na distribuição; para builds fora da loja, a autorização continua manual.

