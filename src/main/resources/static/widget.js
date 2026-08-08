/*
 * Doc-AI embeddable chat widget.
 *
 * Usage — drop this on any page:
 *   <script src="https://YOUR_HOST/widget.js"
 *           data-doc-ai-org="YOUR_ORGANIZATION_ID"
 *           data-doc-ai-endpoint="https://YOUR_HOST"></script>
 *
 * It renders a floating chat launcher that talks to POST /widget/chat. A random
 * per-browser sessionId is persisted in localStorage so AGENTIC conversation memory
 * continues across turns.
 */
(function () {
  var script = document.currentScript;
  var organizationId = script.getAttribute("data-doc-ai-org");
  var endpoint = (script.getAttribute("data-doc-ai-endpoint") || "").replace(/\/$/, "");
  if (!organizationId) {
    console.error("[Doc-AI] data-doc-ai-org is required on the widget script tag.");
    return;
  }

  var SESSION_KEY = "docAiWidgetSession:" + organizationId;
  var sessionId = localStorage.getItem(SESSION_KEY);
  if (!sessionId) {
    sessionId = "web-" + Math.random().toString(36).slice(2) + Date.now().toString(36);
    localStorage.setItem(SESSION_KEY, sessionId);
  }

  var panel = document.createElement("div");
  panel.style.cssText =
    "position:fixed;bottom:88px;right:24px;width:320px;max-height:60vh;display:none;" +
    "flex-direction:column;background:#fff;border:1px solid #ddd;border-radius:12px;" +
    "box-shadow:0 8px 30px rgba(0,0,0,.15);font-family:sans-serif;overflow:hidden;z-index:2147483647";

  var log = document.createElement("div");
  log.style.cssText = "flex:1;overflow-y:auto;padding:12px;font-size:14px;line-height:1.4";

  var form = document.createElement("form");
  form.style.cssText = "display:flex;border-top:1px solid #eee";
  var input = document.createElement("input");
  input.type = "text";
  input.placeholder = "Ask a question…";
  input.style.cssText = "flex:1;border:0;padding:12px;font-size:14px;outline:none";
  var send = document.createElement("button");
  send.type = "submit";
  send.textContent = "Send";
  send.style.cssText = "border:0;background:#2563eb;color:#fff;padding:0 16px;cursor:pointer";
  form.appendChild(input);
  form.appendChild(send);
  panel.appendChild(log);
  panel.appendChild(form);

  var launcher = document.createElement("button");
  launcher.textContent = "Chat";
  launcher.style.cssText =
    "position:fixed;bottom:24px;right:24px;width:56px;height:56px;border-radius:50%;" +
    "border:0;background:#2563eb;color:#fff;font-size:14px;cursor:pointer;z-index:2147483647;" +
    "box-shadow:0 8px 30px rgba(0,0,0,.2)";
  launcher.addEventListener("click", function () {
    panel.style.display = panel.style.display === "none" ? "flex" : "none";
    input.focus();
  });

  function bubble(text, who) {
    var el = document.createElement("div");
    el.textContent = text;
    el.style.cssText =
      "margin:6px 0;padding:8px 10px;border-radius:10px;max-width:85%;white-space:pre-wrap;" +
      (who === "user"
        ? "margin-left:auto;background:#2563eb;color:#fff"
        : "background:#f1f5f9;color:#111");
    log.appendChild(el);
    log.scrollTop = log.scrollHeight;
    return el;
  }

  form.addEventListener("submit", function (e) {
    e.preventDefault();
    var text = input.value.trim();
    if (!text) return;
    bubble(text, "user");
    input.value = "";
    send.disabled = true;
    var typing = bubble("…", "bot");

    fetch(endpoint + "/widget/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ organizationId: organizationId, sessionId: sessionId, message: text })
    })
      .then(function (r) { return r.json(); })
      .then(function (data) { typing.textContent = data.reply || "(no response)"; })
      .catch(function () { typing.textContent = "Error: could not reach the assistant."; })
      .finally(function () {
        send.disabled = false;
        input.focus();
        log.scrollTop = log.scrollHeight;
      });
  });

  document.body.appendChild(panel);
  document.body.appendChild(launcher);
})();
