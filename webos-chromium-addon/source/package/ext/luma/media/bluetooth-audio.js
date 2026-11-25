"use strict";
// Honest Bluetooth reporting. The extension cannot select or inspect system audio routing.
(function (root) {
  function detect() {
    var result = {
      connectionStatus: "Not detected",
      deviceName: "Not detected",
      codec: "Not detected",
      sampleRate: "Not detected",
      channels: "Not detected",
      outputLatency: "Not detected",
      webBluetoothApi: "Not detected",
      systemRoutingApi: "Unsupported",
      pairingControl: "Unsupported",
      note: "Bluetooth pairing and routing are controlled by TV system settings. LDAC/aptX/lossless/Atmos/DTS:X are not claimed."
    };
    try { result.webBluetoothApi = navigator.bluetooth ? "Partially working" : "Not detected"; } catch (err) {}
    return result;
  }

  function profiles() {
    return {
      available: ["Dialogue clarity", "Controlled bass", "Treble adjustment", "Night listening", "Stereo preservation"],
      unavailable: ["Codec selection", "Output device switching", "Lossless transmission", "Native spatial output"],
      note: "Profiles adjust Luma's Web Audio chain only when processing is enabled and the audio element is same-origin."
    };
  }

  root.LumaBluetoothAudio = { detect: detect, profiles: profiles };
})(typeof globalThis !== "undefined" ? globalThis : this);
