# Plano Carolith (2026-10-28)

O nome do mod é **Carolith** (id planejado `carolith`). Este é o plano do Raphael, com as observações do Claude.
Regra de trabalho: antes de cada passo, uma entrevista curta. Com tudo definido, o Claude constrói até a próxima parada.

## Bugs vistos no teste do `ui-done`
- O scan quebra o preview: os blocos brancos somem do mundo.
- Depois do Scan, a vista do scan fica presa na tela e cortada.
- Farming land e outras formas não mostram o grid depois do scan.
- O scan Island corta a ilha, mesmo pequena.
- O scan Hole mostra estruturas abaixo, não o buraco. Ele deve mostrar os blocos de dentro como paredes e ter cortes (horizontal, vertical, em ângulo).

## Passos
1. **Limpeza:** rodar a skill "ponytail" (limpeza de código e otimização).
2. **Scan desligado** até ser consertado.
3. **A forma aparece no mundo ao ser clicada.** Substitui o checkbox antigo que ligava o grid no mundo.
   1. Uma tecla para tirar a forma do mundo, escrita na tela Place.
4. **Vista do mundo no estilo OrthoCamera.**
   1. Analisar o OrthoCamera e fazer uma versão análoga (sem copiar código), para ver a construção ou o grid de outro ângulo.
   2. Ao clicar em World, esconder as barras de propriedades (esquerda) e a da direita. A barra do BG continua.
   3. Um switch 3D/2D no canto superior direito do preview (2D é o padrão).
      1. 2D: a vista de cima de hoje e uma lateral. Ela deve ficar fiel ao mundo, com os blocos reais da estrutura.
      2. 3D: a câmera ortográfica a 45 graus da estrutura, com o resto do mundo, girando com o arrastar do mouse.
      3. Sem filtros nessa tela.
   4. Depois de implementar, pesquisar formas de otimizar esse preview (mods e técnicas parecidas).
   5. Homologação no perfil teste. Só instalar com o Minecraft fechado; com o Modrinth aberto pode.
   6. Publicação.
5. **Island: naturalizar e camadas.**
   1. Naturalizar disponível em Base, Body e Spikes.
   2. Um botão de naturalizar padrão em cada um.
   3. Tirar o limite de tamanho da ilha.
   4. Tirar os botões Randomize e Undo.
   5. Trocar as opções em texto por ícones (Bowl, Cone, Terrace...). Vale para todas as formas com escolhas assim, como Bridge.
   6. Spikes: tirar o limite estranho em formas grandes e pôr ruído no corte dos segmentos, para parecerem desencaixados. Pode ser uma variável.
   7. Base Square e Polygon começam com Corner round 0.2.
   8. Shell blocks padrão = 1.
6. **Sistema de layers.**
   1. Toda forma 3D tem layers.
   2. Um menu Layers no rail, abaixo de Scan e acima de Check.
      1. Até 10 layers, padrões de ruído, cores e a escolha de bloco num menu com todos os blocos do jogo, em grade de inventário com rolagem.
      2. Modos de relevo por forma, por exemplo:
         - Cuboid: uma layer por relevo, como uma caixa (com ou sem o X);
         - Sphere: de fora para dentro;
         - Cone: por altura.
      3. Formas naturais escolhem nas propriedades o critério das layers.
      4. O naturalizar entra aqui também; será detalhado quando chegarmos (layer e relevo trabalham juntos).
7. **Tweakeroo** no mod.
8. **Telas de Check** refeitas.
9. **Scan** consertado.
10. **Mais de uma forma** ao mesmo tempo.

## Observações do Claude
- **2 antes de 1:** o scan quebra o preview do mundo hoje. Desligá-lo leva minutos e deixa a limpeza rodar sobre um mod estável.
- **"ponytail" não está instalada.** Não há skill com esse nome nesta sessão; falta saber o que ela é ou de onde vem.
- **4, 3D:** o OrthoCamera troca a projeção da câmera do próprio jogo; ele não desenha um segundo mundo. Fazer igual (a câmera do jogador vira ortográfica enquanto a tela World está aberta) é muito mais leve que renderizar o mundo dentro do preview.
- **4.5:** o deploy hoje bloqueia também com o Modrinth aberto. A regra nova pede para ajustar o `deploy.ps1`: bloquear só o Minecraft.
- **5.5:** os ícones serão desenhados por nós, pequenos e em pixel. Imagens de sites como Flaticon têm direitos de uso.
- **5.3:** sem limite de tamanho, ilhas enormes podem travar a geração. A proposta é manter um teto alto e avisar na tela.
- **5.1 e 6.4:** o naturalizar aparece nos dois passos. A proposta é definir a regra uma vez no 5, já pensando nas layers do 6.
- **Renomear para Carolith** muda o id do mod. Mundos e configurações salvos com `buildguide` precisam continuar sendo lidos.
