# 🎬 A2 Miguel — Filmes, Séries e Animes no Celular, TV e Windows PC

Aplicativo focado em **Celulares Android**, **Smart TVs / TV Box** e **Windows PC** para assistir a **filmes, séries e animes sem anúncios**, com catálogo ao vivo (Pobreflix + AniChan), busca em tempo real, múltiplos servidores dublados/legendados e histórico sincronizado.

---

## 💻 Como Instalar e Rodar no Windows PC (Apenas 1 Arquivo!)

O pacote `A2-Miguel-PC-Windows.zip` vem com **apenas 1 arquivo (`A2-Miguel-PC.bat`)** que faz tudo sozinho:

1. Baixe e extraia **`A2-Miguel-PC.bat`**.
2. Dê **dois cliques** em **`A2-Miguel-PC.bat`**.
3. Ele configurará tudo automaticamente no seu computador:
   - 📂 Instala os arquivos na pasta **`Downloads\A2-Miguel-PC`** (`app`, `dados`, `Videos\Filmes`, `Videos\Series`).
   - 🖥️ Cria o atalho **`A2 Miguel PC`** na sua Área de Trabalho (Desktop).
   - 🛡️ Inicia o servidor local com **AdBlock 100+ auto-atualizável**, player em **sandbox anti-popup** e proxy anti-anúncios.
   - 💾 Salva todo o seu **histórico, episódios já vistos, favoritos e buscas** gratuitamente em `Downloads\A2-Miguel-PC\dados\a2_dados.json` (com botão para Exportar/Importar JSON compatível com o celular).
   - 🚀 Abre a janela dedicada estilo aplicativo no Microsoft Edge / Chrome.

### Atalhos rápidos no PC:
- **`D`**: Abre a janela de **Download & Edição** dentro do player.
- **`Z`**: Alterna os modos de **Zoom** (`100%`, `115%`, `130%`, `150%`).
- **`Esc`**: Fecha janelas/player rapidamente.

---

## 📱 Recursos no Celular Android, Tablet e TV Box

- 🎨 **Visual Cinema Moderno (Foco em Celular + TV)**: Navegação inferior no telefone, barra lateral em telas grandes e controles visíveis no player com botão flutuante para toque na tela.
- 🛡️ **AdBlock 100+ Auto-Atualizável & Anti-Popup**: 101 regras nativas sempre ativas + bloqueio heurístico (`/xads.js`, popunders, ParkLogic) e proteção contra cliques invisíveis ou redirecionamentos externos.
- 🔍 **Busca Rápida & Sugestões**: Pesquisa ao vivo integrada (Filmes, Séries e Animes), histórico de buscas recentes e filtros por **Todos**, **Filmes**, **Séries** e **Animes**.
- 📺 **Séries e Animes (AniChan)**: Troca de temporada dentro ou fora do player, **Ep Anterior / Próx Ep**, botão **↺ Reiniciar**, alternância **Dublado / Legendado** e marcação automática **✓ Já Visto**.
- ☁️ **Dados Salvos Gratuitamente**: Modo Convidado local + sincronização gratuita na sua Conta Google (Firestore) + **Exportar / Importar Backup JSON** para levar seus dados entre Celular e PC.

---

## 🚀 Build & Release Automática (GitHub Actions)

Ao enviar para o GitHub, o workflow `.github/workflows/release.yml` gera automaticamente:
- 📱 **`A2-Miguel-TV.apk`** — Instalador Android (Celular, Tablet e TV).
- 💻 **`A2-Miguel-PC-Windows.zip`** — Contém exclusivamente **`A2-Miguel-PC.bat`**.
