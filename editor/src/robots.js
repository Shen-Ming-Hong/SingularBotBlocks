// "My robots": how each of a student's robots reads in the list, worked out from the player's own position.
const DIRECTIONS={zh:['北','東北','東','東南','南','西南','西','西北'],en:['north','north-east','east','south-east','south','south-west','west','north-west']};
const DIMENSIONS={'minecraft:overworld':['主世界','the Overworld'],'minecraft:the_nether':['地獄','the Nether'],'minecraft:the_end':['終界','the End']};

/** Compass direction and distance from the player to a robot, such as "東北 32 格"; height is added when it differs a lot. */
export function bearing(player,robot,zh=true){
 if(player.dimension&&robot.dimension&&player.dimension!==robot.dimension){
  const name=DIMENSIONS[robot.dimension]?.[zh?0:1]||robot.dimension;return zh?`在${name}`:`in ${name}`;}
 // Minecraft's east is +x and north is -z; the robot fills the block at its coordinates.
 const dx=robot.x+.5-player.x,dz=robot.z+.5-player.z,distance=Math.round(Math.hypot(dx,dz)),rise=robot.y-Math.floor(player.y);
 let text;
 if(distance<1)text=zh?'就在你旁邊':'right next to you';
 else{const index=Math.round(((Math.atan2(dx,-dz)*180/Math.PI+360)%360)/45)%8,direction=DIRECTIONS[zh?'zh':'en'][index];text=zh?`${direction} ${distance} 格`:`${distance} blocks ${direction}`;}
 if(Math.abs(rise)>=3)text+=zh?(rise>0?`，高 ${rise} 格`:`，低 ${-rise} 格`):(rise>0?`, ${rise} up`:`, ${-rise} down`);
 return text;
}

export function statusText(status,zh=true){
 return {running:zh?'執行中':'Running',error:zh?'出錯':'Error'}[status]||(zh?'待命':'Idle');
}

/** The title and detail line of one robot in the list. */
export function describeRobot(robot,player,zh=true){
 const title=zh?`${robot.number} 號機器人`:`Robot ${robot.number}`;
 const program=robot.program?(zh?`程式：${robot.program}`:`Program: ${robot.program}`):(zh?'還沒選程式':'No program chosen');
 const detail=[program,bearing(player,robot,zh),`(${robot.x}, ${robot.y}, ${robot.z})`].join(' · ');
 return {title,detail,status:statusText(robot.status,zh)};
}

/** Running programs stop when a robot is recalled, so the student is asked first. */
export function needsRecallConfirm(robot){return robot.status==='running';}
