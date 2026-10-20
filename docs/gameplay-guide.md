# 開發版操作指南 / Development gameplay guide

Minecraft Java **1.21.1**、NeoForge **21.1.252** 以上的 21.1.x、Java **21**、CC: Tweaked **1.120.2**。學生端另裝 MCEF **2.1.6-1.21.1** 及校驗過的原生套件；伺服器不用 MCEF。請使用新的測試世界，完整課堂驗收尚未完成。

## 機器人、控制及外觀

創造模式背包的「Singular Bot Blocks」分頁位於 ComputerCraft 分頁之後，只有一個「程式機器人」，內建固定鑽石鎬。若有多頁，使用背包上方的翻頁箭頭。沒有遙控器或其他學生物品，普通工具分頁也不加入本模組物品。

生存模式可將普通或進階 CC 海龜與綠色染料轉換為程式機器人。新放置會指定擁有者並建立新的 CC 電腦磁碟身分，名稱、外觀及背包依正常物品資料保留；不帶入前任程式或磁碟。自己的多台機器人共用一份私人程式庫與活動紀錄，機器人的位置、背包、外觀及執行狀態各自獨立。

直接右鍵自己的機器人開啟控制台，不需燃料或遙控器。控制限同世界、已載入、64 格內；OP 等級 2 教師可以管理，鎖定會撤銷學生控制。他人不能讀取程式、紀錄或控制機器人，但可以破壞它，背包正常掉落一次。程式與紀錄仍保存，自己重放一台即可繼續使用。

固定鑽石鎬單次挖掘可破壞方塊，按照 Minecraft 正常掉落收集；背包滿了則掉在世界中。基岩及其他不可破壞／受保護方塊拒絕。左側鎬不能卸下；右側升級仍可使用。

教師可設定 `/gamerule doTileDrops false`：玩家仍能破壞一般方塊，但手動採礦不會產生掉落物；程式機器人透過控制台、Blockly 或 Lua 採礦仍正常收集，背包滿時正常掉落。拆除機器人仍回收本體與背包一次；教師鎖定及禁建區照常生效。一般 CC 海龜不適用此採集例外。用 `/gamerule doTileDrops true` 恢復一般方塊掉落。這項規則不禁止撿取既有物品、開箱或其他取得物品的方式。

控制台提供上／下、前／後、左／右轉、挖掘及放置，16 格海龜背包與玩家背包。背包空格顯示 1–16 編號，中鍵點格子可選使用格；也可在程式 More 選單切換。外觀頁提供命名、16 色與 17 種裝飾（另有無裝飾）。設定過的名稱與外觀會成為自己的預設，之後放下的全新機器人自動套用；準心對準機器人時頭頂顯示名稱。海龜視角按鈕切換攝影機；關閉畫面會返回原視角。已有部分原生遊戲觀察，完整案例狀態見 `acceptance.md`。

## 程式與除錯

右鍵教學海龜開啟控制台，點選程式分頁即開啟隨模組打包的 Blockly。控制台開啟約一秒後會在背景預先載入編輯器，切到程式分頁時直接顯示；編輯器開啟期間不繪製被遮住的世界與 HUD（逐步或程式執行中時編輯器縮到右側，才繪製左側露出的世界），讓編輯器操作更順。首頁不提供編輯器按鈕，也沒有直接開啟測試編輯器的 F8 快捷鍵。拖曳背景平移，滾輪縮放。可建立、改名、刪除、儲存、載入、另存副本。積木包含海龜動作及成功判斷、背包、方塊／物品 ID、紅石、判斷、迴圈、變數、運算、文字及具名程式呼叫。重複命令使用迴圈。每個積木程式有一個不能刪除的金色「當程式開始」積木，按「執行」或「逐步」時只跑接在它下面的積木；沒接上的積木會變灰，滑過或選取時說明它不會執行。開始積木出現前存的舊程式，第一次打開時會自動在最上方那組積木前加上開始積木並存檔，其他組變灰由學生自行接上；尚未打開過的舊程式仍照舊執行所有積木組。「物品／方塊 ID」積木是任意已註冊的方塊或物品 ID 文字（不限背包內；初階班只顯示物品外觀，進階班另顯示 ID 文字），用來和「前方的方塊 ID」「背包格的物品 ID」比較；點選後開啟方塊外觀選單，列出目前遊戲中全部方塊與物品（含其他模組），可用中文、英文名稱或 ID 搜尋並以「全部／方塊／物品」篩選，滑過方塊顯示介面語言的名稱與 ID。選單資料由遊戲在第一次開啟編輯器時產生並保留到關閉遊戲。

