// A2 Miguel PC - Motor Ao Vivo de Filmes e Séries (Igual ao Celular, sem Anúncios e sem AniChan)

const STORAGE_KEY_FALLBACK = 'a2_miguel_pc_data_v2';

const state = {
    view: 'home', // home | movies | series | history | favorites
    typeFilter: 'all', // all | movie | series
    searchQuery: '',
    catalogPage: 1,
    catalogItems: [],
    searchResults: [],
    isLoadingCatalog: false,
    userData: {
        app: 'A2 Miguel',
        version: 2,
        exportedAt: Date.now(),
        history: [],
        recentSearches: [],
        preferences: {
            zoomPreset: 'normal',
            zoomScale: 1.0,
            focusX: 0.5,
            focusY: 0.48,
            adBlockAutoUpdate: true
        }
    },
    adblock: {
        count: 72,
        baselineCount: 72,
        blockedCount: 0,
        lastUpdate: 0,
        autoUpdate: true
    },
    details: {
        item: null,
        videoId: '',
        seasons: [1],
        currentSeason: 1,
        audio: 'Dublado',
        episodes: []
    },
    player: {
        item: null,
        isSeries: false,
        videoId: '',
        season: 1,
        episodeNumber: 1,
        episodeTitle: '',
        episodeUrl: '',
        servers: [],
        activeServerIdx: 0,
        nextUrl: null,
        prevUrl: null,
        seasons: [1],
        episodes: [],
        zoomIndex: 0
    },
    download: {
        quality: '1080p',
        folder: 'videos'
    }
};

const ZOOM_PRESETS = [
    { key: 'normal', label: '100%', scale: 1.0 },
    { key: 'fit', label: '115%', scale: 1.15 },
    { key: 'cinema', label: '130%', scale: 1.3 },
    { key: 'full', label: '150%', scale: 1.5 }
];

document.addEventListener('DOMContentLoaded', async () => {
    bindEvents();
    bindKeyboardShortcuts();
    await loadUserData();
    await refreshAdblockStatus();
    await loadLiveCatalog(1, false);
});

function bindEvents() {
    document.getElementById('btnBrandHome').addEventListener('click', () => switchView('home'));
    document.querySelectorAll('.nav-item').forEach(btn => {
        btn.addEventListener('click', () => switchView(btn.dataset.view));
    });

    document.querySelectorAll('.filter-pill').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.filter-pill').forEach(p => p.classList.remove('active'));
            btn.classList.add('active');
            state.typeFilter = btn.dataset.type;
            renderCurrentView();
        });
    });

    const searchInput = document.getElementById('searchInput');
    const clearBtn = document.getElementById('btnClearSearch');
    let debounceTimer = null;

    searchInput.addEventListener('input', () => {
        const val = searchInput.value.trim();
        clearBtn.style.display = val ? 'inline-block' : 'none';
        clearTimeout(debounceTimer);
        debounceTimer = setTimeout(() => {
            if (!val) {
                state.searchQuery = '';
                state.searchResults = [];
                renderCurrentView();
            } else if (val.length >= 2) {
                performLiveSearch(val, false);
            }
        }, 450);
    });

    document.getElementById('searchForm').addEventListener('submit', (e) => {
        e.preventDefault();
        const val = searchInput.value.trim();
        if (val) performLiveSearch(val, true);
    });

    clearBtn.addEventListener('click', () => {
        searchInput.value = '';
        clearBtn.style.display = 'none';
        state.searchQuery = '';
        state.searchResults = [];
        renderCurrentView();
    });

    document.getElementById('btnRefreshCatalog').addEventListener('click', () => loadLiveCatalog(1, false));
    document.getElementById('btnLoadMore').addEventListener('click', () => loadLiveCatalog(state.catalogPage + 1, true));
    document.getElementById('btnViewAllHistory').addEventListener('click', () => switchView('history'));
    document.getElementById('btnClearHistory').addEventListener('click', clearAllHistory);

    document.getElementById('btnOpenFolder').addEventListener('click', async () => {
        try {
            await fetch('/api/open-folder', { method: 'POST' });
            showToast('📂 Pasta Downloads\\A2-Miguel-PC aberta!');
        } catch (_) {
            showToast('📂 Pasta: Downloads\\A2-Miguel-PC');
        }
    });

    // Modais de Dados e AdBlock
    const openDataModal = () => {
        updateDataModalUi();
        document.getElementById('dataModal').style.display = 'flex';
    };
    document.getElementById('btnOpenDataTop').addEventListener('click', openDataModal);
    document.getElementById('btnOpenDataModal').addEventListener('click', openDataModal);
    document.getElementById('btnOpenAdBlockModal').addEventListener('click', openDataModal);
    document.getElementById('btnCloseDataModal').addEventListener('click', () => {
        document.getElementById('dataModal').style.display = 'none';
    });

    document.getElementById('btnUpdateAdblockNow').addEventListener('click', async () => {
        showToast('🛡️ Atualizando listas AdBlock...');
        try {
            const res = await fetch('/api/adblock/update', { method: 'POST' });
            const json = await res.json();
            state.adblock = { ...state.adblock, ...json };
            updateAdblockUi();
            updateDataModalUi();
            showToast(`🛡️ AdBlock atualizado: ${state.adblock.count} regras ativas!`);
        } catch (_) {
            showToast('🛡️ Usando 72+ regras locais integradas.');
        }
    });

    document.getElementById('btnExportJson').addEventListener('click', exportJsonBackup);
    document.getElementById('inputImportJson').addEventListener('change', importJsonBackup);

    // Modal Detalhes
    document.getElementById('btnCloseDetails').addEventListener('click', () => {
        document.getElementById('detailsModal').style.display = 'none';
    });
    document.getElementById('btnAudioDub').addEventListener('click', () => switchDetailsAudio('Dublado'));
    document.getElementById('btnAudioLeg').addEventListener('click', () => switchDetailsAudio('Legendado'));
    document.getElementById('btnDetailsContinue').addEventListener('click', () => continueFromDetails(false));
    document.getElementById('btnDetailsRestart').addEventListener('click', () => continueFromDetails(true));
    document.getElementById('btnDetailsFavorite').addEventListener('click', () => {
        if (state.details.item) toggleFavoriteItem(state.details.item);
    });
    document.getElementById('btnDetailsCompleted').addEventListener('click', () => {
        if (state.details.item) toggleCompletedItem(state.details.item);
    });

    // Player
    document.getElementById('btnClosePlayer').addEventListener('click', closePlayer);
    document.getElementById('btnPlayerZoom').addEventListener('click', cycleZoom);
    document.getElementById('btnRestartVideo').addEventListener('click', restartCurrentVideo);
    document.getElementById('btnPrevEpisode').addEventListener('click', () => playAdjacentEpisode(-1));
    document.getElementById('btnNextEpisode').addEventListener('click', () => playAdjacentEpisode(1));
    document.getElementById('btnToggleEpisodesDrawer').addEventListener('click', () => {
        const drawer = document.getElementById('episodesDrawer');
        drawer.style.display = drawer.style.display === 'none' ? 'flex' : 'none';
    });
    document.getElementById('btnCloseDrawer').addEventListener('click', () => {
        document.getElementById('episodesDrawer').style.display = 'none';
    });
    document.getElementById('btnPlayerMarkWatched').addEventListener('click', () => {
        if (state.player.item) toggleCompletedItem(state.player.item);
    });
    document.getElementById('btnPlayerDownload').addEventListener('click', openDownloadModal);

    // Download
    document.getElementById('btnCloseDownload').addEventListener('click', closeDownloadModal);
    document.getElementById('btnCancelDownloadAction').addEventListener('click', closeDownloadModal);
    document.getElementById('btnAutoNameDownload').addEventListener('click', () => {
        document.getElementById('inputDownloadName').value = `A2_Video_${Date.now()}.mp4`;
    });
    document.getElementById('btnStartDownloadAction').addEventListener('click', triggerDownload);

    document.querySelectorAll('.quality-box').forEach(box => {
        box.addEventListener('click', () => {
            document.querySelectorAll('.quality-box').forEach(b => b.classList.remove('selected'));
            box.classList.add('selected');
            state.download.quality = box.dataset.quality;
            if (state.player.item) {
                const clean = sanitizeFilename(state.player.item.title);
                document.getElementById('inputDownloadName').value = `A2_${clean}_${state.download.quality}.mp4`;
            }
        });
    });

    document.querySelectorAll('.folder-box').forEach(box => {
        box.addEventListener('click', () => {
            document.querySelectorAll('.folder-box').forEach(b => b.classList.remove('selected-blue'));
            box.classList.add('selected-blue');
            state.download.folder = box.dataset.folder;
        });
    });
}

