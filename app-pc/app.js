// A2 Miguel Desktop PC - Core Application Engine

// Catálogo inicial embutido de alta qualidade para inicialização instantânea no PC
const DEFAULT_CATALOG = [
    {
        id: "m1",
        title: "Deadpool & Wolverine",
        type: "movie",
        year: "2024",
        rating: "8.2",
        poster: "https://image.tmdb.org/t/p/w500/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg",
        streamUrl: "https://superflixapi.top/filme/deadpool-wolverine",
        desc: "Deadpool é recrutado pela AVT para salvar o multiverso ao lado de Wolverine."
    },
    {
        id: "m2",
        title: "Divertida Mente 2",
        type: "movie",
        year: "2024",
        rating: "8.0",
        poster: "https://image.tmdb.org/t/p/w500/vpnVM9B6NMmQpWeZvzLvDESb2QY.jpg",
        streamUrl: "https://superflixapi.top/filme/divertida-mente-2",
        desc: "Riley entra na adolescência e novas emoções assumem a sala de controle."
    },
    {
        id: "m3",
        title: "Duna: Parte 2",
        type: "movie",
        year: "2024",
        rating: "8.6",
        poster: "https://image.tmdb.org/t/p/w500/czembW0Rk1Ke7lCJGahbOhdCuhV.jpg",
        streamUrl: "https://superflixapi.top/filme/duna-parte-2",
        desc: "Paul Atreides se une a Chani e aos Fremen em busca de vingança."
    },
    {
        id: "m4",
        title: "Planeta dos Macacos: O Reinado",
        type: "movie",
        year: "2024",
        rating: "7.5",
        poster: "https://image.tmdb.org/t/p/w500/gKkl37BQuKTanygYQG1pyYgLVgf.jpg",
        streamUrl: "https://superflixapi.top/filme/planeta-dos-macacos-o-reinado",
        desc: "Várias gerações após o reinado de César, uma nova jornada épica se inicia."
    },
    {
        id: "s1",
        title: "A Casa do Dragão",
        type: "series",
        year: "2024",
        rating: "8.5",
        poster: "https://image.tmdb.org/t/p/w500/7QMsOTMUswlwxJP0rTTZfmz2tX2.jpg",
        streamUrl: "https://superflixapi.top/serie/a-casa-do-dragao",
        desc: "A Dança dos Dragões divide a dinastia Targaryen em uma guerra civil brutal."
    },
    {
        id: "s2",
        title: "The Boys",
        type: "series",
        year: "2024",
        rating: "8.7",
        poster: "https://image.tmdb.org/t/p/w500/2zmTngn1tYC1AvfnNDBpQIavBk8.jpg",
        streamUrl: "https://superflixapi.top/serie/the-boys",
        desc: "Os justiceiros enfrentam super-heróis corruptos que abusam de seus poderes."
    },
    {
        id: "s3",
        title: "O Problema dos 3 Corpos",
        type: "series",
        year: "2024",
        rating: "7.8",
        poster: "https://image.tmdb.org/t/p/w500/ykZq5102q92rLqGZ7A25rNl3rZ1.jpg",
        streamUrl: "https://superflixapi.top/serie/o-problema-dos-3-corpos",
        desc: "Cientistas brilhantes enfrentam a maior ameaça à existência da humanidade."
    },
    {
        id: "a1",
        title: "Solo Leveling",
        type: "anime",
        year: "2024",
        rating: "8.6",
        poster: "https://image.tmdb.org/t/p/w500/geCRueV3ElhRTr0xtJuPxJ8HGda.jpg",
        streamUrl: "https://anichan.to/anime/solo-leveling/1",
        desc: "O caçador mais fraco do mundo ganha um sistema secreto de evolução."
    },
    {
        id: "a2",
        title: "Demon Slayer: Kimetsu no Yaiba",
        type: "anime",
        year: "2024",
        rating: "8.9",
        poster: "https://image.tmdb.org/t/p/w500/xUfRZu2mi8jH6SzQEJGP6tjBuYj.jpg",
        streamUrl: "https://anichan.to/anime/kimetsu-no-yaiba/1",
        desc: "Tanjiro e seus amigos treinam com os Hashiras antes da batalha final."
    },
    {
        id: "a3",
        title: "Jujutsu Kaisen",
        type: "anime",
        year: "2023",
        rating: "8.8",
        poster: "https://image.tmdb.org/t/p/w500/fHpKWfqx7o726UjG2jN97w9QePq.jpg",
        streamUrl: "https://anichan.to/anime/jujutsu-kaisen/1",
        desc: "Yuji Itadori entra no colégio de feiticeiros jujutsu após ingerir um dedo amaldiçoado."
    },
    {
        id: "a4",
        title: "One Piece",
        type: "anime",
        year: "2024",
        rating: "9.0",
        poster: "https://image.tmdb.org/t/p/w500/fcXdJlbSdUEeMSJFsXKsznGwwok.jpg",
        streamUrl: "https://anichan.to/anime/one-piece/1100",
        desc: "Luffy e o bando do Chapéu de Palha continuam a jornada pelo One Piece em Egghead."
    },
    {
        id: "m5",
        title: "Godzilla e Kong: O Novo Império",
        type: "movie",
        year: "2024",
        rating: "7.2",
        poster: "https://image.tmdb.org/t/p/w500/tMefBSflR6PGQLv7WvFPpKLZkyk.jpg",
        streamUrl: "https://superflixapi.top/filme/godzilla-e-kong-o-novo-imperio",
        desc: "Os dois titãs unem forças contra uma colossal ameaça desconhecida na Terra Oca."
    }
];

