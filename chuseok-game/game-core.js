(function(root,factory){const api=factory(typeof module==='object'&&module.exports?require('./bubble-geometry.js'):root.ChuseokGeometry);if(typeof module==='object'&&module.exports)module.exports=api;else root.Chuseok=api;})(typeof globalThis!=='undefined'?globalThis:this,function(G){
 'use strict';
 const W=1280,H=720,DURATION=40,STEP=1/120,PLAYER={halfW:24,halfH:42},BUBBLE=G.BUBBLE,AIM_OFFSETS=[{x:-46,y:-18},{x:46,y:-18},{x:-46,y:18},{x:46,y:18},{x:-34,y:38},{x:34,y:38}];
 const BOUNDS={left:180,right:1100,top:245,bottom:620};
 const LINES=[
 '결혼은 언제 하니?','애인은 있니?','연애는 안 하니?','소개팅 안 해?','좋은 사람 없어?','눈이 너무 높은 거 아니니?','아직도 혼자야?','결혼 생각은 있니?','청첩장은 언제 주니?','요즘 만나는 사람은?',
 '취업은 했니?','회사 어디 다니니?','아직 준비 중이니?','정규직이니?','이직 생각은 없어?','승진은 언제 하니?','일은 할 만하니?','회사 계속 다닐 거니?','공무원 준비 안 해?','자격증은 땄니?',
 '월급은 얼마야?','연봉은 얼마니?','돈은 좀 모았니?','모아 놓은 돈은 있니?','집은 언제 살 거니?','전세야 월세야?','차는 있니?','적금은 들었니?','대출은 없니?','재테크는 하니?',
 '○○이는 벌써 했다던데?','○○이는 잘나가던데?','너는 왜 아직이니?','공부는 잘하고?','계획은 세워뒀니?','언제 철들래?','연락 좀 하고 살아라','부모님께 잘해라','앞으로 어떻게 살 거야?','요즘 뭐 하고 지내니?',
 '살 좀 쪘네?','살 좀 빠졌네?','밥 더 먹어라','왜 이렇게 말랐니?','운동 좀 해야지','늦게 자지 마라','아침은 먹고 다니니?','얼굴이 피곤해 보인다','휴대폰 좀 그만 봐라','명절인데 웃어야지'];
 const RELATIVES=[
 {name:'할머니',x:365,y:167,axis:'x',phase:0},{name:'할아버지',x:915,y:167,axis:'x',phase:1.2},
 {name:'이모',x:99,y:363,axis:'y',phase:2.1},{name:'고모',x:1135,y:363,axis:'y',phase:2.8},
 {name:'삼촌',x:112,y:560,axis:'y',phase:3.5},{name:'큰아빠',x:1120,y:560,axis:'y',phase:4.3}];
 const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
 const scoreFor=t=>Math.min(400,Math.floor(t*10+1e-7));
 function formatTime(t){const tenths=Math.floor(t*10+1e-7);return`${String(Math.floor(tenths/600)).padStart(2,'0')}:${String(Math.floor(tenths/10)%60).padStart(2,'0')}.${tenths%10}`;}
 function difficulty(t){
  const stages=[
   {moving:0,count:1,min:.82,max:1.00,speed:130,warning:.48},
   {moving:2,count:2,min:.58,max:.74,speed:160,warning:.43},
   {moving:4,count:3,min:.42,max:.56,speed:190,warning:.38},
   {moving:6,count:4,min:.28,max:.38,speed:225,warning:.33}
 ];
  const time=clamp(t,0,DURATION),stage=Math.min(3,Math.floor(time/10)),within=(time-stage*10)/10,current=stages[stage],terminal={moving:6,count:5,min:.16,max:.24,speed:340,warning:.28},next=stage===3?terminal:stages[stage+1],blend=within;
  const lerp=(a,b)=>a+(b-a)*blend;
  const earlyBonus=15*Math.max(0,1-time/15);
  return{moving:current.moving,count:current.count,min:lerp(current.min,next.min),max:lerp(current.max,next.max),speed:current.speed+(next.speed-current.speed)*(stage===3?blend*blend:blend)+earlyBonus,warning:lerp(current.warning,next.warning)};
 }
 const bubbleTouchesPlayer=G.bubbleTouchesPlayer;
 function circleRect(p,b){return bubbleTouchesPlayer(p,b);}
 const attackLimit=t=>Math.min(11,3+Math.floor(clamp(t,0,DURATION)/5));
 // Conservative movement planner: keep at least two reachable destinations
 // over the next second. Swept bounds check the whole movement, not just its end.
 function hasEscape(player,hazards,time){
  const speed=difficulty(time).speed,travel=t=>t*(speed+difficulty(time+t).speed)/2;
  const steps=10,dt=.1,moves=[{x:0,y:0},...Array.from({length:8},(_,i)=>({x:Math.cos(i*Math.PI/4),y:Math.sin(i*Math.PI/4)}))];
  let frontier=[{x:player.x,y:player.y}];
  for(let step=1;step<=steps;step++){
   const fromDistance=travel((step-1)*dt),toDistance=travel(step*dt),next=new Map();
   const boxes=hazards.map(b=>{const length=Math.hypot(b.vx,b.vy)||1,ux=b.vx/length,uy=b.vy/length;return{x:b.x+ux*fromDistance,y:b.y+uy*fromDistance,dx:ux*(toDistance-fromDistance),dy:uy*(toDistance-fromDistance),w:BUBBLE.halfW+48,h:BUBBLE.halfH+66};});
   for(const from of frontier)for(const move of moves){
    const to={x:clamp(from.x+move.x*player.speed*dt,BOUNDS.left,BOUNDS.right),y:clamp(from.y+move.y*player.speed*dt,BOUNDS.top,BOUNDS.bottom)},key=Math.round(to.x/40)+','+Math.round(to.y/40);if(next.has(key))continue;
    const blocked=boxes.some(b=>{let enter=0,leave=1;for(const [delta,velocity,extent] of [[from.x-b.x,to.x-from.x-b.dx,b.w],[from.y-b.y,to.y-from.y-b.dy,b.h]]){if(Math.abs(velocity)<1e-8){if(Math.abs(delta)>extent)return false;continue;}const a=(-extent-delta)/velocity,z=(extent-delta)/velocity;enter=Math.max(enter,Math.min(a,z));leave=Math.min(leave,Math.max(a,z));if(enter>leave)return false;}return enter<=leave;});
    if(!blocked)next.set(key,to);
   }
   frontier=[...next.values()];if(frontier.length<1)return false;
  }
  return frontier.some(a=>frontier.some(b=>Math.hypot(a.x-b.x,a.y-b.y)>=120));
 }
 // Two or three balanced lines inside the oval; prefer whole Korean words.
 function layoutText(text,measure,maxWidth=112){
  const candidates=[];
  for(let size=21;size>=16;size--){
   function split(start,lines,cuts,remaining){
    if(remaining===1){const last=text.slice(start).trim();if(!last)return;const result=[...lines,last],widths=result.map(s=>measure(s,size));if(widths.some(w=>w>maxWidth))return;
     const mean=widths.reduce((a,b)=>a+b,0)/widths.length,cost=cuts*1000+(21-size)*6+(result.length-2)*24+widths.reduce((n,w)=>n+(w-mean)**2,0)/100;
     candidates.push({size,lines:result,cost});return;}
    for(let end=start+1;end<text.length;end++){const part=text.slice(start,end).trim();if(!part||measure(part,size)>maxWidth)continue;split(end,[...lines,part],cuts+Number(text[end]!==' '&&text[end-1]!==' '),remaining-1);}
   }
   split(0,[],0,2);split(0,[],0,3);
  }
  candidates.sort((a,b)=>a.cost-b.cost);return candidates[0]||{size:16,lines:[text]};
 }
 class Game{
  constructor(random=Math.random){this.random=random;this.playerMasks={};this.reset();this.mode='menu';}
  reset(){this.mode='play';this.paused=false;this.elapsed=0;this.hearts=3;this.spawnTimer=.8;this.projectiles=[];this.impacts=[];this.pending=[];this.recentLines=[];this.lastRelative=-1;this.hitFlash=0;this.events=[];this.player={x:640,y:430,r:22,halfW:PLAYER.halfW,halfH:PLAYER.halfH,speed:480,moving:false,facing:1};}
  touches(b,p=this.player){const pose=G.playerPose(p,this.elapsed);return bubbleTouchesPlayer(p,b,this.playerMasks[pose.index],pose.bounce);}
  relative(index){const r=RELATIVES[index],since=this.elapsed-(10+Math.floor(index/2)*10);const offset=since>0?Math.sin(since*.55+r.phase)*42*Math.min(1,since/3):0;return{...r,x:r.x+(r.axis==='x'?offset:0),y:r.y+(r.axis==='y'?offset:0)};}
  pickLine(){const pool=LINES.filter(s=>!this.recentLines.includes(s));const text=pool[Math.floor(this.random()*pool.length)];this.recentLines.push(text);if(this.recentLines.length>5)this.recentLines.shift();return text;}
  safeShot(x,y,vx,vy){return hasEscape(this.player,[...this.projectiles,{x,y,vx,vy}],this.elapsed);}
  schedule(){const d=difficulty(this.elapsed),ids=[0,1,2,3,4,5].filter(i=>i!==this.lastRelative&&!this.pending.some(p=>p.id===i));for(let i=ids.length-1;i>0;i--){const j=Math.floor(this.random()*(i+1));[ids[i],ids[j]]=[ids[j],ids[i]];}const base=Math.max(1,Math.floor(d.count)),count=Math.min(ids.length,base+Number(this.random()<d.count-base));let n=0;for(const id of ids){if(n>=count||this.projectiles.length+this.pending.length>=attackLimit(this.elapsed))break;this.pending.push({id,wait:d.warning});n++;}}
  fire(id){
   if(this.projectiles.length>=attackLimit(this.elapsed))return;
   const r=this.relative(id),direct=id===0,aim=direct?{x:0,y:0}:(AIM_OFFSETS[id]??{x:0,y:0}),speed=difficulty(this.elapsed).speed;
   for(const offset of(direct?[0]:[0,100,-100,200,-200,300,-300])){
    const targetX=clamp(this.player.x+aim.x+(r.axis==='x'?offset:0),BOUNDS.left,BOUNDS.right),targetY=clamp(this.player.y+aim.y+(r.axis==='y'?offset:0),BOUNDS.top,BOUNDS.bottom),dx=targetX-r.x,dy=targetY-r.y,n=Math.hypot(dx,dy)||1,ux=dx/n,uy=dy/n;
    // The voice starts at the speaker, not partway across the play field.
    // It grows as it leaves their mouth; render and collision share this scale.
    const gap=24;
    const b={x:r.x+ux*gap,y:r.y+8+uy*gap,vx:ux*speed,vy:uy*speed,source:id,halfW:BUBBLE.halfW,halfH:BUBBLE.halfH,age:0,scale:.28};
    b.outline=G.bubbleOutline(b);b.innerOutline=G.bubbleOutline(b,2.5);
    if(n<gap+70||this.touches(b)||this.projectiles.some(other=>G.pathsOverlap(b,other))||!this.safeShot(b.x,b.y,b.vx,b.vy))continue;
    b.text=this.pickLine();this.projectiles.push(b);this.lastRelative=id;return;
   }
  }
  step(dt,input={}){if(this.mode!=='play'||this.paused||!Number.isFinite(dt)||dt<=0)return;let remaining=Math.min(dt,DURATION-this.elapsed);while(remaining>1e-9&&this.mode==='play'){const s=Math.min(STEP,remaining);this.tick(s,input);remaining-=s;}}
  tick(dt,input){
   this.elapsed=Math.min(DURATION,this.elapsed+dt);this.hitFlash=Math.max(0,this.hitFlash-dt);this.impacts=this.impacts.filter(b=>(b.life-=dt)>0);
   const p=this.player;let dx=input.x||0,dy=input.y||0,dist=Infinity;
   if(input.target){dx=input.target.x-p.x;dy=input.target.y-p.y;dist=Math.hypot(dx,dy);}
   const n=Math.hypot(dx,dy);p.moving=n>.5;
   if(p.moving){const delta=Math.min(p.speed*dt,dist);p.x=clamp(p.x+dx/n*delta,BOUNDS.left,BOUNDS.right);p.y=clamp(p.y+dy/n*delta,BOUNDS.top,BOUNDS.bottom);if(Math.abs(dx)>.1)p.facing=dx<0?-1:1;}
   this.spawnTimer-=dt;if(this.spawnTimer<=0){this.schedule();const d=difficulty(this.elapsed);this.spawnTimer=d.min+this.random()*(d.max-d.min);}
   for(const warning of this.pending){warning.wait-=dt;if(warning.wait<=0)this.fire(warning.id);}this.pending=this.pending.filter(w=>w.wait>0);
   const speed=difficulty(this.elapsed).speed;
   for(const b of this.projectiles){const n=Math.hypot(b.vx,b.vy)||1;b.vx=b.vx/n*speed;b.vy=b.vy/n*speed;b.x+=b.vx*dt;b.y+=b.vy*dt;if(b.age!==undefined){b.age+=dt;b.scale=.28+.72*clamp(b.age/.5,0,1);}}
   this.projectiles=this.projectiles.filter(b=>b.x>-BUBBLE.halfW&&b.x<W+BUBBLE.halfW&&b.y>70-BUBBLE.halfH&&b.y<H+BUBBLE.halfH);
   // Fresh shots must be visible for several frames before they can hurt.
   const hit=this.projectiles.findIndex(b=>(b.age??1)>=.12&&this.touches(b));
   if(hit>=0){const [b]=this.projectiles.splice(hit,1);this.impacts.push({...b,life:.24});this.hearts--;this.hitFlash=.3;this.events.push('hit');if(this.hearts===0){this.finish(false);return;}}
   if(this.elapsed>=DURATION-1e-8){this.elapsed=DURATION;this.finish(true);}
  }
  finish(success){this.mode=success?'clear':'fail';this.pending=[];this.events.push(this.mode);}
 }
 return{...G,W,H,DURATION,BOUNDS,PLAYER,BUBBLE,AIM_OFFSETS,LINES,RELATIVES,Game,clamp,scoreFor,formatTime,layoutText,circleRect,bubbleTouchesPlayer,difficulty,attackLimit,hasEscape};
});
