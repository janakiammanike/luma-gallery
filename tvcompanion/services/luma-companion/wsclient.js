'use strict';

/*
 * Tiny RFC6455 client used by the companion service to talk to the TV's own
 * SSAP WebSocket on 127.0.0.1. Only text frames and unmasked server frames
 * are needed. ES5 syntax keeps it compatible with webOS 4.x Node.
 */

var net = require('net');
var crypto = require('crypto');

function makeBuffer(value) {
  return typeof Buffer.from === 'function' ? Buffer.from(value) : new Buffer(value);
}

function allocBuffer(size) {
  return typeof Buffer.alloc === 'function' ? Buffer.alloc(size) : new Buffer(size);
}

function SimpleWs(port, path, handlers) {
  var self = this;
  this.port = port;
  this.path = path || '/';
  this.handlers = handlers || {};
  this.buffer = makeBuffer('');
  this.ready = false;
  this.queue = [];

  this.socket = net.connect(port, '127.0.0.1', function () {
    var key = crypto.randomBytes(16).toString('base64');
    var request =
      'GET ' + self.path + ' HTTP/1.1\r\n' +
      'Host: 127.0.0.1:' + port + '\r\n' +
      'Upgrade: websocket\r\n' +
      'Connection: Upgrade\r\n' +
      'Sec-WebSocket-Key: ' + key + '\r\n' +
      'Sec-WebSocket-Version: 13\r\n\r\n';
    self.socket.write(request);
  });

  this.socket.on('data', function (chunk) {
    self.onData(chunk);
  });
  this.socket.on('error', function (error) {
    if (self.handlers.error) self.handlers.error(error);
  });
  this.socket.on('close', function () {
    self.ready = false;
    if (self.handlers.close) self.handlers.close();
  });
}

SimpleWs.prototype.onData = function (chunk) {
  var combined = Buffer.concat([this.buffer, chunk]);
  this.buffer = combined;

  if (!this.ready) {
    var end = this.buffer.indexOf('\r\n\r\n');
    if (end < 0) return;
    var headers = this.buffer.slice(0, end).toString('utf8');
    if (headers.indexOf('101') < 0) {
      if (this.handlers.error) this.handlers.error(new Error('WebSocket upgrade failed'));
      this.buffer = makeBuffer('');
      return;
    }
    this.ready = true;
    this.buffer = this.buffer.slice(end + 4);
    var queued = this.queue;
    this.queue = [];
    for (var q = 0; q < queued.length; q++) this.send(queued[q]);
    if (this.handlers.open) this.handlers.open();
  }

  this.parseFrames();
};

SimpleWs.prototype.parseFrames = function () {
  while (this.buffer.length >= 2) {
    var opcode = this.buffer[0] & 0x0F;
    var length = this.buffer[1] & 0x7F;
    var offset = 2;
    if (length === 126) {
      if (this.buffer.length < 4) return;
      length = this.buffer.readUInt16BE(2);
      offset = 4;
    } else if (length === 127) {
      if (this.buffer.length < 10) return;
      length = this.buffer.readUInt32BE(2) * 4294967296 + this.buffer.readUInt32BE(6);
      offset = 10;
    }
    if (this.buffer.length < offset + length) return;

    var payload = this.buffer.slice(offset, offset + length);
    this.buffer = this.buffer.slice(offset + length);

    if (opcode === 0x1 && this.handlers.message) {
      this.handlers.message(payload.toString('utf8'));
    } else if (opcode === 0x9) {
      this.sendFrame(0xA, payload.toString('utf8'));
    } else if (opcode === 0x8) {
      this.close();
      return;
    }
  }
};

SimpleWs.prototype.send = function (text) {
  if (!this.ready) {
    this.queue.push(text);
    return;
  }
  this.sendFrame(0x1, text);
};

SimpleWs.prototype.sendFrame = function (opcode, text) {
  var payload = makeBuffer(text);
  var mask = crypto.randomBytes(4);
  var header;
  if (payload.length < 126) {
    header = makeBuffer([0x80 | opcode, 0x80 | payload.length]);
  } else if (payload.length < 65536) {
    header = makeBuffer([0x80 | opcode, 0x80 | 126, (payload.length >> 8) & 0xFF, payload.length & 0xFF]);
  } else {
    header = makeBuffer([0x80 | opcode, 0x80 | 127, 0, 0, 0, 0,
      (payload.length >>> 24) & 0xFF, (payload.length >>> 16) & 0xFF,
      (payload.length >>> 8) & 0xFF, payload.length & 0xFF]);
  }
  var masked = allocBuffer(payload.length);
  for (var i = 0; i < payload.length; i++) masked[i] = payload[i] ^ mask[i % 4];
  this.socket.write(Buffer.concat([header, mask, masked]));
};

SimpleWs.prototype.close = function () {
  try { this.socket.end(); } catch (e) {}
};

module.exports = SimpleWs;
