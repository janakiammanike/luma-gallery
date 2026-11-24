/*
 * Luma Browser — state and navigation model.
 *
 * Owns tabs, per-tab navigation stacks, bookmarks, history and settings.
 * It never renders DOM and never talks to the network. All persistence uses a
 * guarded localStorage wrapper and can degrade to in-memory state.
 */
(function (global) {
  'use strict';

  var KEY = 'luma.browser.v1';
  var MAX_TABS = 8;
  var MAX_HISTORY = 300;
  var MAX_BOOKMARKS = 200;
  var MAX_SHORTCUTS = 12;

  var ENGINES = {
    google: { name: 'Google', prefix: 'https://www.google.com/search?q=' },
    bing: { name: 'Bing', prefix: 'https://www.bing.com/search?q=' },
    duckduckgo: { name: 'DuckDuckGo', prefix: 'https://html.duckduckgo.com/html/?q=' }
  };

  /*
   * LG webOS turns a frame load blocked by X-Frame-Options / CSP into a
   * top-level "Application Error" page with errorCode=-27. That kills the app
   * shell, so these hosts are rejected before an iframe is created. The list
   * is conservative and can grow when a device test confirms another host.
   */
  var FRAME_BLOCKED_HOSTS = [
    'google.com',
    'youtube.com',
    'youtu.be',
    'duckduckgo.com',
    'github.com',
    'reddit.com',
    'facebook.com',
    'instagram.com',
    'twitter.com',
    'x.com',
    'linkedin.com',
    'amazon.com',
    'netflix.com'
  ];

  var FRAME_BLOCKED_MESSAGE =
    'This site blocks in-app framing on LG webOS. Try Bing, Wikipedia or example.com.';

  var DEFAULT_SHORTCUTS = [
    { id: 'sc_bing', title: 'Bing', url: 'https://www.bing.com' },
    { id: 'sc_wikipedia', title: 'Wikipedia', url: 'https://www.wikipedia.org' },
    { id: 'sc_example', title: 'Example', url: 'https://example.com' }
  ];

  var DEFAULT_SETTINGS = {
    homepage: 'https://www.bing.com',
    searchEngine: 'bing',
    startup: 'newtab',
    accent: 'violet',
    zoom: 100,
    showSidebar: false,
    autoplayVideo: false,
    videoVolume: 70
  };

  var listeners = [];
  var sequence = 0;
  var loadToken = 0;

  var state = {
    tabs: [],
    activeTabId: '',
    screen: 'home',
    sidebarOpen: false,
    bookmarks: [],
    history: [],
    settings: copy(DEFAULT_SETTINGS),
    restoredTabs: false,
    shortcuts: copy(DEFAULT_SHORTCUTS),
    loadRequest: null,
    notice: ''
  };

  function copy(value) {
    return JSON.parse(JSON.stringify(value));
  }

  function uid(prefix) {
    sequence += 1;
    return prefix + '_' + Date.now().toString(16) + '_' + sequence;
  }

  function isObject(value) {
    return Object.prototype.toString.call(value) === '[object Object]';
  }

  function isArray(value) {
    return Object.prototype.toString.call(value) === '[object Array]';
  }

  function emit() {
    var snapshot = getState();
    for (var i = 0; i < listeners.length; i += 1) {
      if (typeof listeners[i] === 'function') {
        try {
          listeners[i](snapshot);
        } catch (error) {
          /* a render listener must never break model state */
        }
      }
    }
  }

  function subscribe(listener) {
    if (typeof listener === 'function') listeners.push(listener);
  }

  function getState() {
    return {
      tabs: state.tabs,
      activeTabId: state.activeTabId,
      screen: state.screen,
      sidebarOpen: state.sidebarOpen,
      bookmarks: state.bookmarks,
      history: state.history,
      settings: state.settings,
      shortcuts: state.shortcuts,
      restoredTabs: state.restoredTabs,
      loadRequest: state.loadRequest,
      notice: state.notice
    };
  }

  function currentTab() {
    for (var i = 0; i < state.tabs.length; i += 1) {
      if (state.tabs[i].id === state.activeTabId) return state.tabs[i];
    }
    return state.tabs[0] || null;
  }

  function findTab(id) {
    for (var i = 0; i < state.tabs.length; i += 1) {
      if (state.tabs[i].id === id) return state.tabs[i];
    }
    return null;
  }

  function persist() {
    try {
      if (!global.localStorage) return false;
      var tabs = [];
      for (var i = 0; i < state.tabs.length; i += 1) {
        var tab = state.tabs[i];
        tabs.push({
          id: tab.id,
          entries: tab.entries,
          index: tab.index,
          title: tab.title,
          url: tab.url
        });
      }
      global.localStorage.setItem(KEY, JSON.stringify({
        tabs: tabs,
        activeTabId: state.activeTabId,
        bookmarks: state.bookmarks,
        history: state.history,
        settings: state.settings,
        shortcuts: state.shortcuts
      }));
      return true;
    } catch (error) {
      return false;
    }
  }

  function restore() {
    var raw = null;
    try {
      raw = global.localStorage ? global.localStorage.getItem(KEY) : null;
    } catch (error) {
      raw = null;
    }
    var saved = null;
    if (raw) {
      try {
        saved = JSON.parse(raw);
      } catch (error) {
        saved = null;
      }
    }
    if (!isObject(saved)) return false;

    if (isObject(saved.settings)) {
      var key;
      for (key in DEFAULT_SETTINGS) {
        if (Object.prototype.hasOwnProperty.call(saved.settings, key) &&
            typeof saved.settings[key] === typeof DEFAULT_SETTINGS[key]) {
          state.settings[key] = saved.settings[key];
        }
      }
    }
    if (isArray(saved.bookmarks)) state.bookmarks = saved.bookmarks.slice(0, MAX_BOOKMARKS);
    if (isArray(saved.history)) state.history = saved.history.slice(0, MAX_HISTORY);
    if (isArray(saved.shortcuts) && saved.shortcuts.length) {
      state.shortcuts = saved.shortcuts.slice(0, MAX_SHORTCUTS);
    }

    /* Migrate settings saved before the framing-block guard existed. */
    if (isFrameBlocked(state.settings.homepage)) state.settings.homepage = DEFAULT_SETTINGS.homepage;
    if (state.settings.searchEngine === 'google' || state.settings.searchEngine === 'duckduckgo') {
      state.settings.searchEngine = DEFAULT_SETTINGS.searchEngine;
    }
    var safeShortcuts = [];
    var seenShortcut = {};
    for (var s = 0; s < state.shortcuts.length; s += 1) {
      var shortcut = state.shortcuts[s];
      if (!shortcut || !shortcut.url || isFrameBlocked(shortcut.url)) continue;
      if (seenShortcut[shortcut.url]) continue;
      seenShortcut[shortcut.url] = true;
      safeShortcuts.push(shortcut);
    }
    for (var d = 0; d < DEFAULT_SHORTCUTS.length && safeShortcuts.length < MAX_SHORTCUTS; d += 1) {
      var fallback = DEFAULT_SHORTCUTS[d];
      if (!seenShortcut[fallback.url]) {
        seenShortcut[fallback.url] = true;
        safeShortcuts.push(copy(fallback));
      }
    }
    state.shortcuts = safeShortcuts;
    persist();

    if (state.settings.startup === 'restore' && isArray(saved.tabs) && saved.tabs.length) {
      state.tabs = saved.tabs.slice(0, MAX_TABS);
      state.activeTabId = saved.activeTabId || state.tabs[0].id;
      state.restoredTabs = true;
      return true;
    }
    return false;
  }

  function newTab(url) {
    var tab = {
      id: uid('tab'),
      entries: [null],
      index: 0,
      title: 'New Tab',
      url: null,
      loadedTitle: 'New Tab',
      loading: false,
      error: null
    };
    if (url) {
      var checked = resolveAddress(url);
      if (checked.ok) {
        tab.entries.push(checked.url);
        tab.index = 1;
        tab.url = checked.url;
        tab.title = titleFromUrl(checked.url);
      }
    }
    return tab;
  }

  function titleFromUrl(url) {
    if (!url) return 'New Tab';
    try {
      var parser = global.document.createElement('a');
      parser.href = url;
      var host = parser.hostname || url;
      return host.replace(/^www\./, '');
    } catch (error) {
      return 'Page';
    }
  }

  function sanitizeUrl(url) {
    if (!url) return '';
    var value = String(url);
    var cut = value.search(/[?#]/);
    if (cut >= 0) value = value.substring(0, cut);
    return value.length > 90 ? value.substring(0, 87) + '…' : value;
  }

  function isFrameBlocked(url) {
    var host = '';
    try {
      var parser = global.document.createElement('a');
      parser.href = String(url);
      host = (parser.hostname || '').toLowerCase();
    } catch (error) {
      return false;
    }
    for (var i = 0; i < FRAME_BLOCKED_HOSTS.length; i += 1) {
      var blocked = FRAME_BLOCKED_HOSTS[i];
      if (host === blocked || host.slice(-(blocked.length + 1)) === '.' + blocked) return true;
    }
    return false;
  }

  function resolveAddress(raw) {
    if (typeof raw !== 'string') return { ok: false, reason: 'Address required.' };
    var value = raw.replace(/^\s+|\s+$/g, '');
    if (!value) return { ok: false, reason: 'Address required.' };
    if (value === 'about:home') return { ok: true, url: 'about:home' };

    var validator = global.LumaBrowserView && global.LumaBrowserView.validateUrl;
    if (typeof validator === 'function') {
      var checked = validator(value);
      if (checked.ok) {
        if (isFrameBlocked(checked.url)) return { ok: false, reason: FRAME_BLOCKED_MESSAGE, blocked: true };
        return { ok: true, url: checked.url };
      }
    } else if (/^https?:\/\//i.test(value)) {
      if (isFrameBlocked(value)) return { ok: false, reason: FRAME_BLOCKED_MESSAGE, blocked: true };
      return { ok: true, url: value };
    }

    if (value.indexOf('.') > 0 && !/[\s/]/.test(value)) {
      value = 'https://' + value;
      if (typeof validator === 'function') {
        var implied = validator(value);
        if (implied.ok) {
          if (isFrameBlocked(implied.url)) return { ok: false, reason: FRAME_BLOCKED_MESSAGE, blocked: true };
          return { ok: true, url: implied.url };
        }
      }
    }

    var engine = ENGINES[state.settings.searchEngine] || ENGINES.bing;
    var query = value;
    if (typeof validator === 'function') {
      var searchUrl = engine.prefix + encodeURIComponent(query);
      var searchChecked = validator(searchUrl);
      if (searchChecked.ok) {
        if (isFrameBlocked(searchChecked.url)) return { ok: false, reason: FRAME_BLOCKED_MESSAGE, blocked: true };
        return { ok: true, url: searchChecked.url, search: true };
      }
    }
    return { ok: false, reason: 'Enter an absolute http(s) address or a search term.' };
  }

  function requestLoad(tab) {
    loadToken += 1;
    state.loadRequest = {
      tabId: tab.id,
      url: tab.url,
      token: loadToken,
      reload: false
    };
  }

  function setScreen(screen) {
    var known = {
      home: true,
      page: true,
      bookmarks: true,
      history: true,
      tabs: true,
      settings: true,
      video: true,
      diagnostics: true,
      companion: true
    };
    state.screen = known[screen] ? screen : 'home';
    state.notice = '';
    emit();
  }

  function toggleSidebar(force) {
    state.sidebarOpen = typeof force === 'boolean' ? force : !state.sidebarOpen;
    emit();
  }

  function init() {
    var restored = restore();
    if (!restored) {
      state.tabs = [newTab()];
      state.activeTabId = state.tabs[0].id;
    }
    if (!findTab(state.activeTabId)) state.activeTabId = state.tabs[0].id;

    if (state.restoredTabs) {
      var active = currentTab();
      state.screen = active && active.url ? 'page' : 'home';
      if (active && active.url) requestLoad(active);
    } else if (state.settings.startup === 'homepage') {
      navigateActive(state.settings.homepage);
    } else {
      state.screen = 'home';
    }
    state.sidebarOpen = !!state.settings.showSidebar;
    emit();
  }

  function addTab(url) {
    if (state.tabs.length >= MAX_TABS) {
      state.notice = 'Tab limit reached (' + MAX_TABS + '). Close a tab first.';
      emit();
      return null;
    }
    var tab = newTab(url);
    state.tabs.push(tab);
    state.activeTabId = tab.id;
    state.screen = tab.url ? 'page' : 'home';
    if (tab.url) requestLoad(tab);
    persist();
    emit();
    return tab;
  }

  function closeTab(id) {
    if (state.tabs.length === 1) {
      state.tabs[0] = newTab();
      state.activeTabId = state.tabs[0].id;
      state.screen = 'home';
      state.loadRequest = { tabId: state.tabs[0].id, url: null, token: ++loadToken, reload: false };
    } else {
      var index = -1;
      for (var i = 0; i < state.tabs.length; i += 1) {
        if (state.tabs[i].id === id) index = i;
      }
      if (index < 0) return;
      state.tabs.splice(index, 1);
      if (state.activeTabId === id) {
        state.activeTabId = state.tabs[Math.min(index, state.tabs.length - 1)].id;
        var active = currentTab();
        state.screen = active && active.url ? 'page' : 'home';
        if (active && active.url) requestLoad(active);
      }
    }
    persist();
    emit();
  }

  function activateTab(id) {
    var tab = findTab(id);
    if (!tab) return;
    state.activeTabId = id;
    state.screen = tab.url ? 'page' : 'home';
    state.sidebarOpen = false;
    if (tab.url) requestLoad(tab);
    emit();
  }

  function addHistory(url, title) {
    var entry = {
      id: uid('hist'),
      url: url,
      title: title || titleFromUrl(url),
      ts: Date.now()
    };
    state.history.unshift(entry);
    if (state.history.length > MAX_HISTORY) state.history.length = MAX_HISTORY;
    persist();
  }

  function navigate(id, raw, options) {
    var tab = findTab(id) || currentTab();
    if (!tab) return false;
    var resolved = resolveAddress(raw);
    if (!resolved.ok || resolved.url === 'about:home') {
      state.notice = resolved.reason || 'Address cannot be opened.';
      emit();
      return false;
    }

    if (tab.index < tab.entries.length - 1) {
      tab.entries = tab.entries.slice(0, tab.index + 1);
    }
    tab.entries.push(resolved.url);
    tab.index = tab.entries.length - 1;
    tab.url = resolved.url;
    tab.title = resolved.search ? ('Search · ' + String(raw).substring(0, 28)) : titleFromUrl(resolved.url);
    tab.loading = true;
    tab.error = null;
    state.screen = 'page';
    state.notice = '';
    addHistory(resolved.url, tab.title);
    requestLoad(tab);
    emit();
    return true;
  }

  function navigateActive(raw) {
    var tab = currentTab();
    return tab ? navigate(tab.id, raw) : false;
  }

  function goHome() {
    var tab = currentTab();
    if (!tab) return;
    state.screen = 'home';
    state.notice = '';
    emit();
  }

  function step(direction) {
    var tab = currentTab();
    if (!tab) return false;
    var target = tab.index + direction;
    if (target < 0 || target >= tab.entries.length) return false;
    tab.index = target;
    tab.url = tab.entries[target];
    tab.loading = !!tab.url;
    tab.error = null;
    state.screen = tab.url ? 'page' : 'home';
    if (tab.url) requestLoad(tab);
    emit();
    return true;
  }

  function reload() {
    var tab = currentTab();
    if (!tab || !tab.url) return false;
    tab.loading = true;
    tab.error = null;
    requestLoad(tab);
    state.loadRequest.reload = true;
    emit();
    return true;
  }

  function markLoaded(id, title) {
    var tab = findTab(id);
    if (!tab) return;
    tab.loading = false;
    tab.error = null;
    if (title && title.indexOf('http') !== 0 && title.length < 80) tab.loadedTitle = title;
    emit();
  }

  function markError(id, reason) {
    var tab = findTab(id);
    if (!tab) return;
    tab.loading = false;
    tab.error = reason || 'The page could not be displayed.';
    emit();
  }

  function toggleBookmark() {
    var tab = currentTab();
    if (!tab || !tab.url) {
      state.notice = 'Open a page before bookmarking it.';
      emit();
      return false;
    }
    for (var i = 0; i < state.bookmarks.length; i += 1) {
      if (state.bookmarks[i].url === tab.url) {
        state.bookmarks.splice(i, 1);
        state.notice = 'Bookmark removed.';
        persist();
        emit();
        return true;
      }
    }
    if (state.bookmarks.length >= MAX_BOOKMARKS) {
      state.notice = 'Bookmark limit reached.';
      emit();
      return false;
    }
    state.bookmarks.unshift({
      id: uid('bm'),
      title: tab.title || titleFromUrl(tab.url),
      url: tab.url,
      ts: Date.now()
    });
    state.notice = 'Bookmark added.';
    persist();
    emit();
    return true;
  }

  function isBookmarked(url) {
    for (var i = 0; i < state.bookmarks.length; i += 1) {
      if (state.bookmarks[i].url === url) return true;
    }
    return false;
  }

  function updateBookmark(id, title, url) {
    for (var i = 0; i < state.bookmarks.length; i += 1) {
      if (state.bookmarks[i].id === id) {
        var resolved = resolveAddress(url);
        if (!resolved.ok) {
          state.notice = resolved.reason;
          emit();
          return false;
        }
        state.bookmarks[i].title = String(title).substring(0, 80) || titleFromUrl(resolved.url);
        state.bookmarks[i].url = resolved.url;
        persist();
        emit();
        return true;
      }
    }
    return false;
  }

  function removeBookmark(id) {
    for (var i = 0; i < state.bookmarks.length; i += 1) {
      if (state.bookmarks[i].id === id) {
        state.bookmarks.splice(i, 1);
        persist();
        emit();
        return true;
      }
    }
    return false;
  }

  function removeHistory(id) {
    for (var i = 0; i < state.history.length; i += 1) {
      if (state.history[i].id === id) {
        state.history.splice(i, 1);
        persist();
        emit();
        return true;
      }
    }
    return false;
  }

  function clearHistory() {
    var tab = currentTab();
    state.history = [];
    if (tab) {
      tab.entries = [tab.url || null];
      tab.index = 0;
    }
    persist();
    state.notice = 'History cleared.';
    emit();
    return true;
  }

  function isDirectVideo(url) {
    if (!url || !/^https?:\/\//i.test(url)) return false;
    var clean = String(url).split(/[?#]/)[0].toLowerCase();
    return /\.(mp4|webm|ogv|ogg|m4v|mov)(?:$|\/)/.test(clean);
  }

  function resetShortcuts() {
    state.shortcuts = copy(DEFAULT_SHORTCUTS);
    persist();
    state.notice = 'Home shortcuts reset.';
    emit();
    return true;
  }

  function addShortcut() {
    var tab = currentTab();
    if (!tab || !tab.url) {
      state.notice = 'Open a page before adding a shortcut.';
      emit();
      return false;
    }
    if (state.shortcuts.length >= MAX_SHORTCUTS) {
      state.notice = 'Shortcut limit reached.';
      emit();
      return false;
    }
    state.shortcuts.push({
      id: uid('sc'),
      title: tab.title || titleFromUrl(tab.url),
      url: tab.url
    });
    persist();
    state.notice = 'Shortcut added to Home.';
    emit();
    return true;
  }

  function removeShortcut(id) {
    for (var i = 0; i < state.shortcuts.length; i += 1) {
      if (state.shortcuts[i].id === id) {
        state.shortcuts.splice(i, 1);
        persist();
        emit();
        return true;
      }
    }
    return false;
  }

  function setSetting(key, value) {
    if (!Object.prototype.hasOwnProperty.call(DEFAULT_SETTINGS, key)) return false;
    if (typeof value !== typeof DEFAULT_SETTINGS[key]) return false;
    if (key === 'zoom') value = Math.max(50, Math.min(200, Math.floor(value)));
    if (key === 'videoVolume') value = Math.max(0, Math.min(100, Math.floor(value)));
    state.settings[key] = value;
    persist();
    emit();
    return true;
  }

  function setNotice(text) {
    state.notice = String(text || '').substring(0, 160);
    emit();
  }

  global.LumaBrowserModel = {
    init: init,
    subscribe: subscribe,
    getState: getState,
    currentTab: currentTab,
    findTab: findTab,
    sanitizeUrl: sanitizeUrl,
    titleFromUrl: titleFromUrl,
    isBookmarked: isBookmarked,
    isDirectVideo: isDirectVideo,
    resolveAddress: resolveAddress,
    isFrameBlocked: isFrameBlocked,
    engines: ENGINES,
    setScreen: setScreen,
    toggleSidebar: toggleSidebar,
    addTab: addTab,
    closeTab: closeTab,
    activateTab: activateTab,
    navigate: navigate,
    navigateActive: navigateActive,
    goHome: goHome,
    back: function () { return step(-1); },
    forward: function () { return step(1); },
    reload: reload,
    markLoaded: markLoaded,
    markError: markError,
    toggleBookmark: toggleBookmark,
    updateBookmark: updateBookmark,
    removeBookmark: removeBookmark,
    removeHistory: removeHistory,
    clearHistory: clearHistory,
    addShortcut: addShortcut,
    removeShortcut: removeShortcut,
    resetShortcuts: resetShortcuts,
    setSetting: setSetting,
    setNotice: setNotice
  };
})(window);
