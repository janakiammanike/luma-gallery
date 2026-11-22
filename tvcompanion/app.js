(function () {
  var ipEl = document.getElementById('ip');
  var versionEl = document.getElementById('version');
  var statusEl = document.getElementById('status');

  function refresh() {
    var xhr = new XMLHttpRequest();
    xhr.open('GET', 'http://127.0.0.1:8081/status', true);
    xhr.onreadystatechange = function () {
      if (xhr.readyState !== 4) return;
      if (xhr.status === 200) {
        try {
          var info = JSON.parse(xhr.responseText);
          ipEl.textContent = info.ip || '—';
          versionEl.textContent = info.version || '—';
          statusEl.textContent = 'Beaconing — Luma remote can find this TV at ' + info.ip;
        } catch (e) {
          statusEl.textContent = 'Status response malformed';
        }
      } else {
        statusEl.textContent = 'Companion service is not running yet';
      }
    };
    xhr.onerror = function () {
      statusEl.textContent = 'Companion service is not running yet';
    };
    xhr.send();
  }

  refresh();
  setInterval(refresh, 5000);
})();
