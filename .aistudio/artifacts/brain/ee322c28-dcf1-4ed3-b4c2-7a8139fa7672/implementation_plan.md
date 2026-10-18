# Correção do Player Multi-Servidor (Sem Erro ParkLogic) e Catálogo Completo com Busca em Tempo Real no PC

Plano de correção e aprimoramento completo para solucionar o erro de domínio estacionado (ParkLogic), substituir a fonte de vídeo por um sistema robusto de múltiplos servidores com áudio dublado e sem anúncios (com seletor rápido no topo do player), e expandir o catálogo para exibir todos os filmes, séries e animes com busca instantânea em tempo real e filtros de categorias.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Decisões Confirmadas pelo Usuário nas Perguntas de Alinhamento**:
> - **Causa do Erro Identificada**: O link anterior utilizava o domínio `superflixapi.top`, que foi estacionado na ParkLogic.
> - **Solução de Reprodução Escolhida**: **Múltiplos servidores com áudio dublado e sem anúncios**, incluindo uma barra de seleção rápida de servidores (Servidor 1 Dublado, Servidor 2 HD, Servidor 3 Alternativo, AniChan) dentro do próprio player para que o usuário nunca fique sem assistir caso uma fonte oscile.
> - **Exibição do Catálogo Escolhida**: **Catálogo completo expandido com busca em tempo real e categorias**, trazendo um acervo rico com dezenas de títulos em alta, lançamentos e animes, além de mecanismo de busca dinâmico por texto para encontrar qualquer filme ou série.

---

## 1. Overview & Core Concept

- **O que faz**:
  1. Remove completamente o provedor antigo com erro ParkLogic e introduz um ecossistema multi-provedor (WarezCDN, EmbedMovies, SuperFlix API ativa, AniChan e streams diretos) que garantem carregamento imediato em português (Dublado).
  2. Adiciona seletor de servidores no topo do reprodutor (`[🎬 Servidor 1 Dublado] [🚀 Servidor 2 HD] [🛡️ Servidor 3 Reserva]`), permitindo alternância instantânea com 1 clique caso um vídeo falhe.
  3. Expande massivamente a base de títulos para cobrir lançamentos de 2024, clássicos, séries em alta e animes populares, organizados em categorias (`Todos`, `Filmes`, `Séries`, `Animes`, `Lançamentos`).
  4. Implementa busca instantânea com filtro em tempo real à medida que o usuário digita na barra de pesquisa.
  5. Mantém a janela de download dedicada (`D`), controle de zoom de tela (`Z`) e o instalador automático da pasta `Downloads\A2-Miguel-PC`.
- **Público-alvo**: Usuários no computador que desejam assistir a filmes e séries com alta estabilidade e sem telas de erro.
- **Benefício principal**: Certeza de reprodução contínua graças ao fallback inteligente entre servidores, sem anúncios invasivos e com catálogo abrangente.

---

## 2. User Experience & Visual Design

### Nova Interface do Player com Seletor de Servidores

```
┌────────────────────────────────────────────────────────────────────────┐
│ 🎬 A2 Miguel - Player Cinema no PC                                [X] │
├────────────────────────────────────────────────────────────────────────┤
│ 🏷️ Deadpool & Wolverine (2024)                                        │
│ Servidores: [★ Servidor 1 Dublado] [Servidor 2 HD] [Servidor 3 Reserva]│
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│                                                                        │
│                 ┌──────────────────────────────────┐                   │
│                 │                                  │                   │
│                 │        VÍDEO CARREGANDO          │                   │
│                 │      100% DUBLADO E SEM ADS      │                   │
│                 │                                  │                   │
│                 └──────────────────────────────────┘                   │
│                                                                        │
│                                                                        │
├────────────────────────────────────────────────────────────────────────┤
│ [🔍 Zoom: Normal 100%]    [📥 Baixar Vídeo (D)]    [✕ Fechar Reprodutor]│
└────────────────────────────────────────────────────────────────────────┘
```

### Catálogo Completo com Categorias e Busca
- **Barra de Busca Inteligente**: Responde a cada caractere digitado, filtrando títulos por nome, gênero, ano ou sinopse.
- **Categorias Dinâmicas**: Chips clicáveis para filtrar rapidamente entre:
  - `🔥 Todos`
  - `🎬 Filmes`
  - `📺 Séries`
  - `⛩️ Animes`
  - `✨ Lançamentos 2024`
