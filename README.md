# 🎬 A2 Miguel - Filmes, Séries e Animes na TV e no PC

Aplicativo completo para streaming de filmes, séries e animes sem anúncios, desenvolvido para **Smart TVs, TV Box Android (Aquário, Mi Box, Fire TV)** e com versão autônoma dedicada para **Windows PC**.

---

## 💻 Como Instalar e Rodar no Computador (Windows)

Agora o A2 Miguel roda de forma **100% autônoma e local no seu computador**, sem depender de servidores na nuvem que ficam fora do ar:

### Passo a passo para rodar no PC:
1. Dê dois cliques no arquivo **`Instalar-e-Rodar-no-PC.bat`** (ou no **`Abrir-no-PC.bat`**).
2. O script fará tudo sozinho:
   - 📂 Criará a pasta dedicada na sua pasta de Downloads: `Downloads\A2-Miguel-PC`.
   - 🎬 Criará as subpastas `Videos`, `Filmes` e `Series` para salvar seus downloads organizados.
   - 🖥️ Criará um atalho oficial na sua Área de Trabalho (Desktop) chamado **A2 Miguel - PC**.
   - 🚀 Abrirá a aplicação imediatamente em **Janela Nativa Dedicada** (estilo `.exe` sem abas de navegador)!

> **💡 Dica de Atalho no Teclado do PC:**
> - Pressione a tecla **`D`** dentro de qualquer vídeo para abrir a **Janela de Download e Edição** (escolha qualidade, nome e pasta).
> - Pressione a tecla **`Z`** para alternar os modos de **Zoom** (100%, 115%, 130%, 150%) e remover barras pretas.
> - Pressione **`Espaço`** para pausar/tocar e **`Setas Esquerda/Direita`** para voltar/avançar 10s.

---

## 📥 Janela Dedicada para Download e Edição (PC e TV)

- 🏷️ **Nome do Arquivo Editável**: Mude o nome do vídeo como quiser antes de salvar.
- 🎬 **Qualidade do Vídeo**: Escolha entre **1080p Full HD**, **720p HD**, **480p Rápido** ou **Original do Servidor**.
- 📂 **Pasta de Salvamento**: Salva diretamente na pasta `Downloads\A2-Miguel-PC\Videos`.
- ⚙️ **Editar o App**: Ajuste o zoom da tela e preferências de reprodução diretamente pela janela.

---

## 🚀 Como Publicar no seu GitHub e Gerar a Release Automática (.apk e .zip para PC)

O projeto já está 100% configurado com **GitHub Actions** (`.github/workflows/release.yml`) para compilar o APK e empacotar a versão de PC automaticamente assim que for enviado ao GitHub!

### Passo a passo para enviar ao GitHub pelo AI Studio:
1. No canto superior direito da tela do **Google AI Studio**, clique no menu de opções (ícone de engrenagem ou três pontinhos `⋮`).
2. Selecione **"Push to GitHub"** (ou **"Export to GitHub"**).
3. Conecte sua conta do GitHub e escolha o nome do repositório (ex: `A2-Miguel`).
4. Clique em **Confirmar**.
5. Assim que o repositório for enviado, o GitHub executará a aba **Actions** e gerará a **Release** com:
   - 📱 **`A2-Miguel-TV.apk`**: Instalador para Smart TV, TV Box e celulares Android.
   - 💻 **`A2-Miguel-PC-Windows.zip`**: Pacote completo para Windows pronto para rodar com o `Instalar-e-Rodar-no-PC.bat`.

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
