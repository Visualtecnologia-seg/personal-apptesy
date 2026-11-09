const P = d => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${d}</svg>`;
const IC = {
  inicio:'<path d="M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z"/>',
  pedidos:'<rect x="5" y="3" width="14" height="18" rx="2"/><path d="M9 8h6M9 12h6M9 16h4"/>',
  usuarios:'<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0"/><path d="M16 4.5a3.5 3.5 0 0 1 0 7M21.5 20a6.5 6.5 0 0 0-4-6"/>',
  verif:'<path d="M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z"/><path d="M8.5 12l2.5 2.5 4.5-5"/>',
  suporte:'<path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z"/>',
  sos:'<path d="M12 9v4M12 17h.01"/><path d="M10.3 3.9L2 18a2 2 0 0 0 1.7 3h16.6a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"/>',
  fin:'<rect x="2.5" y="6" width="19" height="13" rx="2"/><path d="M2.5 10h19M7 15h3"/>',
  cupons:'<path d="M3 9V7a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v2a3 3 0 0 0 0 6v2a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-2a3 3 0 0 0 0-6z"/><path d="M13 5v14" stroke-dasharray="2 2"/>',
  parceiros:'<path d="M3 9l1.5-5h15L21 9"/><path d="M4 9v11h16V9"/><path d="M3 9a3 3 0 0 0 6 0 3 3 0 0 0 6 0 3 3 0 0 0 6 0"/>',
  social:'<rect x="3" y="3" width="18" height="18" rx="4"/><circle cx="12" cy="12" r="4"/>',
  certs:'<circle cx="12" cy="9" r="5"/><path d="M8.5 13l-1.5 8 5-3 5 3-1.5-8"/>',
  avisos:'<path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10 21a2 2 0 0 0 4 0"/>',
  golpes:'<path d="M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z"/><path d="M12 8v5M12 16h.01"/>',
  demanda:'<path d="M9 4L3 6v14l6-2 6 2 6-2V4l-6 2z"/><path d="M9 4v14M15 6v14"/>',
  config:'<circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M4.2 4.2l2.1 2.1M17.7 17.7l2.1 2.1M2 12h3M19 12h3M4.2 19.8l2.1-2.1M17.7 6.3l2.1-2.1"/>',
  equipe:'<circle cx="12" cy="7" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>'
};
const NAV = [['g','Operação'],['inicio','Início'],['pedidos','Pedidos'],['usuarios','Usuários'],['verif','Verificações','verifications'],['suporte','Suporte e disputas','support'],['sos','SOS','sos'],
  ['g','Dinheiro'],['fin','Financeiro'],['cupons','Cupons'],['parceiros','Lojas e seguros'],
  ['g','Comunidade'],['social','Rede social'],['certs','Certificados'],['avisos','Avisos'],['golpes','Alertas de golpe'],['demanda','Mapa de demanda'],
  ['g','Sistema'],['config','Configurações'],['equipe','Equipe']];
let cur = 'inicio', counts = {}, timer = null, lastSos = 0;
function navHtml(){ $('nav').innerHTML = NAV.map(([k,l]) => k==='g' ? `<div class="grp">${l}</div>` : `<a href="#${k}" class="${k===cur?'on':''}" onclick="go('${k}');return false">${P(IC[k]||'')}<span>${l}</span><span class="bd ${k==='sos'?'red':''}" id="bd-${k}"></span></a>`).join(''); paintBadges(); }
function paintBadges(){ const map={verif:counts.verifications,suporte:(counts.support||0)+(counts.disputes||0),sos:counts.sos}; for(const k in map){ const el=$('bd-'+k); if(!el) continue; el.textContent=map[k]||''; el.style.display=map[k]?'flex':'none'; } }
function go(k){ cur=k; location.hash=k; navHtml(); clearInterval(timer); timer=null; VIEWS[k](); }
const V = html => { $('view').innerHTML = html; };
const head = (t,s,right='') => `<div class="row" style="justify-content:space-between"><div><h1 style="font-size:24px">${t}</h1><div class="muted" style="margin-top:4px">${s}</div></div><div class="row">${right}</div></div>`;
const kpi = (l,v,d='',cls='') => `<div class="card kpi"><div class="l">${l}</div><div class="v">${v}</div><div class="d ${cls}">${d}</div></div>`;
const delta = (a,b) => { if(!b) return ['',''] ; const p=Math.round(100*(a-b)/b); return [(p>=0?'+':'')+p+'% vs. período anterior', p>=0?'up':'down']; };
const bars = (vals, labels, color) => { const mx=Math.max(1,...vals); return '<div class="bars" role="img" aria-label="Gráfico">'+vals.map((v,i)=>`<div title="${brl(v)}"><i style="height:${Math.round(170*v/mx)}px;background:${color||'#1D4ED8'}"></i><span>${esc(labels[i])}</span></div>`).join('')+'</div>'; };
const STATUS = {OPEN:['Aguardando','p-warn'],CONFIRMED:['Confirmado','p-blue'],AWAITING_PAYMENT:['Pago · no cofre','p-purple'],IN_PROGRESS:['Em andamento','p-blue'],PENDING_CLIENT_CONFIRMATION:['Para aprovar','p-warn'],DONE:['Concluído','p-ok'],REVIEWED:['Avaliado','p-ok'],CANCELLED:['Cancelado',''],BLOCKED:['Recusado','']};
const st = s => { const x=STATUS[s]||[s,'']; return `<span class="pill ${x[1]}">${esc(x[0])}</span>`; };

async function refreshCounts(){
  try { const d = await api('/admin/dashboard?range=day'); counts = d.alerts || {}; paintBadges();
    const sos = counts.sos||0; $('sosbar').style.display = sos ? 'flex' : 'none'; $('sostext').textContent = sos + ' pedido(s) de SOS aguardando a equipe';
    if (sos > lastSos) beep(); lastSos = sos;
  } catch(e){}
}
function beep(){ try { const c=new (window.AudioContext||window.webkitAudioContext)(); [0,0.35,0.7].forEach(t=>{ const o=c.createOscillator(), g=c.createGain(); o.frequency.value=880; o.connect(g); g.connect(c.destination); g.gain.setValueAtTime(0.25,c.currentTime+t); o.start(c.currentTime+t); o.stop(c.currentTime+t+0.2); }); } catch(e){} }

const VIEWS = {
  async inicio(range='week'){
    V(head('Visão geral','Carregando…')); let d; try { d = await api('/admin/dashboard?range='+range); } catch(e){ V(head('Visão geral',esc(e.message))); return; }
    const seg = ['day','week','month'].map(r=>`<button class="${r===range?'on':''}" onclick="VIEWS.inicio('${r}')">${{day:'Hoje',week:'Semana',month:'Mês'}[r]}</button>`).join('');
    const hr=new Date().getHours(), hi=(hr<12?'Bom dia':hr<18?'Boa tarde':'Boa noite')+', Administrador';
    const [d1,c1]=delta(d.orders,d.ordersPrev),[d2,c2]=delta(+d.gmv,+d.gmvPrev),[d3,c3]=delta(+d.fee,+d.feePrev);
    const a=d.alerts||{};
    const al=[[a.sos,'pedido(s) de SOS ativos','sos','#DC2626'],[a.verifications,'documento(s) para aprovar','verif','#1D4ED8'],[a.disputes,'disputa(s) abertas','suporte','#B91C1C'],[a.support,'conversa(s) de suporte sem resposta','suporte','#B45309']].filter(x=>x[0]);
    V(`<div class="top"><div style="flex:1;min-width:240px"><h1>${hi}</h1><div class="muted" style="margin-top:4px">Resumo da OPUS ${{day:'hoje',week:'nesta semana',month:'neste mês'}[range]}</div></div><input class="gs" id="gsearch" type="search" placeholder="Buscar pedido, cliente ou profissional" aria-label="Buscar"><div class="seg">${seg}</div></div>`+`
      <div class="grid k4">${kpi('Pedidos',d.orders,d1,c1)}${kpi('Valor movimentado',brl(d.gmv),d2,c2)}${kpi('Sua taxa',brl(d.fee),d3,c3)}${kpi('Guardado no cofre agora',brl(d.escrow),d.escrowOrders+' pedidos esperando conclusão')}</div>
      <div class="grid c2"><div class="card"><h2 style="font-size:17px;margin-bottom:14px">Sua taxa por dia</h2>${bars(d.series.map(x=>+x.fee),d.series.map(x=>day(x.date).slice(0,5)))}</div>
      <div class="card"><h2 style="font-size:17px;margin-bottom:12px">Precisa de você</h2><div style="display:flex;flex-direction:column;gap:8px">${al.length?al.map(x=>`<a class="alert" href="#" onclick="go('${x[2]}');return false"><i style="background:${x[3]}"></i>${x[0]} ${x[1]}<span class="muted" style="margin-left:auto">abrir →</span></a>`).join(''):'<div class="muted">Tudo em dia. 🎉</div>'}</div></div></div>
      <div class="grid c3"><div class="card"><h2 style="font-size:17px">Novos cadastros</h2><div class="row" style="gap:28px;margin-top:12px"><div><div class="kpi"><div class="v">${d.newCustomers}</div></div><div class="muted">clientes</div></div><div><div class="kpi"><div class="v">${d.newProfessionals}</div></div><div class="muted">profissionais</div></div></div><div class="muted" style="margin-top:12px">Pedidos concluídos sem disputa: <b class="up">${d.cleanRate}%</b></div></div>
      <div class="card"><h2 style="font-size:17px;margin-bottom:8px">Melhores profissionais</h2>${(d.topProfessionals||[]).map(p=>`<a class="prow" href="#" onclick="go('usuarios');setTimeout(()=>openUser(${p.id}),600);return false"><div class="ini">${esc((p.name||'?').trim()[0]||'?').toUpperCase()}</div><div style="flex:1;min-width:0"><div class="pn">${esc(p.name)}</div></div><div style="text-align:right"><div style="font-size:13px;font-weight:700">${p.rating?'★ '+String(p.rating).replace('.',','):'novo'}</div><div class="muted" style="font-size:12px">${p.done} serviços</div></div></a>`).join('')||'<div class="muted">Ainda sem serviços concluídos.</div>'}</div>
      <div class="card"><h2 style="font-size:17px;margin-bottom:10px">Serviços mais pedidos (30 dias)</h2>${(()=>{const t=(d.topServices||[]).reduce((s,x)=>s+ +x.n,0)||1;return (d.topServices||[]).map(x=>`<div style="margin-bottom:10px"><div class="row" style="justify-content:space-between;font-size:13px;font-weight:600"><span>${esc(x.name)}</span><span>${Math.round(100*x.n/t)}%</span></div><div style="height:8px;border-radius:4px;background:#EEF2F7;margin-top:5px"><div style="width:${Math.round(100*x.n/t)}%;height:8px;border-radius:4px;background:#0891B2"></div></div></div>`).join('')||'<div class="muted">Sem pedidos ainda.</div>';})()}</div></div>`);
  },

  async pedidos(status='ALL', q='', sel=null){
    V(head('Pedidos','Carregando…')); let d; try { d = await api('/admin/orders?status='+status+'&q='+encodeURIComponent(q)); } catch(e){ V(head('Pedidos',esc(e.message))); return; }
    const c=d.counts||{}; const F=[['ALL','Todos'],['OPEN','Aguardando'],['ESCROW','Pago no cofre'],['IN_PROGRESS','Em andamento'],['PENDING_CLIENT_CONFIRMATION','Para aprovar'],['DONE','Concluídos'],['DISPUTE','Disputa'],['CANCELLED','Cancelados']];
    const doneC=(+c.DONE||0)+(+c.REVIEWED||0);
    V(head('Pedidos','Tudo o que acontece no app, com as provas de cada pedido',`<input id="oq" placeholder="Buscar cliente, profissional ou nº" value="${esc(q)}" style="width:280px">`)+
      `<div class="chips">${F.map(([k,l])=>`<button class="chip ${k===status?'on':''}" onclick="VIEWS.pedidos('${k}',$('oq').value)">${l} · ${k==='DONE'?doneC:(c[k]||0)}</button>`).join('')}</div>
      <div class="grid c11" style="align-items:start"><div class="card" style="padding:6px"><div class="wrap"><table><thead><tr><th>Pedido</th><th>Data</th><th>Cliente</th><th>Profissional</th><th>Serviço</th><th>Valor</th><th>Situação</th></tr></thead><tbody>
      ${d.rows.map(r=>`<tr class="click" data-id="${r.id}" onclick="openOrder('${r.id}',this)"><td><b>#${esc(String(r.id).slice(0,6).toUpperCase())}</b></td><td>${day(r.date)}</td><td>${esc(r.customerName)}</td><td>${esc(r.professionalName||'—')}</td><td>${esc(r.product||'—')}</td><td>${r.totalCost?brl(r.totalCost):'A definir'}</td><td>${r.disputeStatus==='OPEN'?'<span class="pill p-bad">Disputa</span>':st(r.status)}</td></tr>`).join('')||'<tr><td colspan="7" class="muted">Nenhum pedido.</td></tr>'}
      </tbody></table></div></div><div class="card" id="odet"><div class="muted">Escolha um pedido para ver os detalhes e as provas.</div></div></div>`);
    $('oq').addEventListener('keydown', e => { if (e.key==='Enter') VIEWS.pedidos(status, $('oq').value); });
    if (sel) openOrder(sel);
  },

  async usuarios(type='ALL', q=''){
    V(head('Usuários','Carregando…')); let rows; try { rows = await api('/admin/users?type='+type+'&q='+encodeURIComponent(q)); } catch(e){ V(head('Usuários',esc(e.message))); return; }
    V(head('Usuários','Clientes e profissionais cadastrados',`<input id="uq" placeholder="Buscar nome, usuário ou e-mail" value="${esc(q)}" style="width:280px">`)+
      `<div class="chips">${[['ALL','Todos'],['CUSTOMER','Clientes'],['PROFESSIONAL','Profissionais'],['BLOCKED','Bloqueados'],['ADMIN','Equipe']].map(([k,l])=>`<button class="chip ${k===type?'on':''}" onclick="VIEWS.usuarios('${k}',$('uq').value)">${l}</button>`).join('')}</div>
      <div class="grid c11" style="align-items:start"><div class="card" style="padding:6px"><div class="wrap"><table><thead><tr><th>Nome</th><th>Tipo</th><th>E-mail</th><th>Serviços</th><th>Nota</th><th>Situação</th></tr></thead><tbody>
      ${rows.map(u=>`<tr class="click" onclick="openUser(${u.id})"><td><b>${esc(u.name||u.username)}</b></td><td>${esc((u.roles||'').includes('PROFESSIONAL')?'Profissional':'Cliente')}${(u.roles||'').includes('ADMIN')?' · equipe':''}</td><td>${esc(u.email)}</td><td>${u.done}</td><td>${u.rating?'★ '+u.rating:'—'}</td><td>${u.blocked?'<span class="pill p-bad">Bloqueado</span>':u.verified?'<span class="pill p-blue">Verificado</span>':'<span class="pill p-ok">Ativo</span>'}</td></tr>`).join('')||'<tr><td colspan="6" class="muted">Ninguém encontrado.</td></tr>'}
      </tbody></table></div></div><div class="card" id="udet"><div class="muted">Escolha alguém para ver a ficha.</div></div></div>`);
    $('uq').addEventListener('keydown', e => { if (e.key==='Enter') VIEWS.usuarios(type, $('uq').value); });
  },

  verif(){ V(head('Verificações','Documentos e selfies enviados pelos profissionais')+`<iframe src="/painel/index.html" title="Verificações"></iframe>`); },
  suporte(){ V(head('Suporte e disputas','Conversas com clientes e profissionais e disputas com prazo')+`<iframe src="/painel/central.html" title="Suporte e disputas"></iframe>`); },

  async sos(all=false){
    const load = async () => {
      let rows; try { rows = await api('/admin/sos'+(all?'?all=true':'')); } catch(e){ V(head('SOS',esc(e.message))); return; }
      V(head('SOS','Pedidos de ajuda feitos pelo botão SOS do app',`<button class="chip ${!all?'on':''}" onclick="VIEWS.sos(false)">Ativos</button><button class="chip ${all?'on':''}" onclick="VIEWS.sos(true)">Histórico</button>`)+
        (rows.length?rows.map(s=>`<div class="card" style="border:2px solid ${s.active&&!s.ackAt?'#DC2626':'#E5E7EB'}"><div class="grid c11"><div>
          <div class="row" style="justify-content:space-between"><h2 style="font-size:19px">${s.active?'🆘 ':''}${esc(s.name)}</h2>${s.active?(s.ackAt?'<span class="pill p-warn">Em atendimento</span>':'<span class="pill p-bad">Aguardando a equipe</span>'):'<span class="pill">Encerrado</span>'}</div>
          <div class="info" style="margin-top:10px"><div><b>Telefone</b>${esc(s.phone||'—')}</div><div><b>Acionado</b>${fmt(s.startedAt)}</div><div><b>Última localização</b>${fmt(s.locAt)}</div><div><b>Contato de confiança</b>${esc(s.contactName||'—')} ${esc(s.contactPhone||'')}</div><div><b>Endereço do pedido</b>${esc(s.serviceAddress||'—')}</div><div><b>Outra parte do pedido</b>${esc(s.otherName||'—')} ${esc(s.otherPhone||'')}</div>${s.ackBy?`<div><b>Atendido por</b>${esc(s.ackBy)} · ${fmt(s.ackAt)}</div>`:''}${s.note?`<div><b>Anotação</b>${esc(s.note)}</div>`:''}</div>
          <div style="margin-top:12px"><b style="font-size:13px">Áudios gravados (${(s.audios||[]).length})</b><div id="aud-${s.id}" style="display:flex;flex-direction:column;gap:6px;margin-top:6px"></div></div>
          ${s.active?`<div class="row" style="margin-top:12px"><input id="note-${s.id}" placeholder="Anotação (ex.: liguei, ela está bem)" style="flex:1;min-width:200px"><button class="primary" onclick="sosAck('${s.id}',false)">Estou acompanhando</button><button class="ok" onclick="sosAck('${s.id}',true)">Encerrar SOS</button></div>`:''}
          <div class="muted" style="margin-top:8px">Em perigo real, ligue 190 e informe a localização.</div>
        </div><div class="map" id="map-${s.id}" style="height:300px"></div></div></div>`).join(''):'<div class="card muted">Nenhum SOS '+(all?'registrado':'ativo agora')+'.</div>'));
      rows.forEach(s=>{ if(s.lat!=null){ const m=L.map('map-'+s.id).setView([s.lat,s.lng],16); L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}').addTo(m); L.marker([s.lat,s.lng]).addTo(m).bindPopup(esc(s.name)).openPopup(); } else { $('map-'+s.id).innerHTML='<div class="muted" style="padding:20px">Sem localização (GPS desligado).</div>'; }
        (s.audios||[]).forEach(async a=>{ try{ const u=await blob('/admin/sos/audio/'+a.id); $('aud-'+s.id).insertAdjacentHTML('beforeend',`<div class="row"><span class="muted">${fmt(a.createdAt)}</span><audio controls src="${u}" style="height:34px"></audio></div>`);}catch(e){} }); });
    };
    await load(); if (!all) timer = setInterval(load, 15000);
  },

  async fin(){
    V(head('Financeiro','Carregando…')); let d; try { d = await api('/admin/finance'); } catch(e){ V(head('Financeiro',esc(e.message))); return; }
    V(head('Financeiro','Para onde vai cada real que passa pela OPUS (mês atual)',`<button class="ghost" onclick="exportCsv()">Exportar Excel (CSV)</button>`)+`
      <div class="grid k4">${kpi('Guardado no cofre',brl(+d.escrow + +d.escrowExtras + +d.escrowClasses),'pedidos, extras e aulas')}${kpi('Liberado aos profissionais',brl(d.released),'este mês')}${kpi('Sua taxa',brl(+d.fee + +d.feeExtras),'este mês')}${kpi('Devolvido a clientes',brl(d.refunded),'cancelamentos e disputas','down')}${kpi('Reserva de pontos',brl(d.pointsReserve),d.pointsOutstanding+' pontos não usados')}</div>
      <div class="grid c11"><div class="card"><h2 style="font-size:17px;margin-bottom:14px">Sua taxa por mês</h2>${bars(d.monthly.map(x=>+x.fee),d.monthly.map(x=>x.month.slice(5)+'/'+x.month.slice(2,4)),'#0891B2')}</div>
      <div class="card" style="display:flex;flex-direction:column;gap:10px"><h2 style="font-size:17px">De onde vem a receita (mês)</h2>${[['Taxa dos serviços',d.fee],['Taxa dos orçamentos extras',d.feeExtras],['Taxas de deslocamento pagas a profissionais',d.cancelFees],['Descontos (pontos e cupons)',-d.pointsDiscount]].map(([l,v])=>`<div class="row" style="justify-content:space-between;padding:10px 12px;background:#F8FAFC;border-radius:12px"><span style="font-weight:600;font-size:14px">${l}</span><b>${brl(v)}</b></div>`).join('')}</div></div>
      <div class="card" style="padding:6px"><h2 style="font-size:17px;padding:10px 12px">Movimentações</h2><div class="wrap"><table><thead><tr><th>Quando</th><th>Pedido</th><th>Movimento</th><th>Pessoa</th><th>Valor</th></tr></thead><tbody>${d.movements.map(m=>`<tr><td>${fmt(m.at)}</td><td>#${esc(String(m.orderId).slice(0,6).toUpperCase())}</td><td>${esc(m.kind)}</td><td>${esc(m.person||'—')}</td><td><b>${brl(m.value)}</b></td></tr>`).join('')||'<tr><td colspan="5" class="muted">Sem movimentações.</td></tr>'}</tbody></table></div></div>`);
  },

  async social(){
    V(head('Rede social','Carregando…')); let rows; try { rows = await api('/admin/posts'); } catch(e){ V(head('Rede social',esc(e.message))); return; }
    V(head('Rede social','Publicações dos profissionais. Apague o que for impróprio (o profissional é avisado).')+`<div class="posts">${rows.map(p=>`<div class="card" style="padding:10px"><img src="${p.photoId?'/posts/photos/'+p.photoId:''}" alt="Publicação de ${esc(p.professionalName)}"><div style="margin-top:8px;font-weight:700;font-size:14px">${esc(p.professionalName)}</div><div class="muted">${fmt(p.createdAt)} · ♥ ${p.likes} · 💬 ${p.comments}</div><div style="font-size:13px;margin:6px 0;max-height:54px;overflow:hidden">${esc(p.caption||'')}</div><button class="danger" style="width:100%" onclick="delPost('${p.id}')">Apagar</button></div>`).join('')||'<div class="card muted">Nenhuma publicação ainda.</div>'}</div>`);
  },

  async avisos(){
    let rows=[]; try { rows = await api('/admin/broadcasts'); } catch(e){}
    V(head('Avisos','Notificação no celular de todos, só clientes ou só profissionais')+`<div class="grid c11"><div class="card" style="display:flex;flex-direction:column;gap:12px">
      <div><label for="aud">Para quem</label><select id="aud"><option value="ALL">Todos</option><option value="CUSTOMERS">Só clientes</option><option value="PROFESSIONALS">Só profissionais</option></select></div>
      <div><label for="btxt">Mensagem (até 180 letras)</label><textarea id="btxt" rows="3" maxlength="180" placeholder="Ex.: Fim de semana com 10% de desconto usando o cupom FDS10!"></textarea></div>
      <button class="primary" onclick="sendBroadcast()">Enviar aviso</button><div id="bmsg"></div></div>
      <div class="card"><h2 style="font-size:17px;margin-bottom:10px">Enviados</h2>${rows.map(b=>`<div style="padding:10px 0;border-bottom:1px solid #F1F5F9"><div style="font-size:14px">${esc(b.text)}</div><div class="muted">${fmt(b.createdAt)} · ${{ALL:'Todos',CUSTOMERS:'Clientes',PROFESSIONALS:'Profissionais'}[b.audience]} · ${b.sentCount} pessoas · ${esc(b.adminName||'')}</div></div>`).join('')||'<div class="muted">Nenhum aviso enviado.</div>'}</div></div>`);
  },

  async certs(){
    let rows=[]; try { rows = await api('/admin/certificates?status=PENDING'); } catch(e){}
    V(head('Certificados dos profissionais','CREF, cursos e registros: aprovados aparecem como selo no perfil')+`<div class="card" style="padding:6px"><div class="wrap"><table><thead><tr><th>Profissional</th><th>Certificado</th><th>Número</th><th>Foto</th><th></th></tr></thead><tbody>
      ${rows.map(c=>`<tr><td><b>${esc(c.proName)}</b></td><td>${esc(c.title)}</td><td>${esc(c.number||'—')}</td><td>${c.hasPhoto?`<a href="#" onclick="certPhoto('${c.id}');return false">Ver foto</a>`:'—'}</td><td><button class="ok" onclick="reviewCert('${c.id}',true)">Aprovar</button> <button class="danger" onclick="reviewCert('${c.id}',false)">Recusar</button></td></tr>`).join('')||'<tr><td colspan="5" class="muted">Nenhum certificado esperando.</td></tr>'}
    </tbody></table></div></div>`);
  },
  async parceiros(){
    let d={stores:[],plans:[],interest:[],leads:[]}; try { d = await api('/admin/partners'); } catch(e){}
    const im={}; (d.interest||[]).forEach(x=>im[x.plan]=x.n);
    V(head('Lojas parceiras e seguros','Vitrine dentro dos apps · página pública: <a href="/painel/parceiros.html" target="_blank">/painel/parceiros.html</a>')+`
    <div class="grid c11" style="align-items:start">
     <div class="card" style="display:flex;flex-direction:column;gap:10px"><h2 style="font-size:17px">Lojas parceiras (${d.stores.length})</h2>
      ${d.stores.map(x=>`<div class="row" style="justify-content:space-between;padding:10px;background:#F8FAFC;border-radius:12px"><div><b>${esc(x.name)}</b> <span class="muted">${esc(x.category||'')}</span><div class="muted">${esc(x.address||'')} · WhatsApp ${esc(x.whatsapp||'—')} · cupom ${esc(x.couponCode||'—')}</div></div><button class="danger" onclick="delStore('${x.id}')">Remover</button></div>`).join('')||'<p class="muted">Nenhuma loja ainda.</p>'}
      <h3 style="font-size:15px;margin:8px 0 0">Nova loja</h3>
      <input id="sn" placeholder="Nome da loja"><input id="scat" placeholder="Categoria (Hidráulica, Elétrica, Construção…)"><input id="sw" placeholder="WhatsApp com DDD"><input id="sa" placeholder="Endereço">
      <div class="row"><input id="slat" placeholder="Latitude" style="flex:1"><input id="slng" placeholder="Longitude" style="flex:1"></div>
      <div class="row"><input id="scc" placeholder="Cupom (OPUS10)" style="flex:1"><input id="sct" placeholder="Texto do cupom (10% OFF)" style="flex:2"></div>
      <input id="sh" placeholder="Horário (seg a sáb, 8h–18h)"><button class="ok" onclick="saveStore()">Salvar loja</button><div id="smsg"></div>
      <p class="muted">Dica: pegue latitude e longitude no Google Maps (clique com o botão direito no endereço).</p>
     </div>
     <div class="card" style="display:flex;flex-direction:column;gap:10px"><h2 style="font-size:17px">Planos de seguro</h2>
      ${d.plans.map(x=>`<div class="row" style="justify-content:space-between;padding:10px;background:#F8FAFC;border-radius:12px"><div><b>${esc(x.title)}</b> <span class="pill">${x.audience==='PRO'?'Profissional':'Cliente'}</span><div class="muted">${esc(x.partner||'sem parceiro')} · ${esc(x.price||'preço a definir')} · ${im[x.id]||0} interessados</div></div><button class="danger" onclick="delPlan('${x.id}')">Remover</button></div>`).join('')||'<p class="muted">Sem planos cadastrados: o app mostra os planos de exemplo com "Quero ser avisado".</p>'}
      <h3 style="font-size:15px;margin:8px 0 0">Novo plano</h3>
      <select id="pa"><option value="PRO">Para profissionais</option><option value="CLIENT">Para clientes</option></select>
      <input id="pt" placeholder="Nome (Acidentes pessoais)"><input id="pk" placeholder="Tipo (Por serviço / Mensal)"><input id="pp" placeholder="Seguradora parceira"><input id="pv" placeholder="Preço (R$ 4,90 por serviço)"><input id="pl" placeholder="Link para contratar">
      <textarea id="pc" placeholder="Coberturas, uma por linha"></textarea><button class="ok" onclick="savePlan()">Salvar plano</button><div id="pmsg"></div>
      <h3 style="font-size:15px;margin:8px 0 0">Lista de espera ("Quero ser avisado")</h3>
      ${(d.interest||[]).map(x=>`<div class="row" style="justify-content:space-between"><span>${esc(x.plan)}</span><b>${x.n}</b></div>`).join('')||'<p class="muted">Ninguém ainda.</p>'}
     </div>
    </div>
    <div class="card" style="margin-top:14px"><h2 style="font-size:17px">Quem quer ser parceiro (${d.leads.length})</h2><div class="wrap"><table><thead><tr><th>Tipo</th><th>Empresa</th><th>Contato</th><th>Mensagem</th><th>Quando</th></tr></thead><tbody>
      ${d.leads.map(x=>`<tr><td>${x.kind==='INSURER'?'Seguradora':'Loja'}</td><td><b>${esc(x.company)}</b></td><td>${esc(x.contact||'')}<div class="muted">${esc(x.phone||'')} ${esc(x.email||'')}</div></td><td>${esc(x.message||'')}</td><td>${day(x.createdAt)}</td></tr>`).join('')||'<tr><td colspan="5" class="muted">Nenhum contato ainda.</td></tr>'}
    </tbody></table></div></div>`);
  },
  async cupons(){
    let rows=[]; try { rows = await api('/admin/coupons'); } catch(e){}
    V(head('Cupons e promoções','O desconto sai da taxa OPUS — o profissional recebe o valor cheio')+`<div class="grid c11" style="align-items:start"><div class="card" style="padding:6px"><div class="wrap"><table><thead><tr><th>Código</th><th>Desconto</th><th>Mínimo</th><th>Validade</th><th>Usos</th><th></th></tr></thead><tbody>
      ${rows.map(c=>`<tr><td><b>${esc(c.code)}</b><div class="muted">${esc(c.description||'')}</div></td><td>${c.percent?Number(c.percent)+'%':brl(c.amount)}</td><td>${brl(c.minOrder)}</td><td>${c.validUntil?day(c.validUntil):'—'}</td><td>${c.used}${c.maxUses?' / '+c.maxUses:''}</td><td><button class="${c.active?'danger':'ok'}" onclick="toggleCoupon('${esc(c.code)}')">${c.active?'Pausar':'Ativar'}</button></td></tr>`).join('')||'<tr><td colspan="6" class="muted">Nenhum cupom.</td></tr>'}
      </tbody></table></div></div><div class="card" style="display:flex;flex-direction:column;gap:10px"><h2 style="font-size:17px">Novo cupom</h2>
      <div><label for="cc">Código</label><input id="cc" placeholder="PRIMEIRA10"></div><div><label for="cd">Descrição</label><input id="cd" placeholder="10% na primeira contratação"></div>
      <div class="row"><div style="flex:1"><label for="cp">Desconto em %</label><input id="cp" inputmode="decimal" placeholder="10"></div><div style="flex:1"><label for="ca">ou em R$</label><input id="ca" inputmode="decimal" placeholder="15"></div></div>
      <div class="row"><div style="flex:1"><label for="cm">Pedido mínimo R$</label><input id="cm" inputmode="decimal" placeholder="0"></div><div style="flex:1"><label for="cu">Limite de usos</label><input id="cu" inputmode="numeric" placeholder="sem limite"></div></div>
      <div><label for="cv">Válido até</label><input id="cv" type="date"></div><button class="primary" onclick="createCoupon()">Criar cupom</button><div id="cmsg"></div></div></div>`);
  },

  async golpes(){
    V(head('Alertas de golpe','Carregando…')); let d; try { d = await api('/admin/fraud'); } catch(e){ V(head('Alertas de golpe',esc(e.message))); return; }
    const box=(t,s,rows,unit)=>`<div class="card"><h2 style="font-size:16px">${t}</h2><div class="muted" style="margin:4px 0 10px">${s}</div>${rows.map(r=>`<div class="row" style="justify-content:space-between;padding:8px 0;border-bottom:1px solid #F1F5F9"><a href="#" onclick="openUser(${r.userId});return false"><b>${esc(r.name)}</b></a><span class="pill p-warn">${r.n} ${unit}</span></div>`).join('')||'<div class="muted">Ninguém por aqui. ✓</div>'}</div>`;
    V(head('Alertas de golpe','Comportamentos suspeitos para você olhar de perto')+`<div class="grid c3">${box('Tentaram passar contato','2 ou mais tentativas de enviar telefone/@ no chat',d.contactAttempts,'tentativas')}${box('Cancelam muito','3 ou mais cancelamentos nos últimos 30 dias',d.manyCancels,'cancelamentos')}${box('Muitas disputas','Envolvidos em 2 ou mais disputas',d.manyDisputes,'disputas')}${box('Conta nova e muito ativa','Criada há menos de 7 dias com 4+ pedidos',d.newAndBusy,'pedidos')}</div>`);
  },

  async demanda(){
    V(head('Mapa de demanda','Onde há pedidos sem profissional (vermelho), pedidos (azul), profissionais (verde) e quem está a caminho agora (🚗)')+'<div class="card" style="padding:8px"><div class="map" id="dmap"></div></div><div class="muted">Últimos 30 dias. Recrute profissionais onde aparecem muitos pontos vermelhos.</div>');
    let d; try { d = await api('/admin/demand'); } catch(e){ return; }
    const m=L.map('dmap').setView([-22.9,-43.2],10); L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}').addTo(m); const pts=[];
    const dot=(p,c,r)=>{ if(p.lat==null) return; pts.push([p.lat,p.lng]); L.circleMarker([p.lat,p.lng],{radius:r,color:c,fillColor:c,fillOpacity:.55,weight:1}).addTo(m); };
    d.orders.forEach(p=>dot(p,'#1D4ED8',5)); d.professionals.forEach(p=>dot(p,'#16A34A',6)); d.unserved.forEach(p=>dot(p,'#DC2626',8));
    d.live.forEach(p=>{ if(p.lat==null) return; L.marker([p.lat,p.lng],{icon:L.divIcon({className:'',html:'<div style="font-size:22px">🚗</div>'})}).addTo(m).bindPopup(esc(p.professionalName)); });
    if (pts.length) m.fitBounds(pts,{padding:[30,30]});
  },

  async config(){
    let s; try { s = await api('/admin/settings'); } catch(e){ V(head('Configurações',esc(e.message))); return; }
    const F=[['fee_percent','Taxa OPUS (%)','Embutida no preço que o cliente vê'],['cancel_km_price','Preço do km no cancelamento (R$)','(km rodados + volta) × este valor'],['cancel_min_fee','Taxa mínima de cancelamento (R$)',''],['points_per_service','Pontos por serviço concluído',''],['points_min_service','Valor mínimo do serviço para ganhar pontos (R$)',''],['redeem_min_order','Pedido mínimo para usar pontos (R$)','100 pontos = R$ 10'],['referral_points','Pontos do indique e ganhe (para cada um)',''],['package_max_discount','Desconto máximo dos pacotes (%)',''],['dispute_timeout_hours','Prazo máximo de um problema travado (horas)','Depois disso o sistema decide sozinho'],['dispute_timeout_refund','Sem decisão no prazo: 1 = devolver ao cliente · 0 = liberar ao profissional','']];
    V(head('Configurações','Mude as regras do app sem precisar de programador. Vale para os próximos pedidos.')+`<div class="card" style="max-width:720px;display:grid;grid-template-columns:1fr 1fr;gap:14px">${F.map(([k,l,h])=>`<div><label for="s-${k}">${l}</label><input id="s-${k}" inputmode="decimal" value="${esc(s[k])}"><div class="muted" style="margin-top:4px">${h}</div></div>`).join('')}<div style="grid-column:1/-1" class="row"><button class="primary" onclick="saveSettings()">Salvar</button><span id="smsg"></span></div></div>`);
    window._settingKeys = F.map(f=>f[0]);
  },

  async equipe(){
    let team=[], acts=[]; try { [team, acts] = await Promise.all([api('/admin/team'), api('/admin/actions')]); } catch(e){}
    setTimeout(render2fa, 0);
    V(head('Equipe','Quem pode entrar no painel, quem pode ver documentos e tudo o que cada um fez (inclusive cada documento aberto)')+`<div class="grid c11" style="align-items:start"><div class="card" style="padding:6px"><h2 style="font-size:17px;padding:10px 12px">Histórico de ações</h2><div class="wrap"><table><thead><tr><th>Quando</th><th>Quem</th><th>O quê</th><th>Detalhe</th></tr></thead><tbody>${acts.map(a=>`<tr><td>${fmt(a.createdAt)}</td><td>${esc(a.adminName)}</td><td>${esc(a.action)} ${esc(a.target||'')}</td><td style="white-space:normal;max-width:320px">${esc(a.detail||'')}</td></tr>`).join('')||'<tr><td colspan="4" class="muted">Nada registrado ainda.</td></tr>'}</tbody></table></div></div>
      <div class="card" style="display:flex;flex-direction:column;gap:10px"><h2 style="font-size:17px">Acessos do painel</h2><div class="muted">Esses logins só entram no painel. Eles são separados das contas do app.</div>${team.filter(t=>t.active).map(t=>`<div class="row" style="justify-content:space-between;padding:8px 0;border-bottom:1px solid #F1F5F9"><div class="row" style="gap:10px"><div class="ini">${esc(t.matricula)}</div><div><b>Matrícula ${esc(t.matricula)}</b><div class="muted">login: ${esc(t.login)} · ${t.twoFactor?'<span style="color:#047857;font-weight:700">🔒 2 etapas ligada</span>':'<span style="color:#B45309;font-weight:700">⚠️ sem 2 etapas</span>'}</div><label class="muted" style="display:flex;gap:6px;align-items:center;margin-top:4px;font-size:12.5px"><input type="checkbox" ${t.canViewDocs?'checked':''} onchange="docsPerm(${t.id},this.checked,this)" style="width:auto;height:auto"> Pode ver documentos e selfies${t.lastDocView?' · última vez '+fmt(t.lastDocView):''}</label></div></div><div class="row" style="gap:6px">${t.twoFactor?`<button class="ghost" onclick="resetTeam2fa(${t.id})" title="Para quem perdeu o celular">Zerar 2 etapas</button>`:''}<button class="danger" onclick="removeAdmin(${t.id})">Tirar acesso</button></div></div>`).join('')}
      <h2 style="font-size:15px;margin-top:10px">Criar novo acesso</h2><div><label for="na">Login</label><input id="na" autocapitalize="none" placeholder="ex.: suporte.ana"></div><div><label for="np">Senha (mínimo 8 caracteres)</label><input id="np" type="password" autocomplete="new-password"></div><button class="primary" onclick="addAdmin()">Criar acesso</button><div id="tmsg"></div>
      <div id="tfaCard" style="margin-top:10px"></div>
      <h2 style="font-size:15px;margin-top:14px">Trocar minha senha</h2><div><label for="cp0">Senha atual</label><input id="cp0" type="password" autocomplete="current-password"></div><div><label for="cp1">Nova senha</label><input id="cp1" type="password" autocomplete="new-password"></div><button class="ghost" onclick="changePw()">Trocar senha</button><div id="pmsg"></div></div></div>`);
  },
};

// ───── ações ─────
async function openOrder(id, tr){
  document.querySelectorAll('tr.sel').forEach(x=>x.classList.remove('sel')); if (tr) tr.classList.add('sel');
  const box=$('odet'); box.innerHTML='<div class="muted">Carregando…</div>';
  let e; try { e = await api('/admin/orders/'+id+'/evidence'); } catch(err){ box.innerHTML=esc(err.message); return; }
  const o=e.order, t=e.trip||{};
  const steps=[['Pedido criado',o.createdAt],['Pago',o.pixPaid?'sim':null],['Saiu para o atendimento',t.departedAt],['Terminou',o.finishedAt],['Liberado ao profissional',o.releasedAt]].filter(x=>x[1]);
  box.innerHTML=`<div class="row" style="justify-content:space-between"><h2 style="font-size:18px">Pedido #${esc(String(o.id).slice(0,6).toUpperCase())}</h2>${o.disputeStatus==='OPEN'?'<span class="pill p-bad">Disputa</span>':st(o.status)}</div>
    <div class="info" style="margin-top:10px"><div><b>Cliente</b>${esc(o.customer&&o.customer.name)}</div><div><b>Profissional</b>${esc(o.professional&&o.professional.name||'—')}</div><div><b>Serviço</b>${esc(o.product||'—')}</div><div><b>Data</b>${day(o.date)}</div><div><b>Cliente pagou</b>${brl(o.totalCost)}${o.pixPaid?' ✓':''}</div><div><b>Profissional recebe</b>${brl(o.serviceValue)}</div>${o.cancelFee?`<div><b>Taxa de km</b>${brl(o.cancelFee)}</div>`:''}${o.clientRefund?`<div><b>Devolvido</b>${brl(o.clientRefund)}</div>`:''}</div>
    ${o.disputeReason?`<div class="msg p-bad" style="margin-top:10px"><b>Problema relatado:</b> ${esc(o.disputeReason)}</div>`:''}
    <h3 style="font-size:14px;margin:14px 0 8px">Linha do tempo</h3><div class="tl">${steps.map(s=>`<div><b>${s[0]}</b><span class="muted">${s[1]==='sim'?'':fmt(s[1])}</span></div>`).join('')}</div>
    ${e.photos.length?`<h3 style="font-size:14px;margin:14px 0 8px">Fotos</h3><div class="row">${e.photos.map(p=>`<a href="/orders/photos/${p.id}" target="_blank"><img src="/orders/photos/${p.id}" alt="Foto ${esc(p.kind)}" style="width:84px;height:84px;object-fit:cover;border-radius:10px"></a>`).join('')}</div>`:''}
    <h3 style="font-size:14px;margin:14px 0 8px">Chamadas (${e.calls.length})</h3><div class="muted">${e.calls.map(c=>fmt(c.createdAt)+' · '+(c.kind==='audio'?'áudio':'vídeo')+' · '+esc(c.status)+(c.seconds?' · '+Math.floor(c.seconds/60)+'min':'')).join('<br>')||'Nenhuma.'}</div>
    <h3 style="font-size:14px;margin:14px 0 8px">Chat</h3><div class="chat">${e.messages.map(m=>`<div class="${m.type==='SYSTEM'?'sys':''}"><b>${esc(m.from)}</b> <span class="muted">${fmt(m.at)}</span><br>${esc(m.text||'')}${m.imageUrl&&String(m.imageUrl).startsWith('data:image')?`<img src="${m.imageUrl}" alt="Foto do chat">`:''}</div>`).join('')||'<span class="muted">Sem mensagens.</span>'}</div>
    <div class="row" style="margin-top:14px">${o.disputeStatus==='OPEN'?`<button class="primary" onclick="go('suporte')">Decidir a disputa</button>`:''}${['OPEN','CONFIRMED','AWAITING_PAYMENT','IN_PROGRESS'].includes(o.status)?`<button class="danger" onclick="adminCancel('${o.id}')">Cancelar pedido</button>`:''}</div>`;
}
async function adminCancel(id){ const note=prompt('Motivo do cancelamento (cliente e profissional não veem este texto):'); if(!note) return; try{ await api('/admin/orders/'+id+'/cancel',{method:'POST',body:JSON.stringify({note})}); alert('Pedido cancelado.'); VIEWS.pedidos(); }catch(e){ alert(e.message); } }

async function openUser(id){
  if (cur!=='usuarios'){ cur='usuarios'; navHtml(); await VIEWS.usuarios(); }
  const box=$('udet'); box.innerHTML='<div class="muted">Carregando…</div>';
  let u; try { u = await api('/admin/users/'+id); } catch(e){ box.innerHTML=esc(e.message); return; }
  const s=u.stats||{}; const pro=(u.roles||[]).includes('PROFESSIONAL');
  box.innerHTML=`<div class="row"><div style="width:58px;height:58px;border-radius:18px;background:linear-gradient(135deg,#1D4ED8,#0891B2);color:#fff;display:flex;align-items:center;justify-content:center;font:700 24px Montserrat">${esc((u.name||'?')[0])}</div><div style="flex:1"><h2 style="font-size:19px">${esc(u.name)}</h2><div class="muted">${pro?'Profissional':'Cliente'} · @${esc(u.username)} · desde ${day(u.createdAt)}</div><div class="row" style="margin-top:6px">${u.verified?'<span class="pill p-blue">Verificado</span>':''}${u.blocked?'<span class="pill p-bad">Bloqueado</span>':'<span class="pill p-ok">Ativo</span>'}</div></div></div>
    <div class="info" style="margin-top:12px"><div><b>E-mail</b>${esc(u.email)}</div><div><b>Telefone</b>${esc(u.phone||'—')}</div>${pro?`<div><b>Serviços feitos</b>${s.servicesDone}</div><div><b>Nota</b>${s.rating?'★ '+s.rating:'—'}</div><div><b>No horário</b>${s.punctuality!=null?s.punctuality+'%':'—'}</div>`:''}<div><b>Pedidos como cliente</b>${s.ordersAsClient}</div><div><b>Cancelamentos</b>${s.cancelsAsClient}</div><div><b>Disputas</b>${s.disputes}</div><div><b>Pontos</b>${u.points}</div></div>
    ${u.blocked?`<div class="msg p-bad" style="margin-top:10px"><b>Motivo do bloqueio:</b> ${esc(u.blockedReason)}</div>`:''}
    ${u.blockedAttempts.length?`<div class="msg p-warn" style="margin-top:10px"><b>${u.blockedAttempts.length} tentativa(s) de passar contato no chat.</b> Última: "${esc(u.blockedAttempts[0].text||'').slice(0,80)}"</div>`:''}
    <h3 style="font-size:14px;margin:14px 0 8px">Ações</h3><div class="row"><button class="primary" onclick="userMsg(${u.id})">Mandar mensagem</button><button class="ghost" onclick="userPoints(${u.id})">Dar pontos</button>${u.blocked?`<button class="ok" onclick="userUnblock(${u.id})">Desbloquear</button>`:`<button class="danger" onclick="userBlock(${u.id})">Bloquear</button>`}</div>
    <h3 style="font-size:14px;margin:14px 0 8px">Últimos pedidos</h3>${u.orders.map(o=>`<div class="row" style="justify-content:space-between;padding:7px 0;border-bottom:1px solid #F1F5F9;font-size:13px"><a href="#" onclick="cur='pedidos';navHtml();VIEWS.pedidos('ALL','','${o.id}');return false">#${esc(String(o.id).slice(0,6).toUpperCase())} · ${esc(o.papel)}</a><span>${day(o.date)} · ${brl(o.totalCost)} · ${st(o.status)}</span></div>`).join('')||'<div class="muted">Nenhum pedido.</div>'}`;
}
async function userMsg(id){ const text=prompt('Mensagem (chega no chat de suporte e no celular):'); if(!text) return; try{ await api('/admin/users/'+id+'/message',{method:'POST',body:JSON.stringify({text})}); alert('Mensagem enviada.'); }catch(e){ alert(e.message); } }
async function userPoints(id){ const amount=prompt('Quantos pontos? (100 pontos = R$ 10; use número negativo para tirar)'); if(!amount) return; const reason=prompt('Motivo (a pessoa vê):','Presente da equipe OPUS')||''; try{ await api('/admin/users/'+id+'/points',{method:'POST',body:JSON.stringify({amount,reason})}); openUser(id); }catch(e){ alert(e.message); } }
async function userBlock(id){ const reason=prompt('Motivo do bloqueio (fica no histórico):'); if(!reason) return; try{ await api('/admin/users/'+id+'/block',{method:'POST',body:JSON.stringify({reason})}); openUser(id); }catch(e){ alert(e.message); } }
async function userUnblock(id){ if(!confirm('Desbloquear esta conta?')) return; try{ await api('/admin/users/'+id+'/unblock',{method:'POST'}); openUser(id); }catch(e){ alert(e.message); } }
async function sosAck(id, close){ const note=($('note-'+id)||{}).value||''; if(close && !confirm('Encerrar este SOS?')) return; try{ await api('/admin/sos/'+id+'/ack',{method:'POST',body:JSON.stringify({note,close})}); refreshCounts(); VIEWS.sos(); }catch(e){ alert(e.message); } }
async function exportCsv(){ try{ const u=await blob('/admin/finance/export.csv'); const a=document.createElement('a'); a.href=u; a.download='opus-financeiro.csv'; a.click(); }catch(e){ alert(e.message); } }
async function delPost(id){ const reason=prompt('Motivo (o profissional é avisado):','Conteúdo impróprio'); if(reason===null) return; try{ await api('/admin/posts/'+id+'?reason='+encodeURIComponent(reason),{method:'DELETE'}); VIEWS.social(); }catch(e){ alert(e.message); } }
async function sendBroadcast(){ const text=$('btxt').value.trim(), audience=$('aud').value; if(!text) return; if(!confirm('Enviar para '+$('aud').selectedOptions[0].text.toLowerCase()+'?')) return; try{ const r=await api('/admin/broadcast',{method:'POST',body:JSON.stringify({text,audience})}); alert('Aviso enviado para '+r.sent+' pessoas.'); VIEWS.avisos(); }catch(e){ $('bmsg').innerHTML='<div class="msg p-bad">'+esc(e.message)+'</div>'; } }
async function reviewCert(id,ok){ const note= ok?'':prompt('Motivo da recusa:')||''; try{ await api('/admin/certificates/'+id+'/review',{method:'POST',body:JSON.stringify({approve:ok,note})}); VIEWS.certs(); }catch(e){ alert(e.message); } }
async function certPhoto(id){
  // mostra a foto aqui mesmo (abrir em nova aba é bloqueado no celular)
  const ov = document.createElement('div');
  ov.style.cssText = 'position:fixed;inset:0;background:rgba(0,0,0,.85);display:flex;align-items:center;justify-content:center;z-index:9999;cursor:zoom-out';
  ov.innerHTML = '<span style="color:#fff">Carregando…</span>'; ov.onclick = () => ov.remove(); document.body.appendChild(ov);
  try{ const u = await blob('/admin/certificates/'+id+'/photo'); ov.innerHTML = '<img src="'+u+'" style="max-width:94vw;max-height:90vh;border-radius:12px">'; }
  catch(e){ ov.innerHTML = '<span style="color:#fff">Não foi possível abrir a foto.</span>'; }
}
async function saveStore(){ try{ await api('/admin/partners/stores',{method:'POST',body:JSON.stringify({name:$('sn').value,category:$('scat').value,whatsapp:$('sw').value,address:$('sa').value,lat:$('slat').value,lng:$('slng').value,couponCode:$('scc').value,couponText:$('sct').value,hours:$('sh').value})}); VIEWS.parceiros(); }catch(e){ $('smsg').innerHTML='<div class="msg p-bad">'+esc(e.message)+'</div>'; } }
async function delStore(id){ if(!confirm('Remover esta loja?')) return; try{ await api('/admin/partners/stores/'+id,{method:'DELETE'}); VIEWS.parceiros(); }catch(e){ alert(e.message); } }
async function savePlan(){ try{ await api('/admin/partners/plans',{method:'POST',body:JSON.stringify({audience:$('pa').value,title:$('pt').value,kind:$('pk').value,partner:$('pp').value,price:$('pv').value,link:$('pl').value,coverage:$('pc').value})}); VIEWS.parceiros(); }catch(e){ $('pmsg').innerHTML='<div class="msg p-bad">'+esc(e.message)+'</div>'; } }
async function delPlan(id){ if(!confirm('Remover este plano?')) return; try{ await api('/admin/partners/plans/'+id,{method:'DELETE'}); VIEWS.parceiros(); }catch(e){ alert(e.message); } }
async function createCoupon(){ try{ await api('/admin/coupons',{method:'POST',body:JSON.stringify({code:$('cc').value,description:$('cd').value,percent:$('cp').value,amount:$('ca').value,minOrder:$('cm').value,maxUses:$('cu').value,validUntil:$('cv').value})}); VIEWS.cupons(); }catch(e){ $('cmsg').innerHTML='<div class="msg p-bad">'+esc(e.message)+'</div>'; } }
async function toggleCoupon(code){ try{ await api('/admin/coupons/'+encodeURIComponent(code)+'/toggle',{method:'PUT'}); VIEWS.cupons(); }catch(e){ alert(e.message); } }
async function saveSettings(){ const body={}; window._settingKeys.forEach(k=>body[k]=$('s-'+k).value); try{ await api('/admin/settings',{method:'PUT',body:JSON.stringify(body)}); $('smsg').innerHTML='<span class="pill p-ok">Salvo</span>'; }catch(e){ $('smsg').innerHTML='<span class="pill p-bad">'+esc(e.message)+'</span>'; } }
async function addAdmin(){ try{ await api('/admin/team',{method:'POST',body:JSON.stringify({login:$('na').value,password:$('np').value})}); VIEWS.equipe(); }catch(e){ $('tmsg').innerHTML='<div class="msg p-bad">'+esc(e.message)+'</div>'; } }
// ───── verificação em duas etapas ─────
async function render2fa(){
  const box=$('tfaCard'); if(!box) return;
  let st={enabled:false}; try{ st=await api('/admin/me/2fa'); }catch(e){}
  box.innerHTML = st.enabled
    ? `<div class="card" style="background:#ECFDF5;border:1px solid #A7F3D0"><h2 style="font-size:16px">🔒 Verificação em duas etapas ligada</h2><div class="muted" style="margin:6px 0 10px">Para entrar no painel é preciso a senha e o código do seu celular. Códigos de emergência restantes: <b>${st.recoveryLeft}</b>.</div>
       <div class="row" style="gap:8px;flex-wrap:wrap"><input id="tfaCode" inputmode="numeric" placeholder="Código atual" style="max-width:150px"><button class="ghost" onclick="tfaNewCodes()">Gerar novos códigos de emergência</button></div>
       <div id="tfaMsg"></div></div>`
    : `<div class="card" style="background:#FFFBEB;border:1px solid #FDE68A"><h2 style="font-size:16px">⚠️ Ligue a verificação em duas etapas (obrigatória)</h2><div class="muted" style="margin:6px 0 10px">Mesmo que alguém descubra a sua senha, não entra sem o código do seu celular. Leva 1 minuto.</div><button class="primary" onclick="tfaStart()">Ligar agora</button><div id="tfaMsg"></div></div>`;
}
async function tfaStart(){
  const box=$('tfaCard'); let r; try{ r=await api('/admin/me/2fa/setup',{method:'POST'}); }catch(e){ $('tfaMsg').innerHTML='<div class="msg p-bad">'+esc(e.message)+'</div>'; return; }
  box.innerHTML=`<div class="card" style="display:flex;flex-direction:column;gap:10px"><h2 style="font-size:16px">Ligar a verificação em duas etapas</h2>
    <div><b>1.</b> Instale no celular o <b>Google Authenticator</b> (grátis, na Play Store ou App Store).</div>
    <div><b>2.</b> No Google Authenticator, toque em <b>+</b> e em <b>Ler código QR</b>. Aponte para este código:</div>
    <div id="tfaQr" style="background:#fff;padding:12px;border-radius:12px;align-self:flex-start"></div>
    <div class="muted" style="font-size:12.5px">Está no celular e não consegue ler? No Google Authenticator escolha "Inserir chave de configuração" e digite: <b style="font-family:monospace;letter-spacing:1px">${esc(r.secret)}</b></div>
    <div><b>3.</b> Digite o código de 6 dígitos que apareceu no Google Authenticator:</div>
    <div class="row" style="gap:8px"><input id="tfaFirst" inputmode="numeric" maxlength="6" placeholder="000000" style="max-width:150px;font-size:20px;letter-spacing:4px;text-align:center"><button class="primary" onclick="tfaEnable()">Confirmar e ligar</button></div><div id="tfaMsg"></div></div>`;
  try{ new QRCode($('tfaQr'), {text:r.uri, width:180, height:180}); }catch(e){ $('tfaQr').innerHTML='<div class="muted">Use a chave escrita abaixo.</div>'; }
}
function showRecovery(codes, title){
  $('tfaCard').innerHTML=`<div class="card" style="background:#EFF6FF;border:1px solid #BFDBFE;display:flex;flex-direction:column;gap:10px"><h2 style="font-size:16px">${title}</h2>
    <div><b>Guarde estes códigos de emergência</b> num lugar seguro (anote em papel ou tire um print e guarde fora do celular). Cada um funciona <b>uma vez só</b>, se você perder o celular.</div>
    <div style="display:grid;grid-template-columns:repeat(2,1fr);gap:6px;font-family:monospace;font-size:17px;background:#fff;padding:12px;border-radius:10px">${codes.map(c=>'<span>'+esc(c)+'</span>').join('')}</div>
    <div class="muted">Eles não vão aparecer de novo.</div><button class="ghost" onclick="forced2fa?location.reload():render2fa()">Já guardei</button></div>`;
}
async function tfaEnable(){ try{ const r=await api('/admin/me/2fa/enable',{method:'POST',body:JSON.stringify({code:$('tfaFirst').value})}); showRecovery(r.recoveryCodes,'🔒 Pronto! Verificação em duas etapas ligada'); }catch(e){ $('tfaMsg').innerHTML='<div class="msg p-bad">'+esc(e.message)+'</div>'; } }
async function tfaNewCodes(){ try{ const r=await api('/admin/me/2fa/recovery',{method:'POST',body:JSON.stringify({code:$('tfaCode').value})}); showRecovery(r.recoveryCodes,'Novos códigos de emergência'); }catch(e){ $('tfaMsg').innerHTML='<div class="msg p-bad">'+esc(e.message)+'</div>'; } }
async function tfaDisable(){ if(!confirm('Desligar a verificação em duas etapas deixa o painel menos protegido. Continuar?')) return; try{ await api('/admin/me/2fa/disable',{method:'POST',body:JSON.stringify({code:$('tfaCode').value,password:$('tfaPw').value})}); render2fa(); }catch(e){ $('tfaMsg').innerHTML='<div class="msg p-bad">'+esc(e.message)+'</div>'; } }
async function resetTeam2fa(id){ const code=prompt('Para zerar a verificação dessa pessoa, digite o SEU código de 6 dígitos:'); if(!code) return; try{ await api('/admin/team/'+id+'/2fa-reset',{method:'POST',body:JSON.stringify({code})}); go('equipe'); }catch(e){ alert(e.message); } }
async function docsPerm(id, allow, el){ try{ await api('/admin/team/'+id+'/docs',{method:'PUT',body:JSON.stringify({allow})}); }catch(e){ alert(e.message); el.checked=!allow; } }
async function nag2fa(){ try{ const st=await api('/admin/me/2fa'); if(st.enabled||$('tfaNag')) return; const v=document.querySelector('main'); v.insertAdjacentHTML('afterbegin',`<div id="tfaNag" class="msg" style="background:#FFFBEB;border:1px solid #FDE68A;color:#92400E;margin:10px 16px 0;display:flex;gap:10px;align-items:center;flex-wrap:wrap"><span>🔒 <b>Proteja o painel:</b> ligue a verificação em duas etapas.</span><button class="primary" style="margin-left:auto" onclick="this.parentNode.remove();go('equipe')">Ligar agora</button></div>`); }catch(e){} }

async function changePw(){ try{ await api('/admin/me/password',{method:'POST',body:JSON.stringify({current:$('cp0').value,password:$('cp1').value})}); $('pmsg').innerHTML='<div class="msg p-ok">Senha trocada.</div>'; $('cp0').value=$('cp1').value=''; }catch(e){ $('pmsg').innerHTML='<div class="msg p-bad">'+esc(e.message)+'</div>'; } }
async function removeAdmin(id){ if(!confirm('Tirar o acesso desta pessoa ao painel?')) return; try{ await api('/admin/team/'+id,{method:'DELETE'}); VIEWS.equipe(); }catch(e){ alert(e.message); } }

function adminName(){ return 'Administrador'; }
document.addEventListener('keydown', e => { if (e.key==='Enter' && e.target && e.target.id==='gsearch') { const q=e.target.value.trim(); if(!q) return; cur='pedidos'; location.hash='pedidos'; navHtml(); VIEWS.pedidos('ALL', q); } });
async function whoAmI(){ try{ const m=await api('/admin/me'); if(m.matricula){ $('meNm').textContent='Matrícula '+m.matricula; $('meAv').textContent=m.matricula; } }catch(e){} }
// A verificação em duas etapas é obrigatória: sem ela o painel só mostra a tela para ligar.
let forced2fa = false;
function force2fa(){ forced2fa = true; $('loginBox').classList.add('hidden'); $('app').classList.remove('hidden'); $('nav').innerHTML='';
  V(`<div style="max-width:620px;margin:20px auto;display:flex;flex-direction:column;gap:12px">${head('Falta um passo para entrar','Para proteger os dados dos clientes, todo acesso ao painel precisa da verificação em duas etapas (Google Authenticator).')}<div id="tfaCard"></div></div>`);
  render2fa(); }
async function start(){ try{ const st=await api('/admin/me/2fa'); if(!st.enabled){ force2fa(); return; } }catch(e){ if(e && e.need2fa){ force2fa(); return; } }
  setTimeout(nag2fa, 1500); whoAmI(); $('loginBox').classList.add('hidden'); $('app').classList.remove('hidden'); const h=(location.hash||'').slice(1); cur = VIEWS[h] ? h : 'inicio'; go(cur); refreshCounts(); setInterval(refreshCounts, 20000); }
window.__boot = () => { if (token) start(); else { $('loginBox').classList.remove('hidden'); checkSetup(); } };

;
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
  // Celular: o menu lateral vira uma gaveta que abre no botão ☰
  window.toggleMenu = function (open) {
    const a = document.querySelector('aside'); if (!a) return;
    const on = open === undefined ? !a.classList.contains('open') : open;
    a.classList.toggle('open', on);
    let sh = $('menuShade');
    if (!sh) { document.body.insertAdjacentHTML('beforeend', '<div id="menuShade" class="menushade" onclick="toggleMenu(false)"></div>'); sh = $('menuShade'); }
    sh.classList.toggle('on', on);
  };
  const st = document.createElement('style');
  st.textContent = `.menubtn{display:none}
  @media(max-width:1000px){.menubtn{display:flex}aside{display:flex!important;position:fixed!important;left:0;top:0;bottom:0;z-index:60;transform:translateX(-105%);transition:transform .22s ease;box-shadow:none}aside.open{transform:none;box-shadow:0 0 40px rgba(0,0,0,.35)}
  .menushade{position:fixed;inset:0;background:rgba(2,6,23,.45);z-index:55;display:none}.menushade.on{display:block}
  .topbar .ask{min-width:0;flex:1}.topbar .ask input{min-width:0}.topbar .kbd{display:none}.topbar .tbtn.wide span{display:none}.topbar>div[style*="flex:1"]{display:none}.topbar{gap:8px}}`;
  document.head.appendChild(st);
  if (typeof go === 'function') { const g = go; window.go = function (k) { toggleMenu(false); return g(k); }; }

  function topbar() {
    if ($('topbar')) return;
    const main = document.querySelector('main');
    main.insertAdjacentHTML('afterbegin', `<div id="topbar" class="topbar">
      <button class="tbtn menubtn" id="menuBtn" aria-label="Abrir o menu" onclick="toggleMenu()"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M4 6h16M4 12h16M4 18h16"/></svg></button>
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
  const NK = { SOS: ['alert', '#FEE2E2', '#B91C1C'], DISPUTE: ['clock', '#FEE2E2', '#B91C1C'], VERIFY: ['shield', '#FEF3C7', '#B45309'], SUPPORT: ['chat', '#E0F2FE', '#075985'], WITHDRAW: ['card', '#EFF6FF', '#1D4ED8'], CERT: ['award', '#EDE9FE', '#5B21B6'], SECURITY: ['shield', '#FEE2E2', '#B91C1C'] };
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
