# 🎬 A2 Miguel - Filmes, Séries e Animes na TV e no PC

Aplicativo completo para streaming de filmes, séries e animes sem anúncios, desenvolvido para **Smart TVs, TV Box Android (Aquário, Mi Box, Fire TV)** e com suporte nativo para **Windows PC**.

---

## 📥 Nova Janela Dedicada para Download e Edição no PC e TV

Agora você pode baixar qualquer vídeo e personalizar antes do download:
- 🏷️ **Nome do Arquivo Editável**: Mude o nome do vídeo como quiser antes de salvar.
- 🎬 **Qualidade do Vídeo**: Escolha entre **1080p Full HD**, **720p HD**, **480p Rápido** ou **Original do Servidor**.
- 📂 **Pasta de Salvamento**: Selecione onde salvar no seu PC ou aparelho (`Downloads`, `Filmes`, `Séries` ou pasta customizada).
- ⚙️ **Editar o App**: Ajuste o zoom da tela, ative calibração de mira de foco e preferências de reprodução diretamente pela janela.
- ⌨️ **Atalho no Teclado do PC**: Pressione a tecla **`D`** para abrir a janela imediatamente durante a reprodução!

---

## 🚀 Como Publicar no seu GitHub e Gerar a Release Automática (.apk e executáveis PC)

O projeto já está 100% configurado com **GitHub Actions** (`.github/workflows/release.yml`) para compilar o APK e empacotar a versão de PC automaticamente assim que for enviado ao GitHub!

### Passo a passo para enviar ao GitHub pelo AI Studio:
1. No canto superior direito da tela do **Google AI Studio**, clique no menu de opções (ícone de engrenagem ou três pontinhos `⋮`).
2. Selecione **"Push to GitHub"** (ou **"Export to GitHub"**).
3. Conecte sua conta do GitHub e escolha o nome do repositório (ex: `A2-Miguel`).
4. Clique em **Confirmar**.
5. Assim que o repositório for enviado, o GitHub executará o workflow da aba **Actions** e gerará a **Release** com:
   - 📱 **`A2-Miguel-TV.apk`**: Instalador para Smart TV, TV Box e Android.
   - 💻 **`A2-Miguel-PC-Windows.zip`**: Pacote executável para Windows.

---

## 💻 Como Rodar no PC (Windows)

Você tem duas opções simples para usar o aplicativo no seu computador:

### Opção 1: Executável Rápido (`Abrir-no-PC.bat`)
1. Dê dois cliques no arquivo `Abrir-no-PC.bat`.
2. O aplicativo abrirá em uma janela nativa limpa, sem barra de endereços e sem abas de navegador, exatamente como um programa `.exe` de computador.

### Opção 2: Instalador de Atalho na Área de Trabalho (`Instalar-Atalho-PC.bat`)
1. Dê dois cliques em `Instalar-Atalho-PC.bat`.
2. Um atalho oficial chamado **"A2 Miguel - Filmes e Series"** será criado na sua Área de Trabalho (Desktop), abrindo o app direto com 2 cliques!

> **💡 Dica para criar um arquivo `.exe` real:**
> Se quiser transformar o `Abrir-no-PC.bat` em um arquivo `.exe` tradicional com ícone próprio, você pode usar o utilitário gratuito **Bat To Exe Converter** ou o **Advanced BAT to EXE Converter**.

### Opção 3: Emulador / Windows Subsystem for Android (WSA)
- No Windows 11 com WSA instalado, ou através de emuladores como **BlueStacks**, **LDPlayer** ou **NoxPlayer**, você pode instalar o `A2-Miguel-TV.apk` diretamente e ter a experiência idêntica à TV no PC.

---

## 📺 Principais Recursos para TV Box e Controle Remoto

- 🔍 **Controle de Zoom da Tela**:
  - Modos de Zoom: **Normal (100%)**, **Ajustar (115%)**, **Cinema (130%)** e **Tela Cheia (150%)**.
  - **Atalho no controle remoto**: Tecla Amarela (`PROG_YELLOW`), botão Zoom (`WINDOW`) ou tecla `Z` alternam os modos instantaneamente.
  - Ajuste fino e enquadramento (Pan) com as setas para eliminar qualquer barra preta.
  - Salvamento automático para todos os filmes e séries.
- 🎯 **Calibração de Foco Automático**: Mira na tela com setas do controle remoto para salvar a posição do Play para todos os vídeos.
- ⏭️ **Controle de Episódios**: Teclas de Volume `+` e `-` mudam para o próximo ou voltam o episódio.
- ⏩ **Avanço/Retrocesso**: Setas Esquerda e Direita avançam ou voltam 10 segundos.
- 🛡️ **No Ads & Autoplay Dublado**: Início automático em áudio português sem telas pretas e sem anúncios.
