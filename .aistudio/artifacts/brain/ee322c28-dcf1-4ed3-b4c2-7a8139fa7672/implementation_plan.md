# Janela Dedicada no PC e TV: Baixar, Editar Vídeo e Configurações do App com Release Executável

Sistema completo com janela dedicada para computador (Windows) e Android TV/TV Box, permitindo ao usuário baixar conteúdos e editar o aplicativo, com personalização de pasta de salvamento, qualidade do vídeo (1080p, 720p, 480p) e nome do arquivo, além de distribuição executável pronta para release no GitHub.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Decisões Confirmadas pelo Usuário nas Perguntas de Alinhamento**:
> - **O que baixar e editar**: Permitir baixar vídeos/mídias e editar configurações do próprio aplicativo em tempo real.
> - **Interface no Computador**: **Janela dedicada** contendo botão de download direto e painel de configurações do vídeo e do app.
> - **Opções de Edição Disponíveis**:
>   - **Nome do arquivo**: Campo editável para personalizar o nome do arquivo final antes de baixar (ex: `A2_Miguel_Filme_1080p.mp4`).
>   - **Qualidade do vídeo**: Seleção interativa entre resoluções (1080p Full HD, 720p HD, 480p Rápido e Original do Servidor).
>   - **Pasta de salvamento**: Escolha da pasta de destino no PC (`Downloads`, `Vídeos`, pasta personalizada via diálogo ou predefinições).
> - **Distribuição no PC & GitHub**: Pacote com executável Windows (`Abrir-no-PC.bat` / utilitário `.exe`) integrado ao workflow de Release do GitHub (`.github/workflows/release.yml`).

---

## 1. Overview & Core Concept

- **O que faz**:
  1. Cria uma **Janela Flutuante Dedicada** no aplicativo ("Central de Download & Edição") acessível por botão nas barras de controle e atalho de teclado (`D`) no PC e controle remoto.
  2. Oferece controle completo de edição antes do download: nome do arquivo customizável, seletor de qualidade de resolução (1080p / 720p / 480p / Original) e seletor da pasta de salvamento no dispositivo/PC.
  3. Adiciona painel de personalização do app dentro da janela (ajustes rápidos de zoom da tela, servidor padrão e modo de foco).
  4. Para o PC (Windows), fornece um executável dedicado (`A2-Miguel-PC.bat` e script de lançamento com janela nativa do Windows) e atualiza o fluxo do GitHub Actions (`release.yml`) para gerar releases com o instalador do aplicativo e os utilitários de PC.
- **Público-alvo**: Usuários no computador (Windows) que querem assistir em janela limpa e baixar vídeos organizados em pastas locais, bem como usuários de TV Box que desejam assistir offline.
- **Benefício principal**: Liberdade para baixar vídeos com qualidade e nomes padronizados, sem travamentos e com fluxo de janela dedicado.

---

## 2. User Experience & Visual Design

### Fluxo da Janela de Download e Edição

```
┌────────────────────────────────────────────────────────────────────────┐
│ 📥 A2 Miguel - Baixar & Editar Vídeo e App                         [X] │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│ 🏷️ NOME DO ARQUIVO:                                                   │
│ ┌────────────────────────────────────────────────────────────────────┐ │
│ │ A2_Miguel_Episodio_01_1080p.mp4                                    │ │
│ └────────────────────────────────────────────────────────────────────┘ │
│                                                                        │
│ 🎬 QUALIDADE DO VÍDEO:                                                 │
│ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐ ┌──────────────┐ │
│ │ 1080p Full HD │ │    720p HD    │ │  480p Rápido  │ │   Original   │ │
│ └───────────────┘ └───────────────┘ └───────────────┘ └──────────────┘ │
│                                                                        │
│ 📂 PASTA DE SALVAMENTO:                                                │
│ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐ ┌──────────────┐ │
│ │ 📁 Downloads  │ │ 🎬 Filmes PC  │ │ 📺 Séries PC  │ │ ⚙️ Escolher │ │
│ └───────────────┘ └───────────────┘ └───────────────┘ └──────────────┘ │
│                                                                        │
│ 🛠️ OPÇÕES DO APLICATIVO:                                               │
│ [X] Zoom Automático (Sem Barras Pretas)   [X] Áudio Dublado Padrão     │
│                                                                        │
│ ┌────────────────────────────────────────────────────────────────────┐ │
│ │  ⬇️ INICIAR DOWNLOAD AGORA  │  💻 GERAR EXECUTÁVEL / ATALHO PC     │ │
│ └────────────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
```

### Visual Identity & Theme
- **Estilo**: Dark Mode cinematográfico premium com bordas em `Color(0xFFE50914)` (Vermelho A2 Miguel) e superfícies escuras `Color(0xEE14151C)` com cantos arredondados (18.dp) e sombras elevadas.
- **Pills de Seleção**: Estado ativo com cor primária brilhante e indicador de foco acessível tanto por clique de mouse quanto pelo direcional (D-pad) da TV.
- **Feedback Interativo**: Indicadores animados com `AnimatedVisibility`, mensagens OSD transparentes na tela e barra de progresso durante o download.

---

## 3. Key Product Decisions & Trade-Offs

