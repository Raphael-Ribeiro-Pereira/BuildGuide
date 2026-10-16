# Tutorial: medir o desempenho do jogo (JFR, modlab e as linhas `timing:`)

Este guia explica, do zero, as ferramentas que usamos para descobrir **o que** deixa o jogo pesado e
**quanto**. A ideia é sempre a mesma: primeiro medir, depois mexer no código, depois medir de novo.

Ferramentas, da mais simples para a mais completa:

| Ferramenta | O que mostra | Onde fica |
|---|---|---|
| Tela F3 do jogo | FPS e gráficos na hora | dentro do jogo |
| Linhas `timing:` do Build Guide | quanto cada etapa do nosso mod demorou | `logs/latest.log` do perfil |
| JFR (Java Flight Recorder) | uma "gravação" de tudo que o Java fez: CPU, memória, pausas | arquivo `.jfr` na pasta `debug` do perfil |
| `modlab.py` + `ModlabJfr.java` | lê o log e o `.jfr` e escreve um relatório em português, com a culpa dividida por mod | `mods/_tools` do perfil |

---

## 1. Palavras que aparecem em todo lugar

- **FPS** (quadros por segundo): quantas imagens o jogo desenha por segundo. 60 é fluido.
- **Frame time** (tempo de quadro): quanto um quadro demorou, em milissegundos (ms). 60 FPS = 16,7 ms
  por quadro. Um **engasgo** é um quadro isolado que demora muito (por exemplo 80 ms), mesmo que a
  média de FPS continue boa.
- **1% low**: o FPS dos piores momentos. Mostra engasgos melhor que a média.
- **Render thread** (thread de render): o "funcionário" que desenha a tela e lê o teclado/mouse. Se ele
  trava, o FPS cai. O Build Guide desenha, valida e reage aos botões aqui.
- **Server thread**: o servidor interno do mundo (mesmo jogando sozinho). Cuida de blocos, mobs,
  redstone. Mede-se em **MSPT** (ms por tick); acima de 50 ms o **TPS** cai abaixo de 20 e o mundo fica
  "lento".
- **Heap / memória**: a memória do Java. **Alocação** é o quanto de memória nova um mod pede por
  segundo; o que ele larga vira **lixo**.
- **GC** (garbage collector, "coletor de lixo"): o Java parando para limpar o lixo. Pausas longas de GC
  viram engasgos.
- **CPU x GPU**: a CPU roda o código Java (o que o JFR vê). A GPU (placa de vídeo) pinta os
  triângulos na tela; **o JFR não enxerga o trabalho da GPU**, só o tempo em que a CPU ficou
  esperando por ela.

---

## 2. Regras de um teste justo

Sem isto, os números enganam (foi o caso da primeira comparação com o Litematica: os dois estavam em
perfis com mods diferentes).

1. **Mesmo perfil e mesmos mods** nos testes que você quer comparar. Hoje o perfil *Fabulously
   Optimized (1)* está com só 4 mods ligados (Sodium, Lithium, ImmediatelyFast, EntityCulling e Iris
   desligados), e o perfil *teste* tem esses ligados. O Sodium sozinho muda muito o FPS.
2. **Mesmo mundo, mesmo lugar, mesma câmera.** Volte sempre ao mesmo ponto (anote as coordenadas do F3).
3. **Mesmas opções de vídeo:** render distance, tamanho da janela, e de preferência **VSync desligado
   e Max Framerate em Unlimited** (assim o FPS mostra o custo real, sem teto).
4. **Espere 30 s depois de entrar no mundo** antes de gravar: o carregamento de chunks no começo
   bagunça a medida.
5. **Cada rodada com uns 60 s**, e faça cada cenário **duas vezes**. Se as duas vezes derem números
   muito diferentes, algo mudou entre elas.
6. **Uma mudança por vez.** Ligou um mod ou mudou uma opção? Grave de novo antes de mudar outra coisa.

---

## 3. A tela F3 (o termômetro rápido)

- **F3**: mostra o FPS no canto de cima.
- **F3 + 2**: gráficos de FPS e TPS (as barras mostram o tempo de cada quadro; uma barra alta e isolada
  é um engasgo).
- **F3 + 1**: o gráfico de pizza do profiler do Minecraft (por etapa do jogo, não por mod).

Serve para ver na hora se algo piorou. Para saber **quem** causou, use o JFR (seção 5).

