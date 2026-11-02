const $ = id => document.getElementById(id);
let token = null, tab = 'sup', currentUser = null, refresh = null;
try { token = sessionStorage.getItem('opus_admin_token'); } catch(e) {}
const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const fmt = d => { if(!d) return '—'; try { return new Date(d).toLocaleString('pt-BR'); } catch(e){ return d; } };
const brl = v => v == null ? '—' : Number(v).toLocaleString('pt-BR',{style:'currency',currency:'BRL'});
async function api(path, opts={}) {
  const r = await fetch(path, {...opts, headers:{'Content-Type':'application/json', Authorization:'Bearer '+token}});
  if (r.status === 401 || r.status === 403) { $('app').classList.add('hidden'); $('needLogin').classList.remove('hidden'); throw new Error('Sem permissão'); }
  const txt = await r.text(); let data; try { data = JSON.parse(txt); } catch(e) { data = txt; }
  if (!r.ok) throw new Error((data && data.message) || 'Erro '+r.status);
  return data;
}
function zoom(src){ $('zoomImg').src = src; $('zoom').style.display = 'flex'; }
$('zoom').onclick = () => $('zoom').style.display = 'none';
document.addEventListener('click', e => { if (e.target.dataset && e.target.dataset.zoom) zoom(e.target.src); });
document.querySelectorAll('.tab').forEach(b => b.onclick = () => {
  document.querySelectorAll('.tab').forEach(x => x.classList.remove('on')); b.classList.add('on'); tab = b.dataset.t;
  $('detail').innerHTML = '<p class="muted">Escolha um item ao lado.</p>'; currentUser = null; load();
});

async function load(){
  const list = $('list');
  try {
    if (tab === 'sup') {
      const rows = await api('/admin/support/threads');
      list.innerHTML = rows.length ? rows.map(r => '<div class="item'+(r.userId===currentUser?' on':'')+(r.priority?' prio':'')+'" data-u="'+r.userId+'">'+(r.unread>0?'<span class="badge">'+r.unread+'</span>':'')+'<b>'+esc(r.name||r.username)+'</b>'+(r.priority?'<span class="tag red">🚨 Prioridade</span>':r.needsHuman?'<span class="tag amb">Esperando a equipe</span>':'<span class="tag opi">Opi atendendo</span>')+'<small>'+esc(r.lastText)+'</small><small>'+fmt(r.lastAt)+'</small></div>').join('') : '<p class="muted">Nenhuma conversa ainda.</p>';
      list.querySelectorAll('.item').forEach(el => el.onclick = () => openThread(Number(el.dataset.u)));
    } else {
      const rows = await api('/admin/disputes?status=' + (tab === 'all' ? 'ALL' : 'OPEN'));
      if (tab !== 'all') disputeAlarm(rows.filter(r => r.disputeStatus === 'OPEN').length);
      list.innerHTML = rows.length ? rows.map(r => '<div class="item" data-o="'+r.id+'"><span class="pill '+esc(r.disputeStatus)+'" style="float:right">'+esc(r.disputeStatus==='OPEN'?'Aberta':'Resolvida')+'</span><b>'+esc(r.customerName)+' × '+esc(r.professionalName)+'</b><small>'+esc(r.disputeReason)+'</small><small>'+fmt(r.disputedAt)+' · '+brl(r.totalCost)+'</small>'+(r.disputeStatus==='OPEN'?deadline(r.disputedAt):'')+'</div>').join('') : '<p class="muted">Nenhuma disputa '+(tab==='all'?'registrada':'aberta')+'.</p>';
      list.querySelectorAll('.item').forEach(el => el.onclick = () => openDispute(el.dataset.o));
    }
  } catch(e) { list.innerHTML = '<p class="muted">'+esc(e.message)+'</p>'; }
}

