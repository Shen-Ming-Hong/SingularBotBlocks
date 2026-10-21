// "My robots": how each of a student's robots reads in the list, worked out from the player's own position.
// `zh` true gives the classroom Chinese; false gives the English text in the editor's current game language.
import {fill,translate} from './i18n.js';
const DIRECTIONS=[['北','north'],['東北','north-east'],['東','east'],['東南','south-east'],['南','south'],['西南','south-west'],['西','west'],['西北','north-west']];
// Minecraft's own names for the three dimensions.
const DIMENSIONS={'minecraft:overworld':['主世界','the Overworld'],'minecraft:the_nether':['地獄','the Nether'],'minecraft:the_end':['終界','the End']};
const say=(zh,[a,b],...args)=>zh?fill(a,args):translate(b,...args);

/** Compass direction and distance from the player to a robot, such as "東北 32 格"; height is added when it differs a lot. */
export function bearing(player,robot,zh=true){
 if(player.dimension&&robot.dimension&&player.dimension!==robot.dimension){
  const pair=DIMENSIONS[robot.dimension],name=pair?say(zh,pair):robot.dimension;return say(zh,['在%1','in %1'],name);}
 // Minecraft's east is +x and north is -z; the robot fills the block at its coordinates.
 const dx=robot.x+.5-player.x,dz=robot.z+.5-player.z,distance=Math.round(Math.hypot(dx,dz)),rise=robot.y-Math.floor(player.y);
 let text;
 if(distance<1)text=say(zh,['就在你旁邊','right next to you']);
 else{const index=Math.round(((Math.atan2(dx,-dz)*180/Math.PI+360)%360)/45)%8;text=say(zh,['%2 %1 格','%1 blocks %2'],distance,say(zh,DIRECTIONS[index]));}
 if(Math.abs(rise)>=3)text+=rise>0?say(zh,['，高 %1 格',', %1 up'],rise):say(zh,['，低 %1 格',', %1 down'],-rise);
 return text;
}

export function statusText(status,zh=true){
 return say(zh,{running:['執行中','Running'],error:['出錯','Error']}[status]||['待命','Idle']);
}

/** The title and detail line of one robot in the list. */
export function describeRobot(robot,player,zh=true){
 const title=say(zh,['%1 號機器人','Robot %1'],robot.number);
 const program=robot.program?say(zh,['程式：%1','Program: %1'],robot.program):say(zh,['還沒選程式','No program chosen']);
 const detail=[program,bearing(player,robot,zh),`(${robot.x}, ${robot.y}, ${robot.z})`].join(' · ');
 return {title,detail,status:statusText(robot.status,zh)};
}

/** Running programs stop when a robot is recalled, so the student is asked first. */
export function needsRecallConfirm(robot){return robot.status==='running';}