編輯器採全畫面 Minecraft 主視覺新設計；介面語言直接跟隨 Minecraft 遊戲語言設定（繁體中文顯示中文，其他語言顯示英文），在遊戲中改語言後下次開啟編輯器即套用；頂端標題列提供亮色／暗色切換、「我的紀錄」與「機器人設定」圖示、儲存狀態及返回；下方依序為程式庫列（「＋ 新增」選單可建立積木或 Lua 程式，旁邊以標籤顯示目前類型）、執行列（燈號以灰／綠／黃／紅表示待命、執行中、已暫停、程式錯誤，暫停與繼續依狀態切換）及只有教師看得到的發送列。圖示按鈕滑過會顯示名稱。右側只在有內容時出現：逐步或執行時的變數、錯誤與操作結果訊息、選取積木時的接法提示；沒有內容時整欄收起。中文輸入法（含注音）可直接在各輸入框使用，組字中的文字顯示在輸入框旁；進階班世界另顯示 Lua 預覽。初階班世界（新世界預設）看不到任何 Lua：沒有 Lua 預覽、Lua 程式、「轉成 Lua」與類型標籤，「＋ 新增」直接建立積木程式，詳見〈教師控制與復原〉。機器人設定保留復原點、背包使用格及教師鎖定。「轉成 Lua」使用伺服器產生的程式碼建立可編輯的獨立 Lua 程式副本。Lua 不反向轉回積木。Blockly 執行會保留舊版動作失敗斷言；攻擊失敗不自動中止。未加除錯註記的 Lua 副本沿用舊版匯出語意。手寫 Lua 可使用 `turtle`、`redstone`、`edu` 與 `turtleedu`。執行錯誤會標示積木或 Lua 行，停止後清除目前標示。

可執行、暫停、繼續、停止及逐步。按「執行」或「繼續」成功後，編輯器會存檔並回到控制台的遙控頁，讓學生看見機器人動作；視角維持原狀，學生要看機器人視角時自行按控制台的相機鍵。控制台的執行鍵同樣不會切換視角。「逐步」「暫停」「停止」留在編輯器，方便對照積木高亮與變數；逐步（已暫停）時，或程式執行中從控制台點程式分頁開啟編輯器時，編輯器縮到畫面右側約 44% 寬，隱藏標題列、程式庫列、左側積木分類與 Lua 預覽、變數移到積木下方，左側露出遊戲畫面讓學生同時看到亮起的積木和機器人動作，高亮積木移出視野時自動捲回；縮小後執行列仍有暫停、繼續、逐步與停止；執行列的「放大編輯器」可暫時恢復全畫面編輯，程式結束、停止或出錯後恢復原尺寸；程式啟動失敗時也留在編輯器顯示錯誤。積木逐步包含迴圈與會求值的運算／呼叫位置。變數顯示目前程式作用域；機器人執行、暫停或出錯時，若它正在跑的程式不是畫面上這支（例如主程式呼叫的子程式），右側會出現「切換到機器人正在執行的程式」，按下即可看正確來源及積木；機器人待命時不顯示。Lua 模式的「逐步動作」以海龜動作為停點，尚不支援任意 Lua 行逐步。

未完成的 workspace 仍可儲存，編譯錯誤另外提示。多人版本衝突不覆蓋別人的修訂，可用「另存副本」保留自己的內容。「我的紀錄」顯示自己所有機器人的放置、破壞、保存與執行事件。儲存前也備份到學生端 `singularbots/recovery/<世界或伺服器識別>/<玩家 UUID>/browser-test-draft.json`；只有儲存或關閉失敗而留下未同步的草稿時，下次開啟才會出現「復原未存草稿」。自動儲存完成後儲存鈕顯示「已儲存」。刪除的伺服器程式保留 `.deleted` 檔；世界程式位於 `singularbots/students/<玩家 UUID>/`。

## 教師發送

學生之間不能分享任何程式，也沒有磁碟匯出匯入；要重複使用的步驟請寫成積木函式。只有教師（OP2）在 `/botblocks sharing true` 之後會看到「教師發送」，可把程式直接放進單一學生或所有附近學生的程式庫。程式庫依賴一併攜帶；相同且不需改寫呼叫的依賴重用，積木同名衝突另存並更新所有上層呼叫。寫入失敗或中斷會回滾新程式，保留原本程式庫。含手寫 Lua 的依賴名稱衝突會拒絕，請先改名處理。手寫 Lua 可動態呼叫名稱，因此發送時包含可用程式庫；總量仍限制為 1 MiB。

## 教師控制與復原

預設教師為 OP 等級 2，沒有另外的教師名單；其他玩家都是學生。專用伺服器在主控台以 `/op <老師帳號>` 指定教師。區域網路由教師電腦開世界並在建立世界時開啟作弊；開放 LAN 時「允許作弊」必須選「關」，否則原版 Minecraft 會讓所有加入者成為 OP，學生即可自行變更下列設定。指令：

```text
/botblocks stop
/botblocks stopped false
/botblocks enabled true
/botblocks visualEditor false
/botblocks suggestions false
/botblocks canBuild false
/botblocks lua true
/botblocks sharing true
```

