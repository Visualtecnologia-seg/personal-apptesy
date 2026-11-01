/* OPUS · Painel v2: barra do topo (assistente, sino, tema), SOS com prioridade, Pedidos, Usuários e Financeiro novos. */
(function () {
  const code = id => String(id || '').slice(0, 6).toUpperCase();
  const ini = n => esc(((n || '?').trim()[0] || '?').toUpperCase());
  const AVC = ['#DBEAFE:#1E40AF', '#FCE7F3:#9D174D', '#DCFCE7:#166534', '#FEF3C7:#92400E', '#E0E7FF:#3730A3', '#CFFAFE:#155E75'];
  const av = (n, size) => { const c = AVC[(String(n || '').charCodeAt(0) || 0) % AVC.length].split(':'); return `<span class="av" style="background:${c[0]};color:${c[1]}${size ? `;width:${size}px;height:${size}px;border-radius:${Math.round(size / 3)}px;font-size:${Math.round(size / 2.6)}px` : ''}">${ini(n)}</span>`; };
  const money0 = v => 'R$ ' + Number(v || 0).toLocaleString('pt-BR', { maximumFractionDigits: 0 });
  const mins = m => m >= 60 ? Math.floor(m / 60) + ' h ' + (m % 60) + ' min' : m + ' min';
  const md = s => esc(s).replace(/\*\*(.+?)\*\*/g, '<b>$1</b>').replace(/\n/g, '<br>');
  const ICN = {
    spark: '<path d="M12 3l1.9 4.6L18.5 9.5l-4.6 1.9L12 16l-1.9-4.6L5.5 9.5l4.6-1.9z"/><path d="M19 15l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z"/>',
    bell: '<path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10 21a2 2 0 0 0 4 0"/>',
    moon: '<path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/>',
    sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
    x: '<path d="M6 6l12 12M18 6L6 18"/>', send: '<path d="M22 2L11 13M22 2l-7 20-4-9-9-4z"/>',
    lock: '<rect x="4" y="10" width="16" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/>', check: '<path d="M20 6L9 17l-5-5"/>',
    clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>', list: '<path d="M4 6h16M4 12h10M4 18h6"/>',
    shield: '<path d="M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z"/>', card: '<rect x="2.5" y="6" width="19" height="13" rx="2"/><path d="M2.5 10h19"/>',
    chat: '<path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z"/>', alert: '<path d="M12 9v4M12 17h.01"/><path d="M10.3 3.9L2 18a2 2 0 0 0 1.7 3h16.6a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"/>',
    award: '<circle cx="12" cy="9" r="5"/><path d="M8.5 13l-1.5 8 5-3 5 3-1.5-8"/>', phone: '<path d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1.9.4 1.8.7 2.7a2 2 0 0 1-.5 2.1L8 9.8a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.7.7a2 2 0 0 1 1.7 2z"/>',
    play: '<path d="M8 5v14l11-7z" fill="currentColor"/>', arrow: '<path d="M2 12h18M14 6l6 6-6 6"/>'
  };
  const I = (k, size, color) => `<svg width="${size || 18}" height="${size || 18}" viewBox="0 0 24 24" fill="none" stroke="${color || 'currentColor'}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICN[k]}</svg>`;

  // ───────── tema claro / escuro ─────────
  function applyTheme(t) { document.documentElement.classList.toggle('dark', t === 'dark'); const b = $('themeBtn'); if (b) { b.innerHTML = I(t === 'dark' ? 'sun' : 'moon', 20); b.setAttribute('aria-label', t === 'dark' ? 'Mudar para modo claro' : 'Mudar para modo escuro'); } }
  let theme = 'light'; try { theme = localStorage.getItem('opus_admin_theme') || (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'); } catch (e) { }
  applyTheme(theme);
  window.toggleTheme = () => { theme = theme === 'dark' ? 'light' : 'dark'; try { localStorage.setItem('opus_admin_theme', theme); } catch (e) { } applyTheme(theme); };

  // ───────── barra do topo ─────────
  function topbar() {
    if ($('topbar')) return;
    const main = document.querySelector('main');
    main.insertAdjacentHTML('afterbegin', `<div id="topbar" class="topbar">
      <div class="ask"><span class="askic">${I('spark', 18, '#1D4ED8')}</span><input id="askq" aria-label="Pergunte ao assistente" placeholder='Pergunte ou busque: "disputas vencendo", "pedido A3F1C2", "quanto ganhei hoje"' autocomplete="off"><span class="kbd">Ctrl K</span></div>
      <div style="flex:1"></div>
      <button class="tbtn" id="themeBtn" onclick="toggleTheme()"></button>
      <button class="tbtn" id="bellBtn" aria-label="Avisos" onclick="openAssistant('avisos')">${I('bell', 20)}<span class="bcount" id="bcount"></span></button>
      <button class="tbtn wide" onclick="openAssistant('chat')">${I('spark', 18, '#1D4ED8')}<span>Assistente</span></button>
    </div>`);
    applyTheme(theme);
    $('askq').addEventListener('keydown', e => { if (e.key === 'Enter' && e.target.value.trim()) { const q = e.target.value.trim(); e.target.value = ''; openAssistant('chat'); askAI(q); } });
    document.addEventListener('keydown', e => { if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); $('askq').focus(); } if (e.key === 'Escape') closeAssistant(); });
    document.body.insertAdjacentHTML('beforeend', `<div id="aiShade" class="shade hidden" onclick="closeAssistant()"></div>
      <section id="aiBox" class="aibox hidden" aria-label="Assistente do painel">
        <div class="aihead"><div class="aiico">${I('spark', 20, '#fff')}</div><div style="flex:1"><b style="font-family:Montserrat;font-size:17px">Assistente OPUS</b><div class="muted" style="font-size:12px">Responde com os dados do painel, na hora</div></div><button class="tbtn" aria-label="Fechar" onclick="closeAssistant()">${I('x', 16)}</button></div>
        <div class="aitabs"><button id="tabChat" onclick="aiTab('chat')">Assistente</button><button id="tabAvisos" onclick="aiTab('avisos')">Avisos <span class="bcount in" id="bcount2"></span></button></div>
        <div id="aiChat" class="aibody"></div>
        <div id="aiAvisos" class="aibody hidden"></div>
        <div class="aifoot" id="aiFoot"><input id="aiq" placeholder="Pergunte sobre pedidos, pessoas, dinheiro…" aria-label="Pergunta"><button class="primary" aria-label="Enviar" onclick="askFromBox()">${I('send', 18, '#fff')}</button></div>
      </section>`);
    $('aiq').addEventListener('keydown', e => { if (e.key === 'Enter') askFromBox(); });
  }
  let aiStarted = false;
  window.openAssistant = tab => { $('aiBox').classList.remove('hidden'); $('aiShade').classList.remove('hidden'); aiTab(tab || 'chat'); if (!aiStarted) { aiStarted = true; loadSummary(); } };
  window.closeAssistant = () => { const b = $('aiBox'); if (b) { b.classList.add('hidden'); $('aiShade').classList.add('hidden'); } };
  window.aiTab = t => { $('aiChat').classList.toggle('hidden', t !== 'chat'); $('aiFoot').classList.toggle('hidden', t !== 'chat'); $('aiAvisos').classList.toggle('hidden', t !== 'avisos'); $('tabChat').classList.toggle('on', t === 'chat'); $('tabAvisos').classList.toggle('on', t === 'avisos'); if (t === 'avisos') paintNotifs(); else setTimeout(() => $('aiq').focus(), 50); };
  window.askFromBox = () => { const q = $('aiq').value.trim(); if (!q) return; $('aiq').value = ''; askAI(q); };
  const actBtns = acts => (acts || []).map(a => `<button class="chipb" onclick='aiGo(${JSON.stringify(a.go)},${JSON.stringify(a.arg || null)})'>${esc(a.label)}</button>`).join('');
  async function loadSummary() {
    const box = $('aiChat');
    box.innerHTML = `<div class="sumbox"><div class="sumh">Resumo de agora · ${new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}</div><div id="sumtxt" class="muted">Carregando…</div><div class="row" id="sumacts" style="margin-top:10px"></div></div>
      <div id="aiLog" style="display:flex;flex-direction:column;gap:10px"></div>
      <div><div class="muted" style="font-size:12px;font-weight:700;margin:4px 0 8px">Perguntas rápidas</div><div class="row">${['Quanto ganhei na semana?', 'Tem disputa vencendo?', 'Quem falta verificar?', 'Saques de hoje', 'Quanto tem no cofre?', 'Melhores do mês', 'Como funciona o cofre?'].map(q => `<button class="chipb" onclick="askAI(this.textContent)">${q}</button>`).join('')}</div></div>`;
    try { const s = await api('/admin/assistant/summary'); $('sumtxt').innerHTML = md(s.text); $('sumtxt').classList.remove('muted'); $('sumacts').innerHTML = actBtns(s.actions); } catch (e) { $('sumtxt').textContent = 'Não consegui montar o resumo agora.'; }
  }
  window.askAI = async q => {
    if (!$('aiLog')) { await loadSummary(); }
    const log = $('aiLog');
    log.insertAdjacentHTML('beforeend', `<div class="bq">${esc(q)}</div><div class="ba" id="ba${Date.now()}"><span class="muted">Pensando…</span></div>`);
    const el = log.lastElementChild; el.scrollIntoView({ block: 'nearest' });
    try { const r = await api('/admin/assistant/ask', { method: 'POST', body: JSON.stringify({ q }) }); el.innerHTML = md(r.text) + (r.actions && r.actions.length ? `<div class="row" style="margin-top:10px">${actBtns(r.actions)}</div>` : ''); }
    catch (e) { el.textContent = 'Não consegui responder agora: ' + e.message; }
    el.scrollIntoView({ block: 'nearest' });
  };
  window.aiGo = (gov, arg) => {
    if (gov === 'ask') { askAI(arg); return; }
    closeAssistant();
    if (gov === 'user') { openUser(arg); return; }
    if (gov === 'pedidos' && arg) {
      cur = 'pedidos'; location.hash = 'pedidos'; navHtml();
      if (/^[0-9a-f-]{20,}$/i.test(arg)) VIEWS.pedidos('ALL', '', arg);
      else if (String(arg).startsWith('q:')) VIEWS.pedidos('ALL', String(arg).slice(2));
      else VIEWS.pedidos(arg);
      return;
    }
    if (gov === 'usuarios' && arg) { cur = 'usuarios'; location.hash = 'usuarios'; navHtml(); VIEWS.usuarios(arg); return; }
    if (VIEWS[gov]) go(gov);
  };

  // ───────── avisos (sino) + SOS ─────────
  let notifs = { items: [], count: 0, sosActive: 0 };
  const NK = { SOS: ['alert', '#FEE2E2', '#B91C1C'], DISPUTE: ['clock', '#FEE2E2', '#B91C1C'], VERIFY: ['shield', '#FEF3C7', '#B45309'], SUPPORT: ['chat', '#E0F2FE', '#075985'], WITHDRAW: ['card', '#EFF6FF', '#1D4ED8'], CERT: ['award', '#EDE9FE', '#5B21B6'] };
  function paintNotifs() {
    const n = notifs.count || 0;
    ['bcount', 'bcount2'].forEach(id => { const el = $(id); if (el) { el.textContent = n; el.style.display = n ? 'inline-flex' : 'none'; } });
    const box = $('aiAvisos'); if (!box) return;
    box.innerHTML = (notifs.items || []).map(i => { const k = NK[i.kind] || NK.SUPPORT; return `<button class="ntf ${i.urgent ? 'urg' : ''}" onclick='aiGo(${JSON.stringify(i.go)},${JSON.stringify(i.arg || null)})'><i style="background:${k[1]}">${I(k[0], 18, k[2])}</i><span style="flex:1;text-align:left"><b>${esc(i.title)}</b><span>${esc(i.sub || '')}</span></span><small>${i.at ? fmt(i.at) : ''}</small></button>`; }).join('') || '<div class="muted" style="padding:20px;text-align:center">Nenhum aviso agora. Tudo em dia.</div>';
  }
  let siren = null, sirenCtx = null, sosSeen = {}, sosShownId = null, titleTimer = null, baseTitle = document.title;
  function startSiren(loud) {
    stopSiren();
    try {
      sirenCtx = sirenCtx || new (window.AudioContext || window.webkitAudioContext)();
      const play = () => { const c = sirenCtx, o = c.createOscillator(), g = c.createGain(); o.type = 'sawtooth'; o.frequency.setValueAtTime(660, c.currentTime); o.frequency.linearRampToValueAtTime(1100, c.currentTime + 0.45); o.frequency.linearRampToValueAtTime(660, c.currentTime + 0.9); g.gain.value = loud ? 0.35 : 0.18; o.connect(g); g.connect(c.destination); o.start(); o.stop(c.currentTime + 0.9); };
      play(); siren = setInterval(play, 1300);
    } catch (e) { }
    let on = false; titleTimer = setInterval(() => { document.title = (on = !on) ? '🆘 SOS ATIVO!' : baseTitle; }, 800);
  }
  function stopSiren() { clearInterval(siren); siren = null; clearInterval(titleTimer); titleTimer = null; document.title = baseTitle; }
  async function pollNotifs() {
    try { notifs = await api('/admin/notifications'); } catch (e) { return; }
    paintNotifs();
    counts = counts || {}; counts.sos = notifs.sosActive;
    const vc = (notifs.items || []).filter(i => i.kind === 'VERIFY').length, dc = (notifs.items || []).filter(i => i.kind === 'DISPUTE').length;
    const sup = (notifs.items || []).find(i => i.kind === 'SUPPORT'); counts.verifications = vc; counts.disputes = dc; counts.support = sup ? parseInt(sup.title) || 1 : 0;
    paintBadges();
    const bar = $('sosbar'); if (bar) bar.style.display = 'none';
    if (notifs.sosActive > 0) showSos(); else hideSos();
  }
  window.pollNotifs = pollNotifs;

  async function showSos() {
    let rows; try { rows = await api('/admin/sos'); } catch (e) { return; }
    const s = rows.find(r => r.active && !r.ackAt); if (!s) { hideSos(); return; }
    if (!sosSeen[s.id]) {
      sosSeen[s.id] = Date.now(); startSiren(false);
      try { if ('Notification' in window) { if (Notification.permission === 'granted') new Notification('🆘 SOS na OPUS', { body: s.name + ' pediu ajuda. Abra o painel.', requireInteraction: true }); else if (Notification.permission !== 'denied') Notification.requestPermission(); } } catch (e) { }
    }
    const waited = Math.floor((Date.now() - new Date(s.startedAt).getTime()) / 1000);
    if (waited > 120 && !sosSeen[s.id + ':esc']) { sosSeen[s.id + ':esc'] = 1; startSiren(true); }
    let ov = $('sosOv');
    if (!ov) { document.body.insertAdjacentHTML('beforeend', '<div id="sosOv" class="sosov" role="alertdialog" aria-label="Pedido de SOS"></div>'); ov = $('sosOv'); }
    if (sosShownId === s.id) { const t = $('sosTimer'); if (t) t.textContent = String(Math.floor(waited / 60)).padStart(2, '0') + ':' + String(waited % 60).padStart(2, '0'); return; }
    sosShownId = s.id;
    const tel = p => 'tel:' + String(p || '').replace(/\D/g, '');
    ov.innerHTML = `<div class="sostop">${I('alert', 22, '#fff')}<b>SOS ATIVO · fica por cima de tudo até alguém da equipe assumir</b><span style="flex:1"></span><span class="pill" style="background:rgba(255,255,255,.2);color:#fff">Sirene tocando</span></div>
      <div class="soscard"><div class="soshead"><div class="sosbadge">SOS</div><div style="flex:1"><div style="font:800 24px Montserrat">${esc(s.name)} pediu ajuda</div><div style="color:#FECACA;font-size:14px">${s.role ? (s.role === 'cliente' ? 'Cliente' : 'Profissional') : ''}${s.orderId && !/^0{8}/.test(s.orderId) ? ' · pedido #' + code(s.orderId) + (s.service ? ' (' + esc(s.service) + ')' : '') : ''}</div></div><div style="text-align:right"><div style="font-size:12px;color:#FECACA;font-weight:700">ESPERANDO A EQUIPE HÁ</div><div id="sosTimer" style="font:800 34px Montserrat">${String(Math.floor(waited / 60)).padStart(2, '0')}:${String(waited % 60).padStart(2, '0')}</div></div></div>
      <div class="sosgrid"><div style="display:flex;flex-direction:column;gap:12px"><div id="sosMap" class="sosmap"></div>
        <div class="grid" style="grid-template-columns:1fr 1fr;gap:10px"><a class="sosbtn" href="${tel(s.phone)}">${I('phone', 18, '#fff')} Ligar para ${esc(String(s.name || '').split(' ')[0])}</a><a class="sosbtn dark" href="tel:190">Ligar 190 (Polícia)</a></div>
        <div class="sinfo"><small>Áudio gravado pelo celular</small><div id="sosAud" style="display:flex;flex-direction:column;gap:6px">${(s.audios || []).length ? '' : '<span class="muted">Ainda sem áudio.</span>'}</div></div></div>
        <div style="display:flex;flex-direction:column;gap:10px"><div class="grid" style="grid-template-columns:1fr 1fr;gap:10px">
          <div class="sinfo"><small>Telefone</small><b>${esc(s.phone || '—')}</b></div><div class="sinfo"><small>Acionou às</small><b>${fmt(s.startedAt)}</b><span>localização: ${fmt(s.locAt)}</span></div>
          <div class="sinfo red"><small>Contato de confiança</small><b>${esc(s.contactName || '—')}</b><span>${esc(s.contactPhone || 'não cadastrado')}</span></div><div class="sinfo"><small>Quem está com a pessoa</small><b>${esc(s.otherName || '—')}</b><span>${esc(s.otherPhone || '')}</span></div></div>
          <div class="sinfo"><small>Endereço do pedido</small><span>${esc(s.serviceAddress || '—')}</span></div>
          <div class="sinfo"><small>O que o app já fez sozinho</small><span>✓ Avisou a equipe · ✓ Grava áudio · ✓ Manda a localização · ✓ Pausou os pagamentos deste pedido</span></div>
          <label for="sosNote" style="margin:0">Anotação da equipe</label><input id="sosNote" placeholder="Ex.: liguei, ela está bem, era engano">
          <button class="sosbtn big" onclick="sosTake('${s.id}',false)">Estou acompanhando (para a sirene)</button>
          <div class="grid" style="grid-template-columns:1fr 1fr;gap:10px"><button class="ok" onclick="sosTake('${s.id}',true)">Encerrar: está tudo bem</button>${s.otherId ? `<button class="danger" onclick="sosBlock(${s.otherId})">Bloquear ${esc(String(s.otherName || '').split(' ')[0])}</button>` : '<span></span>'}</div>
          <div class="sinfo red" style="font-size:12.5px">Se ninguém assumir em 2 minutos, a sirene fica mais alta. Em perigo real, ligue 190 e informe o endereço.</div></div></div></div>`;
    setTimeout(() => { try { if (s.lat != null) { const m = L.map('sosMap').setView([s.lat, s.lng], 16); L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}').addTo(m); L.circleMarker([s.lat, s.lng], { radius: 11, color: '#fff', weight: 4, fillColor: '#DC2626', fillOpacity: 1 }).addTo(m); } else $('sosMap').innerHTML = '<div class="muted" style="padding:20px">Sem localização (GPS desligado).</div>'; } catch (e) { } }, 50);
    (s.audios || []).forEach(async a => { try { const u = await blob('/admin/sos/audio/' + a.id); $('sosAud').insertAdjacentHTML('beforeend', `<div class="row"><span class="muted">${fmt(a.createdAt)}</span><audio controls src="${u}" style="height:34px;flex:1"></audio></div>`); } catch (e) { } });
  }
  function hideSos() { stopSiren(); sosShownId = null; const ov = $('sosOv'); if (ov) ov.remove(); }
  window.sosTake = async (id, close) => { try { await api('/admin/sos/' + id + '/ack', { method: 'POST', body: JSON.stringify({ note: ($('sosNote') || {}).value || '', close }) }); hideSos(); pollNotifs(); if (!close) go('sos'); } catch (e) { alert(e.message); } };
  window.sosBlock = async uid => { const reason = prompt('Motivo do bloqueio (fica no histórico):', 'Bloqueado durante um SOS'); if (!reason) return; try { await api('/admin/users/' + uid + '/block', { method: 'POST', body: JSON.stringify({ reason }) }); alert('Conta bloqueada.'); } catch (e) { alert(e.message); } };

  // ───────── telas novas ─────────
  const kcard = (icon, bg, fg, label, value, sub, extra) => `<div class="card kc ${extra || ''}"><div class="kci" style="background:${bg}">${I(icon, 20, fg)}</div><div><div class="l">${label}</div><div class="v">${value}</div><div class="s">${sub || ''}</div></div></div>`;
  const STATUS2 = { OPEN: ['Aguardando', 'p-warn'], CONFIRMED: ['Confirmado', 'p-blue'], AWAITING_PAYMENT: ['Pago · no cofre', 'p-purple'], IN_PROGRESS: ['Em andamento', 'p-sky'], PENDING_CLIENT_CONFIRMATION: ['Para aprovar', 'p-warn'], DONE: ['Concluído', 'p-ok'], REVIEWED: ['Concluído', 'p-ok'], CANCELLED: ['Cancelado', ''], BLOCKED: ['Recusado', ''] };
  function rowState(r) {
    if (+r.sos) return '<span class="pill p-bad">SOS ativo</span>';
    if (r.disputeStatus === 'OPEN') { let t = ''; if (r.disputedAt) { const left = Math.round((new Date(r.disputedAt).getTime() + (window._dispH || 2) * 3600e3 - Date.now()) / 60000); t = left > 0 ? ' · ' + mins(left) : ' · vencida'; } return `<span class="pill p-bad">Disputa${t}</span>`; }
    if (r.quoteGroup && r.status === 'OPEN') return '<span class="pill p-purple">Comparando</span>';
    const x = STATUS2[r.status] || [r.status, '']; return `<span class="pill ${x[1]}">${esc(x[0])}</span>`;
  }
  function money(r) {
    if (+r.stages) { const a = +r.stagesApproved, n = +r.stages; return `<div style="width:92px"><div class="stbar">${Array.from({ length: n }).map((_, i) => `<i style="background:${i < a ? '#16A34A' : i < a + (+r.stagesDone) ? '#F59E0B' : 'var(--line2)'}"></i>`).join('')}</div><div class="muted" style="font-size:11px;margin-top:4px">etapa ${Math.min(n, a + 1)} de ${n}</div></div>`; }
    if (r.status === 'CANCELLED') return '<span class="muted" style="font-size:12px">devolvido</span>';
    if (r.pixReleased) return '<span style="font-size:12px;color:#047857;font-weight:700">liberado</span>';
    if (r.pixPaid) return '<span style="font-size:12px;color:#7C3AED;font-weight:700">no cofre</span>';
    return '<span class="muted">—</span>';
  }

  VIEWS.pedidos = async function (status = 'ALL', q = '', sel = null) {
    V(head('Pedidos', 'Carregando…'));
    let d; try { d = await api('/admin/orders?status=' + status + '&q=' + encodeURIComponent(q)); } catch (e) { V(head('Pedidos', esc(e.message))); return; }
    try { const st = await api('/admin/settings'); window._dispH = +(st.dispute_timeout_hours || 2); } catch (e) { }
    const c = d.counts || {}, k = d.kpis || {}, doneC = (+c.DONE || 0) + (+c.REVIEWED || 0);
    const nd = k.nextDisputeDeadline ? Math.round((new Date(k.nextDisputeDeadline).getTime() - Date.now()) / 60000) : null;
    const F = [['ALL', 'Todos', c.ALL], ['OPEN', 'Aguardando', c.OPEN], ['ESCROW', 'No cofre', c.ESCROW], ['IN_PROGRESS', 'Em andamento', c.IN_PROGRESS], ['PENDING_CLIENT_CONFIRMATION', 'Para aprovar', c.PENDING_CLIENT_CONFIRMATION], ['DONE', 'Concluídos', doneC], ['DISPUTE', 'Disputa', c.DISPUTE], ['CANCELLED', 'Cancelados', c.CANCELLED]];
    V(head('Pedidos', 'Acompanhe cada pedido, o dinheiro no cofre e as provas', `<input id="oq" type="search" placeholder="Buscar cliente, profissional ou nº" value="${esc(q)}" style="width:280px"><button class="ghost" onclick="exportCsv()">Exportar planilha</button>`) +
      `<div class="grid k4">${kcard('lock', '#EDE9FE', '#5B21B6', 'No cofre agora', money0(k.escrow), (k.escrowOrders || 0) + ' pedidos')}${kcard('check', '#DCFCE7', '#047857', 'Esperando o cliente aprovar', (k.toApprove || 0) + ' pedidos', money0(k.toApproveValue) + ' para profissionais')}${kcard('clock', '#FEE2E2', '#B91C1C', 'Disputas abertas', k.disputes || 0, nd != null ? (nd > 0 ? 'a próxima vence em ' + mins(nd) : 'uma já passou do prazo') : 'nenhuma agora', +k.disputes ? 'hot' : '')}${kcard('list', '#FEF3C7', '#B45309', 'Etapas para o cliente aprovar', k.stagesWaiting || 0, k.stagesOrders ? 'em ' + k.stagesOrders + ' pedido(s)' : 'nenhuma agora')}</div>
      <div class="seg wrapseg">${F.map(([key, l, n]) => `<button class="${key === status ? 'on' : ''} ${key === 'DISPUTE' && +n ? 'red' : ''}" onclick="VIEWS.pedidos('${key}',$('oq').value)">${l} <span>${n || 0}</span></button>`).join('')}</div>
      <div class="grid split" style="align-items:start"><div class="card" style="padding:6px"><div class="wrap"><table><thead><tr><th>Pedido</th><th>Cliente → Profissional</th><th>Valor</th><th>Situação</th></tr></thead><tbody>
      ${d.rows.map(r => `<tr class="click" data-id="${r.id}" onclick="openOrder('${r.id}',this)"><td><b>#${code(r.id)}</b><div class="muted" style="font-size:12px">${esc(r.product || 'Serviço')} · ${day(r.date || r.createdAt).slice(0, 5)}${r.startTime ? ' ' + String(r.startTime).slice(0, 5) : ''}${r.quoteGroup ? ' · em grupo' : ''}</div></td>
        <td><div class="row" style="gap:6px;flex-wrap:nowrap">${av(r.customerName)}<span>${esc(String(r.customerName || '—').split(' ')[0])}</span><span class="muted">→</span>${r.professionalName ? av(r.professionalName) + `<span>${esc(String(r.professionalName).split(' ')[0])}</span>` : '<span class="muted" style="font-size:12px">aguardando</span>'}</div></td>
        <td><b>${r.totalCost ? brl(r.totalCost) : '<span class="muted">A definir</span>'}</b><div style="margin-top:3px">${money(r)}</div></td><td>${rowState(r)}</td></tr>`).join('') || '<tr><td colspan="4" class="muted">Nenhum pedido.</td></tr>'}
      </tbody></table></div><div class="row muted" style="justify-content:space-between;padding:10px 12px;font-size:13px">Mostrando ${d.rows.length} de ${d.total}</div></div>
      <div class="card det" id="odet"><div class="empty">${I('list', 28, '#94A3B8')}<div>Escolha um pedido para ver o dinheiro, as etapas, as provas e a conversa.</div></div></div></div>`);
    $('oq').addEventListener('keydown', e => { if (e.key === 'Enter') VIEWS.pedidos(status, $('oq').value); });
    if (sel) openOrder(sel); else if (d.rows.length && innerWidth > 1100) { const first = d.rows.find(r => r.disputeStatus === 'OPEN') || d.rows[0]; openOrder(first.id, document.querySelector(`tr[data-id="${first.id}"]`)); }
  };

  window.openOrder = async function (id, tr) {
    if (cur !== 'pedidos' || !$('odet')) { cur = 'pedidos'; navHtml(); await VIEWS.pedidos('ALL', '', null); }
    document.querySelectorAll('tr.sel').forEach(x => x.classList.remove('sel')); (tr || document.querySelector(`tr[data-id="${id}"]`) || { classList: { add() { } } }).classList.add('sel');
    const box = $('odet'); box.innerHTML = '<div class="muted" style="padding:18px">Carregando…</div>';
    let e, stages = []; try { [e, stages] = await Promise.all([api('/admin/orders/' + id + '/evidence'), api('/admin/orders/' + id + '/stages').catch(() => [])]); } catch (err) { box.innerHTML = '<div style="padding:18px">' + esc(err.message) + '</div>'; return; }
    const o = e.order, t = e.trip || {}, disp = o.disputeStatus === 'OPEN';
    let left = null; if (disp && o.disputedAt) left = Math.round((new Date(o.disputedAt).getTime() + (window._dispH || 2) * 3600e3 - Date.now()) / 60000);
    const fee = o.totalCost && o.serviceValue ? (+o.totalCost - +o.serviceValue) : null;
    const steps = [['Pedido criado', o.createdAt], ['Pago no cofre', o.pixPaid ? 'sim' : null], ['Saiu para o atendimento', t.departedAt], ['Terminou', o.finishedAt], ['Liberado ao profissional', o.releasedAt]].filter(x => x[1]);
    box.innerHTML = `<div class="dethead"><div class="row" style="justify-content:space-between"><span style="font-size:12px;color:#BFDBFE;font-weight:600">Pedido #${code(o.id)} · ${esc(o.product || 'Serviço')}</span>${disp ? '<span class="pill p-bad">Disputa</span>' : `<span class="pill" style="background:rgba(255,255,255,.18);color:#fff">${esc((STATUS2[o.status] || [o.status])[0])}</span>`}</div>
        <div style="font:700 26px Montserrat;margin-top:6px">${o.totalCost ? brl(o.totalCost) : 'A definir'}</div><div class="row" style="gap:14px;margin-top:8px;font-size:12.5px;color:#DBEAFE">${o.serviceValue ? '<span>Serviço ' + brl(o.serviceValue) + '</span>' : ''}${fee != null ? '<span>Taxa OPUS ' + brl(fee) + '</span>' : ''}<span>${o.pixPaid ? 'Pago por PIX' : 'Ainda não pago'}</span></div></div>
      <div class="detbody">
        ${disp ? `<div class="warnbox">${I('clock', 18, '#B91C1C')}<div><b>${left != null ? (left > 0 ? 'Decida em ' + mins(left) + '.' : 'O prazo já passou.') : 'Disputa aberta.'}</b> Se ninguém decidir, o valor segue a regra das ${window._dispH || 2} h. Motivo: "${esc(o.disputeReason || '')}"</div></div>` : ''}
        <div class="grid" style="grid-template-columns:1fr 1fr;gap:10px"><a class="pbox" href="#" onclick="openUser(${o.customer && o.customer.id});return false">${av(o.customer && o.customer.name, 34)}<div><small>Cliente</small><b>${esc(o.customer && o.customer.name || '—')}</b></div></a><a class="pbox" href="#" onclick="${o.professional ? `openUser(${o.professional.id})` : ''};return false">${av(o.professional && o.professional.name, 34)}<div><small>Profissional</small><b>${esc(o.professional && o.professional.name || '—')}</b></div></a></div>
        ${stages.length ? `<div><h3>Pagamento por etapas</h3>${stages.map(s => `<div class="strow"><span class="stn ${s.status === 'APPROVED' ? 'ok' : s.status === 'DONE' ? 'wait' : ''}">${s.status === 'APPROVED' ? I('check', 13, '#fff') : s.idx}</span><span style="flex:1;font-size:13px">${esc(s.label)} <span class="muted">${s.percent}%</span></span><b style="font-size:13px;color:${s.status === 'APPROVED' ? '#047857' : s.status === 'DONE' ? '#B45309' : 'var(--muted)'}">${brl(s.value)} ${s.status === 'APPROVED' ? 'liberado' : s.status === 'DONE' ? 'esperando aprovação' : 'no cofre'}</b></div>`).join('')}</div>` : ''}
        <div><h3>Linha do tempo</h3><div class="tl">${steps.map(s => `<div><b>${s[0]}</b><span class="muted">${s[1] === 'sim' ? '' : fmt(s[1])}</span></div>`).join('')}</div></div>
        ${e.photos.length ? `<div><h3>Provas (${e.photos.length})</h3><div class="thumbs">${e.photos.slice(0, 8).map(p => `<a href="/orders/photos/${p.id}" target="_blank"><img src="/orders/photos/${p.id}" alt="Prova ${esc(p.kind || '')}"></a>`).join('')}</div></div>` : ''}
        <div><h3>Conversa</h3><div class="chat" id="ochat">${e.messages.map(m => `<div class="${m.type === 'SYSTEM' ? 'sys' : m.role === 'PROFESSIONAL' ? 'me' : ''}"><b>${esc(String(m.from || '').split(' ')[0])}</b> <span class="muted">${fmt(m.at)}</span><br>${m.type === 'AUDIO' && m.imageUrl ? `<span class="aud" data-src="/admin${esc(m.imageUrl)}">🎤 carregando áudio…</span>` : esc(m.text || '')}${m.imageUrl && String(m.imageUrl).startsWith('data:image') ? `<img src="${m.imageUrl}" alt="Foto do chat">` : ''}</div>`).join('') || '<span class="muted">Sem mensagens.</span>'}</div></div>
        <div class="muted" style="font-size:12.5px">Chamadas: ${e.calls.length ? e.calls.map(c => fmt(c.createdAt) + ' · ' + (c.kind === 'audio' ? 'áudio' : 'vídeo') + (c.seconds ? ' · ' + Math.floor(c.seconds / 60) + ' min' : '')).join(' | ') : 'nenhuma'}</div>
        <div class="grid" style="grid-template-columns:1fr 1fr;gap:10px">${disp ? `<button class="ok big" onclick="decide('${o.id}','RELEASE')">Liberar ao profissional</button><button class="danger big" onclick="decide('${o.id}','REFUND')">Devolver ao cliente</button>` : ['OPEN', 'CONFIRMED', 'AWAITING_PAYMENT', 'IN_PROGRESS'].includes(o.status) ? `<button class="danger" onclick="adminCancel('${o.id}')">Cancelar pedido</button>` : ''}</div>
      </div>`;
    box.querySelectorAll('.aud').forEach(async el => { try { const u = await blob(el.dataset.src); el.outerHTML = `<audio controls src="${u}" style="height:34px;max-width:100%"></audio>`; } catch (er) { el.textContent = '🎤 áudio indisponível'; } });
    const ch = $('ochat'); if (ch) ch.scrollTop = ch.scrollHeight;
  };
  window.decide = async (id, decision) => { const note = prompt(decision === 'RELEASE' ? 'Motivo para liberar ao profissional (os dois veem):' : 'Motivo para devolver ao cliente (os dois veem):'); if (!note) return; try { await api('/admin/orders/' + id + '/dispute/resolve', { method: 'POST', body: JSON.stringify({ decision, note }) }); pollNotifs(); VIEWS.pedidos('ALL', '', id); } catch (e) { alert(e.message); } };

  VIEWS.usuarios = async function (type = 'ALL', q = '') {
    V(head('Usuários', 'Carregando…'));
    let rows, k = {}; try { [rows, k] = await Promise.all([api('/admin/users?type=' + type + '&q=' + encodeURIComponent(q)), api('/admin/users/kpis').catch(() => ({}))]); } catch (e) { V(head('Usuários', esc(e.message))); return; }
    const oldest = k.oldestPending ? Math.round((Date.now() - new Date(k.oldestPending).getTime()) / 60000) : null;
    V(head('Usuários', 'Clientes e profissionais, com documentos, histórico e ações', `<input id="uq" type="search" placeholder="Buscar nome, usuário ou e-mail" value="${esc(q)}" style="width:280px"><button class="ghost" onclick="go('avisos')">Mandar aviso para todos</button>`) +
      `<div class="grid k4">${kcard('spark', '#E0E7FF', '#3730A3', 'Clientes', (k.customers || 0).toLocaleString('pt-BR'), '+' + (k.customersWeek || 0) + ' esta semana')}${kcard('award', '#DBEAFE', '#1E40AF', 'Profissionais', k.pros || 0, (k.prosOnline || 0) + ' online agora')}${kcard('shield', '#FEF3C7', '#B45309', 'Esperando verificação', k.pending || 0, oldest != null ? 'o mais antigo há ' + mins(oldest) : 'tudo em dia', +k.pending ? 'warm' : '')}${kcard('x', '#FEE2E2', '#B91C1C', 'Bloqueados', k.blocked || 0, 'contas sem acesso')}</div>
      <div class="seg">${[['ALL', 'Todos'], ['CUSTOMER', 'Clientes'], ['PROFESSIONAL', 'Profissionais'], ['BLOCKED', 'Bloqueados'], ['ADMIN', 'Equipe']].map(([key, l]) => `<button class="${key === type ? 'on' : ''}" onclick="VIEWS.usuarios('${key}',$('uq').value)">${l}</button>`).join('')}<button onclick="go('verif')" class="${+k.pending ? 'amber' : ''}">Verificar ${k.pending || ''}</button></div>
      <div class="grid split" style="align-items:start"><div class="card" style="padding:6px"><div class="wrap"><table><thead><tr><th>Pessoa</th><th>Tipo</th><th>Serviços</th><th>Nota</th><th>Desde</th><th>Situação</th></tr></thead><tbody>
      ${rows.map(u => { const pro = (u.roles || '').includes('PROFESSIONAL'); return `<tr class="click" data-uid="${u.id}" onclick="openUser(${u.id})"><td><div class="row" style="gap:10px;flex-wrap:nowrap">${av(u.name || u.username, 34)}<div><b>${esc(u.name || u.username)}</b><div class="muted" style="font-size:12px">${esc(u.email || '')}</div></div></div></td><td><span class="pill ${pro ? 'p-indigo' : ''}">${pro ? 'Profissional' : 'Cliente'}</span></td><td>${u.done}</td><td>${u.rating ? '★ ' + String(u.rating).replace('.', ',') : '<span class="muted">—</span>'}</td><td>${day(u.createdAt)}</td><td>${u.blocked ? '<span class="pill p-bad">Bloqueado</span>' : u.verified ? '<span class="pill p-blue">Verificado</span>' : '<span class="pill p-ok">Ativo</span>'}</td></tr>`; }).join('') || '<tr><td colspan="6" class="muted">Ninguém encontrado.</td></tr>'}
      </tbody></table></div></div><div class="card det" id="udet"><div class="empty">${I('spark', 28, '#94A3B8')}<div>Escolha alguém para ver a ficha completa.</div></div></div></div>`);
    $('uq').addEventListener('keydown', e => { if (e.key === 'Enter') VIEWS.usuarios(type, $('uq').value); });
  };

  window.openUser = async function (id) {
    if (cur !== 'usuarios' || !$('udet')) { cur = 'usuarios'; location.hash = 'usuarios'; navHtml(); await VIEWS.usuarios(); }
    document.querySelectorAll('tr.sel').forEach(x => x.classList.remove('sel')); const tr = document.querySelector(`tr[data-uid="${id}"]`); if (tr) tr.classList.add('sel');
    const box = $('udet'); box.innerHTML = '<div class="muted" style="padding:18px">Carregando…</div>';
    let u; try { u = await api('/admin/users/' + id); } catch (e) { box.innerHTML = '<div style="padding:18px">' + esc(e.message) + '</div>'; return; }
    const s = u.stats || {}, pro = (u.roles || []).includes('PROFESSIONAL'), v = u.verification;
    const stat = (val, l) => `<div class="stat"><b>${val}</b><span>${l}</span></div>`;
    box.innerHTML = `<div class="uhead">${av(u.name, 58)}<div style="flex:1;min-width:0"><div style="font:700 19px Montserrat">${esc(u.name)}</div><div class="muted">${pro ? 'Profissional' : 'Cliente'} · @${esc(u.username)} · desde ${day(u.createdAt)}</div><div class="row" style="margin-top:6px;gap:6px">${u.verified ? '<span class="pill p-blue">Verificado</span>' : ''}${u.blocked ? '<span class="pill p-bad">Bloqueado</span>' : '<span class="pill p-ok">Ativo</span>'}${+s.disputes ? `<span class="pill p-bad">${s.disputes} disputa(s)</span>` : ''}</div></div></div>
      <div class="detbody">
        <div class="grid" style="grid-template-columns:repeat(4,1fr);gap:8px">${pro ? stat(s.servicesDone || 0, 'serviços') + stat(s.rating ? '★ ' + String(s.rating).replace('.', ',') : '—', 'nota') + stat(money0(u.earnedMonth), 'ganhos no mês') + stat(u.acceptRate != null ? u.acceptRate + '%' : '—', 'aceitação') : stat(s.ordersAsClient || 0, 'pedidos') + stat(s.cancelsAsClient || 0, 'cancelamentos') + stat(s.disputes || 0, 'disputas') + stat(u.points || 0, 'pontos')}</div>
        <div class="kv"><div><small>Telefone</small>${esc(u.phone || '—')}</div><div><small>E-mail</small>${esc(u.email || '—')}</div>${pro ? `<div><small>Chave PIX</small>${esc(u.pixKey || '—')}</div><div><small>Ganhos no total</small>${brl(u.earnedTotal)}</div>` : ''}<div><small>Pontos</small>${u.points}</div>${pro ? `<div><small>No horário</small>${s.punctuality != null ? s.punctuality + '%' : '—'}</div>` : ''}</div>
        ${pro ? `<div><h3>Documentos</h3><div class="sinfo">${v ? `<b>${{ PENDING: 'Esperando você aprovar', APPROVED: 'Aprovado', REJECTED: 'Recusado' }[v.status] || esc(v.status)}</b><span>${v.submittedAt ? 'enviado ' + fmt(v.submittedAt) : ''}${v.reviewedAt ? ' · revisado ' + fmt(v.reviewedAt) : ''}</span>${v.status === 'PENDING' ? `<button class="primary" style="margin-top:8px" onclick="go('verif')">Abrir Verificações</button>` : ''}` : '<span class="muted">Ainda não enviou documentos.</span>'}</div></div>` : ''}
        ${u.blocked ? `<div class="warnbox"><div><b>Motivo do bloqueio:</b> ${esc(u.blockedReason || '')}</div></div>` : ''}
        ${u.blockedAttempts.length ? `<div class="warnbox amber"><div><b>${u.blockedAttempts.length} tentativa(s) de passar contato no chat.</b> Última: "${esc(u.blockedAttempts[0].text || '').slice(0, 80)}"</div></div>` : ''}
        <div><h3>Últimos pedidos</h3>${u.orders.slice(0, 6).map(o => `<a class="lrow" href="#" onclick="aiGo('pedidos','${o.id}');return false"><span>#${code(o.id)} · ${esc(o.papel)} · ${day(o.date)}</span><span>${o.disputeStatus === 'OPEN' ? '<span class="pill p-bad">Disputa</span>' : st(o.status)}</span></a>`).join('') || '<div class="muted">Nenhum pedido.</div>'}</div>
        <div class="grid" style="grid-template-columns:1fr 1fr 1fr;gap:8px"><button class="ghost" onclick="userMsg(${u.id})">Mandar aviso</button><button class="ghost" onclick="userPoints(${u.id})">Dar pontos</button>${u.blocked ? `<button class="ok" onclick="userUnblock(${u.id})">Desbloquear</button>` : `<button class="danger" onclick="userBlock(${u.id})">Bloquear</button>`}</div>
      </div>`;
  };

  VIEWS.fin = async function (filter = 'ALL') {
    V(head('Financeiro', 'Carregando…')); let d; try { d = await api('/admin/finance'); } catch (e) { V(head('Financeiro', esc(e.message))); return; }
    const fee = +d.fee + +d.feeExtras, prev = +d.prevFee, pct = prev ? Math.round(100 * (fee - prev) / prev) : null;
    const escrow = +d.escrow + +d.escrowExtras + +d.escrowClasses;
    const mx = Math.max(1, ...d.monthly.map(x => +x.fee)), MON = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];
    const src = [['Taxa de 5% dos pedidos', +d.fee, '#1D4ED8'], ['Taxa dos orçamentos extras', +d.feeExtras, '#0891B2'], ['Destaques pagos', +d.boosts, '#0891B2'], ['Descontos (pontos e cupons)', -d.pointsDiscount, '#F87171']];
    const smax = Math.max(1, ...src.map(x => Math.abs(x[1])));
    const mv = [...(d.movements || []).map(m => ({ at: m.at, orderId: m.orderId, kind: m.kind, person: m.person, value: m.value, t: m.tipo === 'IN' ? 'PAY' : m.tipo === 'REFUND' ? 'REFUND' : m.tipo === 'FEE' ? 'FEE' : 'REL' })),
    ...(d.wallet || []).filter(w => w.kind === 'WITHDRAW' || w.kind === 'STAGE').map(w => ({ at: w.at, orderId: w.orderId, kind: w.kind === 'WITHDRAW' ? 'Saque PIX' : 'Etapa liberada', person: w.person, value: w.kind === 'WITHDRAW' ? -w.value : w.value, t: w.kind === 'WITHDRAW' ? 'WD' : 'REL', status: w.status }))]
      .filter(m => filter === 'ALL' || m.t === filter).sort((a, b) => new Date(b.at) - new Date(a.at)).slice(0, 60);
    const KP = { PAY: ['Pagamento no cofre', 'p-purple'], REL: ['Liberação', 'p-ok'], FEE: ['Taxa OPUS', 'p-blue'], WD: ['Saque PIX', 'p-blue'], REFUND: ['Devolução', ''] };
    V(head('Financeiro', 'De onde vem o dinheiro, o que está no cofre e o que já saiu (mês atual)', `<button class="ghost" onclick="exportCsv()">Exportar planilha</button>`) +
      `<div class="grid fin4"><div class="card hero"><div class="l">Sua receita no mês</div><div class="v">${brl(fee)}</div><div class="s">${pct != null ? (pct >= 0 ? '+' : '') + pct + '% vs. mês passado' : 'primeiro mês com dados'}</div></div>
        <div class="card kpi"><div class="l">Valor movimentado</div><div class="v">${money0(d.gmv)}</div><div class="d muted">${d.paidOrders || 0} pedidos pagos</div></div>
        <div class="card kpi"><div class="l">Guardado no cofre</div><div class="v" style="color:#7C3AED">${money0(escrow)}</div><div class="d muted">${d.escrowOrders || 0} pedidos esperando</div></div>
        <div class="card kpi"><div class="l">Sacado pelos profissionais</div><div class="v">${money0(d.withdrawn)}</div><div class="d ${+d.withdrawFailed ? 'bad' : 'muted'}">${+d.withdrawFailed ? d.withdrawFailed + ' saque(s) falharam' : 'nenhum problema'}</div></div></div>
      <div class="card"><h2 style="font-size:17px;margin-bottom:14px">Caminho do dinheiro neste mês</h2><div class="flow">
        <div class="fstep"><small>1 · Clientes pagaram</small><b>${money0(d.gmv)}</b><span>por PIX</span></div>${I('arrow', 22, '#94A3B8')}
        <div class="fstep pur"><small>2 · Guardado no cofre</small><b>${money0(escrow)}</b><span>até o cliente aprovar</span></div>${I('arrow', 22, '#94A3B8')}
        <div class="fstep grn"><small>3 · Liberado</small><b>${money0(+d.released + +d.stagesReleased)}</b><span>${money0(d.stagesReleased)} em etapas</span></div>${I('arrow', 22, '#94A3B8')}
        <div class="fstep blu"><small>4 · Sacado no PIX</small><b>${money0(d.withdrawn)}</b><span>${money0(d.wallets)} ainda nas carteiras</span></div></div>
        <div class="muted" style="margin-top:12px">Devolvido aos clientes: <b>${brl(d.refunded)}</b> · Reserva para pontos: <b>${brl(d.pointsReserve)}</b> (${d.pointsOutstanding} pontos)</div></div>
      <div class="grid c2"><div class="card"><div class="row" style="justify-content:space-between;margin-bottom:14px"><h2 style="font-size:17px">Sua receita por mês</h2><span class="muted">últimos 6 meses</span></div>
        <div class="bars" role="img" aria-label="Gráfico da receita por mês">${d.monthly.map((x, i) => `<div title="${brl(x.fee)}"><em class="bv">${money0(x.fee)}</em><i style="height:${Math.max(4, Math.round(150 * x.fee / mx))}px;background:${i === d.monthly.length - 1 ? '#1D4ED8' : 'var(--barsoft)'}"></i><span style="${i === d.monthly.length - 1 ? 'font-weight:700;color:var(--text)' : ''}">${MON[+x.month.slice(5) - 1]}</span></div>`).join('')}</div></div>
        <div class="card"><h2 style="font-size:17px;margin-bottom:12px">De onde vem a receita</h2>${src.map(([l, v, c]) => `<div style="margin-bottom:12px"><div class="row" style="justify-content:space-between;font-size:13px;font-weight:600"><span>${l}</span><span style="${v < 0 ? 'color:#B91C1C' : ''}">${v < 0 ? '− ' + brl(-v) : brl(v)}</span></div><div class="track"><div style="width:${Math.max(2, Math.round(100 * Math.abs(v) / smax))}%;background:${c}"></div></div></div>`).join('')}</div></div>
      <div class="card" style="padding:6px"><div class="row" style="padding:10px 12px;gap:8px"><h2 style="font-size:17px;flex:1">Movimentações</h2>${[['ALL', 'Tudo'], ['PAY', 'Pagamentos'], ['REL', 'Liberações'], ['WD', 'Saques'], ['REFUND', 'Devoluções']].map(([key, l]) => `<button class="chip ${key === filter ? 'on' : ''}" onclick="VIEWS.fin('${key}')">${l}</button>`).join('')}</div>
        <div class="wrap"><table><thead><tr><th>Quando</th><th>O quê</th><th>Pedido</th><th>Quem</th><th style="text-align:right">Valor</th><th>Situação</th></tr></thead><tbody>${mv.map(m => `<tr><td>${fmt(m.at)}</td><td><span class="pill ${(KP[m.t] || ['', ''])[1]}">${esc(m.kind)}</span></td><td>${m.orderId ? '<a href="#" onclick="aiGo(\'pedidos\',\'' + m.orderId + '\');return false">#' + code(m.orderId) + '</a>' : '<span class="muted">—</span>'}</td><td>${esc(m.person || '—')}</td><td style="text-align:right"><b>${+m.value < 0 ? '− ' + brl(-m.value) : brl(m.value)}</b></td><td>${m.status === 'FAILED' ? '<span class="pill p-bad">Falhou</span>' : m.status === 'PENDING' ? '<span class="pill p-warn">Processando</span>' : '<span class="pill p-ok">Feito</span>'}</td></tr>`).join('') || '<tr><td colspan="6" class="muted">Sem movimentações.</td></tr>'}</tbody></table></div></div>`);
  };

  // ───────── liga tudo ─────────
  const oldStart = window.start;
  window.refreshCounts = pollNotifs;
  window.start = function () { topbar(); oldStart(); };
  setInterval(() => { if (token && !$('app').classList.contains('hidden')) pollNotifs(); }, 6000);
  window.__boot();
})();
