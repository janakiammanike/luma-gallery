'use strict';

/*
 * Luma TV companion service (beacon phase).
 *
 * Runs on the TV as a webOS service. It broadcasts the TV's CURRENT LAN IP on
 * UDP/50550 so the Android app never has to guess an IP after DHCP renewal,
 * and serves GET /status on TCP/8081 for the on-TV UI and the phone.
 *
 * Keep this file ES5-friendly: webOS 4.x ships an older Node runtime.
 */

var dgram = require('dgram');
var os = require('os');
var http = require('http');
var crypto = require('crypto');
var SimpleWs = require('./wsclient');

var APP_ID = 'com.luma.tvcompanion';
var SERVICE_ID = 'com.luma.tvcompanion.service';
var VERSION = '1.0.0';
var BEACON_PORT = 50550;
var STATUS_PORT = 8081;
var BEACON_INTERVAL_MS = 2000;
var BROADCAST_ADDR = '255.255.255.255';

var systemInfo = null;
var bridge = null;

// Local SSAP forwarder state (proxy phase). The phone sends a command over
// HTTP; the TV executes it against its OWN SSAP server on 127.0.0.1:3000.
var ssap = null;
var inputWs = null;
var pending = {};
var sequence = 0;
var registeredKey = null;
var activeKey = null;
var ssapRegistered = false;
var ssapConnecting = false;
var connectWaiters = [];

try {
  var WebOsService = require('webos-service');
  bridge = new WebOsService(SERVICE_ID);
  bridge.call('luna://com.webos.service.systemservice/getSystemInfo', {}, function (message) {
    if (message && message.payload && message.payload.returnValue !== false) {
      systemInfo = message.payload;
    }
  });
  bridge.register('/status', function (message) {
    message.respond(status());
  });
} catch (e) {
  // The beacon and HTTP endpoint work even if the Luna bridge is unavailable.
  bridge = null;
}

function addresses() {
  var result = [];
  var ifaces = os.networkInterfaces() || {};
  var names = Object.keys(ifaces);
  for (var i = 0; i < names.length; i++) {
    var list = ifaces[names[i]] || [];
    for (var j = 0; j < list.length; j++) {
      var a = list[j];
      var family = String(a.family || '');
      if ((family === 'IPv4' || family === '4') && !a.internal && a.address) {
        result.push({ address: a.address, mac: a.mac });
      }
    }
  }
  return result;
}

function localIp() {
  var all = addresses();
  for (var i = 0; i < all.length; i++) {
    if (/^192\.168\./.test(all[i].address)) return all[i].address;
  }
  if (all.length) return all[0].address;
  return '127.0.0.1';
}

function macs() {
  var result = [];
  var all = addresses();
  for (var i = 0; i < all.length; i++) {
    if (all[i].mac && all[i].mac !== '00:00:00:00:00:00') result.push(all[i].mac);
  }
  return result;
}

function tokenFor(ip, uuid) {
  var seed = uuid || ip + '|' + os.hostname();
  return crypto.createHash('sha256').update(String(seed)).digest('hex').substring(0, 16);
}

function status() {
  var ip = localIp();
  var uuid = (systemInfo && (systemInfo.uuid || systemInfo.deviceId)) || null;
  var result = {
    app: APP_ID,
    protocol: 1,
    version: VERSION,
    ip: ip,
    ports: { ssapPlain: 3000, ssapTls: 3001, status: STATUS_PORT },
    uuid: uuid,
    model: (systemInfo && (systemInfo.modelName || systemInfo.model)) || null,
    macs: macs(),
    token: tokenFor(ip, uuid),
    ts: Date.now()
  };
  // Omit the field entirely until paired: some JSON implementations turn a
  // JSON null into the string "null", which the phone would save as a key.
  if (activeKey && activeKey !== 'null') result.clientKey = activeKey;
  return result;
}

function fail(reason) {
  return { returnValue: false, errorText: String(reason) };
}

function disposeSsap() {
  if (ssap) { try { ssap.close(); } catch (e) {} }
  if (inputWs) { try { inputWs.close(); } catch (e) {} }
  ssap = null;
  inputWs = null;
  pending = {};
  registeredKey = null;
  activeKey = null;
  ssapRegistered = false;
  if (inputConnecting) {
    inputConnecting = false;
    var stranded = inputWaiters;
    inputWaiters = [];
    for (var i = 0; i < stranded.length; i++) stranded[i](fail('SSAP connection reset'));
  }
}

/*
 * Only ONE SSAP connection is opened. Callers that arrive while hello/register
 * is still in flight are queued instead of sending too early — otherwise a
 * rapid D-pad press gets a "not registered" rejection from the TV.
 */
