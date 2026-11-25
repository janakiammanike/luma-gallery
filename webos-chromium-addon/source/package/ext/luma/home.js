"use strict";
(function () {
  var status = document.getElementById("status");
  function say(text) { status.textContent = text || ""; }

  function call(type, payload) {
    return LumaBus.send(type, payload || {}).then(function (reply) {
      if (!reply || reply.ok === false) throw new Error((reply && reply.error) || "Luma command failed.");
      return reply.value;
    });
  }

  function renderLinks(links) {
    var box = document.getElementById("links");
    box.textContent = "";
    (links || []).forEach(function (link) {
      var b = document.createElement("button");
      b.className = "button";
      b.textContent = link.title || link.url;
      b.setAttribute("data-url", link.url);
      box.appendChild(b);
    });
    if (!box.children.length) box.textContent = "No quick links yet. Add them in Settings.";
  }

  function open(raw) {
    say("Opening " + raw);
    return call("open-url", { url: raw }).then(function () {
      say("Opened in a new tab.");
    }).catch(function (err) { say(err.message); });
  }

  document.addEventListener("DOMContentLoaded", function () {
    call("get-state").then(function (state) { renderLinks(state.quickLinks); }).catch(function (err) { say(err.message); });

    document.getElementById("open").addEventListener("click", function () {
      open(document.getElementById("url").value);
    });
    document.getElementById("url").addEventListener("keydown", function (event) {
      if (event.key === "Enter") open(this.value);
    });

    document.getElementById("links").addEventListener("click", function (event) {
      var b = event.target.closest("button[data-url]");
      if (b) open(b.getAttribute("data-url"));
    });
  });
})();
