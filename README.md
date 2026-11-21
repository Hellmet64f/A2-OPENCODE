# 🎬 A2 Miguel — Filmes e Séries no Celular, TV e Windows PC

Aplicativo focado em **Celulares Android**, **Smart TVs / TV Box** e **Windows PC** para assistir a **filmes e séries sem anúncios**, com catálogo ao vivo, busca em tempo real, múltiplos servidores dublados/legendados e histórico sincronizado.

---

## 💻 Como Instalar e Rodar no Windows PC (Apenas 1 Arquivo!)

O pacote `A2-Miguel-PC-Windows.zip` vem com **apenas 1 arquivo (`A2-Miguel-PC.bat`)** que faz tudo sozinho:

1. Baixe e extraia **`A2-Miguel-PC.bat`**.
2. Dê **dois cliques** em **`A2-Miguel-PC.bat`**.
3. Ele configurará tudo automaticamente no seu computador:
   - 📂 Instala os arquivos na pasta **`Downloads\A2-Miguel-PC`** (`app`, `dados`, `Videos\Filmes`, `Videos\Series`).
   - 🖥️ Cria o atalho **`A2 Miguel PC`** na sua Área de Trabalho (Desktop).
   - 🛡️ Inicia o servidor local com **AdBlock 70+ auto-atualizável** e proxy anti-anúncios.
   - 💾 Salva todo o seu **histórico, episódios já vistos, favoritos e buscas** gratuitamente em `Downloads\A2-Miguel-PC\dados\a2_dados.json` (com botão para Exportar/Importar JSON compatível com o celular).
   - 🚀 Abre a janela dedicada estilo aplicativo no Microsoft Edge / Chrome.

### Atalhos rápidos no PC:
- **`D`**: Abre a janela de **Download & Edição** dentro do player.
- **`Z`**: Alterna os modos de **Zoom** (`100%`, `115%`, `130%`, `150%`).
- **`Esc`**: Fecha janelas/player rapidamente.

---

## 📱 Recursos no Celular Android, Tablet e TV Box

- 🎨 **Visual Cinema Moderno (Foco em Celular + TV)**: Navegação inferior no telefone, barra lateral em telas grandes e controles visíveis no player com botão flutuante para toque na tela.
- 🛡️ **AdBlock 70+ Auto-Atualizável**: 72 regras nativas sempre ativas + atualização automática de listas contra popups, trackers e anúncios de apostas.
- 🔍 **Busca Rápida & Sugestões**: Histórico de buscas recentes e filtros por **Todos**, **Filmes** e **Séries**.
- 📺 **Séries Aprimoradas**: Troca de temporada dentro ou fora do player, **Ep Anterior / Próx Ep**, botão **↺ Reiniciar** (vídeo atual ou série do T1 E1), alternância **Dublado / Legendado** e marcação automática **✓ Já Visto**.
- ☁️ **Dados Salvos Gratuitamente**: Sincronização automática na sua Conta Google (Firestore) + **Exportar / Importar Backup JSON** para levar seus dados entre Celular e PC sem custo.

---

## 🚀 Build & Release Automática (GitHub Actions)

Ao enviar para o GitHub, o workflow `.github/workflows/release.yml` gera automaticamente:
- 📱 **`A2-Miguel-TV.apk`** — Instalador Android (Celular, Tablet e TV).
- 💻 **`A2-Miguel-PC-Windows.zip`** — Contém exclusivamente **`A2-Miguel-PC.bat`**.