`enabled` 停用海龜動作；`stopped` 急停並取消既有佇列；`visualEditor` 停用積木編輯，獨立 Lua 程式仍可使用；`suggestions` 控制積木接續提示；`canBuild` 是沒有局部權限方塊時的預設建造權限。`lua` 預設 false，為初階班：伺服器拒絕建立、開啟、執行、轉換或發送 Lua 程式，程式庫隱藏既有 Lua 程式但不刪除；設為 true 為進階班。教師以 `visualEditor false` 停用積木時，Lua 自動開放，避免學生沒有編輯器可用。`sharing` 預設 false，關閉教師發送並隱藏發送列；學生無論設定為何都看不到也不能使用。舊世界存的 `disks` 設定會被忽略。兩者都依世界保存，不同班級請使用不同世界。控制台亦有教師鎖定／解鎖。教師可直接開啟學生的機器人帶著操作：編輯的是學生的私人程式庫，停手約 0.3 秒自動存檔，學生在同一台機器人上開著、且沒有未存修改的編輯器約 0.3 秒內載入新內容並保留捲動位置（另加網路延遲）；教師切換程式時學生也跟著切換。拖曳中的動作不會即時出現，要放下並自動存檔後才同步。學生不能開啟教師或其他學生的機器人。

教師示範時可在學生機器人的編輯器按「老師接手」（只有 OP2 在別人的機器人上看得到）。系統先等學生放下拖曳中的積木、完成中文組字並存檔，再交給教師單獨修改；學生畫面顯示「老師示範中」，仍可觀看、捲動、縮放，但整個私人程式庫暫時不能修改，被接手的那台機器人也不能操作，其他機器人照常。學生存檔失敗或 20 秒內沒完成交接會取消接手並保留學生內容。教師再按「結束接手」、關閉控制台、改開別台、斷線或機器人消失時自動解除，學生先載入教師最後存的版本才恢復編輯。每位學生同時只能有一位教師接手；這和永久的教師鎖定是分開的。

權限方塊：綠＝允許建造、紅＝禁止建造、藍＝邊界、黃＝邊界隔離。建造依下方柱狀範圍最近的允許／禁止方塊決定；邊界向上／下延伸，遇隔離方塊中止，支援負 Y。移動、放置、工具與丟出／吸取路徑檢查伺服器權限。

「記錄狀態」記下位置、方向、背包與使用格；後續指令記錄方塊、容器和物品變更。「復原狀態」先驗證所有預期狀態，另一人改動方塊、背包或撿走物品會拒絕復原。最多 512 個方塊、256 個物品實體及約 1 MiB 記錄。戰鬥、非物品實體生成、不支援的物品／流體使用會停用此次復原；診斷保留，不嘗試不安全地補回物品。

## English quick start

Teachers can set `/gamerule doTileDrops false` to suppress ordinary block drops from manual mining. Programming robots still collect drops through the controller, Blockly and Lua, spilling overflow when their inventory is full. Teacher locks and protected areas still apply; regular CC turtles retain vanilla behaviour. Breaking a programming robot still returns its body and inventory once. Set the rule back to `true` to restore ordinary drops; this rule does not prevent picking up existing items or opening containers.

In Creative mode, open the Singular Bot Blocks tab after ComputerCraft; it contains one programming robot with a permanent diamond pickaxe. In Survival, convert a CC turtle with green dye. Right-click your robot directly, then choose the Program tab for bundled Blockly or independent Lua. No remote item or fuel is required. Your multiple robots share a private library and activity history; other students cannot read or control them, but may break them. Inventory drops normally; your programs survive. A newly placed recovered robot uses its new owner's workspace and a fresh CC computer identity. OP2 teachers retain lock, stop and distribution controls. In another student's editor, an OP2 teacher can press Teacher takeover: once the student's drag, IME input and save finish, only the teacher can edit; the student sees "Teacher demonstration", can still watch, scroll and zoom, but cannot edit their library or control that robot. A failed save or a 20-second handoff timeout cancels it. End takeover, closing the controller, switching robots, disconnecting or losing the robot releases it, and the student first loads the teacher's last save. Editors auto-save and refresh about every 0.3 s.

Save copy resolves revision conflicts without overwriting another editor. Recover local draft restores a client backup. Students cannot share programs or use floppy disks; reuse steps with Blockly functions. Only an OP2 teacher sees Teacher send, which writes directly into a student library. A new world is a beginner class: `/botblocks lua` defaults to false, hiding the Lua preview, Lua programs and conversion and refusing Lua through the server and teacher delivery; `/botblocks sharing` defaults to false, disabling teacher delivery. Library dependencies travel with programs. Blockly dependency collisions are renamed; ambiguous Lua name collisions are rejected.

Record a snapshot before an exercise. Undo validates terrain, containers, inventory and tracked item entities before restoring. Unsupported effects and conflicts invalidate restoration. Raw Lua steps at turtle actions. Full platform, native UI and classroom acceptance remains pending; see `acceptance.md`.
