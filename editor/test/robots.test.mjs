import test from 'node:test';
import assert from 'node:assert/strict';
import {bearing,describeRobot,statusText,needsRecallConfirm} from '../src/robots.js';

const player={dimension:'minecraft:overworld',x:0.5,y:64,z:0.5};
const at=(x,y,z,extra={})=>({dimension:'minecraft:overworld',x,y,z,...extra});

test('bearing names the compass direction and distance from the player',()=>{
 assert.equal(bearing(player,at(22,64,-23)),'東北 32 格');
 assert.equal(bearing(player,at(0,64,-10)),'北 10 格');
 assert.equal(bearing(player,at(10,64,0)),'東 10 格');
 assert.equal(bearing(player,at(0,64,10)),'南 10 格');
 assert.equal(bearing(player,at(-10,64,10)),'西南 14 格');
 assert.equal(bearing(player,at(-10,64,10),false),'14 blocks south-west');
});

test('bearing says when the robot is beside the player, far above or below, or in another dimension',()=>{
 assert.equal(bearing(player,at(0,64,0)),'就在你旁邊');
 assert.equal(bearing(player,at(0,70,-5)),'北 5 格，高 6 格');
 assert.equal(bearing(player,at(0,60,-5),false),'5 blocks north, 4 down');
 assert.equal(bearing(player,at(0,64,0,{dimension:'minecraft:the_nether'})),'在地獄');
 assert.equal(bearing(player,at(0,64,0,{dimension:'minecraft:the_end'}),false),'in the End');
});

test('each robot row shows its number, program, state, direction and coordinates',()=>{
 const view=describeRobot(at(3,64,-4,{number:2,program:'挖隧道',status:'running'}),player);
 assert.equal(view.title,'2 號機器人');
 assert.equal(view.status,'執行中');
 assert.equal(view.detail,'程式：挖隧道 · 東北 5 格 · (3, 64, -4)');
 assert.equal(describeRobot(at(0,64,0,{number:1,program:'',status:'stopped'}),player,false).detail,'No program chosen · right next to you · (0, 64, 0)');
 assert.deepEqual(['stopped','running','error'].map(s=>statusText(s)),['待命','執行中','出錯']);
});

test('only a running robot asks before it is recalled',()=>{
 assert.equal(needsRecallConfirm({status:'running'}),true);
 assert.equal(needsRecallConfirm({status:'stopped'}),false);
 assert.equal(needsRecallConfirm({status:'error'}),false);
});
