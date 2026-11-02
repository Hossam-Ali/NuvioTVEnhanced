package com.nuvio.tv.core.server

/** The phone page for downloading or sending a home setup file. */
internal object HomeSetupWebPage {
    val HTML = """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Nuvio home setup</title>
<style>
  :root { color-scheme: dark; }
  body { margin: 0; font-family: system-ui, sans-serif; background: #0f1115; color: #eceff4; }
  main { max-width: 640px; margin: 0 auto; padding: 24px 16px 48px; }
  h1 { font-size: 22px; margin: 0 0 6px; }
  p { color: #a7adba; line-height: 1.5; }
  section { background: #181b22; border-radius: 14px; padding: 18px; margin-top: 18px; }
  h2 { font-size: 16px; margin: 0 0 10px; }
  button, a.button { display: inline-block; background: #4c8dff; color: #fff; border: 0; border-radius: 10px; padding: 11px 16px; font-size: 15px; text-decoration: none; cursor: pointer; margin: 4px 6px 4px 0; }
  button.secondary { background: #2a2f3a; }
  textarea { width: 100%; box-sizing: border-box; min-height: 160px; background: #0f1115; color: #eceff4; border: 1px solid #2a2f3a; border-radius: 10px; padding: 10px; font: 13px ui-monospace, monospace; }
  #status { margin-top: 12px; min-height: 20px; }
  .ok { color: #7ee2a8; } .err { color: #ff8f8f; }
</style>
</head>
<body>
<main>
  <h1>Home setup</h1>
  <p>Save your TV's home screen (layout, row order, collections, row styles and rating badges) as a file, or send a setup file to the TV.</p>
  <section>
    <h2>From the TV</h2>
    <a class="button" href="/api/setup?download=1">Download setup file</a>
    <button class="secondary" onclick="copySetup()">Copy as text</button>
  </section>
  <section>
    <h2>To the TV</h2>
    <p>Paste a setup file, or choose one. Collections already on the TV are replaced only by ones with the same id; nothing else is deleted.</p>
    <textarea id="json" placeholder='{"format": "nuvio-home-setup", ...}'></textarea>
    <div>
      <button onclick="sendSetup()">Send to TV</button>
      <label class="button secondary">Choose file<input type="file" accept="application/json,.json" style="display:none" onchange="readFile(this)"></label>
    </div>
    <div id="status"></div>
  </section>
</main>
<script>
  function status(text, ok) { const s = document.getElementById('status'); s.textContent = text; s.className = ok ? 'ok' : 'err'; }
  async function copySetup() {
    const text = await (await fetch('/api/setup')).text();
    document.getElementById('json').value = text;
    try { await navigator.clipboard.writeText(text); status('Copied.', true); } catch (e) { status('Shown below; select and copy it.', true); }
  }
  function readFile(input) {
    const file = input.files[0]; if (!file) return;
    const reader = new FileReader();
    reader.onload = () => { document.getElementById('json').value = reader.result; status('File loaded. Press Send to TV.', true); };
    reader.readAsText(file);
  }
  async function sendSetup() {
    const body = document.getElementById('json').value.trim();
    if (!body) { status('Paste or choose a setup file first.', false); return; }
    const response = await fetch('/api/setup', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body });
    const result = await response.json().catch(() => ({}));
    if (response.ok) status('Sent. ' + result.collections + ' collections imported.', true);
    else status(result.error || 'The TV could not use that file.', false);
  }
</script>
</body>
</html>
""".trimIndent()
}
