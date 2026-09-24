(()=>{
 'use strict';
 const C=window.Chuseok,A=window.ChuseokArt,$=id=>document.getElementById(id),canvas=$('game'),ctx=canvas.getContext('2d'),game=new C.Game();
 const shell=$('shell'),keys=new Set(),art={},layouts=new Map();
 const colors=A.colors;
 // Source rectangles refer only to complete, alpha-transparent, isolated artwork.
 const sprites=A.sprites;
 let ready=false,pointer=null,last=0,raf=0,lastStatus=-1,muted=false,audioStarted=false,creditReturn=null;
 const bgm=$('bgm'),clearAudio=$('clearAudio'),failAudio=$('failAudio'),sounds=[bgm,clearAudio,failAudio];
 bgm.volume=.32;bgm.preservesPitch=true;clearAudio.volume=.5;failAudio.volume=.45;
 try{muted=localStorage.getItem('chuseok-muted')==='true';}catch{}
 function updateSound(){sounds.forEach(a=>a.muted=muted);for(const id of ['soundBtn','portraitSound']){$(id).setAttribute('aria-pressed',String(muted));$(id).setAttribute('aria-label',muted?'소리 켜기':'소리 끄기');}}
 updateSound();
 function playAudio(a){if(muted)return;const p=a.play();if(p)p.catch(()=>{$('soundBtn').title='소리 버튼을 눌러 재생해주세요';});}
 function stopAudio(){for(const a of sounds){a.pause();a.currentTime=0;}bgm.playbackRate=1;}
 function bgmRate(t){if(t<10)return 1;if(t<20)return 1.05;if(t<30)return 1.12;return 1.22;}
 function syncBgmTempo(){const rate=bgmRate(game.elapsed);if(bgm.playbackRate!==rate)bgm.playbackRate=rate;}
 function resetInput(){keys.clear();if(pointer&&canvas.hasPointerCapture(pointer.id))canvas.releasePointerCapture(pointer.id);pointer=null;game.player.moving=false;}
 function screen(name){for(const id of ['menu','help','result'])$(id).classList.add('hidden');$('playControls').classList.toggle('hidden',name!=='play');if(['menu','help','clear','fail'].includes(name))$(name==='clear'||name==='fail'?'result':name).classList.remove('hidden');shell.dataset.screen=name;const primary=$('portraitPrimary'),secondary=$('portraitSecondary');primary.textContent=name==='play'?'일시정지':name==='clear'||name==='fail'?'다시하기':'게임 시작';primary.onclick=name==='play'?()=>game.paused?resume():pause():start;secondary.textContent=name==='menu'?'게임 설명':'처음으로';secondary.onclick=name==='menu'?()=>$('helpBtn').click():home;}
 function start(){if(!ready)return;resetInput();game.reset();stopAudio();syncBgmTempo();audioStarted=true;playAudio(bgm);$('pausePanel').classList.add('hidden');$('creditsPanel').classList.add('hidden');screen('play');last=performance.now();lastStatus=-1;canvas.focus({preventScroll:true});if(!raf)raf=requestAnimationFrame(frame);draw();}
 function home(){game.mode='menu';resetInput();stopAudio();$('pausePanel').classList.add('hidden');screen('menu');$('startBtn').focus({preventScroll:true});}
 function pause(reason){if(game.mode!=='play'||game.paused)return;game.paused=true;resetInput();bgm.pause();$('pauseReason').textContent=reason||'시간도 잔소리도 잠시 멈췄어요.';$('pausePanel').classList.remove('hidden');$('resumeBtn').focus({preventScroll:true});}
 function resume(){if(game.mode!=='play')return;game.paused=false;last=performance.now();syncBgmTempo();$('pausePanel').classList.add('hidden');canvas.focus({preventScroll:true});playAudio(bgm);}
 function finish(){resetInput();stopAudio();$('result').className='screen art-screen '+game.mode;$('scoreValue').textContent=C.scoreFor(game.elapsed);$('timeValue').textContent=C.formatTime(game.elapsed);$('timeLabel').textContent=game.mode==='clear'?'생존 시간':'버틴 시간';screen(game.mode);$('retryBtn').focus({preventScroll:true});playAudio(game.mode==='clear'?clearAudio:failAudio);}
 $('startBtn').onclick=start;$('helpStartBtn').onclick=start;$('retryBtn').onclick=start;$('homeBtn').onclick=home;$('pauseHomeBtn').onclick=home;
 $('helpBtn').onclick=()=>{screen('help');$('helpStartBtn').focus({preventScroll:true});};$('closeHelp').onclick=home;
 $('pauseBtn').onclick=()=>pause();$('resumeBtn').onclick=resume;
 $('soundBtn').onclick=()=>{muted=!muted;updateSound();try{localStorage.setItem('chuseok-muted',String(muted));}catch{}if(!muted&&audioStarted&&game.mode==='play'&&!game.paused)playAudio(bgm);};
 $('creditsBtn').onclick=()=>{creditReturn=document.activeElement;$('creditsPanel').classList.remove('hidden');$('closeCredits').focus({preventScroll:true});};
 $('closeCredits').onclick=()=>{$('creditsPanel').classList.add('hidden');creditReturn?.focus({preventScroll:true});};
 $('portraitSound').onclick=()=>$('soundBtn').click();$('portraitCredits').onclick=()=>{if(game.mode==='play')pause();$('creditsBtn').click();};
 const moveCodes=['ArrowUp','ArrowDown','ArrowLeft','ArrowRight','KeyW','KeyA','KeyS','KeyD'];
 addEventListener('keydown',e=>{
  if(e.code==='Tab'){const modal=['creditsPanel','pausePanel'].map($).find(x=>!x.classList.contains('hidden'));if(modal){const nodes=[...modal.querySelectorAll('button,a')],first=nodes[0],end=nodes.at(-1);if(e.shiftKey&&document.activeElement===first){e.preventDefault();end.focus();}else if(!e.shiftKey&&document.activeElement===end){e.preventDefault();first.focus();}}}
  if(game.mode!=='play')return;
  if(moveCodes.includes(e.code)){e.preventDefault();if(!game.paused)keys.add(e.code);}
  if(e.code==='KeyP'&&!e.repeat){e.preventDefault();game.paused?resume():pause();}
 });
 addEventListener('keyup',e=>keys.delete(e.code));
 addEventListener('blur',()=>{resetInput();pause('창을 벗어나 잠시 멈췄어요. 준비되면 계속하세요.');});
 document.addEventListener('visibilitychange',()=>{if(document.hidden){pause('다른 화면을 보는 동안 자동으로 멈췄어요.');sounds.forEach(a=>a.pause());}});
 function point(e){const r=canvas.getBoundingClientRect();return{x:(e.clientX-r.left)*C.W/r.width,y:(e.clientY-r.top)*C.H/r.height};}
 canvas.addEventListener('pointerdown',e=>{if(game.mode!=='play'||game.paused||pointer||e.button>0)return;const p=point(e);pointer={id:e.pointerId,start:p,current:p,player:{x:game.player.x,y:game.player.y}};canvas.setPointerCapture(e.pointerId);e.preventDefault();});
 canvas.addEventListener('pointermove',e=>{if(pointer?.id===e.pointerId)pointer.current=point(e);});
 for(const event of ['pointerup','pointercancel','lostpointercapture'])canvas.addEventListener(event,e=>{if(pointer?.id===e.pointerId)pointer=null;});
 function input(){if(pointer)return{target:{x:C.clamp(pointer.player.x+pointer.current.x-pointer.start.x,C.BOUNDS.left,C.BOUNDS.right),y:C.clamp(pointer.player.y+pointer.current.y-pointer.start.y,C.BOUNDS.top,C.BOUNDS.bottom)}};return{x:Number(keys.has('ArrowRight')||keys.has('KeyD'))-Number(keys.has('ArrowLeft')||keys.has('KeyA')),y:Number(keys.has('ArrowDown')||keys.has('KeyS'))-Number(keys.has('ArrowUp')||keys.has('KeyW'))};}
 function rect(x,y,w,h,r,fill,stroke,width=2){ctx.beginPath();ctx.roundRect(x,y,w,h,r);if(fill){ctx.fillStyle=fill;ctx.fill();}if(stroke){ctx.strokeStyle=stroke;ctx.lineWidth=width;ctx.stroke();}}
 function sprite(index,x,y,w,h,flip=false){const [sx,sy,sw,sh]=sprites[index],scale=Math.min(w/sw,h/sh),dw=sw*scale,dh=sh*scale;ctx.save();ctx.translate(x,y);if(flip)ctx.scale(-1,1);ctx.drawImage(art.characters,sx,sy,sw,sh,-dw/2,-dh/2,dw,dh);ctx.restore();}
 function shadow(x,y,w){ctx.fillStyle='#66422626';ctx.beginPath();ctx.ellipse(x,y,w,7,0,0,Math.PI*2);ctx.fill();}
 function relative(i){const r=game.relative(i),warning=game.pending.some(w=>w.id===i),bounce=warning?Math.sin(game.elapsed*30)*2:0;shadow(r.x,r.y+65,47);sprite(i,r.x,r.y+bounce,133,137);rect(r.x-44,r.y+65,88,24,12,'#fff8ebed',colors[i]);ctx.font='800 16px "Malgun Gothic",sans-serif';ctx.textAlign='center';ctx.textBaseline='middle';ctx.fillStyle='#583b29';ctx.fillText(r.name,r.x,r.y+77);if(warning){ctx.font='900 34px sans-serif';ctx.lineWidth=5;ctx.strokeStyle='#fff9e9';ctx.strokeText('!!',r.x+53,r.y-59);ctx.fillStyle='#e34527';ctx.fillText('!!',r.x+53,r.y-59);}}
 function bubble(b,impact=false){A.bubble(ctx,b,layouts.get(b.text),impact);}
 function heart(x,y,full){ctx.save();ctx.translate(x,y);ctx.beginPath();ctx.moveTo(0,12);ctx.bezierCurveTo(-30,-9,-30,-31,-13,-31);ctx.bezierCurveTo(-5,-31,0,-25,0,-22);ctx.bezierCurveTo(0,-25,5,-31,13,-31);ctx.bezierCurveTo(30,-31,30,-9,0,12);const g=ctx.createLinearGradient(0,-30,0,12);g.addColorStop(0,full?'#ff8190':'#e5dccb');g.addColorStop(1,full?'#e8334d':'#c7baa6');ctx.fillStyle=g;ctx.lineWidth=3;ctx.strokeStyle='#693d2c';ctx.fill();ctx.stroke();if(full){ctx.beginPath();ctx.arc(-11,-21,5,Math.PI,Math.PI*1.8);ctx.strokeStyle='#fff8';ctx.lineWidth=3;ctx.stroke();}ctx.restore();}
 function hud(){ctx.save();rect(18,14,237,59,22,'#fffaf0f2','#a98254',2);for(let i=0;i<3;i++)heart(53+i*58,50,i<game.hearts);ctx.font='800 13px "Malgun Gothic",sans-serif';ctx.fillStyle='#876144';ctx.textAlign='center';ctx.fillText('체력',222,46);
  rect(378,14,524,60,22,'#fffaf0f5','#a98254',2);ctx.font='800 17px "Malgun Gothic",sans-serif';ctx.fillStyle=game.elapsed>=30?'#c74623':'#69432a';ctx.textAlign='left';ctx.fillText(game.elapsed>=30?'잔소리 폭주!':game.elapsed>=20?'잔소리가 빨라져요!':'명절 생존',398,40);ctx.textAlign='right';ctx.fillText(`${Math.ceil(C.DURATION-game.elapsed)}초 / 40초`,880,40);rect(399,50,480,11,6,'#e1cfad',null);const progress=480*game.elapsed/C.DURATION;if(progress>0)rect(399,50,progress,11,Math.min(6,progress/2),game.elapsed>=30?'#d96235':'#32975b',null);
  rect(922,14,190,59,22,'#fffaf0f2','#a98254',2);ctx.font='900 24px "Malgun Gothic",sans-serif';ctx.fillStyle='#bc7e1b';ctx.textAlign='left';ctx.fillText('★',940,51);ctx.fillStyle='#4c3020';ctx.textAlign='right';ctx.fillText(`${C.scoreFor(game.elapsed)}점`,1095,52);ctx.restore();}
 function draw(){if(!ready)return;const sx=canvas.width/C.W,sy=canvas.height/C.H;ctx.setTransform(sx,0,0,sy,0,0);ctx.clearRect(0,0,C.W,C.H);ctx.drawImage(art.room,0,0,C.W,C.H);C.RELATIVES.forEach((_,i)=>relative(i));game.projectiles.forEach(b=>bubble(b));game.impacts.forEach(b=>bubble(b,true));const p=game.player;shadow(p.x,p.y+57,31);A.player(ctx,p,game.elapsed,art.player.frames);if(game.hitFlash>0){ctx.strokeStyle='#e4403670';ctx.lineWidth=4;ctx.beginPath();ctx.arc(p.x,p.y,62+(1-game.hitFlash/.3)*15,0,Math.PI*2);ctx.stroke();}hud();}
 function status(){const tenth=Math.floor(game.elapsed*10);if(tenth===lastStatus)return;lastStatus=tenth;canvas.dataset.elapsed=game.elapsed.toFixed(3);canvas.dataset.playerX=game.player.x.toFixed(1);canvas.dataset.playerY=game.player.y.toFixed(1);canvas.dataset.projectiles=game.projectiles.length;canvas.dataset.projectileSpeed=C.difficulty(game.elapsed).speed.toFixed(1);canvas.dataset.bgmRate=bgm.playbackRate.toFixed(2);canvas.dataset.hearts=game.hearts;$('gameStatus').textContent=`남은 시간 ${Math.ceil(40-game.elapsed)}초, 하트 ${game.hearts}개, 점수 ${C.scoreFor(game.elapsed)}점`;$('playHint').classList.toggle('hidden',game.elapsed>5);}
 function frame(now){raf=0;if(game.mode!=='play')return;const dt=(now-last)/1000;last=now;if(dt>.25&&!game.paused)pause('화면이 잠시 멈춰 자동으로 일시정지했어요. 준비되면 계속하세요.');game.step(Math.min(dt,1/30),input());syncBgmTempo();game.events.length=0;draw();status();if(game.mode!=='play'){finish();return;}raf=requestAnimationFrame(frame);}
 function resize(){const r=canvas.getBoundingClientRect(),dpr=Math.min(devicePixelRatio||1,2);canvas.width=Math.max(1,Math.round(r.width*dpr));canvas.height=Math.max(1,Math.round(r.height*dpr));draw();}new ResizeObserver(resize).observe(canvas);
 function load(src){return new Promise((resolve,reject)=>{const image=new Image();image.onload=()=>resolve(image);image.onerror=()=>reject(new Error(src));image.src=src;});}
 Promise.all([load('./assets/characters-v2.png'),load('./assets/living-room-v2.png'),...['main-screen','play-reference','clear-screen','fail-screen'].map(n=>load(`./assets/${n}.png`))]).then(images=>{art.characters=images[0];art.room=images[1];art.player=A.playerArt(art.characters,()=>document.createElement('canvas'));game.playerMasks=art.player.masks;for(const text of C.LINES)layouts.set(text,C.layoutText(text,(s,size)=>{ctx.font=`800 ${size}px "Malgun Gothic",sans-serif`;return ctx.measureText(s).width;}));ready=true;$('startBtn').disabled=false;$('helpStartBtn').disabled=false;$('loadNote').classList.add('hidden');resize();}).catch(()=>{$('loadNote').textContent='그림을 읽지 못했어요. assets 폴더와 함께 실행해주세요.';});
 screen('menu');
})();
