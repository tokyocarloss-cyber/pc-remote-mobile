# Teste físico — Android + seu Windows

Antes de substituir sua instalação, mantenha a versão anterior disponível. Esta versão é beta.

1. **Pareamento:** instale APK e EXE correspondentes. Abra o PIN no PC, compare os certificados e conecte. Feche/reabra o Android: deve reconectar sem novo PIN. Revogue o dispositivo no PC: comandos e tela devem parar; só um novo pareamento recupera acesso.
2. **Reconexão:** desligue o Wi-Fi por 15 segundos e ligue novamente. Confirme o estado no cabeçalho antes de controlar o PC. Repita após suspender/acordar o Windows.
3. **Mouse e teclado:** mova lentamente na diagonal, clique, duplo clique, toque longo, rolagem de dois dedos e pinça. Envie texto com `ação, coração, ç, 😀` para o Bloco de Notas. Verifique Tab, Backspace, Enter e Esc quando disponíveis.
4. **Gamepad:** com driver instalado, abra `joy.cpl`. Segure A, gire a tela, abra outro aplicativo e desligue Wi-Fi: nenhuma entrada deve ficar presa por mais que o timeout de segurança. Teste os dois analógicos e perfis antes de jogar. O teste automatizado usa um dispositivo simulado e não comprova o driver.
5. **Tela:** em diferentes proporções de monitor (por exemplo, 1440×900), toque nos quatro cantos da imagem e no centro. As barras vazias não devem gerar cliques. Comece em HD/30 FPS; observe o FPS efetivo, CPU e qualidade da rede antes de aumentar para 60.
6. **Arquivos:** envie arquivos de 0 bytes, 10 MB e 500 MB, inclusive nomes com acentos e `+`. Envie dois nomes iguais. Cancele e desligue Wi-Fi no meio: o PC não deve mostrar o arquivo como concluído. Receba um arquivo pelo Drop Edge do PC e confira tamanho/hash, não só a notificação.
7. **Interface:** abra as dez telas, gire o aparelho, abra o teclado e alterne os dez temas. Teste a roda radial, favoritos, filtros e edição do controle. Ajustes antigos devem continuar disponíveis.
8. **Som:** ouça Tema/Suave/Retrô, volume zero, pré-escuta e silêncio com música tocando. Faça toques rápidos e verifique se não há fila de sons ou corte incômodo.
9. **Windows:** feche a janela, reabra pela bandeja, habilite/desabilite autostart e use Sair. Confirme que a porta 8765 deixa de responder após Sair. Não teste desligamento com documentos sem salvar.

Ao relatar falha: informe tela, tema, Android, Windows, passos exatos e horário. O log local fica em `%LOCALAPPDATA%\NEXUS\nexus.log`. Não compartilhe a pasta `security`.