// Estado da Aplicação
let currentFilter = 'all';
let currentPlayingItem = null;
let currentZoomScale = 1.0;
let currentZoomIndex = 0;
const ZOOM_PRESETS = [
    { label: 'Normal (100%)', scale: 1.0 },
    { label: 'Ajustar (115%)', scale: 1.15 },
    { label: 'Cinema (130%)', scale: 1.30 },
    { label: 'Tela Cheia (150%)', scale: 1.50 }
];

let selectedQualityTag = '1080p';
let selectedFolderKey = 'videos';

// Inicialização
document.addEventListener('DOMContentLoaded', () => {
    renderCatalog();
    setupEventListeners();
    setupKeyboardShortcuts();
});

function renderCatalog(items = DEFAULT_CATALOG) {
    const grid = document.getElementById('mediaGrid');
    grid.innerHTML = '';

    const filtered = items.filter(item => {
        if (currentFilter === 'all') return true;
        return item.type === currentFilter;
    });

    if (filtered.length === 0) {
        grid.innerHTML = `
            <div style="grid-column: 1/-1; text-align: center; padding: 60px 20px; color: #8a90a4;">
                <p style="font-size: 18px; font-weight: 600;">Nenhum título encontrado</p>
                <p style="font-size: 13px; margin-top: 8px;">Tente pesquisar outro termo ou use os botões de categoria.</p>
            </div>
        `;
        return;
    }

    filtered.forEach(item => {
        const card = document.createElement('div');
        card.className = 'media-card';
        card.innerHTML = `
            <div class="poster-wrapper">
                <img src="${item.poster}" alt="${item.title}" class="poster-img" loading="lazy" />
                <div class="rating-badge">★ ${item.rating}</div>
            </div>
            <div class="media-info">
                <div class="media-title">${item.title}</div>
                <div class="media-meta">
                    <span>${item.year}</span>
                    <span style="text-transform: capitalize; color: #e50914; font-weight: 700;">${item.type === 'movie' ? 'Filme' : (item.type === 'series' ? 'Série' : 'Anime')}</span>
                </div>
            </div>
        `;

        card.addEventListener('click', () => openPlayer(item));
        grid.appendChild(card);
    });
}

function openPlayer(item) {
    currentPlayingItem = item;
    const modal = document.getElementById('playerModal');
    const titleEl = document.getElementById('playerTitle');
    const metaEl = document.getElementById('playerMeta');
    const frame = document.getElementById('playerFrame');

    titleEl.textContent = item.title;
    metaEl.textContent = `${item.year} • A2 Miguel Cinema No-Ads`;

    // Carrega o stream
    frame.src = item.streamUrl;
    modal.style.display = 'flex';

    // Reset zoom
    currentZoomScale = 1.0;
    currentZoomIndex = 0;
    applyZoom();

    showToast(`▶ Reproduzindo: ${item.title}`);
}

function closePlayer() {
    const modal = document.getElementById('playerModal');
    const frame = document.getElementById('playerFrame');
    frame.src = 'about:blank';
    modal.style.display = 'none';
    currentPlayingItem = null;
}

// Controle de Zoom
function cycleZoom() {
    currentZoomIndex = (currentZoomIndex + 1) % ZOOM_PRESETS.length;
    currentZoomScale = ZOOM_PRESETS[currentZoomIndex].scale;
    applyZoom();
    showToast(`🔍 Zoom: ${ZOOM_PRESETS[currentZoomIndex].label}`);
}

function applyZoom() {
    const wrapper = document.getElementById('videoFrameWrapper');
    if (wrapper) {
        wrapper.style.transform = `scale(${currentZoomScale})`;
    }
}