function ensureSsap(key, callback) {
  if (ssap && ssapRegistered) { callback(null); return; }
  connectWaiters.push(callback);
  if (ssapConnecting) return;
  ssapConnecting = true;
  // Dispose FIRST: it resets the remembered key. Setting the key afterwards
  // would silently drop a stored pairing key and force a TV pairing prompt.
  disposeSsap();
  registeredKey = key || null;

  // A fresh pairing may wait for a human to approve the TV prompt.
  var pairingTimer = setTimeout(function () {
    finish(new Error('TV pairing timed out — approve the prompt on the TV'));
  }, 90000);

  var finished = false;
  function finish(error) {
    if (finished) return;
    finished = true;
    clearTimeout(pairingTimer);
    ssapConnecting = false;
    var waiters = connectWaiters;
    connectWaiters = [];
    for (var i = 0; i < waiters.length; i++) waiters[i](error);
  }

  var ws = new SimpleWs(3000, '/', {
    open: function () {
      ws.send(JSON.stringify({ id: 'hello', type: 'hello', payload: {} }));
    },
    message: function (text) {
      var msg;
      try { msg = JSON.parse(text); } catch (e) { return; }
      if (msg.type === 'hello') {
        ws.send(JSON.stringify({
          id: 'register_0',
          type: 'register',
          payload: {
            forcePairing: false,
            manifest: { appVersion: '1.1', manifestVersion: 1, permissions: [] },
            pairingType: 'PROMPT',
            'client-key': registeredKey
          }
        }));
        return;
      }
      if (msg.type === 'registered') {
        ssapRegistered = true;
        activeKey = registeredKey || (msg.payload && msg.payload['client-key']) || null;
        finish(null);
        return;
      }
      if (msg.type === 'response' || msg.type === 'error') {
        var entry = pending[msg.id];
        if (entry) {
          clearTimeout(entry.timer);
          delete pending[msg.id];
          entry.callback(msg);
        }
      }
    },
    error: function (error) { finish(error); },
    close: function () {
      if (ssap === ws) {
        ssap = null;
        ssapRegistered = false;
      }
      finish(new Error('SSAP connection closed'));
    }
  });
  ssap = ws;
}

function ssapRequest(uri, payload, key, callback) {
  ensureSsap(key, function (error) {
    if (error) { callback(fail(error)); return; }
    var id = 'req_' + (++sequence);
    var timer = setTimeout(function () {
      if (pending[id]) {
        delete pending[id];
        callback(fail('SSAP request timed out'));
      }
    }, 20000);
    pending[id] = {
      timer: timer,
      callback: function (message) {
        callback(message.payload || fail('empty SSAP response'));
      }
    };
    ssap.send(JSON.stringify({
      id: id,
      type: 'request',
      uri: 'ssap://' + uri,
      payload: payload || {}
    }));
  });
}

var inputConnecting = false;
var inputWaiters = [];

function ensureInput(callback, key) {
  if (inputWs && inputWs.ready) { callback(null); return; }
  inputWaiters.push(callback);
  if (inputConnecting) return;
  inputConnecting = true;

  function finishInput(error) {
    inputConnecting = false;
    var waiters = inputWaiters;
    inputWaiters = [];
    for (var i = 0; i < waiters.length; i++) waiters[i](error);
  }

  ssapRequest('com.webos.service.networkinput/getPointerInputSocket', {}, key, function (payload) {
    if (payload.returnValue === false || !payload.socketPath) {
      finishInput(fail(payload.errorText || 'pointer input socket unavailable'));
      return;
    }
    var finished = false;
    var pointer = new SimpleWs(3000, payload.socketPath, {
      open: function () {
        if (finished) return;
        finished = true;
        finishInput(null);
      },
      error: function (error) {
        if (finished) return;
        finished = true;
        finishInput(error);
      },
      close: function () {
        if (inputWs === pointer) inputWs = null;
        if (!finished) {
          finished = true;
          finishInput(new Error('pointer input socket closed'));
        }
      }
    });
    inputWs = pointer;
  });
}

function handleCommand(request, response) {
  var body = '';
  request.on('data', function (chunk) {
    body += chunk.toString('utf8');
    if (body.length > 16384) request.destroy();
  });
  request.on('end', function () {
    var command;
    try { command = JSON.parse(body); } catch (e) {
      response.writeHead(400);
      response.end('invalid JSON');
      return;
    }
    var expected = status().token;
    if (expected && command.token && command.token !== expected) {
      response.writeHead(403);
      response.end('bad token');
      return;
    }
    function reply(payload) {
      response.writeHead(200, { 'Content-Type': 'application/json' });
      response.end(JSON.stringify(payload));
    }
    if (typeof command.input === 'string') {
      ensureInput(function (error) {
        if (error) { reply(fail(error)); return; }
        inputWs.send(command.input);
        reply({ returnValue: true });
      }, command.key);
      return;
    }
    if (typeof command.uri === 'string') {
      ssapRequest(command.uri, command.payload, command.key, reply);
      return;
    }
    reply(fail('uri or input required'));
  });
}

var udp = dgram.createSocket('udp4');
udp.on('error', function (error) {
  console.log('Luma companion UDP error: ' + error);
});
udp.bind(function () {
  try { udp.setBroadcast(true); } catch (e) {}
});

function broadcast() {
  var payload = status();
  var text = JSON.stringify(payload);
  var buffer = typeof Buffer.from === 'function' ? Buffer.from(text) : new Buffer(text);
  try {
    udp.send(buffer, 0, buffer.length, BEACON_PORT, BROADCAST_ADDR, function () {});
  } catch (e) {}
  if (payload.ip !== '127.0.0.1') {
    var subnet = payload.ip.substring(0, payload.ip.lastIndexOf('.') + 1) + '255';
    try {
      udp.send(buffer, 0, buffer.length, BEACON_PORT, subnet, function () {});
    } catch (e) {}
  }
}

setInterval(broadcast, BEACON_INTERVAL_MS);
broadcast();

http.createServer(function (request, response) {
  var path = (request.url || '/').split('?')[0];
  response.setHeader('Access-Control-Allow-Origin', '*');
  if (path === '/command' && request.method === 'POST') {
    handleCommand(request, response);
    return;
  }
  if (path === '/health') {
    response.writeHead(200, { 'Content-Type': 'text/plain' });
    response.end('ok');
    return;
  }
  if (path === '/status' || path === '/') {
    response.writeHead(200, { 'Content-Type': 'application/json' });
    response.end(JSON.stringify(status()));
    return;
  }
  response.writeHead(404, { 'Content-Type': 'text/plain' });
  response.end('not found');
}).listen(STATUS_PORT, '0.0.0.0', function () {
  console.log('Luma companion status server listening on ' + STATUS_PORT);
});
