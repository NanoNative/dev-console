// === Dev Console: Service Start/Stop ===
(() => {
  const sys = document.getElementById('system');
  if (!sys) return;

  // Modal + toast elements
  const modal   = document.getElementById('confirmModal');
  const titleEl = document.getElementById('confirmTitle');
  const textEl  = document.getElementById('confirmText');
  const okBtn   = document.getElementById('confirmOk');
  const cancel  = document.getElementById('confirmCancel');
  const toast   = document.getElementById('globalToast');

  // Current intent state
  let current = /** @type {null | {action:'stop'|'start', name:string}} */ (null);

  // --- Helpers ---------------------------------------------------------------
  function showToast(msg, isError = false) {
    if (!toast) return;
    toast.textContent = msg;
    toast.classList.remove('error');
    if (isError) toast.classList.add('error');
    toast.classList.add('show');
    setTimeout(() => toast.classList.remove('show'), 2400);
  }

  function setConfirmBody(name, action) {
    if (!textEl) return;
    // Rebuild body from scratch every time
    const verb = action === 'start' ? 'start ' : 'shutdown ';
    textEl.innerHTML = 'Do you want to ' + verb + '<strong><span class="svc-name"></span></strong> ?';
    const span = textEl.querySelector('.svc-name');
    if (span) span.textContent = name;
  }

  function openModal(action, name) {
    current = { action, name };
    if (titleEl) titleEl.textContent = action === 'start' ? 'Confirm Start' : 'Confirm Shutdown';
    if (okBtn)   okBtn.textContent   = action === 'start' ? 'Start' : 'Shutdown';
    setConfirmBody(name, action);
    modal?.classList.add('show');
    okBtn?.focus();
  }

  function hideModal() {
    modal?.classList.remove('show');
    if (textEl) textEl.innerHTML = ''; // purge any lingering copy
    current = null;
  }

  // Label chips and stamp definitive data on every tag
  function labelServiceChips() {
    sys.querySelectorAll('.k').forEach(kEl => {
      const raw = (kEl.textContent || '').trim();
      const key = raw.toLowerCase().replace(/[^a-z]/g, ''); // normalize
      const vEl = kEl.nextElementSibling;
      if (!vEl) return;

      let section = '';
      if (key === 'activeservices') section = 'active';
      else if (key === 'inactiveservices') section = 'inactive';

      if (section) vEl.dataset.section = section; else delete vEl.dataset.section;

      vEl.querySelectorAll('.tag').forEach(t => {
        const svc = (t.textContent || '').trim();
        t.dataset.svc = svc;
        t.dataset.action = section === 'inactive' ? 'start' : 'stop'; // lock action on the chip
        t.classList.toggle('inactive', section === 'inactive');
        t.classList.toggle('active', section === 'active');
      });
    });
  }

  labelServiceChips();
  new MutationObserver(() => { try { labelServiceChips(); } catch(_) {} })
    .observe(sys, { childList: true, subtree: true });

  // --- Event delegation (capture) — use *only* chip data, block other handlers
  sys.addEventListener('click', (ev) => {
    const el = ev.target;
    const tag = el && /** @type {HTMLElement} */(el).closest('.tag');
    if (!tag || !sys.contains(tag)) return;

    ev.preventDefault();
    ev.stopPropagation();
    ev.stopImmediatePropagation();

    const name   = tag.dataset.svc || (tag.textContent || '').trim();
    let   action = tag.dataset.action;

    // Fallback in case dataset isn't stamped yet (should be rare)
    if (action !== 'start' && action !== 'stop') {
      const section = tag.closest('[data-section]')?.dataset?.section;
      action = section === 'inactive' ? 'start' : 'stop';
      tag.dataset.action = action; // stamp it now
    }

    openModal(action, name);
  }, true); // capture=true so nothing else can rewrite the modal after cancel

  // --- Confirm action --------------------------------------------------------
  okBtn?.addEventListener('click', async () => {
    if (!current) return;

    const { action, name } = current;
    const url    = '/dev-console/service/' + encodeURIComponent(name);
    const method = action === 'start' ? 'PATCH' : 'DELETE';

    try {
      const res = await fetch(url, { method });
      hideModal();

      if (res.ok) {
        showToast(action === 'start' ? `${name} starting...` : `${name} shutting down...`, false);
      } else {
        showToast('Operation failed', true);
      }
    } catch {
      hideModal();
      showToast('Operation failed', true);
    }
  });

  // --- Cancel / close --------------------------------------------------------
  cancel?.addEventListener('click', hideModal);
  modal?.addEventListener('click', (e) => {
    const target = /** @type {HTMLElement} */ (e.target);
    if (target?.dataset?.close === 'true') hideModal();
  });
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && modal?.classList.contains('show')) hideModal();
  });
})();
