"use strict";
// Magic Remote already works through the browser's spatial-navigation patches.
// Luma only makes sure a control is focused when an extension page opens.
(function () {
  function focusFirst() {
    var target = document.querySelector("[autofocus]") || document.querySelector("button, a.button, input");
    if (target) target.focus();
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", focusFirst);
  else focusFirst();
})();
