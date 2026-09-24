(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.ChuseokGeometry=api;})(typeof globalThis!=='undefined'?globalThis:this,function(){
 'use strict';
 const BUBBLE=Object.freeze({bodyHalfW:74,bodyHalfH:56,tail:14,halfW:88,halfH:70});
 const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
 // One closed silhouette, shared by the visible fill and every collision check.
 function bubbleOutline(b={},inset=0){
  const w=(b.bodyHalfW??BUBBLE.bodyHalfW)-inset,h=(b.bodyHalfH??BUBBLE.bodyHalfH)-inset;
  const length=Math.hypot(b.vx||0,b.vy||0),nx=length?-(b.vx||0)/length:-1,ny=length?-(b.vy||0)/length:0;
  const direction=Math.atan2(ny/h,nx/w),gap=.13,pts=[];
  // An oval, not a long rounded rectangle. The tail replaces a short arc,
  // forming one closed silhouette without a seam through the bubble.
  for(let i=0;i<=96;i++){const a=direction+gap+(Math.PI*2-gap*2)*i/96;pts.push({x:Math.cos(a)*w,y:Math.sin(a)*h});}
  pts.push({x:Math.cos(direction)*w+nx*(BUBBLE.tail-inset),y:Math.sin(direction)*h+ny*(BUBBLE.tail-inset)});
  return pts;
 }
 function inside(p,poly){
  let result=false;
  for(let i=0,j=poly.length-1;i<poly.length;j=i++){
   const a=poly[j],b=poly[i],cross=(p.x-a.x)*(b.y-a.y)-(p.y-a.y)*(b.x-a.x);
   if(Math.abs(cross)<1e-7&&p.x>=Math.min(a.x,b.x)-1e-7&&p.x<=Math.max(a.x,b.x)+1e-7&&p.y>=Math.min(a.y,b.y)-1e-7&&p.y<=Math.max(a.y,b.y)+1e-7)return true;
   if((a.y>p.y)!==(b.y>p.y)&&p.x<(b.x-a.x)*(p.y-a.y)/(b.y-a.y)+a.x)result=!result;
  }
  return result;
 }
 function scanline(poly,y){
  const xs=[];
  for(let i=0,j=poly.length-1;i<poly.length;j=i++){
   const a=poly[j],b=poly[i];
   if((a.y<=y&&b.y>y)||(b.y<=y&&a.y>y))xs.push(a.x+(y-a.y)*(b.x-a.x)/(b.y-a.y));
  }
  return xs.sort((a,b)=>a-b);
 }
 function rectTouches(poly,left,top,right,bottom){
  if(poly.some(p=>p.x>=left&&p.x<=right&&p.y>=top&&p.y<=bottom))return true;
  if([{x:left,y:top},{x:right,y:top},{x:right,y:bottom},{x:left,y:bottom}].some(p=>inside(p,poly)))return true;
  for(let i=0,j=poly.length-1;i<poly.length;j=i++){
   const a=poly[j],b=poly[i],dx=b.x-a.x,dy=b.y-a.y;
   for(const x of [left,right]){const t=(x-a.x)/dx,y=a.y+t*dy;if(t>=0&&t<=1&&y>=top&&y<=bottom)return true;}
   for(const y of [top,bottom]){const t=(y-a.y)/dy,x=a.x+t*dx;if(t>=0&&t<=1&&x>=left&&x<=right)return true;}
  }
  return false;
 }
 // Transparent sprite pixels and cast shadows never become hit targets.
 function alphaMask(rgba,width,height){
  const rows=[];
  for(let y=0;y<height;y++){
   const spans=[];let start=-1;
   for(let x=0;x<=width;x++){
    const solid=x<width&&rgba[(y*width+x)*4+3]>=64;
    if(solid&&start<0)start=x;
    if(!solid&&start>=0){spans.push([start-width/2,x-width/2]);start=-1;}
   }
   if(spans.length)rows.push({y:y+.5-height/2,spans});
  }
  return{width,height,rows};
 }
 function bubbleTouchesPlayer(p,b,mask=null,bounce=0){
  const scale=b.scale??1,outline=b.outline||bubbleOutline(b),poly=scale===1?outline:outline.map(v=>({x:v.x*scale,y:v.y*scale})),dx=p.x-b.x,dy=p.y+bounce-b.y;
  const pw=mask?mask.width/2:(p.halfW??24),ph=mask?mask.height/2:(p.halfH??42);
  if(Math.abs(dx)>pw+BUBBLE.halfW*scale||Math.abs(dy)>ph+BUBBLE.halfH*scale)return false;
  if(!mask)return rectTouches(poly,dx-pw,dy-ph,dx+pw,dy+ph);
  for(const row of mask.rows){
   const y=dy+row.y;
   if(Math.abs(y)>BUBBLE.halfH)continue;
   const xs=scanline(poly,y);
   for(const span of row.spans){
    const left=dx+(p.facing<0?-span[1]:span[0]),right=dx+(p.facing<0?-span[0]:span[1]);
    for(let i=0;i+1<xs.length;i+=2)if(right>=xs[i]&&left<=xs[i+1])return true;
   }
  }
  return false;
 }
 function playerPose(p,t){return{index:p.moving?7:6,bounce:p.moving?Math.sin(t*20)*2:Math.sin(t*3)*1.3};}
 // All projectiles share the same speed curve: relative motion can be checked
 // in travelled pixels, even while that speed increases with game time.
 function travelLimit(b){
  const speed=Math.hypot(b.vx,b.vy)||1,ux=b.vx/speed,uy=b.vy/speed;
  const sx=ux>0?(1280+BUBBLE.halfW-b.x)/ux:ux<0?(-BUBBLE.halfW-b.x)/ux:Infinity;
  const sy=uy>0?(720+BUBBLE.halfH-b.y)/uy:uy<0?(70-BUBBLE.halfH-b.y)/uy:Infinity;
  return Math.max(0,Math.min(sx,sy));
 }
 function pathsOverlap(a,b){
  const na=Math.hypot(a.vx,a.vy)||1,nb=Math.hypot(b.vx,b.vy)||1;
  let enter=0,leave=Math.min(travelLimit(a),travelLimit(b));
  for(const [delta,velocity,extent] of [[a.x-b.x,a.vx/na-b.vx/nb,2*BUBBLE.halfW+6],[a.y-b.y,a.vy/na-b.vy/nb,2*BUBBLE.halfH+6]]){
   if(Math.abs(velocity)<1e-9){if(Math.abs(delta)>=extent)return false;continue;}
   const t1=(-extent-delta)/velocity,t2=(extent-delta)/velocity;
   enter=Math.max(enter,Math.min(t1,t2));leave=Math.min(leave,Math.max(t1,t2));
   if(enter>=leave)return false;
  }
  return enter<leave;
 }
 return{BUBBLE,bubbleOutline,bubbleTouchesPlayer,alphaMask,playerPose,pathsOverlap,inside,scanline};
});
