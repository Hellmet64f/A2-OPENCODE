# A2 Miguel para PC: Instalador .bat Autônomo com Pasta em Downloads e App Completo Offline

Sistema completo com script `.bat` autônomo que instala e configura o A2 Miguel diretamente no computador do usuário (criando a pasta dedicada em `Downloads`), executando a aplicação desktop completa com a mesma interface, reprodutor sem anúncios, controle de zoom, janela de download e catálogo da versão móvel, funcionando 100% no Windows sem depender de servidores em nuvem temporários.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Decisões Confirmadas pelo Usuário nas Perguntas de Alinhamento**:
> - **Problema Identificado**: O link antigo no `.bat` apontava para uma URL temporária do container de desenvolvimento (`ais-pre-...`), gerando "Erro de conexão ou servidor fora do ar" ao ser aberto fora da sessão da nuvem.
> - **Funcionamento do .bat**: O `.bat` irá rodar de forma 100% autônoma, instalar todos os componentes necessários na máquina do usuário e criar a pasta dedicada do aplicativo dentro da pasta **`Downloads`** (`%USERPROFILE%\Downloads\A2-Miguel-PC`).
> - **Experiência no Computador**: A interface e funcionamento no PC serão **idênticos à versão de celular e TV**, trazendo:
>   - Catálogo de filmes, séries e animes com pesquisa integrada.
>   - Reprodutor cinematográfico com reprodução automática dublada e sem anúncios.
>   - Janela de download dedicada para salvar arquivos `.mp4` nas resoluções 1080p, 720p e 480p com edição de nome e seleção de pastas.
>   - Modos de zoom da tela (Normal, Ajustar, Cinema, Tela Cheia) para eliminar barras pretas no monitor.

---

## 1. Overview & Core Concept

- **O que faz**:
  1. Fornece o arquivo `Instalar-e-Rodar-no-PC.bat` (e atualiza o `Abrir-no-PC.bat`) que, ao ser executado no Windows com 2 cliques, cria automaticamente a pasta `%USERPROFILE%\Downloads\A2-Miguel-PC`.
  2. Instala e inicia o aplicativo desktop nativo do A2 Miguel (construído em HTML5/CSS3/JS modular autônomo com visual Dark Red idêntico ao Android Compose).
  3. Abre em modo de **Janela Dedicada do Windows** (`--app` no Edge ou Chrome), sem abas e sem barra de navegação, com proporção 16:9 ajustada (1280x720) exatamente como um programa `.exe` profissional.
  4. Cria um atalho oficial na Área de Trabalho (Desktop) para que o usuário possa abrir o app a qualquer momento sem precisar do terminal.
  5. Integra o reprodutor de vídeo, sistema de busca e a janela de download com salvamento direto na pasta `Downloads\A2-Miguel-PC\Videos`.
- **Público-alvo**: Usuários no Windows que querem assistir a filmes, séries e animes com a mesma facilidade da Smart TV e baixar conteúdos localmente sem erros de servidor.
- **Benefício principal**: Independência total de servidores temporários da nuvem: o programa roda localmente no Windows com alta velocidade e estabilidade.

---

## 2. User Experience & Visual Design

### Fluxo de Execução no Computador (Windows)

```
┌────────────────────────────────────────────────────────────────────────┐
│                        ÁREA DE TRABALHO / DOWNLOADS                    │
│                                                                        │
│  [2 Cliques em "Instalar-e-Rodar-no-PC.bat"]                           │
│                     │                                                  │
│                     ▼                                                  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ Prompt do Windows (Instalação Automática):                       │  │
│  │ 1. Cria pasta: C:\Users\Nome\Downloads\A2-Miguel-PC              │  │
│  │ 2. Cria subpastas: Videos, Filmes, Series                        │  │
│  │ 3. Instala arquivos do aplicativo desktop autônomo               │  │
│  │ 4. Cria atalho "A2 Miguel" na Área de Trabalho                   │  │
│  │ 5. Abre janela dedicada do Windows imediatamente!                │  │
│  └──────────────────────────────────┬───────────────────────────────┘  │
│                                     │                                  │
│                                     ▼                                  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ JANELA NATIVA DEDICADA DO WINDOWS (1280x720)                     │  │
│  │ 🎬 A2 Miguel - Filmes, Séries e Animes                       [X] │  │
│  ├──────────────────────────────────────────────────────────────────┤  │
│  │ [🔍 Buscar Filme...] [Todos] [Filmes] [Séries] [Animes]          │  │
│  │                                                                  │  │
│  │ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐            │  │
│  │ │ Poster Filme  │ │ Poster Série  │ │ Poster Anime  │            │  │
│  │ │ Assistir / ⬇️  │ │ Assistir / ⬇️  │ │ Assistir / ⬇️  │            │  │
│  │ └───────────────┘ └───────────────┘ └───────────────┘            │  │
│  │                                                                  │  │
│  │ [Ao clicar em um título: Abre Reprodutor Cinema + Janela Down]   │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

### Janela do Player e Central de Download no PC
- **Design Visual**: Dark Mode cinematográfico (`#0E0F14` e `#E50914`), inspirado na versão Android Compose, com cabeçalho de controle, barra de status e botões de ação com feedback ao passar o mouse (hover) e clique.
- **Janela de Download Dedicada (Tecla `D` ou Botão `📥 Baixar`)**:
  - Edição do nome do arquivo (ex: `A2_Miguel_MeuFilme_1080p.mp4`).
  - Escolha de qualidade (1080p, 720p, 480p, Original).
  - Seleção da pasta de destino (`Downloads\A2-Miguel-PC\Videos`).
  - Botão de download direto que salva o arquivo no disco do computador.

