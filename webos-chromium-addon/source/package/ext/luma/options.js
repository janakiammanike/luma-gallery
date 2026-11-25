"use strict";
(function () {
  var state = { settings: { showDiagnostics: true }, quickLinks: [] };
  var status = document.getElementById("status");
  function say(text) { status.textContent = text || ""; }

  function call(type, payload) {
    return LumaBus.send(type, payload || {}).then(function (reply) {
      if (!reply || reply.ok === false) throw new Error((reply && reply.error) || "Luma command failed.");
      return reply.value;
    });
  }

  function validUrl(raw) {
    try {
      var u = new URL(String(raw || ""));
      return (u.protocol === "http:" || u.protocol === "https:") ? u.href : null;
    } catch (err) { return null; }
  }

  function renderLinks() {
    var box = document.getElementById("linkList");
    box.textContent = "";
    var ul = document.createElement("ul");
    state.quickLinks.forEach(function (link, index) {
      var li = document.createElement("li");
      var title = document.createElement("span");
      title.className = "title";
      title.textContent = link.title || link.url;
      var url = document.createElement("span");
      url.className = "url";
      url.textContent = link.url;
      var remove = document.createElement("button");
      remove.textContent = "Remove";
      remove.setAttribute("data-index", String(index));
      li.appendChild(title);
      li.appendChild(url);
      li.appendChild(remove);
      ul.appendChild(li);
    });
    if (!state.quickLinks.length) ul.textContent = "No quick links yet.";
    box.appendChild(ul);
  }

  function renderRemote(remote) {
    if (!remote) return;
    var host = document.getElementById("remoteHost");
    var port = document.getElementById("remotePort");
    if (!host.value && remote.host) host.value = remote.host;
    if (remote.port) port.value = String(remote.port);
    var label = remote.status || "idle";
    if (remote.sessionShort) label += " · " + remote.sessionShort;
    if (remote.lastError && remote.lastError !== "none") label += " · " + remote.lastError;
    document.getElementById("remoteStatus").textContent = label;
  }

  function load() {
    return Promise.all([call("get-state"), call("remote-state")]).then(function (values) {
      var data = values[0];
      state.settings = data.settings;
      state.quickLinks = data.quickLinks;
      document.getElementById("showDiagnostics").checked = !!data.settings.showDiagnostics;
      renderLinks();
      renderRemote(values[1]);
    }).catch(function (err) { say(err.message); });
  }

  function save() {
    state.settings.showDiagnostics = document.getElementById("showDiagnostics").checked;
    return call("set-settings", { settings: state.settings })
      .then(function () { return call("set-quick-links", { links: state.quickLinks }); })
      .then(function () { say("Saved on this TV."); })
      .catch(function (err) { say(err.message); });
  }

  document.addEventListener("DOMContentLoaded", function () {
    load();

    var remoteTimer = setInterval(function () {
      call("remote-state").then(renderRemote).catch(function () { /* worker may be idle */ });
    }, 2000);
    window.addEventListener("unload", function () { clearInterval(remoteTimer); });

    document.getElementById("save").addEventListener("click", save);

    document.getElementById("remoteConnect").addEventListener("click", function () {
      var host = document.getElementById("remoteHost").value.trim();
      var port = document.getElementById("remotePort").value.trim();
      say("Requesting remote session…");
      call("remote-connect", { host: host, port: port, autoConnect: true })
        .then(function (remote) {
          renderRemote(remote);
          say("Approve this TV on the phone to finish pairing.");
        })
        .catch(function (err) { say(err.message); });
    });

    document.getElementById("remoteDisconnect").addEventListener("click", function () {
      call("remote-disconnect", {})
        .then(function (remote) { renderRemote(remote); say("Remote channel closed."); })
        .catch(function (err) { say(err.message); });
    });

    document.getElementById("addLink").addEventListener("click", function () {
      var title = document.getElementById("newTitle").value.trim();
      var url = validUrl(document.getElementById("newUrl").value.trim());
      if (!url) { say("Enter a valid http:// or https:// address."); return; }
      state.quickLinks.push({ title: title || url, url: url });
      document.getElementById("newTitle").value = "";
      document.getElementById("newUrl").value = "";
      save().then(renderLinks);
    });

    document.getElementById("linkList").addEventListener("click", function (event) {
      var b = event.target.closest("button[data-index]");
      if (!b) return;
      state.quickLinks.splice(Number(b.getAttribute("data-index")), 1);
      save().then(renderLinks);
    });
  });
})();
