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

var APP_ID = 'com.luma.tvcompanion';
var SERVICE_ID = 'com.luma.tvcompanion.service';
var VERSION = '1.0.0';
var BEACON_PORT = 50550;
var STATUS_PORT = 8081;
var BEACON_INTERVAL_MS = 2000;
var BROADCAST_ADDR = '255.255.255.255';

var systemInfo = null;
var bridge = null;

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
  return {
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
  var buffer = new Buffer(JSON.stringify(payload));
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