- **Decisão 1: Janela Dedicada Híbrida (Android + PC Windows)**:
  - *Abordagem Escolhida*: Criar o diálogo Compose `DownloadAndEditWindow` no reprodutor com suporte total a mouse/touch/D-pad e, simultaneamente, disponibilizar o launcher executável de PC Windows com modo de janela dedicada sem moldura de navegador.
  - *Por que*: Atende aos dois ambientes (o usuário que roda o app no emulador/WSA/TV Box e o usuário que executa diretamente no PC via Windows).
  - *Alternativa Considerada*: Fazer apenas um script de terminal no Windows. Rejeitada porque o usuário pediu explicitamente uma janela dedicada com botões e configurações visuais.

- **Decisão 2: Download com `DownloadManager` e Suporte a Streams**:
  - *Abordagem Escolhida*: O aplicativo extrai a URL real de vídeo via JavaScript no WebView (`video.currentSrc || video.src`) e botões nativos de download dos servidores (Pobreflix/AniChan), passando para o gerenciador nativo com o nome e pasta escolhidos pelo usuário.
  - *Por que*: O download roda em segundo plano, não congela o app e permite continuar assistindo.

- **Decisão 3: Release Automática no GitHub com Executáveis**:
  - *Abordagem Escolhida*: Atualizar o arquivo `.github/workflows/release.yml` para compilar o APK, empacotar os scripts executáveis do Windows (`Abrir-no-PC.bat` e instalador de atalhos) em um arquivo compactado `.zip` anexado à Release oficial do GitHub.
  - *Por que*: Atende diretamente ao pedido "faça uma versão no github com exe no pc. com release".

---

## 4. Technical Architecture & Data Strategy

### Componentes e Diagrama de Arquitetura

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                               CAMADA DE INTERFACE                            │
│                                                                              │
│  [PlayerScreen Top Bar] & [Bottom Bar]  ───▶  Botão "📥 Baixar & Editar"     │
│  [Atalho Teclado/Controle: Tecla 'D']   ───▶  Abre DownloadAndEditWindow     │
│                                                                              │
│                         ┌───────────────────────────┐                        │
│                         │   DownloadAndEditWindow   │                        │
│                         ├───────────────────────────┤                        │
│                         │ • TextField: Nome Arquivo │                        │
│                         │ • Row: Qualidades (1080p) │                        │
│                         │ • Row: Pastas de Destino  │                        │
│                         │ • Toggles: Configs do App │                        │
│                         │ • Ação: Iniciar Download  │                        │
│                         └─────────────┬─────────────┘                        │
│                                       │                                      │
└───────────────────────────────────────┼──────────────────────────────────────┘
                                        │ Dispara requisição
                                        ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                              CAMADA DE EXECUÇÃO                              │
│                                                                              │
│  ┌─────────────────────────┐                ┌─────────────────────────────┐  │
│  │   VideoDownloadManager  │                │     Windows PC Companion    │  │
│  ├─────────────────────────┤                ├─────────────────────────────┤  │
│  │ • Extrai URL do vídeo   │                │ • Abrir-no-PC.bat           │  │
│  │ • Envia ao Android      │                │ • Script de download no PC  │  │
│  │   DownloadManager       │                │ • Modo janela dedicada Edge │  │
│  │ • Salva na pasta eleita │                │ • Release no GitHub Actions │  │
│  └─────────────────────────┘                └─────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Arquivos a Serem Criados e Modificados:

1. **`app/src/main/java/com/example/network/VideoDownloadManager.kt`**:
   - Objeto utilitário responsável por disparar o download com nome de arquivo customizado, subpasta (`Downloads`, `Filmes`, `Series`) e monitorar o status.
2. **`app/src/main/java/com/example/ui/DownloadAndEditDialog.kt`**:
   - Janela modal estilizada com visual de janela de PC/TV com abas de download e preferências do app, seleção de qualidade, pasta e nome editável.
3. **`app/src/main/java/com/example/ui/PlayerScreen.kt`**:
   - Adicionar o botão "📥 Baixar & Editar" no cabeçalho e na barra inferior.
   - Conectar atalho de teclado `KeyEvent.KEYCODE_D` para abrir a janela imediatamente.
4. **`Abrir-no-PC.bat` e Utilitário de PC**:
   - Melhorar o script de inicialização do Windows para incluir opções de janela dedicada, download direto e atalhos na área de trabalho.
5. **`.github/workflows/release.yml`**:
   - Incluir no pacote de release tanto o `A2-Miguel-TV.apk` quanto o pacote executável para Windows `A2-Miguel-PC.zip`.

---

## 5. Verification Plan

1. **Compilação**: Executar `compile_applet` para certificar que os novos componentes Compose e helpers compilam sem erros.
2. **Abertura da Janela**: Testar abertura via botão na barra superior, botão na barra inferior e tecla de atalho.
3. **Validação de Edição**: Testar a personalização do nome do arquivo, seleção de resolução e pasta de destino.
4. **Acionamento de Download**: Verificar que o `DownloadManager` recebe os parâmetros corretos e notifica o usuário via OSD na tela.
5. **Workflow de Release**: Validar que o `.github/workflows/release.yml` inclui todos os artefatos solicitados (APK e utilitário de PC).