async function openThread(uid){
  currentUser = uid;
  const t = await api('/admin/support/' + uid);
  const d = $('detail');
  d.innerHTML = '<div class="row" style="justify-content:space-between"><div><h2>'+esc(t.name)+'</h2><div class="muted">@'+esc(t.username)+' · '+esc(t.email)+' · '+(t.professional?'Profissional':'Cliente')+'</div></div></div>'
    + (t.state && t.state.human ? '<div class="row" style="justify-content:space-between;background:#FEF3C7;border-radius:12px;padding:10px 12px;margin:8px 0"><span>'+(t.state.priority?'🚨 <b>Prioridade.</b> ':'')+'A Opi chamou a equipe para esta conversa.</span><button id="release">Encerrar e devolver para a Opi</button></div>' : '<div class="muted" style="margin:8px 0">🤖 A Opi está fazendo o atendimento. Se você responder, a equipe assume a conversa.</div>')
    + '<div class="chat" id="chat">' + t.messages.map(m => '<div class="bub '+(m.fromAdmin?'me':'them')+(m.bot?' opi':'')+'">'+esc(m.text)+(m.buttons&&m.buttons.length?'<div class="btns">'+m.buttons.map(b=>'<span>'+esc(b.label)+'</span>').join('')+'</div>':'')+'<small>'+(m.fromAdmin?esc(m.adminName||'Equipe')+' · ':'')+fmt(m.createdAt)+'</small></div>').join('') + '</div>'
    + '<div class="row"><textarea id="reply" rows="2" placeholder="Escreva a resposta…" style="flex:1"></textarea><button class="primary" id="send">Enviar</button></div>'
    + (t.orders.length ? '<h3>Pedidos recentes</h3><div class="muted">' + t.orders.map(o => esc(o.status)+' · '+esc(o.date)+' · '+brl(o.totalCost)+(o.disputeStatus?' · disputa '+esc(o.disputeStatus):'')).join('<br>') + '</div>' : '');
  const c = $('chat'); c.scrollTop = c.scrollHeight;
  if ($('release')) $('release').onclick = async () => { try { await api('/admin/support/' + uid + '/release', {method:'POST'}); await openThread(uid); load(); } catch(e){ alert(e.message); } };
  $('send').onclick = async () => {
    const text = $('reply').value.trim(); if (!text) return;
    $('send').disabled = true;
    try { await api('/admin/support/' + uid, {method:'POST', body: JSON.stringify({text})}); await openThread(uid); load(); }
    catch(e){ alert(e.message); } finally { $('send').disabled = false; }
  };
  load();
}