// Janela Dedicada de Download & Edição
function openDownloadWindow() {
    if (!currentPlayingItem) {
        showToast('Abra um filme ou série primeiro para baixar!');
        return;
    }

    const overlay = document.getElementById('downloadOverlay');
    const inputName = document.getElementById('inputDownloadName');

    // Sugere nome limpo
    const cleanTitle = currentPlayingItem.title.replace(/[\/\\:*?"<>|]/g, '_').trim();
    inputName.value = `A2_${cleanTitle}_${selectedQualityTag}.mp4`;

    overlay.style.display = 'flex';
}

function closeDownloadWindow() {
    document.getElementById('downloadOverlay').style.display = 'none';
}

function triggerDownload() {
    if (!currentPlayingItem) return;

    const inputName = document.getElementById('inputDownloadName');
    let fileName = inputName.value.trim();
    if (!fileName.endsWith('.mp4')) fileName += '.mp4';

    // Cria e dispara download nativo no Windows
    const dummyBlob = new Blob([
        `A2 Miguel - Stream de Vídeo\nTítulo: ${currentPlayingItem.title}\nQualidade: ${selectedQualityTag}\nPasta: Downloads\\A2-Miguel-PC\\${selectedFolderKey}\nLink Original: ${currentPlayingItem.streamUrl}\n`
    ], { type: 'video/mp4' });

    const downloadUrl = URL.createObjectURL(dummyBlob);
    const a = document.createElement('a');
    a.href = currentPlayingItem.streamUrl || downloadUrl;
    a.download = fileName;
    a.target = '_blank';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);

    closeDownloadWindow();
    showToast(`⬇️ Download iniciado: ${fileName}`);
}

function showToast(message) {
    const existing = document.querySelector('.toast-osd');
    if (existing) existing.remove();

    const toast = document.createElement('div');
    toast.className = 'toast-osd';
    toast.innerHTML = `<span>🎬</span> <span>${message}</span>`;
    document.body.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transition = 'opacity 0.3s ease';
        setTimeout(() => toast.remove(), 300);
    }, 2500);
}

// Configuração de Eventos
function setupEventListeners() {
    // Busca
    const searchInput = document.getElementById('searchInput');
    searchInput.addEventListener('input', (e) => {
        const query = e.target.value.toLowerCase().trim();
        if (!query) {
            renderCatalog(DEFAULT_CATALOG);
            return;
        }

        const filtered = DEFAULT_CATALOG.filter(item =>
            item.title.toLowerCase().includes(query) ||
            item.desc.toLowerCase().includes(query)
        );
        renderCatalog(filtered);
    });

    // Chips de categorias
    document.querySelectorAll('.chip').forEach(chip => {
        chip.addEventListener('click', () => {
            document.querySelectorAll('.chip').forEach(c => c.classList.remove('active'));
            chip.classList.add('active');
            currentFilter = chip.getAttribute('data-type');
            renderCatalog(DEFAULT_CATALOG);
        });
    });

    // Qualidade no modal de download
    document.querySelectorAll('.quality-box').forEach(box => {
        box.addEventListener('click', () => {
            document.querySelectorAll('.quality-box').forEach(b => b.classList.remove('selected'));
            box.classList.add('selected');
            selectedQualityTag = box.getAttribute('data-quality');

            if (currentPlayingItem) {
                const cleanTitle = currentPlayingItem.title.replace(/[\/\\:*?"<>|]/g, '_').trim();
                document.getElementById('inputDownloadName').value = `A2_${cleanTitle}_${selectedQualityTag}.mp4`;
            }
        });
    });

    // Pasta no modal de download
    document.querySelectorAll('.folder-box').forEach(box => {
        box.addEventListener('click', () => {
            document.querySelectorAll('.folder-box').forEach(b => b.classList.remove('selected-blue'));
            box.classList.add('selected-blue');
            selectedFolderKey = box.getAttribute('data-folder');
        });
    });

    // Botões do Player
    document.getElementById('btnClosePlayer').addEventListener('click', closePlayer);
    document.getElementById('btnPlayerZoom').addEventListener('click', cycleZoom);
    document.getElementById('btnPlayerDownload').addEventListener('click', openDownloadWindow);

    // Botões do Modal de Download
    document.getElementById('btnCloseDownload').addEventListener('click', closeDownloadWindow);
    document.getElementById('btnStartDownloadAction').addEventListener('click', triggerDownload);
    document.getElementById('btnCancelDownloadAction').addEventListener('click', closeDownloadWindow);
}

// Atalhos de teclado no PC
function setupKeyboardShortcuts() {
    window.addEventListener('keydown', (e) => {
        // Tecla D: Abre janela de download
        if (e.key === 'd' || e.key === 'D') {
            if (currentPlayingItem && document.getElementById('downloadOverlay').style.display !== 'flex') {
                openDownloadWindow();
            }
        }
        // Tecla Z: Cicla zoom
        if (e.key === 'z' || e.key === 'Z') {
            if (currentPlayingItem) {
                cycleZoom();
            }
        }
        // Tecla Escape: Fecha janela de download ou player
        if (e.key === 'Escape') {
            if (document.getElementById('downloadOverlay').style.display === 'flex') {
                closeDownloadWindow();
            } else if (currentPlayingItem) {
                closePlayer();
            }
        }
    });
}
