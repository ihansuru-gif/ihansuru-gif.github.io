(function(root,factory){const api=factory(typeof module==='object'&&module.exports?require('./game-core.js'):root.Chuseok);if(typeof module==='object'&&module.exports)module.exports=api;else root.ChuseokArt=api;})(typeof globalThis!=='undefined'?globalThis:this,function(C){
 'use strict';
 const colors=['#e99a9b','#8daa80','#e5a4c7','#b8a0c8','#91b9d3','#a8b67e'];
 const sprites=[[30,12,350,389],[472,13,360,390],[889,22,390,378],[1365,20,380,381],[20,444,400,423],[465,444,392,420],[925,402,345,475],[1350,402,350,475]];
 function polygon(ctx,points,fill){ctx.beginPath();points.forEach((p,i)=>i?ctx.lineTo(p.x,p.y):ctx.moveTo(p.x,p.y));ctx.closePath();ctx.fillStyle=fill;ctx.fill();}
 function bubble(ctx,b,layout,impact=false){
  ctx.save();ctx.translate(b.x,b.y);ctx.scale(b.scale??1,b.scale??1);
  // One outer silhouette: no separate tail strokes or seams through the text.
  polygon(ctx,b.outline||C.bubbleOutline(b),impact?'#d94532':'#654733');
  polygon(ctx,b.innerOutline||C.bubbleOutline(b,2.5),impact?'#ffe9d9':'#fffbee');
  // Tiny color cue identifies the speaker without another border across the tail.
  ctx.fillStyle=colors[b.source]||colors[0];ctx.beginPath();ctx.arc(-64,0,2.5,0,Math.PI*2);ctx.fill();
  ctx.font=`800 ${layout.size}px "Malgun Gothic",sans-serif`;ctx.fillStyle='#493024';ctx.textAlign='center';ctx.textBaseline='middle';
  layout.lines.forEach((line,i)=>ctx.fillText(line,0,(i-(layout.lines.length-1)/2)*22));ctx.restore();
 }
 function playerArt(image,makeCanvas){
  const frames={},masks={};
  for(const index of [6,7]){
   const [sx,sy,sw,sh]=sprites[index],scale=Math.min(112/sw,133/sh),width=Math.round(sw*scale),height=Math.round(sh*scale),canvas=makeCanvas();
   canvas.width=width;canvas.height=height;const ctx=canvas.getContext('2d',{willReadFrequently:true});
   ctx.drawImage(image,sx,sy,sw,sh,0,0,width,height);
   frames[index]=canvas;masks[index]=C.alphaMask(ctx.getImageData(0,0,width,height).data,width,height);
  }
  return{frames,masks};
 }
 function player(ctx,p,t,frames){const pose=C.playerPose(p,t),frame=frames[pose.index];ctx.save();ctx.translate(p.x,p.y+pose.bounce);if(p.facing<0)ctx.scale(-1,1);ctx.drawImage(frame,-frame.width/2,-frame.height/2);ctx.restore();}
 return{colors,sprites,bubble,playerArt,player,polygon};
});
