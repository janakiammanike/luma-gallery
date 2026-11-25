/*
 * Luma Media Capabilities — honest, read-only runtime detection.
 *
 * Nothing here claims a feature works. Every value comes from the browser's
 * own canPlayType/isTypeSupported/AudioContext answers or from the viewport.
 * Output paths such as Bluetooth, eARC, Atmos and DTS:X are not exposed to a
 * packaged web app, so they are reported as "not exposed", never as enabled.
 */
(function (global) {
  'use strict';

  function support(value) {
    if (value === 'probably') return 'supported';
    if (value === 'maybe') return 'maybe';
    return 'not reported';
  }

  function ask(element, type) {
    if (!element || typeof element.canPlayType !== 'function') return 'not reported';
    try {
      return support(element.canPlayType(type));
    } catch (error) {
      return 'not reported';
    }
  }

  function mseSupport(type) {
    var source = global.MediaSource;
    if (!source || typeof source.isTypeSupported !== 'function') return 'not reported';
    try {
      return source.isTypeSupported(type) ? 'supported' : 'not reported';
    } catch (error) {
      return 'not reported';
    }
  }

  function engineName() {
    var agent = global.navigator && global.navigator.userAgent ? global.navigator.userAgent : '';
    var match = agent.match(/(Chrome|Chromium)\/([0-9.]+)/);
    if (match) return match[1] + ' ' + match[2];
    return agent ? 'unrecognised engine' : 'not available';
  }

  function detect() {
    var video = global.document ? global.document.createElement('video') : null;
    var audio = global.document ? global.document.createElement('audio') : null;
    var width = global.innerWidth || 0;
    var height = global.innerHeight || 0;
    var dpr = global.devicePixelRatio || 1;
    var screenWidth = (global.screen && global.screen.width) || width;
    var screenHeight = (global.screen && global.screen.height) || height;
    var physicalWidth = Math.round(screenWidth * dpr);
    var physicalHeight = Math.round(screenHeight * dpr);
    var audioContext = global.AudioContext || global.webkitAudioContext;
    var sampleRate = 'not exposed';
    var context = null;

    if (audioContext) {
      try {
        context = new audioContext();
        sampleRate = context.sampleRate + ' Hz (context rate, not a file measurement)';
        if (typeof context.close === 'function') context.close();
      } catch (error) {
        sampleRate = 'not available without user gesture';
      }
    }

    return {
      engine: engineName(),
      display: {
        css: width + ' × ' + height,
        dpr: String(dpr),
        physical: physicalWidth + ' × ' + physicalHeight,
        is4k: physicalWidth >= 3840 && physicalHeight >= 2160
      },
      video: [
        { name: 'MP4 / H.264', detail: 'avc1.42E01E', result: ask(video, 'video/mp4; codecs="avc1.42E01E"') },
        { name: 'HEVC', detail: 'hvc1.1.6.L93.B0', result: ask(video, 'video/mp4; codecs="hvc1.1.6.L93.B0"') },
        { name: 'VP9', detail: 'video/webm', result: ask(video, 'video/webm; codecs="vp9"') },
        { name: 'AV1', detail: 'av01.0.05M.08', result: ask(video, 'video/mp4; codecs="av01.0.05M.08"') },
        { name: 'HLS (native)', detail: 'application/vnd.apple.mpegurl', result: ask(video, 'application/vnd.apple.mpegurl') },
        { name: 'DASH (native)', detail: 'application/dash+xml', result: ask(video, 'application/dash+xml') }
      ],
      audio: [
        { name: 'AAC / MP4', detail: 'audio/mp4', result: ask(audio, 'audio/mp4') },
        { name: 'MP3', detail: 'audio/mpeg', result: ask(audio, 'audio/mpeg') },
        { name: 'FLAC', detail: 'audio/flac', result: ask(audio, 'audio/flac') },
        { name: 'WAV / PCM', detail: 'audio/wav', result: ask(audio, 'audio/wav') },
        { name: 'Ogg Vorbis', detail: 'audio/ogg', result: ask(audio, 'audio/ogg') }
      ],
      mediaSource: {
        available: !!(global.MediaSource && global.MediaSource.isTypeSupported),
        mp4: mseSupport('video/mp4; codecs="avc1.42E01E"'),
        webm: mseSupport('video/webm; codecs="vp9"')
      },
      webAudio: {
        available: !!audioContext,
        sampleRate: sampleRate,
        channelControl: !!(audioContext && audioContext.prototype && audioContext.prototype.createChannelMerger)
      },
      outputs: [
        { name: 'TV speakers / Bluetooth / eARC', result: 'not exposed to web app' },
        { name: 'Dolby Digital / Digital Plus', result: 'not exposed to web app' },
        { name: 'Atmos / DTS:X', result: 'not exposed to web app' },
        { name: '24-bit / 96 kHz file verification', result: 'not exposed to HTMLMediaElement' }
      ]
    };
  }

  global.LumaMediaCapabilities = {
    detect: detect,
    support: support
  };
})(window);