function bindKeyboardShortcuts() {
    window.addEventListener('keydown', (e) => {
        if (document.activeElement && document.activeElement.tagName === 'INPUT') return;
        const playerOpen = document.getElementById('playerModal').style.display !== 'none';
        const downloadOpen = document.getElementById('downloadOverlay').style.display !== 'none';
        const detailsOpen = document.getElementById('detailsModal').style.display !== 'none';

        if ((e.key === 'd' || e.key === 'D') && playerOpen && !downloadOpen) {
            openDownloadModal();
        } else if ((e.key === 'z' || e.key === 'Z') && playerOpen) {
            cycleZoom();
        } else if (e.key === 'Escape') {
            if (downloadOpen) closeDownloadModal();
            else if (document.getElementById('dataModal').style.display !== 'none') {
                document.getElementById('dataModal').style.display = 'none';
            } else if (playerOpen) closePlayer();
            else if (detailsOpen) document.getElementById('detailsModal').style.display = 'none';
        }
    });
}

// --- Persistência Gratuita (Arquivo JSON em Downloads + localStorage) ---

async function loadUserData() {
    try {
        const res = await fetch('/api/data');
        if (res.ok) {
            const serverData = await res.json();
            if (serverData && Array.isArray(serverData.history)) {
                state.userData = mergeUserData(state.userData, serverData);
            }
        }
    } catch (_) {
        const raw = localStorage.getItem(STORAGE_KEY_FALLBACK);
        if (raw) {
            try {
                state.userData = mergeUserData(state.userData, JSON.parse(raw));
            } catch (_) {}
        }
    }

    if (state.userData.preferences && state.userData.preferences.zoomPreset) {
        const idx = ZOOM_PRESETS.findIndex(p => p.key === state.userData.preferences.zoomPreset);
        if (idx >= 0) state.player.zoomIndex = idx;
    }
    renderRecentSearches();
    renderContinueRow();
}

async function persistUserData() {
    state.userData.exportedAt = Date.now();
    try {
        localStorage.setItem(STORAGE_KEY_FALLBACK, JSON.stringify(state.userData));
    } catch (_) {}
    try {
        await fetch('/api/data', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(state.userData)
        });
    } catch (_) {}
}

function mergeUserData(base, incoming) {
    const map = new Map();
    const all = [...(base.history || []), ...(incoming.history || [])];
    for (const item of all) {
        const key = item.id || item.itemUrl;
        if (!key) continue;
        const prev = map.get(key);
        if (!prev) {
            map.set(key, normalizeHistoryEntry(item));
        } else {
            const watched = Array.from(new Set([...(prev.watchedEpisodes || []), ...(item.watchedEpisodes || [])]));
            const newer = (item.timestamp || 0) >= (prev.timestamp || 0) ? item : prev;
            map.set(key, normalizeHistoryEntry({
                ...newer,
                watchedEpisodes: watched,
                favorite: Boolean(prev.favorite || item.favorite),
                completed: Boolean(prev.completed || item.completed)
            }));
        }
    }
    const history = Array.from(map.values()).sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0)).slice(0, 100);
    const recentSearches = Array.from(new Set([...(incoming.recentSearches || []), ...(base.recentSearches || [])])).slice(0, 15);
    return {
        app: 'A2 Miguel',
        version: 2,
        exportedAt: Date.now(),
        history,
        recentSearches,
        preferences: { ...(base.preferences || {}), ...(incoming.preferences || {}) }
    };
}

