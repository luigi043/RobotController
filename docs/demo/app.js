(() => {
 'use strict';
 const $=id=>document.getElementById(id),commands=[...document.querySelectorAll('[data-command]')];
 let connected=false,auto=false,motion='S',colour='Off',x=50,y=50,angle=0,last=0,lines=[],held=null,pulse;
 const names={F:'Forward',B:'Backward',L:'Turning left',R:'Turning right',S:'Stopped'};
 function log(text){lines.unshift(`${new Date().toLocaleTimeString('en-GB')}  ${text}`);lines=lines.slice(0,80);$('log').textContent=lines.join('\n')||'No commands yet.'}
 function render(){commands.forEach(b=>b.disabled=!connected);$('connect').disabled=connected;$('disconnect').disabled=!connected;$('loss').disabled=!connected;$('status').className=`status ${connected?'connected':''}`;$('status').textContent=connected?'Demo connected':'Disconnected';$('motion').textContent=names[motion];$('colour').textContent=colour;$('auto-state').textContent=auto?'On (simulated)':'Off';$('auto').setAttribute('aria-pressed',String(auto));$('robot').style.setProperty('--led',{Red:'#ff796b',Blue:'#77baff',Green:'#80ffbb',Off:'#213539'}[colour]);$('overlay').hidden=connected;}
 function stop(){clearTimeout(pulse);held=null;if(connected)send('S');else motion='S'}
 function send(command){if(!connected)return;log(`TX ${command}\\n  →  SIM OK: ${command}`);if(command in names){motion=command;if(command==='S')auto=false;}if(command==='AUTO'){auto=!auto;motion='S';}if(['RED','BLUE','GREEN'].includes(command))colour=command[0]+command.slice(1).toLowerCase();render()}
 function disconnect(lost=false){stop();connected=false;auto=false;motion='S';render();$('help').textContent=lost?'Simulated link lost. Reconnect the demo to continue.':'Disconnected. Connect the demo to start a new session.';if(lost){$('status').textContent='Connection error';$('status').className='status error'}log(lost?'SIM: connection lost. Controls disabled.':'SIM: disconnected.')}
 $('connect').onclick=()=>{connected=true;auto=false;motion='S';$('help').textContent='Demo connected. Commands and responses are simulated in this browser.';log('SIM: connected. No hardware in use.');render()};
 $('disconnect').onclick=()=>disconnect();$('loss').onclick=()=>disconnect(true);$('clear').onclick=()=>{lines=[];$('log').textContent='No commands yet.'};
 $('reset').onclick=()=>{stop();connected=false;auto=false;motion='S';colour='Off';x=50;y=50;angle=0;lines=[];$('log').textContent='No commands yet.';$('help').textContent='Connect the demo. No permission or hardware is needed.';render()};
 commands.forEach(button=>{const cmd=button.dataset.command;if(['F','B','L','R'].includes(cmd)){
   button.onpointerdown=e=>{if(!connected)return;e.preventDefault();stop();held=e.pointerId;button.setPointerCapture(e.pointerId);send(cmd)};
   button.onpointerup=e=>{if(held===e.pointerId)stop()};button.onpointercancel=stop;button.onlostpointercapture=()=>{if(held!==null)stop()};
   button.onclick=e=>{if(e.detail===0&&connected){send(cmd);clearTimeout(pulse);pulse=setTimeout(stop,150)}};
 }else button.onclick=()=>{if(cmd==='S')stop();else send(cmd)}});
 const keys={ArrowUp:'F',ArrowDown:'B',ArrowLeft:'L',ArrowRight:'R'};let key=null;
 document.addEventListener('keydown',e=>{if(!connected)return;if(e.code==='Space'){e.preventDefault();key=null;stop()}else if(keys[e.key]){e.preventDefault();if(e.repeat)return;key=e.key;send(keys[e.key])}});
 document.addEventListener('keyup',e=>{if(e.key===key){e.preventDefault();key=null;stop()}});
 window.addEventListener('blur',()=>{key=null;stop()});document.addEventListener('visibilitychange',()=>{if(document.hidden){key=null;stop()}});
 function frame(now){const dt=Math.min((now-last)||0,40)/1000;last=now;if(connected){if(auto){angle+=dt*45;x=50+20*Math.cos(now/2000);y=50+20*Math.sin(now/2000)}else if(motion==='L'||motion==='R')angle+=dt*(motion==='L'?-120:120);else if(motion==='F'||motion==='B'){const distance=dt*(motion==='F'?18:-18),r=angle*Math.PI/180;x=Math.max(8,Math.min(92,x+Math.sin(r)*distance));y=Math.max(20,Math.min(80,y-Math.cos(r)*distance))}}$('robot').style.left=x+'%';$('robot').style.top=y+'%';$('robot').style.transform=`rotate(${angle}deg)`;requestAnimationFrame(frame)}
 render();requestAnimationFrame(frame);
})();