---

## 3. Key Product Decisions & Trade-Offs

- **Decisão 1: Aplicação Desktop Autônoma Local vs. Dependência da Nuvem**:
  - *Abordagem Escolhida*: Empacotar uma aplicação desktop autônoma completa (HTML5/CSS3/JS modular) que é instalada pelo `.bat` diretamente no computador do usuário.
  - *Por que*: Elimina completamente o erro "servidor fora do ar", pois a aplicação roda localmente e consome os fluxos de streaming diretamente das fontes (Pobreflix/AniChan), garantindo que sempre abra mesmo sem o AI Studio ativo.
  - *Alternativa Considerada*: Continuar usando apenas a URL do Cloud Run. Rejeitada porque a URL expira e causa o erro relatado pelo usuário.

- **Decisão 2: Instalação Automática na Pasta Downloads (`%USERPROFILE%\Downloads\A2-Miguel-PC`)**:
  - *Abordagem Escolhida*: O script `.bat` detecta automaticamente a pasta de Downloads do usuário do Windows, cria a estrutura necessária, copia os arquivos do aplicativo e cria as pastas `Videos`, `Filmes` e `Series` para organização dos downloads.
  - *Por que*: Atende com precisão cirúrgica ao pedido do usuário ("o .bat vai rodar tudo e instlar tudo pra rodar e pasta vai aparecer no Downloads").

- **Decisão 3: Manter Compatibilidade Total com TV Box (APK) e Release no GitHub**:
  - *Abordagem Escolhida*: O projeto continuará mantendo o APK do Android compilando 100% no Gradle para TV Box e o workflow do GitHub Actions (`release.yml`) empacotará o APK e o instalador do PC em um único pacote de Release.
  - *Por que*: O usuário pediu que fosse "igual na versão de telefone, mas pra pc", permitindo que o mesmo repositório atenda tanto Smart TVs quanto computadores.

---

## 4. Technical Architecture & Data Strategy

### Diagrama de Arquitetura do Pacote PC

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                            ARQUITETURA DO SISTEMA PC                         │
│                                                                              │
│  [Instalar-e-Rodar-no-PC.bat]                                                │
│         │                                                                    │
│         ├──▶ 1. Cria diretório: %USERPROFILE%\Downloads\A2-Miguel-PC         │
│         ├──▶ 2. Cria subpastas: \Videos, \Filmes, \Series                    │
│         ├──▶ 3. Descompacta/Cria: app-pc\index.html, app.js, style.css       │
│         ├──▶ 4. Cria atalho: Desktop\A2 Miguel.lnk                           │
│         └──▶ 5. Dispara: msedge.exe --app=file:///.../index.html             │
│                                                                              │
│                         ┌───────────────────────────┐                        │
│                         │   A2 Miguel Desktop PC    │                        │
│                         ├───────────────────────────┤                        │
│                         │ • Catálogo de Filmes/Série│                        │
│                         │ • Player Cinema No Ads    │                        │
│                         │ • Zoom e Enquadramento    │                        │
│                         │ • Janela de Download (D)  │                        │
│                         │ • Salvamento em Downloads │                        │
│                         └───────────────────────────┘                        │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Arquivos a Serem Criados e Atualizados:

1. **`app-pc/index.html`**:
   - Interface completa do aplicativo para PC: cabeçalho com busca, catálogo de filmes e séries, reprodutor embutido sem anúncios, controles de zoom e modal de download idêntico à versão Android.
2. **`app-pc/app.js`**:
   - Lógica do aplicativo no PC: busca em tempo real via APIs de streaming (Pobreflix/AniChan), alternância de servidores e dublado, modos de zoom da tela, atalhos de teclado (`D` para download, `Z` para zoom, setas para seek) e gerenciador de download direto para o PC.
3. **`app-pc/style.css`**:
   - Folha de estilo cinematográfica Dark Red com fontes modernas, cartões de poster, efeitos de hover, player responsivo e janela de download flutuante.
4. **`Instalar-e-Rodar-no-PC.bat`**:
   - Script batch inteligente para Windows que instala e copia o A2 Miguel na pasta `Downloads\A2-Miguel-PC`, cria o atalho no Desktop e executa a aplicação em modo janela nativa.
5. **`Abrir-no-PC.bat`**:
   - Atualizado para executar o app local instalado em `Downloads\A2-Miguel-PC` ou chamar o instalador automático caso ainda não exista.
6. **`.github/workflows/release.yml`**:
   - Empacota o executável e instalador do PC junto com o APK na release oficial.
7. **`README.md`**:
   - Instruções atualizadas passo a passo de como rodar com 1 clique no Windows.

---

## 5. Verification Plan

1. **Validação do Script Batch**: Testar a lógica de criação de diretórios em `%USERPROFILE%\Downloads\A2-Miguel-PC` e inicialização no Edge/Chrome no modo `--app`.
2. **Validação da Interface PC**: Verificar que o catálogo de filmes/séries carrega, a busca funciona, os vídeos tocam sem anúncios e com áudio dublado.
3. **Validação da Janela de Download**: Testar edição de nome do arquivo, seleção de qualidade e download de vídeo na pasta `Downloads`.
4. **Validação do APK Android**: Executar `compile_applet` para garantir que o código Android permanece 100% íntegro e compilável.