function normalizeHistoryEntry(raw) {
    const id = raw.id || raw.itemUrl || raw.currentUrl || '';
    return {
        id,
        itemUrl: id,
        title: raw.title || 'Sem Título',
        coverUrl: raw.coverUrl || raw.cover || '',
        currentUrl: raw.currentUrl || id,
        isSeries: Boolean(raw.isSeries || raw.mediaType === 'series'),
        mediaType: (raw.isSeries || raw.mediaType === 'series') ? 'series' : 'movie',
        season: Number(raw.season || 1),
        episodeNumber: Number(raw.episodeNumber || 1),
        episodeTitle: raw.episodeTitle || '',
        watchedEpisodes: Array.isArray(raw.watchedEpisodes) ? raw.watchedEpisodes : [],
        favorite: Boolean(raw.favorite),
        completed: Boolean(raw.completed),
        progressSeconds: Number(raw.progressSeconds || 0),
        timestamp: Number(raw.timestamp || Date.now())
    };
}

function findHistoryEntry(urlOrId) {
    return state.userData.history.find(h => h.id === urlOrId || h.itemUrl === urlOrId);
}

function saveHistoryProgress(payload, markWatched = true) {
    const existing = findHistoryEntry(payload.id);
    const watched = new Set(existing?.watchedEpisodes || []);
    if (markWatched && payload.isSeries) {
        watched.add(`${payload.season || 1}x${payload.episodeNumber || 1}`);
    }
    const entry = normalizeHistoryEntry({
        ...existing,
        ...payload,
        watchedEpisodes: Array.from(watched),
        favorite: Boolean(payload.favorite || existing?.favorite),
        completed: Boolean(payload.completed || existing?.completed),
        timestamp: Date.now()
    });
    state.userData.history = [entry, ...state.userData.history.filter(h => h.id !== entry.id)].slice(0, 100);
    persistUserData();
    renderContinueRow();
    renderCurrentView();
}

function toggleFavoriteItem(item) {
    const id = item.url || item.id;
    const existing = findHistoryEntry(id);
    const nextFav = !(existing?.favorite);
    saveHistoryProgress({
        id,
        title: item.title,
        coverUrl: item.coverUrl || item.cover || '',
        currentUrl: existing?.currentUrl || id,
        isSeries: Boolean(item.isSeries),
        season: existing?.season || 1,
        episodeNumber: existing?.episodeNumber || 1,
        episodeTitle: existing?.episodeTitle || '',
        favorite: nextFav
    }, false);
    if (state.details.item && (state.details.item.url === id || state.details.item.id === id)) {
        document.getElementById('btnDetailsFavorite').textContent = nextFav ? '♥ Favoritado' : '♡ Favoritar';
    }
    showToast(nextFav ? `❤️ ${item.title} adicionado aos Favoritos` : `Removido dos Favoritos`);
}

function toggleCompletedItem(item) {
    const id = item.url || item.id;
    const existing = findHistoryEntry(id);
    const nextCompleted = !(existing?.completed);
    saveHistoryProgress({
        id,
        title: item.title,
        coverUrl: item.coverUrl || item.cover || '',
        currentUrl: existing?.currentUrl || id,
        isSeries: Boolean(item.isSeries),
        season: existing?.season || 1,
        episodeNumber: existing?.episodeNumber || 1,
        episodeTitle: existing?.episodeTitle || '',
        completed: nextCompleted
    }, false);
    updateWatchedButtons();
    showToast(nextCompleted ? `✓ ${item.title} marcado como Já Visto!` : 'Marcado como Não Visto');
}

function clearAllHistory() {
    state.userData.history = [];
    persistUserData();
    renderContinueRow();
    renderCurrentView();
    showToast('🗑️ Histórico limpo!');
}

