/*
 * Luma Browser — screen controller.
 *
 * Connects the browser model, the sandboxed iframe renderer and the reusable
 * UI components. Owns delegated events, Magic Remote focus, video controls and
 * the prepared Android command states. It never invents platform features.
 */
(function (global) {
  'use strict';

  var model = global.LumaBrowserModel;
  var view = global.LumaBrowserView;
  var ui = global.LumaBrowserUI;
  var documentRef = global.document;

  var ACCENTS = {
    violet: ['#8b5cf6', '#b99cff', '#5b21b6'],
    blue: ['#3b82f6', '#8dc2ff', '#1d4ed8'],
    amber: ['#f59e0b', '#ffd58a', '#b45309']
  };

  var nodes = {};
  var state = {
    overflowOpen: false,
    queries: { bookmarks: '', history: '' },
    suggestions: [],
    suggestionOpen: false,
    lastToken: 0,
    lastSearchToken: 0,
    searchResults: { status: 'idle', query: '', items: [], error: '' },
    searchTimer: null,
    searchAbort: null,
    lastScreen: '',
    loadTimer: null,
    focusRequest: null,
    videoIdleTimer: null,
    videoStats: { waits: 0, recoveries: 0, errors: 0 },
    snapshot: null
  };

  function byId(id) {
    return documentRef ? documentRef.getElementById(id) : null;
  }

  function setText(id, value) {
    var element = byId(id);
    if (element) element.textContent = value;
  }

  function isObject(value) {
    return Object.prototype.toString.call(value) === '[object Object]';
  }

  function findAction(node, root) {
    while (node && node !== root && node !== documentRef) {
      if (node.getAttribute && node.getAttribute('data-action')) return node;
      node = node.parentNode;
    }
    return null;
  }

  function focusKey(element) {
    if (!element) return '';
    if (element.id) return 'id:' + element.id;
    var action = element.getAttribute('data-action') || '';
    var id = element.getAttribute('data-id') || '';
    var value = element.getAttribute('data-value') || '';
    return 'act:' + action + ':' + id + ':' + value;
  }

  function rememberFocus() {
    var active = documentRef && documentRef.activeElement;
    if (!active || active === documentRef.body) return '';
    return focusKey(active);
  }

  function elementsWithFocus(root) {
    var found = root ? root.querySelectorAll('[data-focusable="true"]') : [];
    var result = [];
    for (var i = 0; i < found.length; i += 1) {
      var element = found[i];
      var rect = element.getBoundingClientRect ? element.getBoundingClientRect() : { width: 0, height: 0 };
      if (!element.disabled && rect.width > 0 && rect.height > 0) result.push(element);
    }
    return result;
  }

  function findByKey(key, root) {
    if (!key) return null;
    var list = elementsWithFocus(root || documentRef);
    for (var i = 0; i < list.length; i += 1) {
      if (focusKey(list[i]) === key) return list[i];
    }
    return null;
  }

  function requestFocus(name) {
    state.focusRequest = name;
  }

  function applyFocusRequest() {
    if (!state.focusRequest) return false;
    var name = state.focusRequest;
    state.focusRequest = null;
    var target = null;
    if (name === 'address') {
      target = byId('address-input');
    } else if (name === 'overlay') {
      target = elementsWithFocus(nodes.overlay)[0] || null;
    } else {
      target = byId(name);
      var list = target ? [] : documentRef.querySelectorAll('[data-action="' + name + '"]');
      for (var i = 0; i < list.length; i += 1) {
        if (!list[i].disabled) {
          target = list[i];
          break;
        }
      }
    }
    if (target && typeof target.focus === 'function') {
      target.focus();
      return true;
    }
    return false;
  }

  function applyAccent(settings) {
    if (!documentRef || !documentRef.documentElement) return;
    var colors = ACCENTS[settings.accent] || ACCENTS.violet;
    var style = documentRef.documentElement.style;
    style.setProperty('--accent', colors[0]);
    style.setProperty('--accent-bright', colors[1]);
    style.setProperty('--accent-deep', colors[2]);
  }

  function updateViewportInfo() {
    var info = global.LumaDiagnosticsInfo || {};
    info.viewport = global.innerWidth + ' × ' + global.innerHeight;
    info.dpr = global.devicePixelRatio || 1;
    global.LumaDiagnosticsInfo = info;
  }

  function hideOverlay() {
    if (!nodes.overlay) return;
    nodes.overlay.className = 'browser-overlay';
    nodes.overlay.innerHTML = '';
  }

  function showOverlay(kind, message, action, label) {
    if (!nodes.overlay) return;
    var button = action
      ? '<button type="button" class="ui-button primary" data-focusable="true" tabindex="-1" data-action="' +
        ui.esc(action) + '">' + ui.esc(label || 'Retry') + '</button>'
      : '';
    nodes.overlay.className = 'browser-overlay is-visible overlay-' + kind;
    nodes.overlay.innerHTML = '<div class="overlay-card">' +
      '<span class="overlay-mark" aria-hidden="true">' + (kind === 'error' ? '!' : '↻') + '</span>' +
      '<strong>' + ui.esc(message) + '</strong>' + button + '</div>';
  }

  function clearWatchdog() {
    if (state.loadTimer) {
      global.clearTimeout(state.loadTimer);
      state.loadTimer = null;
    }
  }

  function cleanSearchText(value) {
    return String(value === null || value === undefined ? '' : value)
      .replace(/<[^>]*>/g, ' ')
      .replace(/\s+/g, ' ')
      .replace(/^\s+|\s+$/g, '');
  }

  function searchHost(url) {
    try {
      var link = documentRef.createElement('a');
      link.href = url;
      return (link.hostname || '').replace(/^www\./, '');
    } catch (error) {
      return '';
    }
  }

  function searchItems(data) {
    var raw = [];
    if (!isObject(data)) return raw;
    if (data.AbstractURL && data.AbstractText) {
      raw.push({
        url: data.AbstractURL,
        title: data.Heading || cleanSearchText(data.AbstractText).substring(0, 90),
        snippet: cleanSearchText(data.AbstractText)
      });
    }
    var groups = [data.Results, data.RelatedTopics];
    for (var g = 0; g < groups.length; g += 1) {
      var group = groups[g];
      if (!group || !group.length) continue;
      for (var i = 0; i < group.length; i += 1) {
        var item = group[i];
        if (!isObject(item) || !item.FirstURL) continue;
        raw.push({ url: item.FirstURL, title: cleanSearchText(item.Text), snippet: cleanSearchText(item.Text) });
      }
    }

    var items = [];
    var seen = {};
    /* Prefer direct answers and external sites over DuckDuckGo topic pages. */
    raw.sort(function (a, b) {
      return (/duckduckgo\.com/i.test(a.url) ? 1 : 0) - (/duckduckgo\.com/i.test(b.url) ? 1 : 0);
    });
    for (var j = 0; j < raw.length && items.length < 12; j += 1) {
      var checked = view.validateUrl(raw[j].url);
      if (!checked.ok || seen[checked.url]) continue;
      seen[checked.url] = true;
      items.push({
        url: checked.url,
        title: raw[j].title.substring(0, 110) || searchHost(checked.url),
        snippet: raw[j].snippet.substring(0, 220),
        host: searchHost(checked.url),
        blocked: model.isFrameBlocked(checked.url)
      });
    }
    return items;
  }

  function cancelSearchFetch() {
    if (state.searchTimer) {
      global.clearTimeout(state.searchTimer);
      state.searchTimer = null;
    }
    if (state.searchAbort && typeof state.searchAbort.abort === 'function') {
      try { state.searchAbort.abort(); } catch (error) { /* already finished */ }
    }
    state.searchAbort = null;
  }

  function setSearchResults(status, items, error, query) {
    state.searchResults = {
      status: status,
      query: query || '',
      items: items || [],
      error: error || ''
    };
    if (state.snapshot && state.snapshot.screen === 'search') render(state.snapshot);
  }

  function loadSearchResults(query) {
    cancelSearchFetch();
    var cleanQuery = String(query || '').replace(/^\s+|\s+$/g, '');
    state.searchResults = { status: 'loading', query: cleanQuery, items: [], error: '' };
    if (state.snapshot && state.snapshot.screen === 'search') render(state.snapshot);
    if (!cleanQuery) {
      setSearchResults('error', [], 'Enter a search phrase first.', cleanQuery);
      return;
    }
    if (typeof global.fetch !== 'function') {
      setSearchResults('error', [], 'This TV engine does not expose fetch for search results.', cleanQuery);
      return;
    }
    var endpoint = String(model.searchEndpoint || '').replace('{query}', encodeURIComponent(cleanQuery));
    var checked = view.validateUrl(endpoint);
    if (!checked.ok) {
      setSearchResults('error', [], 'Search results cannot be requested from this build.', cleanQuery);
      return;
    }

    var done = false;
    var abort = typeof global.AbortController === 'function' ? new global.AbortController() : null;
    state.searchAbort = abort;
    function finish(status, items, error) {
      if (done) return;
      done = true;
      cancelSearchFetch();
      setSearchResults(status, items, error, cleanQuery);
    }
    state.searchTimer = global.setTimeout(function () {
      finish('error', [], 'Search results timed out. Check the network and try again.');
    }, 15000);

    global.fetch(checked.url, {
      credentials: 'omit',
      signal: abort ? abort.signal : undefined
    }).then(function (response) {
      if (!response.ok) throw new Error('http_' + response.status);
      return response.json();
    }).then(function (data) {
      var items = searchItems(data);
      finish(items.length ? 'ready' : 'error', items,
        items.length ? '' : 'No instant-answer results were returned for that phrase.');
    }).catch(function () {
      finish('error', [], 'Search results could not be fetched. Check the network and try again.');
    });
  }

  function maybeLoadSearch(snapshot) {
    var search = snapshot.search || {};
    if (!search.token || search.token === state.lastSearchToken) return;
    state.lastSearchToken = search.token;
    loadSearchResults(search.query);
  }

  function armWatchdog(tabId) {
    clearWatchdog();
    state.loadTimer = global.setTimeout(function () {
      state.loadTimer = null;
      var tab = model.findTab(tabId);
      if (!tab || !tab.loading) return;
      model.markError(tabId, 'The page did not finish loading. Some sites block being shown inside another app.');
      requestFocus('overlay');
      state.snapshot = model.getState();
      render(state.snapshot);
    }, 18000);
  }

  function renderScreen(snapshot) {
    if (!nodes.screen) return;
    var tab = model.currentTab();
    var screen = snapshot.screen;
    var supported = !!(tab && model.isDirectVideo(tab.url));
    var audioSupported = !!(tab && model.isDirectAudio(tab.url));

    if (screen === 'companion') {
      nodes.screen.style.display = 'none';
      nodes.screen.innerHTML = '';
      if (nodes.companion) nodes.companion.style.display = 'block';
      if (nodes.frames) nodes.frames.style.display = 'none';
      view.showFrame(null);
      hideOverlay();
      return;
    }

    if (nodes.companion) nodes.companion.style.display = 'none';
    nodes.screen.style.display = 'block';

    if (screen === 'page') {
      nodes.screen.innerHTML = '';
      if (nodes.frames) nodes.frames.style.display = 'block';
      if (tab) view.showFrame(tab.id);
      if (tab && tab.error) {
        showOverlay('error', tab.error, 'reload', 'Try again');
      } else if (tab && tab.loading) {
        showOverlay('loading', 'Loading ' + model.sanitizeUrl(tab.url || 'page') + '…', 'stop', 'Stop');
      } else {
        hideOverlay();
      }
      return;
    }

    if (nodes.frames) nodes.frames.style.display = 'none';
    view.showFrame(null);
    hideOverlay();

    if (screen === 'home') nodes.screen.innerHTML = ui.home(snapshot);
    else if (screen === 'search') nodes.screen.innerHTML = ui.searchResults(snapshot, state.searchResults);
    else if (screen === 'bookmarks') nodes.screen.innerHTML = ui.bookmarks(snapshot, state.queries.bookmarks);
    else if (screen === 'history') nodes.screen.innerHTML = ui.history(snapshot, state.queries.history);
    else if (screen === 'tabs') nodes.screen.innerHTML = ui.tabManager(snapshot);
    else if (screen === 'settings') nodes.screen.innerHTML = ui.settings(snapshot);
    else if (screen === 'capabilities') nodes.screen.innerHTML = ui.capabilities();
    else if (screen === 'video') nodes.screen.innerHTML = ui.video(snapshot, supported);
    else if (screen === 'audio') nodes.screen.innerHTML = ui.audio(snapshot, audioSupported);
    else nodes.screen.innerHTML = ui.emptyState('This screen is not available.');

    if (screen === 'video' && supported) attachVideo();
    if (screen === 'audio' && audioSupported) attachAudio();
  }

  function render(snapshot) {
    state.snapshot = snapshot;
    var focus = rememberFocus();
    var tab = model.currentTab();

    updateViewportInfo();
    applyAccent(snapshot.settings);

    var notice = byId('browser-notice');
    if (notice) {
      notice.textContent = snapshot.notice || '';
      notice.className = 'browser-notice' + (snapshot.notice ? ' is-visible' : '');
    }

    if (nodes.topbar) nodes.topbar.innerHTML = ui.tabBar(snapshot, state.overflowOpen);
    if (nodes.navbar) nodes.navbar.innerHTML = ui.navigationBar(snapshot);
    if (nodes.sidebar) {
      nodes.sidebar.innerHTML = ui.sidebar(snapshot);
      nodes.sidebar.className = 'browser-sidebar ' + (snapshot.sidebarOpen ? 'is-open' : 'is-collapsed');
    }

    if (snapshot.screen !== state.lastScreen) {
      if (nodes.screen) nodes.screen.scrollTop = 0;
      state.lastScreen = snapshot.screen;
    }

    view.syncTabs(snapshot.tabs);
    renderScreen(snapshot);
    if (snapshot.screen === 'search') maybeLoadSearch(snapshot);

    var request = snapshot.loadRequest;
    if (request && request.token !== state.lastToken) {
      state.lastToken = request.token;
      view.load(request.tabId, request.url, snapshot.settings.zoom);
      if (request.url) armWatchdog(request.tabId);
    } else if (snapshot.screen === 'page' && tab && tab.url && !view.hasFrame(tab.id)) {
      view.load(tab.id, tab.url, snapshot.settings.zoom);
      if (tab.loading) armWatchdog(tab.id);
    }

    if (tab) view.setZoom(tab.id, snapshot.settings.zoom);

    setText('browser-status-page', snapshot.screen === 'search'
      ? 'Search · ' + String((snapshot.search && snapshot.search.query) || '').substring(0, 60)
      : (tab && tab.url
        ? (tab.loading ? 'Loading · ' + model.sanitizeUrl(tab.url) : model.sanitizeUrl(tab.url))
        : 'New Tab'));
    setText('browser-status-tabs', snapshot.tabs.length + (snapshot.tabs.length === 1 ? ' tab' : ' tabs'));

    if (state.suggestionOpen) renderSuggestions();
    if (!applyFocusRequest()) {
      var restored = findByKey(focus, nodes.topbar) || findByKey(focus, nodes.navbar) ||
        findByKey(focus, nodes.sidebar) || findByKey(focus, nodes.screen);
      if (restored && typeof restored.focus === 'function') restored.focus();
    }
  }

  function currentInputValue(id) {
    var input = byId(id);
    return input ? input.value : '';
  }

  function suggestionsFor(raw) {
    var snapshot = state.snapshot || model.getState();
    var query = String(raw || '').toLowerCase();
    var items = [];
    var groups = [
      snapshot.shortcuts,
      snapshot.bookmarks,
      snapshot.history
    ];
    var seen = {};
    var i;
    for (i = 0; i < groups.length; i += 1) {
      var group = groups[i];
      for (var j = 0; j < group.length && items.length < 6; j += 1) {
        var entry = group[j];
        if (!entry || !entry.url) continue;
        if (query && entry.title.toLowerCase().indexOf(query) < 0 && entry.url.toLowerCase().indexOf(query) < 0) continue;
        if (seen[entry.url]) continue;
        seen[entry.url] = true;
        items.push({ id: 'suggest_' + items.length, title: entry.title, value: entry.url });
      }
    }
    if (query && items.length < 6 && query.indexOf(' ') > 0) {
      var engine = model.engines[snapshot.settings.searchEngine] || model.engines.google;
      items.push({
        id: 'search',
        title: 'Search with ' + engine.name,
        value: engine.prefix + encodeURIComponent(String(raw).replace(/^\s+|\s+$/g, ''))
      });
    }
    return items;
  }

  function renderSuggestions() {
    if (!nodes.suggestions) return;
    var input = documentRef && documentRef.activeElement;
    var isAddress = input && input.id === 'address-input';
    var isHome = input && input.id === 'home-search-input';
    if (!isAddress && !isHome) {
      closeSuggestions();
      return;
    }
    state.suggestions = suggestionsFor(input.value);
    if (!state.suggestions.length) {
      closeSuggestions();
      return;
    }
    nodes.suggestions.innerHTML = '<div class="suggestion-panel">' +
      ui.suggestionList(state.suggestions) + '</div>';
    nodes.suggestions.className = 'suggestion-layer is-open';
  }

  function closeSuggestions() {
    state.suggestionOpen = false;
    state.suggestions = [];
    if (nodes.suggestions) {
      nodes.suggestions.className = 'suggestion-layer';
      nodes.suggestions.innerHTML = '';
    }
  }

  function navigateFrom(id) {
    var value = currentInputValue(id);
    closeSuggestions();
    requestFocus('address');
    return model.navigateActive(value);
  }

  function updateVideoTime() {
    var video = byId('luma-video');
    var label = byId('video-time');
    var seek = byId('video-seek');
    if (!video) return;
    var current = isFinite(video.currentTime) ? video.currentTime : 0;
    var total = isFinite(video.duration) ? video.duration : 0;
    if (label) {
      function clock(value) {
        var seconds = Math.floor(value % 60);
        var minutes = Math.floor(value / 60) % 60;
        var hours = Math.floor(value / 3600);
        var text = (hours ? hours + ':' + (minutes < 10 ? '0' : '') : '') +
          minutes + ':' + (seconds < 10 ? '0' : '') + seconds;
        return text;
      }
      label.textContent = clock(current) + ' / ' + (total ? clock(total) : '—');
    }
    if (seek && total) seek.value = String(Math.floor(current / total * 100));
  }

  function videoMessage(text) {
    var error = byId('video-error');
    if (error) error.textContent = text || '';
  }

  function armVideoIdle() {
    var shell = documentRef && documentRef.querySelector('.video-shell');
    if (!shell) return;
    if (state.videoIdleTimer) global.clearTimeout(state.videoIdleTimer);
    var controls = shell.querySelector('.video-controls');
    if (controls) controls.className = 'video-controls';
    state.videoIdleTimer = global.setTimeout(function () {
      var active = documentRef.activeElement;
      if (controls && (!active || !shell.contains(active))) controls.className = 'video-controls is-idle';
    }, 6000);
  }

  function attachVideo() {
    var video = byId('luma-video');
    if (!video) return;
    var snapshot = state.snapshot || model.getState();
    video.volume = Math.max(0, Math.min(1, snapshot.settings.videoVolume / 100));
    if (snapshot.settings.autoplayVideo && typeof video.play === 'function') {
      var promise = video.play();
      if (promise && typeof promise.catch === 'function') {
        promise.catch(function () { videoMessage('Autoplay was blocked. Press Play to start.'); });
      }
    }
    video.addEventListener('loadedmetadata', updateVideoTime);
    video.addEventListener('timeupdate', updateVideoTime);
    video.addEventListener('waiting', function () {
      state.videoStats.waits += 1;
      var buffer = byId('video-buffering');
      if (buffer) buffer.className = 'video-buffering is-visible';
      updateVideoDiag();
    });
    video.addEventListener('playing', function () {
      state.videoStats.recoveries += 1;
      var buffer = byId('video-buffering');
      if (buffer) buffer.className = 'video-buffering';
      videoMessage('');
      updateVideoDiag();
    });
    video.addEventListener('error', function () {
      state.videoStats.errors += 1;
      videoMessage('This video file could not be decoded by the TV engine.');
      updateVideoDiag();
    });
    video.addEventListener('ended', function () { videoMessage('Playback ended.'); });
    video.addEventListener('mousemove', armVideoIdle);
    video.addEventListener('touchstart', armVideoIdle);
    updateVideoTime();
    updateVideoDiag();
    armVideoIdle();
  }

  function videoAction(action, value) {
    var video = byId('luma-video');
    if (!video) return false;
    if (action === 'video-play') {
      if (typeof video.play !== 'function') return false;
      var promise = video.play();
      if (promise && typeof promise.catch === 'function') {
        promise.catch(function () { videoMessage('Playback was blocked by the TV engine.'); });
      }
      return true;
    }
    if (action === 'video-pause') {
      if (typeof video.pause === 'function') video.pause();
      return true;
    }
    if (action === 'video-back10' || action === 'video-forward10') {
      var step = action === 'video-back10' ? -10 : 10;
      if (!isFinite(video.duration)) return false;
      video.currentTime = Math.max(0, Math.min(video.duration, (video.currentTime || 0) + step));
      return true;
    }
    if (action === 'video-seek') {
      if (!isFinite(video.duration) || !isFinite(Number(value))) return false;
      video.currentTime = Math.max(0, Math.min(video.duration, video.duration * Number(value) / 100));
      return true;
    }
    if (action === 'video-volume') {
      video.volume = Math.max(0, Math.min(1, Number(value) / 100));
      return true;
    }
    if (action === 'video-fullscreen') {
      var request = video.webkitRequestFullscreen || video.requestFullscreen;
      if (typeof request !== 'function') {
        videoMessage('Fullscreen is not exposed by this TV engine.');
        return true;
      }
      request.call(video);
      return true;
    }
    return false;
  }

  function updateVideoDiag() {
    var stats = state.videoStats;
    var video = byId('luma-video');
    var tracks = byId('video-tracks');
    if (video && tracks && video.audioTracks && typeof video.audioTracks.length === 'number') {
      tracks.className = 'video-track-info';
      tracks.textContent = 'Audio tracks exposed by engine: ' + video.audioTracks.length;
    }
    var label = byId('video-diag');
    if (label) {
      label.textContent = 'Local diagnostics · buffering pauses: ' + stats.waits +
        ' · recoveries: ' + stats.recoveries + ' · decode errors: ' + stats.errors +
        ' · audio tracks: ' + (video && video.audioTracks ? video.audioTracks.length : 'not exposed');
    }
  }

  function loadSubtitles() {
    var video = byId('luma-video');
    var input = byId('video-subtitle-url');
    var raw = input ? input.value : '';
    if (!video || !raw) {
      videoMessage('Enter a WebVTT address first.');
      return false;
    }
    var checked = view.validateUrl(raw);
    if (!checked.ok) {
      videoMessage('Subtitles need an absolute http(s) WebVTT address.');
      return false;
    }
    var old = video.querySelector('track');
    if (old && old.parentNode) old.parentNode.removeChild(old);
    var track = documentRef.createElement('track');
    track.setAttribute('kind', 'subtitles');
    track.setAttribute('srclang', 'en');
    track.setAttribute('label', 'Luma WebVTT');
    track.setAttribute('default', 'default');
    track.setAttribute('src', checked.url);
    track.addEventListener('error', function () {
      videoMessage('The subtitle file could not be fetched by the TV engine.');
    });
    video.appendChild(track);
    if (typeof video.load === 'function') video.load();
    videoMessage('Subtitle track requested.');
    return true;
  }

  function updateAudioTime() {
    var audio = byId('luma-audio');
    var label = byId('audio-time');
    var seek = byId('audio-seek');
    if (!audio) return;
    var current = isFinite(audio.currentTime) ? audio.currentTime : 0;
    var total = isFinite(audio.duration) ? audio.duration : 0;
    function clock(value) {
      var seconds = Math.floor(value % 60);
      var minutes = Math.floor(value / 60) % 60;
      return minutes + ':' + (seconds < 10 ? '0' : '') + seconds;
    }
    if (label) label.textContent = clock(current) + ' / ' + (total ? clock(total) : '—');
    if (seek && total) seek.value = String(Math.floor(current / total * 100));
  }

  function attachAudio() {
    var audio = byId('luma-audio');
    if (!audio) return;
    var snapshot = state.snapshot || model.getState();
    audio.volume = Math.max(0, Math.min(1, snapshot.settings.videoVolume / 100));
    audio.addEventListener('loadedmetadata', updateAudioTime);
    audio.addEventListener('timeupdate', updateAudioTime);
    audio.addEventListener('waiting', function () { setText('audio-diag', 'Local status: buffering'); });
    audio.addEventListener('playing', function () { setText('audio-diag', 'Local status: playing'); });
    audio.addEventListener('ended', function () { setText('audio-diag', 'Local status: ended'); });
    audio.addEventListener('error', function () {
      setText('audio-error', 'This audio file could not be decoded by the TV engine.');
      setText('audio-diag', 'Local status: decode error');
    });
    updateAudioTime();
  }

  function audioAction(action, value) {
    var audio = byId('luma-audio');
    if (!audio) return false;
    if (action === 'audio-play') {
      if (typeof audio.play !== 'function') return false;
      var promise = audio.play();
      if (promise && typeof promise.catch === 'function') {
        promise.catch(function () { setText('audio-error', 'Playback was blocked by the TV engine.'); });
      }
      return true;
    }
    if (action === 'audio-pause') {
      if (typeof audio.pause === 'function') audio.pause();
      return true;
    }
    if (action === 'audio-back10' || action === 'audio-forward10') {
      if (!isFinite(audio.duration)) return false;
      var step = action === 'audio-back10' ? -10 : 10;
      audio.currentTime = Math.max(0, Math.min(audio.duration, (audio.currentTime || 0) + step));
      return true;
    }
    if (action === 'audio-seek') {
      if (!isFinite(audio.duration) || !isFinite(Number(value))) return false;
      audio.currentTime = Math.max(0, Math.min(audio.duration, audio.duration * Number(value) / 100));
      return true;
    }
    if (action === 'audio-volume') {
      audio.volume = Math.max(0, Math.min(1, Number(value) / 100));
      return true;
    }
    return false;
  }

  function closeActiveTab(id) {
    var tab = model.findTab(id);
    if (!tab) return false;
    if (tab.id === (model.currentTab() || {}).id) requestFocus('tab-add');
    model.closeTab(tab.id);
    return true;
  }

  function cycleTab(direction) {
    var snapshot = model.getState();
    if (!snapshot.tabs.length) return false;
    var current = model.currentTab();
    var index = 0;
    for (var i = 0; i < snapshot.tabs.length; i += 1) {
      if (snapshot.tabs[i].id === current.id) index = i;
    }
    var next = (index + direction + snapshot.tabs.length) % snapshot.tabs.length;
    model.activateTab(snapshot.tabs[next].id);
    return true;
  }

  function moveFocus(direction) {
    var list = elementsWithFocus(documentRef);
    if (!list.length) return;
    var active = documentRef.activeElement;
    var index = list.indexOf(active);
    if (index < 0) {
      list[0].focus();
      return;
    }
    var rect = active.getBoundingClientRect();
    var x = rect.left + rect.width / 2;
    var y = rect.top + rect.height / 2;
    var best = null;
    var bestScore = Infinity;
    for (var i = 0; i < list.length; i += 1) {
      var candidate = list[i];
      if (candidate === active) continue;
      var box = candidate.getBoundingClientRect();
      var dx = box.left + box.width / 2 - x;
      var dy = box.top + box.height / 2 - y;
      var primary = direction === 'right' ? dx : direction === 'left' ? -dx : direction === 'down' ? dy : -dy;
      if (primary <= 2) continue;
      var secondary = direction === 'left' || direction === 'right' ? Math.abs(dy) : Math.abs(dx);
      var score = primary + secondary * 1.4;
      if (score < bestScore) {
        bestScore = score;
        best = candidate;
      }
    }
    if (best && typeof best.focus === 'function') best.focus();
  }

  function handleKey(event) {
    var code = event.keyCode || event.which;
    var target = event.target;
    var tag = target ? target.tagName : '';
    var inField = tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT';

    if ((event.ctrlKey || event.metaKey) && code === 76) {
      var address = byId('address-input');
      if (address && typeof address.focus === 'function') {
        address.focus();
        state.suggestionOpen = true;
        renderSuggestions();
      }
      event.preventDefault();
      return;
    }
    if (code === 116) {
      model.reload();
      event.preventDefault();
      return;
    }
    if (event.altKey && code === 37) {
      model.back();
      event.preventDefault();
      return;
    }
    if (event.altKey && code === 39) {
      model.forward();
      event.preventDefault();
      return;
    }

    if (inField) {
      if (code === 13 && (target.id === 'address-input' || target.id === 'home-search-input')) {
        navigateFrom(target.id);
        event.preventDefault();
      } else if (code === 27 || code === 461) {
        closeSuggestions();
        if (typeof target.blur === 'function') target.blur();
        event.preventDefault();
      }
      return;
    }

    if (code === 461 || code === 27) {
      if (state.suggestionOpen) closeSuggestions();
      else if (state.snapshot && state.snapshot.sidebarOpen) model.toggleSidebar(false);
      else if (state.snapshot && state.snapshot.screen !== 'home') model.setScreen('home');
      else if (state.snapshot && state.snapshot.screen === 'page') model.back();
      event.preventDefault();
      return;
    }

    if (code === 37) { moveFocus('left'); event.preventDefault(); }
    else if (code === 38) { moveFocus('up'); event.preventDefault(); }
    else if (code === 39) { moveFocus('right'); event.preventDefault(); }
    else if (code === 40) { moveFocus('down'); event.preventDefault(); }
    else if (code === 13 || code === 32) {
      if (documentRef.activeElement && typeof documentRef.activeElement.click === 'function' &&
          documentRef.activeElement.tagName !== 'IFRAME') {
        documentRef.activeElement.click();
        event.preventDefault();
      }
    }
  }

  function handleClick(event) {
    var target = findAction(event.target, nodes.root);
    if (!target) {
      if (state.suggestionOpen && nodes.suggestions && !nodes.suggestions.contains(event.target)) closeSuggestions();
      return;
    }
    var action = target.getAttribute('data-action');
    var id = target.getAttribute('data-id') || '';
    var value = target.getAttribute('data-value') ||
      (target.value === undefined || target.value === null ? '' : String(target.value));
    var tab = model.currentTab();

    if (action === 'tab-activate') { model.activateTab(id); return; }
    if (action === 'tab-close') {
      event.stopPropagation();
      closeActiveTab(id);
      return;
    }
    if (action === 'tab-add') { requestFocus('address'); model.addTab(); return; }
    if (action === 'tab-overflow') {
      state.overflowOpen = !state.overflowOpen;
      render(model.getState());
      return;
    }
    if (action === 'screen-settings') { model.setScreen('settings'); return; }
    if (action === 'back') { model.back(); return; }
    if (action === 'forward') { model.forward(); return; }
    if (action === 'reload') { requestFocus('address'); model.reload(); return; }
    if (action === 'stop') {
      if (tab) {
        view.stop(tab.id);
        model.markLoaded(tab.id);
        model.setNotice('Loading stopped.');
      }
      return;
    }
    if (action === 'home') { requestFocus('home-search-input'); model.goHome(); return; }
    if (action === 'address-clear') {
      var input = byId('address-input');
      if (input) { input.value = ''; input.focus(); }
      return;
    }
    if (action === 'address-go') { navigateFrom('address-input'); return; }
    if (action === 'bookmark-toggle') { model.toggleBookmark(); return; }
    if (action === 'sidebar-toggle') { model.toggleSidebar(); return; }
    if (action === 'screen-video') { model.setScreen('video'); return; }
    if (action === 'suggestion') { closeSuggestions(); model.navigateActive(value); return; }
    if (action === 'home-search-go') { navigateFrom('home-search-input'); return; }
    if (action === 'navigate') { model.navigateActive(value); return; }
    if (action === 'search-open') { model.navigateActive(value); return; }
    if (action === 'search-retry') {
      loadSearchResults((state.snapshot && state.snapshot.search && state.snapshot.search.query) || '');
      return;
    }
    if (action === 'shortcut-add') { model.addShortcut(); return; }
    if (action === 'shortcut-remove') { model.removeShortcut(id); return; }
    if (action === 'shortcuts-reset') { model.resetShortcuts(); return; }
    if (action === 'bookmark-remove') { model.removeBookmark(id); return; }
    if (action === 'bookmark-save') {
      model.updateBookmark(id, currentInputValue('bm-title-' + id), currentInputValue('bm-url-' + id));
      return;
    }
    if (action === 'history-remove') { model.removeHistory(id); return; }
    if (action === 'history-clear') { model.clearHistory(); return; }
    if (action === 'setting-homepage-save') {
      var homepage = currentInputValue('setting-homepage');
      var checked = model.resolveAddress(homepage);
      model.setNotice(checked.ok ? 'Homepage saved.' : 'Enter an absolute http(s) homepage.');
      if (checked.ok) model.setSetting('homepage', checked.url);
      return;
    }
    if (action === 'video-close') { model.setScreen('page'); return; }
    if (action === 'audio-close') { model.setScreen('page'); return; }
    if (action === 'video-subtitle-load') { loadSubtitles(); return; }
    if (action.indexOf('audio-') === 0 && audioAction(action, value)) return;
    if (action.indexOf('video-') === 0 && videoAction(action, value)) return;
    if (action.indexOf('screen-') === 0) {
      model.setScreen(action.substring(7));
      return;
    }
    if (action === 'replay-load') { model.reload(); return; }
  }

  function handleInput(event) {
    var target = event.target;
    if (!target) return;
    var id = target.id;
    if (id === 'address-input' || id === 'home-search-input') {
      state.suggestionOpen = true;
      renderSuggestions();
      return;
    }
    if (id === 'bookmark-search-input') {
      state.queries.bookmarks = target.value;
      var focused = focusKey(target);
      renderScreen(state.snapshot || model.getState());
      var again = findByKey(focused, nodes.screen);
      if (again) again.focus();
      return;
    }
    if (id === 'history-search-input') {
      state.queries.history = target.value;
      var historyFocus = focusKey(target);
      renderScreen(state.snapshot || model.getState());
      var historyAgain = findByKey(historyFocus, nodes.screen);
      if (historyAgain) historyAgain.focus();
    }
  }

  function handleChange(event) {
    var target = findAction(event.target, nodes.root) || event.target;
    if (!target) return;
    var action = target.getAttribute && target.getAttribute('data-action');
    if (!action) return;
    var value = target.value;

    if (action === 'setting-engine' || action === 'setting-startup' || action === 'setting-accent') {
      model.setSetting(action, value);
    } else if (action === 'setting-zoom') {
      model.setSetting('zoom', parseInt(value, 10));
      var snapshot = model.getState();
      for (var i = 0; i < snapshot.tabs.length; i += 1) view.setZoom(snapshot.tabs[i].id, snapshot.settings.zoom);
    } else if (action === 'setting-sidebar') {
      model.setSetting('showSidebar', !!target.checked);
      model.toggleSidebar(!!target.checked);
    } else if (action === 'setting-autoplay') {
      model.setSetting('autoplayVideo', !!target.checked);
    } else if (action === 'setting-volume' || action === 'video-volume') {
      model.setSetting('videoVolume', parseInt(value, 10));
      videoAction('video-volume', value);
    } else if (action === 'video-seek') {
      videoAction('video-seek', value);
      armVideoIdle();
    } else if (action === 'audio-volume') {
      model.setSetting('videoVolume', parseInt(value, 10));
      audioAction('audio-volume', value);
    } else if (action === 'audio-seek') {
      audioAction('audio-seek', value);
    }
  }

  function handleFocus(event) {
    var target = event.target;
    if (target && (target.id === 'address-input' || target.id === 'home-search-input')) {
      state.suggestionOpen = true;
      renderSuggestions();
    }
    if (target && target.closest && target.closest('.video-shell')) armVideoIdle();
  }

  function attachEvents() {
    if (!documentRef) return;
    if (nodes.root) {
      nodes.root.addEventListener('click', handleClick);
      nodes.root.addEventListener('input', handleInput);
      nodes.root.addEventListener('change', handleChange);
      nodes.root.addEventListener('focusin', handleFocus);
      nodes.root.addEventListener('focusout', function (event) {
        if (event.target && (event.target.id === 'address-input' || event.target.id === 'home-search-input')) {
          global.setTimeout(function () {
            var active = documentRef.activeElement;
            if (!active || (active.id !== 'address-input' && active.id !== 'home-search-input')) closeSuggestions();
          }, 180);
        }
      });
    }
    documentRef.addEventListener('keydown', handleKey);
    global.addEventListener('resize', function () {
      updateViewportInfo();
      if (state.snapshot && (state.snapshot.screen === 'home' || state.snapshot.screen === 'settings')) {
        render(model.getState());
      }
    });
  }

  function init() {
    nodes = {
      root: byId('browser-app'),
      topbar: byId('browser-topbar'),
      navbar: byId('browser-navbar'),
      sidebar: byId('browser-sidebar'),
      screen: byId('browser-screen'),
      frames: byId('browser-frames'),
      overlay: byId('browser-overlay'),
      suggestions: byId('suggestion-layer'),
      companion: byId('companion-screen')
    };
    if (!nodes.root || !model || !view || !ui) return;

    model.subscribe(render);
    view.onLoad(function (tabId, title) {
      clearWatchdog();
      model.markLoaded(tabId, title);
    });
    view.onError(function (tabId, reason) {
      clearWatchdog();
      requestFocus('overlay');
      model.markError(tabId, reason);
      render(model.getState());
    });
    attachEvents();
    applyAccent(model.getState().settings);
    model.init();
  }

  function command(action, args) {
    var options = isObject(args) ? args : {};
    var tab = model.currentTab();
    if (action === 'browser.open') return model.navigateActive(options.url || options.address || '');
    if (action === 'browser.close') { view.close(); return true; }
    if (action === 'browser.back') return model.back();
    if (action === 'browser.forward') return model.forward();
    if (action === 'browser.reload') return model.reload();
    if (action === 'browser.home') { model.goHome(); return true; }
    if (action === 'browser.new_tab') { model.addTab(options.url); return true; }
    if (action === 'browser.close_tab') return closeActiveTab(options.id || (tab && tab.id));
    if (action === 'browser.next_tab') return cycleTab(1);
    if (action === 'browser.previous_tab') return cycleTab(-1);
    if (action === 'video.play' || action === 'video.pause' || action === 'video.seek') {
      if (!tab || !model.isDirectVideo(tab.url)) return false;
      if (model.getState().screen !== 'video') model.setScreen('video');
      var uiAction = action.replace('.', '-');
      return videoAction(uiAction, options.position || 0);
    }
    if (action === 'audio.play' || action === 'audio.pause' || action === 'audio.seek') {
      if (!tab || !model.isDirectAudio(tab.url)) return false;
      if (model.getState().screen !== 'audio') model.setScreen('audio');
      return audioAction(action.replace('.', '-'), options.position || 0);
    }
    return false;
  }

  function suspend() {
    closeSuggestions();
    clearWatchdog();
    cancelSearchFetch();
    if (state.videoIdleTimer) {
      global.clearTimeout(state.videoIdleTimer);
      state.videoIdleTimer = null;
    }
    var video = byId('luma-video');
    if (video && typeof video.pause === 'function') video.pause();
    var audio = byId('luma-audio');
    if (audio && typeof audio.pause === 'function') audio.pause();
    if (nodes.frames) nodes.frames.style.display = 'none';
  }

  function resume() {
    var snapshot = model.getState();
    applyAccent(snapshot.settings);
    render(snapshot);
    if (snapshot.screen === 'search' && state.searchResults.status !== 'ready') {
      loadSearchResults(snapshot.search && snapshot.search.query);
    }
  }

  global.LumaBrowserController = {
    init: init,
    command: command,
    suspend: suspend,
    resume: resume,
    closeSuggestions: closeSuggestions
  };
})(window);