---

## 4. As linhas `timing:` do Build Guide

O nosso mod escreve no `latest.log` uma linha sempre que uma etapa dele passa de 8 ms (4 ms para o envio
ao mundo), no máximo 200 linhas por sessão. Exemplo real do seu log de hoje:

```
[Build Guide] timing: phase=world-buffer ms=5 blocks=4998 reason=idle world-end=0.9 world-close=0.0 world-other=3.9 native=0.0 focus=948/4998
```

| Campo | Significado |
|---|---|
| `phase=generation` | gerar a forma (calcular os blocos), fora da thread de render |
| `phase=world-buffer` | levar a forma nova para o mundo (no quadro em que ela aparece) |
| `phase=scan` | a validação contra o mundo; `start=` é a preparação e `publish=` a publicação, cada uma num quadro só |
| `phase=preview-snapshot` / `preview-rebuild` | copiar e remontar o preview 3D |
| `ms=` | quanto durou |
| `blocks=` | quantos blocos a forma tem |
| `reason=` | o que liberou o envio: `apply` (Live), `idle` (pausa do Idle), `close` (menu fechado) |
| `world-end` / `world-close` / `world-other` | as três partes do envio ao mundo, em ms |
| `native=` | memória de vértices ainda presa, em MiB (deve voltar a 0) |
| `focus=n/total` | com o foco: quantos blocos o mundo desenha, de quantos |
| `slices=` / `maxslice=` | em quantos quadros o scan foi dividido e o maior pedaço |
| `sections=` | (desde a etapa 1 de desempenho) em quantas seções de 16³ blocos a forma foi dividida |
| `frames=` / `maxframe=` | em quantos quadros a forma nova foi enviada à GPU e o maior pedaço, em ms (deve ficar perto de 3) |
| `gpu=` | memória de vídeo dos buffers do mod, em MiB (deve ficar estável enquanto você edita) |

Desde a etapa 1 há também uma linha a cada 30 s enquanto alguma forma está ligada:

```
[Build Guide] timing: phase=render frames=1800 avg=0.42 max=3.1 sections=34/260 gpu=39.2 native=0.0
```

| Campo | Significado |
|---|---|
| `frames=` | quadros desenhados nesses 30 s (dividido por 30 dá o FPS médio) |
| `avg=` / `max=` | quanto o Build Guide gastou por quadro na thread de render, em ms: a média e o pior |
| `sections=n/total` | quantas seções foram desenhadas (na média por quadro) de quantas existem; as de trás de você e as além da render distance são puladas |
| `gpu=` / `native=` | memória de vídeo e memória de vértices presas, em MiB |

Para achar as linhas: abra `logs/latest.log` do perfil e procure por `timing:`. O `modlab report` (seção
6) já as coloca na linha do tempo junto com o resto.

> Cuidado: o `latest.log` é **apagado** quando o jogo abre de novo. Gere o relatório antes de reabrir,
> ou use o arquivo antigo (`logs/AAAA-MM-DD-N.log.gz`) com `--log`.

---

## 5. Gravar com o JFR (Java Flight Recorder)

O JFR vem dentro do próprio Java. Ele anota, várias vezes por segundo, **o que cada thread está
fazendo** (qual método de qual classe), quanta memória foi pedida e quando o GC parou o jogo. O custo de
gravar é pequeno (por volta de 1 a 2%).

O Minecraft tem o comando `/jfr`, que precisa de **comandos liberados (cheats)**:

- Mundo novo: na criação, **Allow Commands: ON**.
- Mundo existente: **Esc → Open to LAN → Allow Commands: ON → Start LAN World** (vale até sair do mundo).

Passo a passo:

1. Entre no mundo, vá ao lugar do teste e espere uns 30 s.
2. Digite `/jfr start`. O chat avisa que a gravação começou.
3. Faça o cenário do teste por uns 60 s (veja a seção 8).
4. Digite `/jfr stop`. O chat diz onde salvou: um arquivo `.jfr` na pasta **`debug`** do perfil
   (`%APPDATA%\ModrinthApp\profiles\<perfil>\debug`).
5. Pode sair do jogo (ou não). Só **não reabra** antes de gerar o relatório (por causa do `latest.log`).