function exportJsonBackup() {
    const blob = new Blob([JSON.stringify(state.userData, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `a2_miguel_backup_${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(url);
    showToast('📤 Backup JSON exportado!');
}

function importJsonBackup(event) {
    const file = event.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = async (e) => {
        try {
            const parsed = JSON.parse(e.target.result);
            const normalized = Array.isArray(parsed) ? { history: parsed } : parsed;
            state.userData = mergeUserData(state.userData, normalized);
            await persistUserData();
            renderRecentSearches();
            renderContinueRow();
            renderCurrentView();
            showToast(`✅ Importado! ${state.userData.history.length} títulos sincronizados.`);
        } catch (_) {
            showToast('❌ Arquivo JSON inválido.');
        }
    };
    reader.readAsText(file);
    event.target.value = '';
}

// --- AdBlock Status ---

async function refreshAdblockStatus() {
    try {
        const res = await fetch('/api/adblock/status');
        if (res.ok) {
            const data = await res.json();
            state.adblock = { ...state.adblock, ...data };
            updateAdblockUi();
        }
    } catch (_) {}
}

function updateAdblockUi() {
    const textEl = document.getElementById('sidebarAdblockText');
    const countEl = document.getElementById('sidebarBlockedCount');
    const pillEl = document.getElementById('playerAdblockPill');
    if (textEl) textEl.textContent = `${state.adblock.count} regras • Auto-atualizável`;
    if (countEl) countEl.textContent = `${state.adblock.blockedCount} bloqueios nesta sessão`;
    if (pillEl) pillEl.textContent = `🛡️ AdBlock ${state.adblock.count}`;
}

function updateDataModalUi() {
    const statusEl = document.getElementById('modalAdblockStatus');
    if (statusEl) {
        statusEl.textContent = `${state.adblock.count} regras ativas (${state.adblock.blockedCount} bloqueios nesta sessão)`;
    }
    const pathEl = document.getElementById('modalDataPath');
    if (pathEl && state.adblock.dataFile) {
        pathEl.textContent = state.adblock.dataFile;
    }
}

// --- Catálogo Ao Vivo & Busca ---

async function loadLiveCatalog(page = 1, append = false) {
    if (state.isLoadingCatalog) return;
    state.isLoadingCatalog = true;
    const banner = document.getElementById('catalogStatusBanner');
    banner.style.display = 'none';

    if (!append) {
        document.getElementById('totalTitlesCount').textContent = 'Carregando ao vivo...';
    }

    let category = 'home';
    if (state.view === 'movies') category = 'filmes';
    else if (state.view === 'series') category = 'series';

    try {
        const res = await fetch(`/api/catalog?category=${encodeURIComponent(category)}&page=${page}`);
        const data = await res.json();
        const items = Array.isArray(data.items) ? data.items : [];
        state.catalogPage = page;
        if (append) {
            const existingUrls = new Set(state.catalogItems.map(i => i.url));
            items.forEach(it => {
                if (!existingUrls.has(it.url)) state.catalogItems.push(it);
            });
        } else {
            state.catalogItems = items;
        }
        if (items.length === 0 && !append) {
            banner.style.display = 'block';
            banner.innerHTML = '⚠️ Não foi possível carregar o catálogo ao vivo neste momento. Verifique sua internet e clique em <strong>🔄 Atualizar</strong>.';
        }
        document.getElementById('paginationBar').style.display = items.length >= 12 ? 'flex' : 'none';
    } catch (_) {
        banner.style.display = 'block';
        banner.innerHTML = '⚠️ Servidor local desconectado ou sem internet. Clique em <strong>🔄 Atualizar</strong>.';
    } finally {
        state.isLoadingCatalog = false;
        renderCurrentView();
    }
}

async function performLiveSearch(query, saveRecent = true) {
    state.searchQuery = query;
    if (saveRecent && query.length >= 2) {
        state.userData.recentSearches = [
            query,
            ...state.userData.recentSearches.filter(q => q.toLowerCase() !== query.toLowerCase())
        ].slice(0, 15);
        persistUserData();
        renderRecentSearches();
    }

    document.getElementById('totalTitlesCount').textContent = 'Buscando...';
    try {
        const res = await fetch(`/api/search?q=${encodeURIComponent(query)}`);
        const data = await res.json();
        state.searchResults = Array.isArray(data.items) ? data.items : [];
    } catch (_) {
        state.searchResults = state.catalogItems.filter(i =>
            i.title.toLowerCase().includes(query.toLowerCase())
        );
    }
    renderCurrentView();
}

function switchView(viewName) {
    state.view = viewName;
    document.querySelectorAll('.nav-item').forEach(b => b.classList.toggle('active', b.dataset.view === viewName));
    if (viewName === 'movies' || viewName === 'series' || viewName === 'home') {
        state.searchQuery = '';
        state.searchResults = [];
        document.getElementById('searchInput').value = '';
        document.getElementById('btnClearSearch').style.display = 'none';
        loadLiveCatalog(1, false);
    } else {
        renderCurrentView();
    }
}

function renderRecentSearches() {
    const row = document.getElementById('recentSearchesRow');
    row.innerHTML = '';
    const list = state.userData.recentSearches || [];
    const fallbackSuggestions = ['Vingadores', 'Deadpool', 'Breaking Bad', 'Stranger Things', 'Batman'];
    const chips = list.length ? list : fallbackSuggestions;
    chips.slice(0, 8).forEach(term => {
        const btn = document.createElement('button');
        btn.className = 'recent-chip';
        btn.textContent = list.length ? `🕒 ${term}` : `✨ ${term}`;
        btn.addEventListener('click', () => {
            document.getElementById('searchInput').value = term;
            document.getElementById('btnClearSearch').style.display = 'inline-block';
            performLiveSearch(term, true);
        });
        row.appendChild(btn);
    });
}

function renderContinueRow() {
    const section = document.getElementById('continueSection');
    const row = document.getElementById('continueRow');
    const items = state.userData.history.slice(0, 12);
    if (!items.length || state.searchQuery) {
        section.style.display = 'none';
        return;
    }
    section.style.display = 'block';
    row.innerHTML = '';
    items.forEach(entry => {
        const card = document.createElement('div');
        card.className = 'continue-card';
        const sub = entry.isSeries
            ? `T${entry.season} &bull; Ep ${entry.episodeNumber}${entry.completed ? ' • ✓ Concluído' : ''}`
            : (entry.completed ? '✓ Filme Já Visto' : 'Continuar Filme');
        card.innerHTML = `
            <div class="continue-thumb">
                <img src="${escapeAttr(entry.coverUrl)}" alt="${escapeAttr(entry.title)}" onerror="this.style.display='none'">
            </div>
            <div class="continue-body">
                <div class="continue-title">${escapeHtml(entry.title)}</div>
                <div class="continue-sub">${sub}</div>
            </div>
        `;
        card.addEventListener('click', () => {
            if (entry.isSeries) {
                openPlayerDirect({
                    title: entry.title,
                    url: entry.id,
                    coverUrl: entry.coverUrl,
                    isSeries: true
                }, entry.currentUrl, entry.season, entry.episodeNumber, entry.episodeTitle);
            } else {
                openPlayerDirect({
                    title: entry.title,
                    url: entry.id,
                    coverUrl: entry.coverUrl,
                    isSeries: false
                }, entry.currentUrl, 1, 1, entry.title);
            }
        });
        row.appendChild(card);
    });
}

function renderCurrentView() {
    const grid = document.getElementById('mediaGrid');
    const titleEl = document.getElementById('gridSectionTitle');
    const countEl = document.getElementById('totalTitlesCount');
    const heroEl = document.getElementById('heroBanner');
    const clearHistBtn = document.getElementById('btnClearHistory');

    let sourceItems = [];
    clearHistBtn.style.display = 'none';

    if (state.searchQuery) {
        titleEl.textContent = `🔍 Resultados para "${state.searchQuery}"`;
        sourceItems = state.searchResults;
        heroEl.style.display = 'none';
    } else if (state.view === 'history') {
        titleEl.textContent = '🕒 Meu Histórico & Já Vistos';
        sourceItems = state.userData.history.map(h => ({
            title: h.title,
            cover: h.coverUrl,
            url: h.id,
            isSeries: h.isSeries,
            year: h.isSeries ? `T${h.season} E${h.episodeNumber}` : 'Filme',
            historyEntry: h
        }));
        heroEl.style.display = 'none';
        clearHistBtn.style.display = sourceItems.length ? 'inline-flex' : 'none';
    } else if (state.view === 'favorites') {
        titleEl.textContent = '❤️ Meus Favoritos';
        sourceItems = state.userData.history.filter(h => h.favorite).map(h => ({
            title: h.title,
            cover: h.coverUrl,
            url: h.id,
            isSeries: h.isSeries,
            year: h.isSeries ? `T${h.season} E${h.episodeNumber}` : 'Filme',
            historyEntry: h
        }));
        heroEl.style.display = 'none';
    } else {
        titleEl.textContent = state.view === 'movies'
            ? '🎬 Todos os Filmes'
            : (state.view === 'series' ? '📺 Todas as Séries' : '🔥 Catálogo Completo ao Vivo');
        sourceItems = state.catalogItems;
        renderHeroBanner(sourceItems[0]);
    }

    const filtered = sourceItems.filter(item => {
        if (state.typeFilter === 'movie') return !item.isSeries;
        if (state.typeFilter === 'series') return Boolean(item.isSeries);
        return true;
    });

    countEl.textContent = `${filtered.length} títulos`;
    grid.innerHTML = '';

    if (filtered.length === 0) {
        grid.innerHTML = `
            <div style="grid-column: 1/-1; text-align:center; padding: 60px 20px; color: var(--text-muted);">
                <p style="font-size: 16px; font-weight: 700;">Nenhum título encontrado nesta aba</p>
                <p style="font-size: 13px; margin-top: 6px;">Use a barra de busca acima ou alterne o filtro Todos / Filmes / Séries.</p>
            </div>
        `;
        return;
    }

    filtered.forEach(item => {
        const hist = item.historyEntry || findHistoryEntry(item.url);
        const card = document.createElement('div');
        card.className = 'media-card';
        const watchedTag = hist && (hist.completed || (hist.watchedEpisodes && hist.watchedEpisodes.length > 0))
            ? `<span class="card-pill-watched">${hist.completed ? '✓ JÁ VISTO' : `${hist.watchedEpisodes.length} EP`}</span>`
            : '';

        card.innerHTML = `
            <div class="poster-wrapper">
                <img src="${escapeAttr(item.cover)}" alt="${escapeAttr(item.title)}" class="poster-img" loading="lazy">
                <div class="card-badges-top">
                    <span class="card-pill-type">${item.isSeries ? '📺 SÉRIE' : '🎬 FILME'}</span>
                    ${watchedTag}
                </div>
                <button class="card-fav-btn ${hist?.favorite ? 'active' : ''}" title="Favoritar">${hist?.favorite ? '♥' : '♡'}</button>
            </div>
            <div class="media-info">
                <div class="media-title">${escapeHtml(item.title)}</div>
                <div class="media-meta">
                    <span>${escapeHtml(item.year || 'HD')}</span>
                    <span style="color: #34d399; font-weight: 700;">Dublado</span>
                </div>
            </div>
        `;

        card.querySelector('.card-fav-btn').addEventListener('click', (e) => {
            e.stopPropagation();
            toggleFavoriteItem(item);
        });
        card.addEventListener('click', () => openItem(item));
        grid.appendChild(card);
    });
}

function renderHeroBanner(heroItem) {
    const hero = document.getElementById('heroBanner');
    if (!heroItem || state.view !== 'home') {
        hero.style.display = 'none';
        return;
    }
    hero.style.display = 'flex';
    document.getElementById('heroBackdrop').style.backgroundImage = `url('${heroItem.cover}')`;
    document.getElementById('heroTitle').textContent = heroItem.title;
    document.getElementById('heroTypeBadge').textContent = heroItem.isSeries ? '📺 SÉRIE' : '🎬 FILME';
    document.getElementById('btnHeroPlay').onclick = () => openItem(heroItem);
    document.getElementById('btnHeroFavorite').onclick = () => toggleFavoriteItem(heroItem);
}

// --- Detalhes da Série & Temporadas ---

async function openItem(item) {
    if (!item.isSeries) {
        openPlayerDirect(item, item.url, 1, 1, item.title);
        return;
    }

    const modal = document.getElementById('detailsModal');
    const hist = findHistoryEntry(item.url);
    state.details = {
        item,
        videoId: '',
        seasons: [1],
        currentSeason: hist?.season || 1,
        audio: 'Dublado',
        episodes: []
    };

    document.getElementById('detailsPoster').src = item.cover || '';
    document.getElementById('detailsTitle').textContent = item.title;
    document.getElementById('detailsTypeBadge').textContent = '📺 SÉRIE';
    document.getElementById('detailsYearBadge').textContent = item.year || 'HD';
    document.getElementById('detailsDesc').textContent = 'Carregando temporadas e lista completa de episódios...';
    document.getElementById('detailsSeriesControls').style.display = 'flex';
    document.getElementById('detailsEpisodesList').innerHTML = '<p style="color: var(--text-muted);">Carregando episódios...</p>';
    document.getElementById('btnDetailsRestart').style.display = hist ? 'inline-flex' : 'none';
    document.getElementById('btnDetailsContinue').textContent = hist
        ? `▶ Continuar T${hist.season} Ep ${hist.episodeNumber}`
        : '▶ Assistir Temporada 1 Ep 1';
    document.getElementById('btnDetailsFavorite').textContent = hist?.favorite ? '♥ Favoritado' : '♡ Favoritar';
    updateWatchedButtons();
    modal.style.display = 'flex';

    try {
        const res = await fetch(`/api/series?url=${encodeURIComponent(item.url)}`);
        const data = await res.json();
        state.details.videoId = data.videoId || '';
        state.details.seasons = Array.isArray(data.seasons) && data.seasons.length ? data.seasons : [1];
        if (!state.details.seasons.includes(state.details.currentSeason)) {
            state.details.currentSeason = state.details.seasons[0];
        }
        document.getElementById('detailsDesc').textContent = data.synopsis || 'Série disponível com áudio Dublado e Legendado sem anúncios.';
        renderDetailsSeasons();
        await loadDetailsEpisodes(state.details.currentSeason, state.details.audio, data.fallbackEpisodes || []);
    } catch (_) {
        document.getElementById('detailsDesc').textContent = 'Falha ao carregar metadados da série.';
    }
}

function updateWatchedButtons() {
    if (!state.details.item) return;
    const hist = findHistoryEntry(state.details.item.url);
    const badge = document.getElementById('detailsWatchedBadge');
    const btn = document.getElementById('btnDetailsCompleted');
    if (hist?.completed) {
        badge.style.display = 'inline-block';
        btn.textContent = '✓ Já Visto (Desmarcar)';
    } else {
        badge.style.display = 'none';
        btn.textContent = '✓ Marcar Já Visto';
    }
}

function renderDetailsSeasons() {
    const container = document.getElementById('detailsSeasonTabs');
    container.innerHTML = '';
    state.details.seasons.forEach(s => {
        const btn = document.createElement('button');
        btn.className = `season-tab ${s === state.details.currentSeason ? 'active' : ''}`;
        btn.textContent = `Temporada ${s}`;
        btn.addEventListener('click', () => {
            state.details.currentSeason = s;
            renderDetailsSeasons();
            loadDetailsEpisodes(s, state.details.audio);
        });
        container.appendChild(btn);
    });
}

function switchDetailsAudio(audio) {
    state.details.audio = audio;
    document.getElementById('btnAudioDub').classList.toggle('active', audio === 'Dublado');
    document.getElementById('btnAudioLeg').classList.toggle('active', audio === 'Legendado');
    loadDetailsEpisodes(state.details.currentSeason, audio);
}

async function loadDetailsEpisodes(season, audio, fallbackEpisodes = []) {
    const listEl = document.getElementById('detailsEpisodesList');
    listEl.innerHTML = '<p style="color: var(--text-muted);">Carregando episódios da temporada...</p>';
    let eps = [];
    if (state.details.videoId) {
        try {
            const res = await fetch(`/api/episodes?id=${encodeURIComponent(state.details.videoId)}&season=${season}&audio=${encodeURIComponent(audio)}`);
            const data = await res.json();
            if (Array.isArray(data.episodes)) eps = data.episodes;
        } catch (_) {}
    }
    if (!eps.length && fallbackEpisodes.length) {
        eps = fallbackEpisodes.filter(e => Number(e.season) === Number(season));
        if (!eps.length) eps = fallbackEpisodes;
    }
    state.details.episodes = eps;
    renderDetailsEpisodes();
}

function renderDetailsEpisodes() {
    const listEl = document.getElementById('detailsEpisodesList');
    listEl.innerHTML = '';
    const hist = state.details.item ? findHistoryEntry(state.details.item.url) : null;
    if (!state.details.episodes.length) {
        listEl.innerHTML = '<p style="color: var(--text-muted);">Nenhum episódio encontrado para esta temporada.</p>';
        return;
    }
    state.details.episodes.forEach(ep => {
        const epKey = `${state.details.currentSeason}x${ep.number}`;
        const isWatched = hist?.watchedEpisodes?.includes(epKey);
        const isCurrent = hist && hist.season === state.details.currentSeason && hist.episodeNumber === ep.number;
        const card = document.createElement('div');
        card.className = `episode-item ${isWatched ? 'watched' : ''} ${isCurrent ? 'current' : ''}`;
        card.innerHTML = `
            <span class="ep-num">EP ${ep.number}</span>
            <span class="ep-title">${escapeHtml(ep.title || `Episódio ${ep.number}`)}</span>
            <span class="ep-check">${isWatched ? '✓ Visto' : '▶'}</span>
        `;
        card.addEventListener('click', () => {
            document.getElementById('detailsModal').style.display = 'none';
            openPlayerDirect(
                state.details.item,
                ep.url,
                state.details.currentSeason,
                ep.number,
                ep.title,
                state.details.seasons,
                state.details.episodes
            );
        });
        listEl.appendChild(card);
    });
}

function continueFromDetails(restartFromBeginning) {
    if (!state.details.item) return;
    const hist = findHistoryEntry(state.details.item.url);
    document.getElementById('detailsModal').style.display = 'none';
    if (restartFromBeginning) {
        loadDetailsEpisodes(1, state.details.audio).then(() => {
            const firstEp = state.details.episodes[0];
            openPlayerDirect(
                state.details.item,
                firstEp ? firstEp.url : state.details.item.url,
                1,
                1,
                firstEp?.title || 'Episódio 1',
                state.details.seasons,
                state.details.episodes
            );
        });
        return;
    }

    if (hist && hist.currentUrl) {
        openPlayerDirect(
            state.details.item,
            hist.currentUrl,
            hist.season,
            hist.episodeNumber,
            hist.episodeTitle,
            state.details.seasons,
            state.details.episodes
        );
    } else if (state.details.episodes.length) {
        const first = state.details.episodes[0];
        openPlayerDirect(
            state.details.item,
            first.url,
            state.details.currentSeason,
            first.number,
            first.title,
            state.details.seasons,
            state.details.episodes
        );
    }
}

// --- Player & Multi-Servidor Sem Anúncios ---

async function openPlayerDirect(item, playUrl, season = 1, episodeNumber = 1, episodeTitle = '', seasons = [1], episodes = []) {
    state.player.item = item;
    state.player.isSeries = Boolean(item.isSeries);
    state.player.season = season;
    state.player.episodeNumber = episodeNumber;
    state.player.episodeTitle = episodeTitle || item.title;
    state.player.episodeUrl = playUrl || item.url;
    state.player.seasons = seasons && seasons.length ? seasons : [1];
    state.player.episodes = episodes || [];
    state.player.servers = [];
    state.player.activeServerIdx = 0;

    document.getElementById('playerModal').style.display = 'flex';
    document.getElementById('episodesDrawer').style.display = 'none';
    document.getElementById('playerLoadingOverlay').style.display = 'flex';
    document.getElementById('playerTitle').textContent = state.player.isSeries
        ? `${item.title} — T${season} Ep ${episodeNumber}`
        : item.title;
    document.getElementById('playerMeta').textContent = `${state.player.episodeTitle} • AdBlock ${state.adblock.count}+ ativo`;
    document.getElementById('btnToggleEpisodesDrawer').style.display = state.player.isSeries ? 'inline-flex' : 'none';
    document.getElementById('btnPrevEpisode').style.display = state.player.isSeries ? 'inline-flex' : 'none';
    document.getElementById('btnNextEpisode').style.display = state.player.isSeries ? 'inline-flex' : 'none';

    applyZoom();
    saveHistoryProgress({
        id: item.url || item.id,
        title: item.title,
        coverUrl: item.coverUrl || item.cover || '',
        currentUrl: state.player.episodeUrl,
        isSeries: state.player.isSeries,
        season,
        episodeNumber,
        episodeTitle: state.player.episodeTitle
    }, true);

    if (state.player.isSeries) {
        if (state.player.episodes.length === 0 || state.player.seasons.length <= 1) {
            fetch(`/api/series?url=${encodeURIComponent(item.url || item.id)}`)
                .then(r => r.json())
                .then(async sData => {
                    if (sData.videoId) state.details.videoId = sData.videoId;
                    if (Array.isArray(sData.seasons) && sData.seasons.length) {
                        state.player.seasons = sData.seasons;
                    }
                    const activeVid = state.details.videoId || state.player.videoId;
                    if (activeVid && state.player.episodes.length === 0) {
                        const er = await fetch(`/api/episodes?id=${encodeURIComponent(activeVid)}&season=${season}&audio=${encodeURIComponent(state.details.audio || 'Dublado')}`);
                        const ed = await er.json();
                        if (Array.isArray(ed.episodes) && ed.episodes.length) {
                            state.player.episodes = ed.episodes;
                        }
                    }
                    if (state.player.episodes.length === 0 && Array.isArray(sData.fallbackEpisodes)) {
                        state.player.episodes = sData.fallbackEpisodes;
                    }
                    renderDrawerSeasonsAndEpisodes();
                })
                .catch(() => {});
        }
        renderDrawerSeasonsAndEpisodes();
    }

    try {
        const res = await fetch(`/api/player?url=${encodeURIComponent(state.player.episodeUrl)}`);
        const data = await res.json();
        state.player.videoId = data.videoId || '';
        state.player.servers = Array.isArray(data.servers) ? data.servers : [];
        state.player.nextUrl = data.nextUrl || null;
        state.player.prevUrl = data.prevUrl || null;
        renderServerButtons();

        if (state.player.servers.length > 0) {
            loadServer(0);
        } else {
            document.getElementById('playerLoadingText').textContent = 'Nenhum servidor direto retornado; abrindo página filtrada...';
            document.getElementById('playerFrame').src = `/api/proxy?url=${encodeURIComponent(state.player.episodeUrl)}`;
            setTimeout(() => {
                document.getElementById('playerLoadingOverlay').style.display = 'none';
            }, 900);
        }
    } catch (_) {
        document.getElementById('playerLoadingOverlay').style.display = 'none';
        showToast('⚠️ Erro ao carregar servidores do vídeo.');
    }
    refreshAdblockStatus();
}

function renderServerButtons() {
    const container = document.getElementById('serverButtonsList');
    container.innerHTML = '';
    if (!state.player.servers.length) {
        container.innerHTML = '<span style="font-size:11px; color:var(--text-muted);">Modo Automático</span>';
        return;
    }
    state.player.servers.forEach((srv, idx) => {
        const btn = document.createElement('button');
        btn.className = `server-btn ${idx === state.player.activeServerIdx ? 'active' : ''}`;
        btn.textContent = srv.name;
        btn.addEventListener('click', () => loadServer(idx));
        container.appendChild(btn);
    });
}

function loadServer(idx) {
    state.player.activeServerIdx = idx;
    renderServerButtons();
    const srv = state.player.servers[idx];
    if (!srv) return;
    document.getElementById('playerLoadingOverlay').style.display = 'flex';
    document.getElementById('playerLoadingText').textContent = `Carregando ${srv.name} sem anúncios...`;
    const frame = document.getElementById('playerFrame');
    frame.src = `/api/proxy?url=${encodeURIComponent(srv.embedUrl)}`;
    frame.onload = () => {
        document.getElementById('playerLoadingOverlay').style.display = 'none';
    };
    setTimeout(() => {
        document.getElementById('playerLoadingOverlay').style.display = 'none';
    }, 2500);
    showToast(`▶ ${srv.name}`);
}

function restartCurrentVideo() {
    if (state.player.servers.length > 0) {
        loadServer(state.player.activeServerIdx);
    } else {
        const frame = document.getElementById('playerFrame');
        frame.src = frame.src;
    }
    showToast('↺ Vídeo reiniciado do começo');
}

function playAdjacentEpisode(delta) {
    const eps = state.player.episodes;
    if (eps.length > 0) {
        const idx = eps.findIndex(e => Number(e.number) === Number(state.player.episodeNumber));
        if (idx >= 0 && eps[idx + delta]) {
            const target = eps[idx + delta];
            openPlayerDirect(state.player.item, target.url, state.player.season, target.number, target.title, state.player.seasons, eps);
            return;
        }
    }
    const targetUrl = delta > 0 ? state.player.nextUrl : state.player.prevUrl;
    if (targetUrl) {
        openPlayerDirect(
            state.player.item,
            targetUrl,
            state.player.season,
            Math.max(1, state.player.episodeNumber + delta),
            `Episódio ${Math.max(1, state.player.episodeNumber + delta)}`,
            state.player.seasons,
            eps
        );
    } else {
        showToast(delta > 0 ? 'Você já está no último episódio disponível.' : 'Você está no primeiro episódio.');
    }
}

function renderDrawerSeasonsAndEpisodes() {
    document.getElementById('drawerSeriesName').textContent = state.player.item?.title || 'Série';
    const seasonsEl = document.getElementById('drawerSeasonsList');
    seasonsEl.innerHTML = '';
    state.player.seasons.forEach(s => {
        const btn = document.createElement('button');
        btn.className = `season-tab ${s === state.player.season ? 'active' : ''}`;
        btn.textContent = `T${s}`;
        btn.addEventListener('click', async () => {
            const vidToUse = state.details.videoId || state.player.videoId;
            if (vidToUse) {
                const res = await fetch(`/api/episodes?id=${encodeURIComponent(vidToUse)}&season=${s}&audio=${encodeURIComponent(state.details.audio || 'Dublado')}`);
                const data = await res.json();
                if (Array.isArray(data.episodes)) {
                    state.player.episodes = data.episodes;
                    state.player.season = s;
                    renderDrawerSeasonsAndEpisodes();
                }
            }
        });
        seasonsEl.appendChild(btn);
    });

    const epsEl = document.getElementById('drawerEpisodesList');
    epsEl.innerHTML = '';
    const hist = state.player.item ? findHistoryEntry(state.player.item.url) : null;
    state.player.episodes.forEach(ep => {
        const epKey = `${state.player.season}x${ep.number}`;
        const watched = hist?.watchedEpisodes?.includes(epKey);
        const current = Number(ep.number) === Number(state.player.episodeNumber);
        const row = document.createElement('div');
        row.className = `episode-item ${watched ? 'watched' : ''} ${current ? 'current' : ''}`;
        row.innerHTML = `
            <span class="ep-num">EP ${ep.number}</span>
            <span class="ep-title">${escapeHtml(ep.title || `Episódio ${ep.number}`)}</span>
            <span class="ep-check">${watched ? '✓' : '▶'}</span>
        `;
        row.addEventListener('click', () => {
            openPlayerDirect(state.player.item, ep.url, state.player.season, ep.number, ep.title, state.player.seasons, state.player.episodes);
        });
        epsEl.appendChild(row);
    });
}

function closePlayer() {
    document.getElementById('playerFrame').src = 'about:blank';
    document.getElementById('playerModal').style.display = 'none';
    state.player.item = null;
}

// --- Zoom ---

function cycleZoom() {
    state.player.zoomIndex = (state.player.zoomIndex + 1) % ZOOM_PRESETS.length;
    const preset = ZOOM_PRESETS[state.player.zoomIndex];
    state.userData.preferences.zoomPreset = preset.key;
    state.userData.preferences.zoomScale = preset.scale;
    persistUserData();
    applyZoom();
    showToast(`🔍 Zoom: ${preset.label}`);
}

function applyZoom() {
    const preset = ZOOM_PRESETS[state.player.zoomIndex] || ZOOM_PRESETS[0];
    document.getElementById('videoFrameWrapper').style.transform = `scale(${preset.scale})`;
    document.getElementById('btnPlayerZoom').textContent = `🔍 Zoom (${preset.label})`;
}

// --- Download ---

function openDownloadModal() {
    if (!state.player.item) return;
    const clean = sanitizeFilename(state.player.item.title);
    document.getElementById('inputDownloadName').value = `A2_${clean}_${state.download.quality}.mp4`;
    document.getElementById('downloadOverlay').style.display = 'flex';
}

function closeDownloadModal() {
    document.getElementById('downloadOverlay').style.display = 'none';
}

async function triggerDownload() {
    if (!state.player.item) return;
    let fileName = sanitizeFilename(document.getElementById('inputDownloadName').value || 'A2_Video');
    if (!fileName.endsWith('.mp4')) fileName += '.mp4';
    const srv = state.player.servers[state.player.activeServerIdx];
    const targetUrl = srv ? srv.embedUrl : state.player.episodeUrl;

    try {
        const res = await fetch('/api/download', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                fileName,
                folder: state.download.folder,
                quality: state.download.quality,
                url: targetUrl,
                title: state.player.item.title
            })
        });
        const data = await res.json();
        closeDownloadModal();
        showToast(data.message || `📥 Salvo em Downloads\\A2-Miguel-PC\\Videos\\${state.download.folder}`);
    } catch (_) {
        closeDownloadModal();
        window.open(targetUrl, '_blank');
    }
}

// --- Utilitários ---

function sanitizeFilename(name) {
    return String(name).replace(/[\/\\:*?"<>|]/g, '_').trim();
}

function escapeHtml(str) {
    return String(str).replace(/[&<>"']/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[m]));
}

function escapeAttr(str) {
    return escapeHtml(str);
}

function showToast(msg) {
    document.querySelectorAll('.toast-osd').forEach(t => t.remove());
    const toast = document.createElement('div');
    toast.className = 'toast-osd';
    toast.textContent = msg;
    document.body.appendChild(toast);
    setTimeout(() => toast.remove(), 2600);
}
