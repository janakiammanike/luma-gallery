/*
 * Luma Browser — real website renderer and URL validation.
 *
 * Each open tab owns one sandboxed iframe. The app never reads a framed page's
 * document unless the browser reports same-origin access, and it never reports
 * a visited address through telemetry. Third-party framing and DRM are not
 * promised; the UI reports the frame's own load event only.
 */
(function (global) {
  'use strict';

  var MAX_URL_LENGTH = 2048;
  var BLANK = 'about:blank';
  var frames = {};
  var loadCallback = null;
  var errorCallback = null;

  function validateUrl(value) {
    if (typeof value !== 'string' || value.length === 0) {
      return { ok: false, reason: 'url_required' };
    }
    if (value.length > MAX_URL_LENGTH) {
      return { ok: false, reason: 'url_too_long' };
    }
    if (/[\u0000-\u001F\u007F]/.test(value) || /^\s|\s$/.test(value)) {
      return { ok: false, reason: 'url_control_character' };
    }

    var parser = document.createElement('a');
    parser.href = value;
    var protocol = (parser.protocol || '').toLowerCase();
    if (protocol !== 'http:' && protocol !== 'https:') {
      return { ok: false, reason: 'url_scheme_not_allowed' };
    }
    if (!parser.hostname) {
      return { ok: false, reason: 'url_host_required' };
    }
    if (parser.username || parser.password) {
      return { ok: false, reason: 'url_credentials_not_allowed' };
    }
    if (parser.port && !/^\d+$/.test(parser.port)) {
      return { ok: false, reason: 'url_port_invalid' };
    }

    return { ok: true, url: parser.href };
  }

  function container() {
    return document.getElementById('browser-frames');
  }

  function createFrame(tabId) {
    if (frames[tabId]) return frames[tabId];
    var host = container();
    if (!host) return null;
    var frame = document.createElement('iframe');
    frame.id = 'frame_' + tabId;
    frame.className = 'browser-frame';
    frame.title = 'Luma Browser page';
    /*
     * allow-popups is kept for ordinary pages that open helper windows. On this
     * webOS build it did not make search-result target="_blank" links work, so
     * search terms use the local results screen instead. Popups still inherit
     * this sandbox and blocked hosts are refused before any frame is created.
     */
    frame.setAttribute('sandbox', 'allow-scripts allow-forms allow-popups');
    frame.setAttribute('allow', 'fullscreen; autoplay');
    frame.setAttribute('allowfullscreen', '');
    frame.setAttribute('data-tab', tabId);
    frame.src = BLANK;
    frame.addEventListener('load', function () {
      var title = '';
      if (frame.src === BLANK) return;
      try {
        var doc = frame.contentDocument;
        if (doc && doc.title) title = doc.title;
      } catch (error) {
        /* cross-origin pages are opaque; title stays empty */
      }
      if (typeof loadCallback === 'function') loadCallback(tabId, title);
    });
    frame.addEventListener('error', function () {
      if (typeof errorCallback === 'function') errorCallback(tabId, 'The page could not be loaded.');
    });
    host.appendChild(frame);
    frames[tabId] = frame;
    return frame;
  }

  function showFrame(tabId) {
    var key;
    for (key in frames) {
      if (Object.prototype.hasOwnProperty.call(frames, key)) {
        frames[key].style.display = key === tabId ? 'block' : 'none';
      }
    }
  }

  function load(tabId, url, zoom) {
    var frame = createFrame(tabId);
    if (!frame) return false;
    frame.style.zoom = (zoom || 100) / 100;
    frame.src = url || BLANK;
    showFrame(tabId);
    return true;
  }

  function reload(tabId, zoom) {
    var frame = frames[tabId];
    if (!frame || !frame.src || frame.src === BLANK) return false;
    frame.style.zoom = (zoom || 100) / 100;
    frame.src = frame.src;
    return true;
  }

  function stop(tabId) {
    var frame = frames[tabId];
    if (!frame) return false;
    try {
      if (frame.contentWindow && frame.contentWindow.location.href !== BLANK) {
        frame.contentWindow.stop();
        return true;
      }
    } catch (error) {
      /* cross-origin pages cannot be stopped through their window */
    }
    frame.src = BLANK;
    return true;
  }

  function hasFrame(tabId) {
    return !!frames[tabId];
  }

  function setZoom(tabId, zoom) {
    var frame = frames[tabId];
    if (!frame) return false;
    frame.style.zoom = (zoom || 100) / 100;
    return true;
  }

  function destroy(tabId) {
    var frame = frames[tabId];
    if (!frame) return;
    if (frame.parentNode) frame.parentNode.removeChild(frame);
    delete frames[tabId];
  }

  function syncTabs(tabs) {
    var live = {};
    var i;
    for (i = 0; i < tabs.length; i += 1) live[tabs[i].id] = true;
    for (var key in frames) {
      if (Object.prototype.hasOwnProperty.call(frames, key) && !live[key]) destroy(key);
    }
  }

  function onLoad(callback) { loadCallback = typeof callback === 'function' ? callback : null; }
  function onError(callback) { errorCallback = typeof callback === 'function' ? callback : null; }

  /* Backward-compatible hooks used by the remote command handler. */
  function open(rawUrl) {
    var model = global.LumaBrowserModel;
    if (model && typeof model.navigateActive === 'function') return model.navigateActive(rawUrl);
    var checked = validateUrl(rawUrl);
    return checked.ok;
  }

  function close() {
    var model = global.LumaBrowserModel;
    if (model) {
      var tab = model.currentTab();
      if (tab && tab.url) {
        var host = container();
        if (host) host.innerHTML = '';
        frames = {};
      }
      model.setScreen('home');
      return true;
    }
    var key;
    for (key in frames) {
      if (Object.prototype.hasOwnProperty.call(frames, key)) destroy(key);
    }
    return true;
  }

  global.LumaBrowserView = {
    validateUrl: validateUrl,
    createFrame: createFrame,
    showFrame: showFrame,
    load: load,
    reload: reload,
    stop: stop,
    hasFrame: hasFrame,
    setZoom: setZoom,
    destroy: destroy,
    syncTabs: syncTabs,
    onLoad: onLoad,
    onError: onError,
    open: open,
    close: close
  };
})(window);