- **Cards com Dados Ricos**: Pôsteres em alta definição com badge de nota IMDb e indicação de áudio Dublado.

---

## 3. Key Product Decisions & Trade-Offs

- **Decisão 1: Arquitetura Multi-Servidor com Fallback Automático**:
  - *Abordagem Escolhida*: Cada título possuirá múltiplos links de servidores confiáveis (como WarezCDN, EmbedMovies, SuperFlix API ativa e AniChan). Se o usuário encontrar lentidão no Servidor 1, um clique no botão de Servidor 2 ou 3 alterna a transmissão instantaneamente.
  - *Por que*: Elimina totalmente o problema de domínio fora do ar ou página estacionada da ParkLogic.
- **Decisão 2: Acervo Local Amplo e Busca em Tempo Real**:
  - *Abordagem Escolhida*: Incluir um acervo de mais de 50 títulos famosos em todas as categorias dentro do `app.js`, com busca instantânea que responde à digitação sem travamentos.
  - *Por que*: O usuário terá um catálogo farto disponível no seu PC imediatamente ao abrir o aplicativo, sem precisar esperar requisições lentas.
- **Decisão 3: Atualização Automática nos Arquivos em Downloads**:
  - *Abordagem Escolhida*: O script `Instalar-e-Rodar-no-PC.bat` e `Abrir-no-PC.bat` atualizarão os arquivos na pasta `%USERPROFILE%\Downloads\A2-Miguel-PC` na próxima execução, garantindo que o usuário receba a correção sem precisar fazer configurações manuais.

---

## 4. Technical Architecture & Data Strategy

### Componentes e Fluxo de Dados

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                              ARQUITETURA PC ATUALIZADA                       │
│                                                                              │
│  [Barra de Busca / Filtros] ───▶ Filtra catálogo completo de filmes/séries   │
│                                           │                                  │
│                                           ▼                                  │
│  [Clique no Card do Filme] ────▶ Abre Player Cinema                          │
│                                           │                                  │
│                                           ▼                                  │
│  ┌────────────────────────────────────────────────────────────────────────┐  │
│  │ MultiServer Engine:                                                    │  │
│  │ • Servidor 1: https://embed.warezcdn.net/filme/{id} (Dublado Oficial) │  │
│  │ • Servidor 2: https://embedmovies.org/embed/movie/{id} (HD Rápido)     │  │
│  │ • Servidor 3: Stream Alternativo / Pobreflix                           │  │
│  │ • AniChan: https://anichan.to/anime/{id}/{ep} (Animes)                 │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                           │                                  │
│  [Janela de Download (D)] ─────▶ Salva MP4 em Downloads\A2-Miguel-PC\Videos   │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Arquivos Impactados:
1. **`app-pc/app.js`**:
   - Substituição dos endpoints que estavam estacionados no ParkLogic por fontes de streaming ativas e verificadas.
   - Implementação da lógica de múltiplos servidores com botões de alternância.
   - Expansão do catálogo com dezenas de filmes, séries e animes com pôsteres HD e sinopses.
2. **`app-pc/index.html`**:
   - Adição da barra de seleção de servidores no topo do modal de vídeo.
   - Inclusão de novas categorias (`Lançamentos 2024`).
3. **`app-pc/style.css`**:
   - Estilização moderna dos botões de seleção de servidores e badges de qualidade.
4. **`Instalar-e-Rodar-no-PC.bat` e `Abrir-no-PC.bat`**:
   - Garantir que a cópia dos novos arquivos atualize a pasta `Downloads\A2-Miguel-PC` imediatamente.

---

## 5. Verification Plan

1. **Teste do Player Multi-Servidor**: Abrir "Deadpool & Wolverine" e os demais títulos para confirmar que o erro da ParkLogic desapareceu e o vídeo carrega com áudio dublado.
2. **Teste de Alternância de Servidores**: Clicar nos botões de Servidor 1, 2 e 3 para confirmar que o iframe troca a URL e carrega o stream correspondente.
3. **Teste do Catálogo Completo**: Verificar que todos os filmes, séries e animes aparecem na tela e que os filtros de categoria funcionam.
4. **Teste da Busca**: Digitar termos de busca (ex: "Deadpool", "Aranha", "Casa", "Dragon") e verificar o filtro instantâneo.
5. **Verificação do Build Android**: Executar `compile_applet` para confirmar integridade do projeto geral.
