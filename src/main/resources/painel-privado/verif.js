const STATUS = {PENDING:'Em análise',APPROVED:'Aprovada',REJECTED:'Recusada'};
const $ = id => document.getElementById(id);
let token = null, current = null, filter = 'PENDING';
try { token = sessionStorage.getItem('opus_admin_token'); } catch(e) {}

async function api(path, opts={}) {
  const r = await fetch(path, {...opts, headers:{'Content-Type':'application/json', ...(token?{Authorization:'Bearer '+token}:{}), ...(opts.headers||{})}});
  const txt = await r.text(); let data; try { data = JSON.parse(txt); } catch(e) { data = txt; }
  if (r.status === 403 && data && data.message) throw new Error(data.message); // sem permissão para documentos
  if (r.status === 401 || r.status === 403) { if (path !== '/auth') { logout(); throw new Error('Sessão expirada ou sem permissão de administrador.'); } }
  if (!r.ok) throw new Error((data && data.message) || 'Erro '+r.status);
  return data;
}
function show(id){ ['loginBox','listBox','detailBox'].forEach(x=>$(x).classList.toggle('hidden', x!==id)); $('logout').classList.toggle('hidden', id==='loginBox'); }
function msg(el, text, ok){ el.textContent=text; el.className='msg '+(ok?'APPROVED':'REJECTED'); }
function fmt(d){ if(!d) return '—'; try { return new Date(d).toLocaleString('pt-BR'); } catch(e){ return d; } }
function logout(){ token=null; try{sessionStorage.removeItem('opus_admin_token');}catch(e){} show('loginBox'); }

$('doLogin').onclick = async () => {
  $('loginMsg').classList.add('hidden');
  try {
    const t = await api('/auth', {method:'POST', body: JSON.stringify({username:$('u').value.trim(), password:$('p').value})});
    token = typeof t === 'string' ? t : (t.token || '');
    try { sessionStorage.setItem('opus_admin_token', token); } catch(e) {}
    await loadList();
  } catch(e) { msg($('loginMsg'), e.message || 'Não foi possível entrar.'); }
};
$('p').addEventListener('keydown', e => { if (e.key === 'Enter') $('doLogin').click(); });
$('logout').onclick = logout;
$('back').onclick = () => loadList();
document.querySelectorAll('.tab').forEach(b => b.onclick = () => { document.querySelectorAll('.tab').forEach(x=>x.classList.remove('on')); b.classList.add('on'); filter=b.dataset.s; loadList(); });
$('zoom').onclick = () => $('zoom').style.display='none';

async function loadReserve(){
  try {
    const r = await api('/admin/points-reserve');
    $('resValue').textContent = Number(r.reserveValue||0).toLocaleString('pt-BR',{style:'currency',currency:'BRL'});
    $('resPts').textContent = (r.pointsOutstanding||0).toLocaleString('pt-BR');
    $('resGU').textContent = (r.pointsGiven||0).toLocaleString('pt-BR') + ' / ' + (r.pointsUsed||0).toLocaleString('pt-BR');
  } catch(e) {}
}
async function loadList(){
  loadReserve();
  show('listBox');
  const rows = $('rows'); rows.innerHTML='';
  let items = [];
  try { items = await api('/verifications?status='+filter); } catch(e) { $('empty').textContent = e.message; $('empty').classList.remove('hidden'); return; }
  $('empty').textContent = 'Nenhuma verificação aqui.';
  $('empty').classList.toggle('hidden', items.length>0);
  for (const v of items) {
    const tr = document.createElement('tr');
    const cells = [v.name||'—', v.isProfessional?'Profissional':'Cliente', v.docType||'—', fmt(v.submittedAt)];
    cells.forEach(c => { const td=document.createElement('td'); td.textContent=c; tr.appendChild(td); });
    const st=document.createElement('td'); const sp=document.createElement('span'); sp.className='pill '+v.status; sp.textContent=STATUS[v.status]||v.status; st.appendChild(sp); tr.appendChild(st);
    const act=document.createElement('td'); const b=document.createElement('button'); b.className='primary'; b.textContent='Abrir'; b.onclick=()=>openDetail(v.id); act.appendChild(b); tr.appendChild(act);
    rows.appendChild(tr);
  }
}

function addInfo(label, value){ const d=document.createElement('div'); const b=document.createElement('b'); b.textContent=label; d.appendChild(b); d.appendChild(document.createTextNode(value||'—')); $('dInfo').appendChild(d); }
function addImg(src, title, sub, extra){
  const f=document.createElement('figure'); if (extra) f.classList.add(extra);
  if (src) { const i=document.createElement('img'); i.src=src; i.alt=title; i.onclick=()=>{ $('zoomImg').src=src; $('zoom').style.display='flex'; }; f.appendChild(i); }
  else { const p=document.createElement('div'); p.style.cssText='height:260px;display:flex;align-items:center;justify-content:center;color:#94A3B8'; p.textContent='Sem imagem (recusada para reenvio)'; f.appendChild(p); }
  const c=document.createElement('figcaption'); c.textContent=title; if (sub){ const s=document.createElement('small'); s.textContent=sub; c.appendChild(s);} f.appendChild(c); $('imgs').appendChild(f);
}

