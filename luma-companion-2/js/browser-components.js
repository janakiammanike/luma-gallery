/*
 * Luma Browser — reusable UI components.
 *
 * Every component renders plain HTML into a container and exposes no private
 * state. Actions are delegated through data-action attributes so the screen
 * controller owns focus, keyboard input and event routing.
 */
(function (global) {
  'use strict';

  function esc(value) {
    return String(value === null || value === undefined ? '' : value)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function focusAttrs(extra) {
    return ' data-focusable="true" tabindex="-1"' + (extra || '');
  }

  function button(action, label, cls, extra) {
    return '<button type="button" class="' + (cls || 'ui-button') + '"' +
      focusAttrs(' data-action="' + action + '"' + (extra || '')) + '>' +
      esc(label) + '</button>';
  }

  function favicon(title, url) {
    var source = title || (url ? global.LumaBrowserModel.titleFromUrl(url) : '?');
    var letter = String(source).replace(/^www\./, '').charAt(0).toUpperCase() || '?';
    return '<span class="site-fav" aria-hidden="true">' + esc(letter) + '</span>';
  }

  function tabBar(model, overflowOpen) {
    var html = '<div class="luma-mark" aria-hidden="true">L</div><div class="tab-strip">';
    for (var i = 0; i < model.tabs.length; i += 1) {
      var tab = model.tabs[i];
      var active = tab.id === model.activeTabId;
      html += '<div class="tab-item' + (active ? ' is-active' : '') + '"' +
        focusAttrs(' data-action="tab-activate" data-id="' + esc(tab.id) + '"') + '>' +
        favicon(tab.title, tab.url) +
        '<span class="tab-title">' + esc(tab.title) + '</span>' +
        '<button type="button" class="tab-close" aria-label="Close tab"' +
        focusAttrs(' data-action="tab-close" data-id="' + esc(tab.id) + '"') + '>×</button>' +
        '</div>';
    }
    html += button('tab-add', '+', 'tab-add', ' aria-label="New tab"') + '</div>';
    html += button('tab-overflow', '⋯', 'tab-overflow', ' aria-label="Tab list"');
    html += button('screen-settings', 'Settings', 'tab-settings');
    if (overflowOpen) html += '<div class="tab-overflow-note">' + esc(model.tabs.length) + ' tabs open</div>';
    return html;
  }

  function navigationBar(model) {
    var tab = global.LumaBrowserModel.currentTab();
    var url = tab && tab.url ? tab.url : '';
    var canBack = !!(tab && tab.index > 0);
    var canForward = !!(tab && tab.index < tab.entries.length - 1);
    var secure = /^https:/i.test(url);
    var indicator = url ? (secure ? 'Secure' : 'Not secure') : 'Home';
    var bookmarked = url ? global.LumaBrowserModel.isBookmarked(url) : false;

    var html = '<div class="nav-controls">';
    html += button('back', '‹', 'nav-icon', (canBack ? '' : ' disabled="disabled"') + ' aria-label="Back"');
    html += button('forward', '›', 'nav-icon', (canForward ? '' : ' disabled="disabled"') + ' aria-label="Forward"');
    html += tab && tab.loading
      ? button('stop', '■', 'nav-icon', ' aria-label="Stop"')
      : button('reload', '↻', 'nav-icon', (url ? '' : ' disabled="disabled"') + ' aria-label="Reload"');
    html += button('home', '⌂', 'nav-icon', ' aria-label="Home"');
    html += '</div>';

    html += '<div class="address-wrap" data-security="' + (secure ? 'secure' : 'plain') + '">';
    html += '<span class="security-label">' + esc(indicator) + '</span>';
    html += '<input id="address-input" class="address-input" type="text" value="' + esc(url) +
      '" placeholder="Search or enter an http(s) address" autocomplete="off" spellcheck="false"' +
      focusAttrs(' aria-label="Address and search"') + ' />';
    html += button('address-clear', '×', 'address-clear', ' aria-label="Clear address"');
    html += button('address-go', 'Go', 'address-go');
    html += '</div>';

    html += '<div class="nav-controls nav-controls-right">';
    html += button('bookmark-toggle', bookmarked ? '★' : '☆', 'nav-icon',
      (url ? '' : ' disabled="disabled"') + ' aria-label="Bookmark this page"');
    html += button('screen-video', '▶', 'nav-icon', ' aria-label="Luma video player"');
    html += button('sidebar-toggle', '☰', 'nav-icon', ' aria-label="Toggle side menu"');
    html += button('screen-settings', '⚙', 'nav-icon', ' aria-label="Settings"');
    html += '</div>';
    return html;
  }

  function sidebar(model) {
    var items = [
      ['home', 'Home', '⌂'],
      ['tab-add', 'New Tab', '+'],
      ['bookmarks', 'Bookmarks', '☆'],
      ['history', 'History', '◷'],
      ['tabs', 'Recent Tabs', '▤'],
      ['video', 'Luma Player', '▶'],
      ['settings', 'Settings', '⚙'],
      ['companion', 'Companion', '⌁']
    ];
    var html = '<div class="side-title">LUMA MENU</div>';
    for (var i = 0; i < items.length; i += 1) {
      var action = items[i][0] === 'tab-add' ? 'tab-add' : 'screen-' + items[i][0];
      var selected = model.screen === items[i][0];
      html += '<button type="button" class="side-item' + (selected ? ' is-active' : '') + '"' +
        focusAttrs(' data-action="' + action + '"') + '>' +
        '<span class="side-glyph">' + esc(items[i][2]) + '</span>' +
        '<span>' + esc(items[i][1]) + '</span></button>';
    }
    html += '<p class="side-note">No downloads or site permissions API is exposed to this packaged webOS app. Those controls are intentionally absent.</p>';
    return html;
  }

  function suggestionList(items) {
    if (!items || !items.length) return '';
    var html = '';
    for (var i = 0; i < items.length; i += 1) {
      html += '<button type="button" class="suggestion"' +
        focusAttrs(' data-action="suggestion" data-id="' + esc(items[i].id) + '" data-value="' + esc(items[i].value) + '"') + '>' +
        favicon(items[i].title, items[i].value) +
        '<span class="suggestion-text"><strong>' + esc(items[i].title) + '</strong>' +
        '<small>' + esc(global.LumaBrowserModel.sanitizeUrl(items[i].value)) + '</small></span>' +
        '</button>';
    }
    return html;
  }

  function quickAccess(model) {
    var html = '<div class="quick-grid">';
    for (var i = 0; i < model.shortcuts.length; i += 1) {
      var item = model.shortcuts[i];
      html += '<div class="quick-item">';
      html += '<button type="button" class="quick-open"' + focusAttrs(' data-action="navigate" data-value="' + esc(item.url) + '"') + '>';
      html += favicon(item.title, item.url) + '<span>' + esc(item.title) + '</span></button>';
      html += '<button type="button" class="quick-remove" aria-label="Remove shortcut"' +
        focusAttrs(' data-action="shortcut-remove" data-id="' + esc(item.id) + '"') + '>×</button>';
      html += '</div>';
    }
    html += '</div>';
    return html;
  }

  function linkRow(item, action, removeAction, extra) {
    return '<div class="list-row">' +
      '<button type="button" class="list-open"' + focusAttrs(' data-action="' + action + '" data-value="' + esc(item.url) + '"') + '>' +
      favicon(item.title, item.url) +
      '<span class="list-text"><strong>' + esc(item.title) + '</strong>' +
      '<small>' + esc(global.LumaBrowserModel.sanitizeUrl(item.url)) + '</small></span></button>' +
      (extra || '') +
      (removeAction ? '<button type="button" class="list-remove" aria-label="Remove"' +
        focusAttrs(' data-action="' + removeAction + '" data-id="' + esc(item.id) + '"') + '>×</button>' : '') +
      '</div>';
  }

  function emptyState(message) {
    return '<div class="empty-state">' + esc(message) + '</div>';
  }

  function home(model) {
    var info = global.LumaDiagnosticsInfo || {};
    var html = '<div class="home-wrap">';
    html += '<header class="home-header">' +
      '<div class="home-logo">L</div><div><p class="home-kicker">LUMA BROWSER</p>' +
      '<h2>Where to today?</h2></div></header>';
    html += '<div class="home-search">' +
      '<span class="home-search-icon">⌕</span>' +
      '<input id="home-search-input" type="text" placeholder="Search with ' +
      esc((global.LumaBrowserModel.engines[model.settings.searchEngine] || {}).name || 'Google') +
      ' or enter an address" autocomplete="off" spellcheck="false"' + focusAttrs() + ' />' +
      button('home-search-go', 'Search', 'ui-button primary') + '</div>';

    html += '<section class="home-section"><div class="section-head"><h3>Quick Access</h3>' +
      button('shortcut-add', 'Add current page', 'ui-button subtle') + '</div>' +
      quickAccess(model) + '</section>';

    var frequent = model.history.slice(0, 6);
    html += '<section class="home-section"><div class="section-head"><h3>Frequently visited</h3></div>';
    html += frequent.length ? '<div class="list-grid">' : emptyState('No visited pages yet. Open a page to see it here.');
    for (var i = 0; i < frequent.length; i += 1) {
      html += linkRow(frequent[i], 'navigate', 'history-remove');
    }
    html += frequent.length ? '</div>' : '';
    html += '</section>';

    var recent = model.history.slice(6, 12);
    html += '<section class="home-section"><div class="section-head"><h3>Recently opened</h3>' +
      button('screen-history', 'Full history', 'ui-button subtle') + '</div>';
    html += recent.length ? '<div class="list-grid">' : emptyState('No older visits yet.');
    for (var r = 0; r < recent.length; r += 1) {
      html += linkRow(recent[r], 'navigate', 'history-remove');
    }
    html += recent.length ? '</div>' : '';
    html += '</section>';

    html += '<section class="home-section"><div class="section-head"><h3>Bookmarks</h3>' +
      button('screen-bookmarks', 'Manage', 'ui-button subtle') + '</div>';
    var marks = model.bookmarks.slice(0, 6);
    html += marks.length ? '<div class="list-grid">' : emptyState('No bookmarks yet. Use the star in the address bar.');
    for (var j = 0; j < marks.length; j += 1) {
      html += linkRow(marks[j], 'navigate', '');
    }
    html += marks.length ? '</div>' : '';
    html += '</section>';

    html += '<section class="home-section"><div class="section-head"><h3>Recent tabs</h3>' +
      button('screen-tabs', 'Tab manager', 'ui-button subtle') + '</div>';
    html += '<div class="list-grid">';
    for (var k = 0; k < model.tabs.length; k += 1) {
      var tab = model.tabs[k];
      html += linkRow({ id: tab.id, title: tab.title, url: tab.url || 'about:home' }, 'tab-activate', '');
    }
    html += '</div></section>';

    html += '<p class="home-foot">Engine: ' + esc(info.engine || 'detecting') +
      ' · Viewport: ' + esc(info.viewport || 'detecting') +
      ' · Frame rendering only; no system-browser launch is assumed.</p>';
    return html + '</div>';
  }

  function bookmarks(model, query) {
    var q = String(query || '').toLowerCase();
    var rows = [];
    for (var i = 0; i < model.bookmarks.length; i += 1) {
      var item = model.bookmarks[i];
      if (!q || item.title.toLowerCase().indexOf(q) >= 0 || item.url.toLowerCase().indexOf(q) >= 0) rows.push(item);
    }
    var html = '<div class="screen-head"><h2>Bookmarks</h2>' +
      button('screen-home', 'Back to Home', 'ui-button subtle') + '</div>';
    html += '<div class="screen-tools"><input id="bookmark-search-input" type="text" value="' + esc(query || '') +
      '" placeholder="Search bookmarks" autocomplete="off" spellcheck="false"' + focusAttrs() + ' /></div>';
    if (!rows.length) return html + emptyState(model.bookmarks.length ? 'No bookmarks match that search.' : 'No bookmarks yet. Open a page and press the star.');
    html += '<div class="list-grid list-grid-wide">';
    for (var j = 0; j < rows.length; j += 1) {
      var row = rows[j];
      var edit = '<div class="edit-fields">' +
        '<input id="bm-title-' + esc(row.id) + '" type="text" value="' + esc(row.title) + '" aria-label="Bookmark title"' + focusAttrs() + ' />' +
        '<input id="bm-url-' + esc(row.id) + '" type="text" value="' + esc(row.url) + '" aria-label="Bookmark address"' + focusAttrs() + ' />' +
        button('bookmark-save', 'Save', 'ui-button small', ' data-id="' + esc(row.id) + '"') + '</div>';
      html += linkRow(row, 'navigate', 'bookmark-remove', edit);
    }
    return html + '</div>';
  }

  function dateLabel(ts) {
    var date = new Date(ts);
    var today = new Date();
    if (date.toDateString() === today.toDateString()) return 'Today';
    var yesterday = new Date(today.getTime() - 86400000);
    if (date.toDateString() === yesterday.toDateString()) return 'Yesterday';
    return date.toLocaleDateString();
  }

  function history(model, query) {
    var q = String(query || '').toLowerCase();
    var rows = [];
    for (var i = 0; i < model.history.length; i += 1) {
      var item = model.history[i];
      if (!q || item.title.toLowerCase().indexOf(q) >= 0 || item.url.toLowerCase().indexOf(q) >= 0) rows.push(item);
    }
    var html = '<div class="screen-head"><h2>History</h2>' +
      button('history-clear', 'Clear history', 'ui-button subtle') +
      button('screen-home', 'Back to Home', 'ui-button subtle') + '</div>';
    html += '<div class="screen-tools"><input id="history-search-input" type="text" value="' + esc(query || '') +
      '" placeholder="Search history" autocomplete="off" spellcheck="false"' + focusAttrs() + ' /></div>';
    if (!rows.length) return html + emptyState(model.history.length ? 'No history entries match that search.' : 'No history yet. Visited pages are listed here.');
    var lastGroup = '';
    html += '<div class="list-grid list-grid-wide">';
    for (var j = 0; j < rows.length; j += 1) {
      var row = rows[j];
      var group = dateLabel(row.ts);
      if (group !== lastGroup) {
        html += '</div><h3 class="group-label">' + esc(group) + '</h3><div class="list-grid list-grid-wide">';
        lastGroup = group;
      }
      var time = new Date(row.ts).toLocaleTimeString();
      var item = { id: row.id, title: row.title + ' · ' + time, url: row.url };
      html += linkRow(item, 'navigate', 'history-remove');
    }
    return html + '</div>';
  }

  function tabManager(model) {
    var html = '<div class="screen-head"><h2>Tab Manager</h2>' +
      button('tab-add', 'New tab', 'ui-button primary') +
      button('screen-home', 'Back to Home', 'ui-button subtle') + '</div>';
    if (!model.tabs.length) return html + emptyState('No open tabs.');
    html += '<div class="tab-card-grid">';
    for (var i = 0; i < model.tabs.length; i += 1) {
      var tab = model.tabs[i];
      html += '<div class="tab-card' + (tab.id === model.activeTabId ? ' is-active' : '') + '"' +
        focusAttrs(' data-action="tab-activate" data-id="' + esc(tab.id) + '"') + '>' +
        '<div class="tab-card-top">' + favicon(tab.title, tab.url) +
        '<span class="tab-card-state">' + (tab.id === model.activeTabId ? 'ACTIVE' : 'OPEN') + '</span></div>' +
        '<strong>' + esc(tab.title) + '</strong>' +
        '<small>' + esc(global.LumaBrowserModel.sanitizeUrl(tab.url || 'about:home')) + '</small>' +
        '<p class="tab-card-note">' + (tab.loading ? 'Loading…' : (tab.error ? 'Load error' : (tab.url ? 'Page ready' : 'New tab page'))) + '</p>' +
        button('tab-close', 'Close', 'ui-button small', ' data-id="' + esc(tab.id) + '"') +
        '</div>';
    }
    return html + '</div><p class="screen-note">Live page previews are not captured: cross-origin frame contents cannot be read by this app.</p>';
  }

  function optionSelect(label, id, action, options, current) {
    var html = '<label class="setting-row"><span>' + esc(label) + '</span><select id="' + esc(id) + '"' +
      focusAttrs(' data-action="' + action + '"') + '>';
    for (var i = 0; i < options.length; i += 1) {
      html += '<option value="' + esc(options[i][0]) + '"' + (options[i][0] === current ? ' selected="selected"' : '') + '>' +
        esc(options[i][1]) + '</option>';
    }
    return html + '</select></label>';
  }

  function settings(model) {
    var s = model.settings;
    var info = global.LumaDiagnosticsInfo || {};
    var html = '<div class="screen-head"><h2>Settings</h2>' +
      button('screen-home', 'Back to Home', 'ui-button subtle') + '</div>';
    html += '<div class="settings-grid">';

    html += '<section class="settings-card"><h3>General</h3>' +
      '<label class="setting-row setting-wide"><span>Homepage</span>' +
      '<input id="setting-homepage" type="text" value="' + esc(s.homepage) + '"' + focusAttrs() + ' /></label>' +
      button('setting-homepage-save', 'Save homepage', 'ui-button small') +
      optionSelect('Default search engine', 'setting-engine', 'setting-engine', [
        ['google', 'Google'], ['bing', 'Bing'], ['duckduckgo', 'DuckDuckGo']
      ], s.searchEngine) +
      optionSelect('Startup behavior', 'setting-startup', 'setting-startup', [
        ['newtab', 'New tab'], ['homepage', 'Homepage'], ['restore', 'Restore last tabs']
      ], s.startup) +
      optionSelect('Website zoom', 'setting-zoom', 'setting-zoom', [
        [50, '50%'], [75, '75%'], [100, '100%'], [125, '125%'], [150, '150%'], [200, '200%']
      ], s.zoom) +
      '</section>';

    html += '<section class="settings-card"><h3>Appearance</h3>' +
      '<p class="setting-note">Dark theme is always on for TV viewing.</p>' +
      optionSelect('Accent color', 'setting-accent', 'setting-accent', [
        ['violet', 'Luma violet'], ['blue', 'Cool blue'], ['amber', 'Warm amber']
      ], s.accent) +
      '<label class="setting-row"><span>Open side menu at startup</span>' +
      '<input type="checkbox" id="setting-sidebar"' + (s.showSidebar ? ' checked="checked"' : '') + focusAttrs(' data-action="setting-sidebar"') + ' /></label>' +
      '</section>';

    html += '<section class="settings-card"><h3>Privacy</h3>' +
      '<p class="setting-note">History and bookmarks stay in this app on the TV. No browsing data is sent to the phone or a cloud service.</p>' +
      button('history-clear', 'Clear browsing history', 'ui-button small') +
      button('shortcuts-reset', 'Reset Home shortcuts', 'ui-button small subtle') +
      '<p class="setting-note">Cookies and site permissions are managed by the webOS engine; this app exposes no controls for them.</p>' +
      '</section>';

    html += '<section class="settings-card"><h3>Playback</h3>' +
      '<p class="setting-note">Applies only to the dedicated Luma Video Player, never to a website’s own player.</p>' +
      '<label class="setting-row"><span>Autoplay direct video links</span>' +
      '<input type="checkbox" id="setting-autoplay"' + (s.autoplayVideo ? ' checked="checked"' : '') + focusAttrs(' data-action="setting-autoplay"') + ' /></label>' +
      '<label class="setting-row"><span>Default volume</span>' +
      '<input type="range" min="0" max="100" id="setting-volume" value="' + esc(s.videoVolume) + '"' + focusAttrs(' data-action="setting-volume"') + ' /></label>' +
      '<p class="setting-note">Subtitles and codec/DRM choices are not offered without verified platform support.</p>' +
      '</section>';

    html += '<section class="settings-card"><h3>About</h3>' +
      '<div class="about-list">' +
      '<div><span>Luma Companion</span><strong>' + esc(info.version || '0.1.0') + '</strong></div>' +
      '<div><span>webOS SDK</span><strong>' + esc(info.sdk || 'unavailable') + '</strong></div>' +
      '<div><span>TV model</span><strong>' + esc(info.model || 'unavailable') + '</strong></div>' +
      '<div><span>Browser engine</span><strong>' + esc(info.engine || 'unavailable') + '</strong></div>' +
      '<div><span>Viewport</span><strong>' + esc(info.viewport || 'unavailable') + '</strong></div>' +
      '<div><span>Device pixel ratio</span><strong>' + esc(info.dpr || 'unavailable') + '</strong></div>' +
      '</div><p class="setting-note">No third-party browser framework is bundled.</p></section>';

    return html + '</div>';
  }

  function video(model, supported) {
    var tab = global.LumaBrowserModel.currentTab();
    var source = tab && tab.url ? tab.url : '';
    var html = '<div class="screen-head"><h2>Luma Video Player</h2>' +
      button('video-close', 'Back to page', 'ui-button subtle') + '</div>';
    if (!supported || !source) {
      return html + emptyState('The Luma Player opens direct video files only. Open an .mp4, .webm, .ogv, .m4v or .mov address first; video inside a website stays under that website’s own player.');
    }
    html += '<div class="video-shell"><video id="luma-video" playsinline preload="metadata" src="' + esc(source) + '"></video>';
    html += '<div id="video-buffering" class="video-buffering">Buffering…</div>';
    html += '<div id="video-error" class="video-error"></div>';
    html += '<div class="video-controls">' +
      '<div class="video-progress"><input id="video-seek" type="range" min="0" max="100" value="0" aria-label="Seek"' + focusAttrs() + ' />' +
      '<span id="video-time">0:00 / 0:00</span></div>' +
      '<div class="video-buttons">' +
      button('video-play', 'Play', 'ui-button primary') +
      button('video-pause', 'Pause', 'ui-button') +
      button('video-back10', '−10s', 'ui-button subtle') +
      button('video-forward10', '+10s', 'ui-button subtle') +
      '<label class="video-volume"><span>Volume</span><input id="video-volume" type="range" min="0" max="100" value="' + esc(model.settings.videoVolume) + '"' + focusAttrs(' data-action="video-volume"') + ' /></label>' +
      button('video-fullscreen', 'Fullscreen', 'ui-button subtle') +
      '<span class="video-disabled" title="No subtitle track is provided by this app">Subtitles unavailable</span>' +
      '</div></div></div>';
    html += '<p class="screen-note">This player opens direct video files only. It cannot control video inside a website frame.</p>';
    return html + '</div>';
  }

  function diagnosticShell() {
    return '<div class="screen-head"><h2>Diagnostics</h2>' +
      '<p class="screen-note">Local checks only. Telemetry remains off.</p></div>';
  }

  global.LumaBrowserUI = {
    esc: esc,
    focusAttrs: focusAttrs,
    tabBar: tabBar,
    navigationBar: navigationBar,
    sidebar: sidebar,
    suggestionList: suggestionList,
    home: home,
    bookmarks: bookmarks,
    history: history,
    tabManager: tabManager,
    settings: settings,
    video: video,
    diagnosticShell: diagnosticShell,
    emptyState: emptyState
  };
})(window);