Dica: anote num papel o horário de cada coisa que você fez durante a gravação ("00:12 segurei o + no
Depth", "00:40 cliquei Validate"). Fica fácil achar no relatório.

---

## 6. Gerar o relatório com o `modlab`

O `modlab` lê o `latest.log` e o `.jfr` mais novo do mesmo perfil e escreve um relatório. Precisa do
Python (`py`, você já tem o 3.14) e de um JDK (ele acha o `C:\Program Files\Java\jdk-27`).

Abra o **PowerShell** e rode:

```powershell
cd "$env:APPDATA\ModrinthApp\profiles\Fabulously Optimized (1)\mods\_tools"
py modlab.py report
```

- O resumo aparece na tela e o relatório completo vai para `_tools\reports\modlab-<data>.log`.
- Para outro perfil (por exemplo o *teste*), diga onde estão os mods dele (o `--mods-dir` vem
  **antes** de `report`):

```powershell
py modlab.py --mods-dir "$env:APPDATA\ModrinthApp\profiles\teste\mods" report
```

- Outras opções úteis: `--jfr caminho\arquivo.jfr` (uma gravação específica), `--log caminho` (um log
  antigo, aceita `.log.gz`), `--every 5` (uma linha `[PERF]` a cada 5 s em vez de 10).

---

## 7. Como ler o relatório

### O resumo (topo do arquivo)

- **Marcos**: quando o jogo abriu, entrou no mundo, pausou, fechou. Situa a gravação no tempo.
- **FPS: média | mediana | pior segundo | 1% low**. Compare a **mediana** entre os cenários; o **1% low**
  e o **pior segundo** mostram engasgos.
- **Servidor: ms/tick** (MSPT). Acima de 50 ms, o mundo atrasa.
- **Memória após GC: mín, máx** e **GC: pausas ≥ 20 ms, pior X ms**. Muitas pausas ou pausas grandes =
  algum mod produzindo lixo demais.
- **CPU por mod**, separado por thread. Na **Render thread**, a porcentagem do "Build Guide" é quanto da
  CPU de desenhar/reagir foi gasto no nosso código. Nomes especiais:
  - `GPU/VSync (swap buffers)`: a CPU esperando a placa de vídeo terminar (ou o VSync). Se isso é
    grande, o gargalo é a **GPU**, e o JFR não diz qual mod a encheu de trabalho (ver seção 8, teste A/B).
  - `Minecraft`: o próprio jogo. `LWJGL/OpenGL (driver)`: chamadas ao driver de vídeo. `Java/JVM`:
    o próprio Java. `~ocioso`: esperando, não conta.
- **Quem CRESCE nos momentos de FPS/travadas**: `% no geral → % nos picos`. Um mod que gasta 5% no
  geral e 40% nos segundos ruins é o suspeito do engasgo (`<== suspeito` quando sobe 10 pontos ou mais).
- **Alocação de memória por mod (MB/s)**: quanto lixo cada mod gera por segundo. Dezenas de MB/s de
  um mod só é sinal de problema (é uma das nossas suspeitas: a validação cria coleções grandes a cada scan).

### A parte do log

- **Conflitos de mixin**: dois mods mexendo no mesmo método do jogo. "Accessor duplicado" e "mixin sem
  alvo" geralmente são inofensivos.
- **Dicas**, **erros** e **spam**: mensagens repetidas milhares de vezes custam FPS (no relatório de
  30/09, um mod pedia uma tecla inexistente 6009 vezes; não é o Build Guide).

### A linha do tempo (o resto do arquivo)

É o `latest.log` original em ordem, com linhas `##` do modlab no meio:

- `## [PERF] fps=… mspt=… heap=… cpu_pc=…` a cada 10 s, com os mods que mais gastaram naquele momento.
- `## [ALERTA] QUEDA DE FPS / PAUSA DE GC / TRAVADA / TICK LENTO`: o momento exato de um problema, com
  quem estava rodando.
- As nossas linhas `timing:` aparecem ali também. Um `## [ALERTA]` colado numa linha `timing:` com
  `ms=` alto é a pista mais direta que existe.

Procure no editor por: `[ALERTA]`, `[PERF]`, `timing:`, `Build Guide`.

---

## 8. O protocolo para a próxima etapa (Build Guide x Litematica)

Use a mesma forma grande (por exemplo a Island de uns 100 mil blocos), no mesmo lugar. Para cada
cenário: `/jfr start`, 60 s, `/jfr stop`, `py modlab.py report`, e guarde o relatório com um nome que
diga o cenário.

| Cenário | O que fazer | O que mede |
|---|---|---|
| A. Escondida | Part: **Hidden** no painel do preview, parado | a base: o jogo sem o desenho da forma |
| B. Visível, parado | Part: All, parado olhando a forma | o custo de **desenhar** (comparar o FPS com A) |
| C. Visível, andando | andar em volta da forma | desenho + mudar o ângulo |
| D. Editando | segurar o + num controle e clicar Validate | gerar, enviar ao mundo, validar (CPU e lixo) |
| E. Litematica | um esquema de tamanho parecido, **no mesmo perfil e com os mesmos mods** | a comparação justa |

Como ler a comparação:

- **B menos A** (diferença de FPS mediano) = custo de **GPU** do nosso desenho. O JFR não mostra isso
  sozinho; é por isso que existe o cenário A.
- **D**: olhe a fatia do Build Guide na Render thread, a alocação em MB/s, as pausas de GC e as linhas
  `timing:` (`scan start=`/`publish=`, `world-buffer`). Isso diz se o engasgo vem do scan, do envio ao
  mundo ou do lixo.
- **E x B/C**: o mesmo número de blocos desenhado pelos dois mods.

Com esses relatórios decidimos o que atacar primeiro: dividir a forma em seções de 16³ (como o
Litematica), não desenhar as faces escondidas, ou espalhar o scan e tirar o boxing.

**Etapa 1 (16/10, `perf-wip`): as seções de 16³ já estão no jar.** O que olhar com ela:

- Olhando a forma e depois de costas para ela: na linha `phase=render`, o `sections=n/total` deve cair
  bastante de costas, e o FPS (F3) subir.
- Segurando o + num controle de uma forma grande (Live): a linha `world-buffer` deve mostrar
  `frames=` maior que 1 e `maxframe=` perto de 3, e o F3 + 2 sem a barra alta isolada que aparecia
  antes. A forma nova aparece alguns quadros depois, inteira, nunca pela metade.
- O cenário B menos A continua valendo para o custo de GPU que sobra (as faces escondidas, que são a
  etapa seguinte, dependem da sua decisão sobre o visual).

---

## 9. As outras ferramentas do `modlab` (com cuidado)

### `bisect`: achar um mod culpado por busca binária

Serve quando "o jogo ficou ruim e não sei por quê". Ele liga e desliga metade dos mods a cada teste,
renomeando os jars para `.disabled`, e com umas 8 rodadas acha 1 culpado entre 300.

```powershell
py modlab.py bisect start            # todos os mods ligados viram suspeitos (menos o Fabric API)
py modlab.py bisect start --keep "Build Guide"   # nunca desliga este
py modlab.py bisect good             # o problema SUMIU neste teste
py modlab.py bisect bad              # o problema CONTINUA neste teste
py modlab.py bisect undo             # desfaz a última resposta
py modlab.py bisect reset            # volta a pasta EXATAMENTE como estava antes do start
```

Regras de ouro:
- **Feche o jogo antes de cada comando** (ele renomeia arquivos dentro de `mods`).
- Se desistir no meio, rode `bisect reset`. Enquanto um bisect está em andamento existe o arquivo
  `_tools\bisect_state.json`.
- **Não faça deploy do Build Guide no meio de um bisect** (o deploy também renomeia jars). O script de
  deploy agora se recusa a rodar se encontrar o `bisect_state.json`.

### `mixins`: quem mexe no mesmo pedaço do jogo

```powershell
py modlab.py mixins --mod "Build Guide"    # as classes do jogo que o nosso mod altera e quem mais altera
py modlab.py mixins --class LevelRenderer  # quem mexe no LevelRenderer
```

`!!` = dois mods no mesmo método e um deles substitui o código (um pode anular o outro); `!` = dois mods
no mesmo método (os custos somam).

---

## 10. Resumo de bolso

1. Mesmo perfil, mesmo lugar, VSync desligado, esperar 30 s.
2. `/jfr start` → 60 s do cenário → `/jfr stop`.
3. `py modlab.py report` (antes de reabrir o jogo).
4. Olhe: FPS mediana e 1% low, CPU da Render thread por mod, alocação MB/s, GC, `[ALERTA]` e `timing:`.
5. Compare com o cenário Hidden para saber o custo de GPU.