async function openDispute(id){
  const e = await api('/admin/orders/' + id + '/evidence');
  const o = e.order, d = $('detail');
  const calls = e.calls.map(c => fmt(c.createdAt)+' · '+(c.kind==='audio'?'Áudio':'Vídeo')+' de '+esc(c.callerName)+' · '+esc(c.status)+(c.seconds?' · '+Math.floor(c.seconds/60)+' min '+(c.seconds%60)+' s':'')).join('<br>') || 'Nenhuma chamada.';
  const msgs = e.messages.map(m => {
    const cls = m.type === 'SYSTEM' ? 'sys' : (m.role === 'PROFESSIONAL' ? 'me' : 'them');
    let body = esc(m.text || '');
    if (m.type === 'QUOTE') body = 'Orçamento: ' + brl(m.quoteTotal) + ' (' + esc(m.quoteStatus) + ')';
    if (m.imageUrl && String(m.imageUrl).startsWith('data:image')) body += '<img data-zoom="1" src="'+m.imageUrl+'" alt="Foto do chat">';
    return '<div class="bub '+cls+'"><b>'+esc(m.from)+'</b><br>'+body+'<small>'+fmt(m.at)+'</small></div>';
  }).join('');
  const photos = e.photos.map(p => '<img data-zoom="1" src="/orders/photos/'+p.id+'" alt="Foto '+esc(p.kind)+'" title="'+esc(p.kind)+' · '+fmt(p.createdAt)+'">').join('') || '<span class="muted">Sem fotos.</span>';
  const consents = e.consents.map(c => esc(c.name)+' aceitou o guia de proteção v'+c.version+' em '+fmt(c.acceptedAt)).join('<br>') || 'Nenhum aceite registrado.';
  const extras = e.extras.map(x => esc(x.description)+' · '+brl(x.clientTotal)+' · '+esc(x.status)).join('<br>') || 'Nenhum.';
  d.innerHTML = '<div class="row" style="justify-content:space-between"><h2>Pedido #'+esc(String(o.id).slice(0,8))+'</h2><span class="pill '+esc(o.disputeStatus)+'">'+esc(o.disputeStatus||'—')+'</span></div>'
    + '<div class="info">'
    + '<div><b>Cliente</b>'+esc(o.customer&&o.customer.name)+'</div><div><b>Profissional</b>'+esc(o.professional&&o.professional.name)+'</div>'
    + '<div><b>Serviço</b>'+esc(o.product||'—')+'</div><div><b>Data</b>'+esc(o.date)+'</div>'
    + '<div><b>Cliente pagou</b>'+brl(o.totalCost)+(o.pixPaid?' ✓':' (não pago)')+'</div><div><b>Valor do profissional</b>'+brl(o.serviceValue)+'</div>'
    + '<div><b>Terminou em</b>'+fmt(o.finishedAt)+'</div><div><b>Liberado em</b>'+fmt(o.releasedAt)+'</div>'
    + '<div style="grid-column:1/-1"><b>Motivo da disputa</b>'+esc(o.disputeReason)+' ('+fmt(o.disputedAt)+')</div>'
    + (o.resolution ? '<div style="grid-column:1/-1"><b>Decisão</b>'+esc(o.resolution)+'</div>' : '')
    + '</div>'
    + (o.disputeStatus === 'OPEN' ? '<h3>Decidir</h3><textarea id="note" rows="2" placeholder="Motivo da decisão (os dois lados recebem)"></textarea><div class="row" style="margin-top:8px"><button class="ok" id="rel">Liberar para o profissional</button><button class="danger" id="ref">Devolver ao cliente</button></div>' : '')
    + '<h3>Fotos do serviço</h3><div class="ph">'+photos+'</div>'
    + '<h3>Chamadas</h3><div class="muted">'+calls+'</div>'
    + '<h3>Orçamentos extras</h3><div class="muted">'+extras+'</div>'
    + '<h3>Aceites do guia de proteção</h3><div class="muted">'+consents+'</div>'
    + '<h3>Tentativas bloqueadas no chat</h3><div class="muted">'+(e.blockedAttempts.length ? e.blockedAttempts.length + ' tentativa(s) de passar contato.' : 'Nenhuma.')+'</div>'
    + '<h3>Chat completo</h3><div class="chat">'+(msgs || '<span class="muted">Sem mensagens.</span>')+'</div>';
  const decide = async decision => {
    const note = $('note').value.trim();
    if (note.length < 5) { alert('Escreva o motivo da decisão.'); return; }
    if (!confirm(decision === 'RELEASE' ? 'Liberar o pagamento para o profissional?' : 'Devolver o valor ao cliente e cancelar o pedido?')) return;
    try { await api('/admin/orders/'+id+'/dispute/resolve', {method:'POST', body: JSON.stringify({decision, note})}); await openDispute(id); load(); }
    catch(err){ alert(err.message); }
  };
  if ($('rel')) { $('rel').onclick = () => decide('RELEASE'); $('ref').onclick = () => decide('REFUND'); }
}

if (!token) { $('needLogin').classList.remove('hidden'); }
else { $('app').classList.remove('hidden'); load(); refresh = setInterval(() => { load(); }, 20000); }

function deadline(at){ if(!at) return ''; const end=new Date(new Date(at).getTime()+2*3600e3); const m=Math.round((end-Date.now())/60000);
  return m>0 ? '<small style="color:#B91C1C;font-weight:700">Decidir em '+(m>=60?Math.floor(m/60)+'h '+(m%60)+'min':m+' min')+' (depois o sistema decide sozinho)</small>' : '<small style="color:#B91C1C;font-weight:700">Prazo esgotado: o sistema vai decidir</small>'; }
let _openDisputes = null;
function disputeAlarm(n){ if(_openDisputes!==null && n>_openDisputes){ try{ const a=new (window.AudioContext||window.webkitAudioContext)(); [0,0.25,0.5].forEach(t=>{const o=a.createOscillator(),g=a.createGain();o.frequency.value=880;o.connect(g);g.connect(a.destination);g.gain.value=0.15;o.start(a.currentTime+t);o.stop(a.currentTime+t+0.15);}); }catch(e){} document.title='Nova disputa · OPUS'; } _openDisputes=n; }