async function openDetail(id){
  show('detailBox'); $('actMsg').classList.add('hidden'); $('reason').value='';
  $('imgs').innerHTML=''; $('dInfo').innerHTML=''; $('dName').textContent='Carregando…';
  let v; try { v = await api('/verifications/'+id); } catch(e) { $('dName').textContent=e.message; return; }
  current = v;
  $('dName').textContent = v.name || 'Usuário #'+v.userId;
  $('dStatus').className='pill '+v.status; $('dStatus').textContent=STATUS[v.status]||v.status;
  addInfo('Tipo de conta', v.isProfessional?'Profissional':'Cliente');
  addInfo('CPF (cadastro)', v.cpf); addInfo('Nascimento (cadastro)', v.birthday); addInfo('E-mail', v.email);
  addInfo('Documento', v.docType); addInfo('Enviado em', fmt(v.submittedAt)); addInfo('Termo aceito em', fmt(v.consentAt));
  addInfo('Prova de vida no celular', v.livenessPassed ? 'Concluída (virou a cabeça e piscou)' : 'Não concluída');
  if (v.livenessInfo) addInfo('Medições da prova de vida', v.livenessInfo);
  if (v.status!=='PENDING') { addInfo('Analisado por', v.reviewedBy); addInfo('Analisado em', fmt(v.reviewedAt)); if (v.reason) addInfo('Motivo', v.reason); }
  addImg(v.docFront, 'Documento · frente', 'Confira nome, CPF e data de nascimento');
  addImg(v.docBack, 'Documento · verso');
  addImg(v.selfieNeutral, 'Selfie', 'Compare com a foto do documento');
  addImg(v.selfieTurnA, 'Prova de vida · lado 1', 'Rosto virado para um lado', 'gesture');
  addImg(v.selfieTurnB, 'Prova de vida · lado 2', 'Rosto virado para o outro lado', 'gesture');
  addImg(v.selfieWithDoc, 'Selfie segurando o documento');
  if (v.quality) {
    const names = {docFront:'Frente', docBack:'Verso', selfie:'Selfie', selfieWithDoc:'Selfie com documento'};
    const parts = Object.keys(names).filter(k => v.quality[k]).map(k => names[k] + ': ' + (v.quality[k].ok ? 'boa' : (v.quality[k].issues||[]).join(' ')));
    addInfo('Checagem automática das fotos', parts.join(' · '));
  }
  $('actions').classList.toggle('hidden', v.status!=='PENDING');
  // Conferência lado a lado: o que foi digitado no cadastro x o que está no documento
  const cpf = String(v.cpf||'').replace(/\D/g,'');
  $('cName').textContent = v.name || '—';
  $('cCpf').textContent = cpf.length===11 ? cpf.slice(0,3)+'.'+cpf.slice(3,6)+'.'+cpf.slice(6,9)+'-'+cpf.slice(9) : (v.cpf||'—');
  const b = v.birthday ? String(v.birthday).slice(0,10).split('-') : null;
  $('cBirth').textContent = b && b.length===3 ? b[2]+'/'+b[1]+'/'+b[0] : 'não informado';
  if (b && b.length===3) { const d=new Date(+b[0],+b[1]-1,+b[2]), n=new Date(); let a=n.getFullYear()-d.getFullYear(); if (n<new Date(n.getFullYear(),d.getMonth(),d.getDate())) a--; $('cAge').textContent='('+a+' anos)'; } else $('cAge').textContent='';
  document.querySelectorAll('#cmpBox .chk').forEach(c => { c.checked=false; });
  $('approve').disabled = true;
}
document.querySelectorAll('#cmpBox .chk').forEach(c => c.addEventListener('change', () => {
  $('approve').disabled = [...document.querySelectorAll('#cmpBox .chk')].some(x => !x.checked);
}));

$('approve').onclick = async () => {
  if (!current) return;
  const checks = {}; document.querySelectorAll('#cmpBox .chk').forEach(c => { checks[c.value] = c.checked; });
  try { await api('/verifications/'+current.id+'/approve', {method:'PUT', body: JSON.stringify({checks})}); msg($('actMsg'),'Verificação aprovada.',true); setTimeout(loadList, 900); }
  catch(e){ msg($('actMsg'), e.message); }
};
$('reject').onclick = async () => {
  if (!current) return;
  const steps=[...document.querySelectorAll('#steps input:checked')].map(i=>i.value);
  try { await api('/verifications/'+current.id+'/reject', {method:'PUT', body: JSON.stringify({reason:$('reason').value, steps})}); msg($('actMsg'),'Verificação recusada. O usuário verá o motivo no app.',true); setTimeout(loadList, 900); }
  catch(e){ msg($('actMsg'), e.message); }
};

if (token) loadList(); else show('loginBox');
