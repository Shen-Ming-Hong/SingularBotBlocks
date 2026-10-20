# 課堂用詞（繁體中文）

學生看到的積木、選單、對話框與訊息，以國小三年級聽得懂的說法為準；和遊戲有關的名詞（方塊、物品、物品欄、紅石訊號、挖掘、放置、丟棄、撿起、裝備等）一律使用 Minecraft 1.21.1 官方 `zh_tw` 語言檔的名稱。英文介面不受影響。

Blockly 內建 zh-hant 語言包的用詞在 `editor/src/wording.js` 覆寫；自訂積木與工作台文字在 `editor/src/blocks.js`、`editor/src/editor.js`、`editor/src/index.html`；原生控制台與診斷訊息在 `src/main/resources/assets/singularbots/lang/zh_tw.json`。`editor/test/wording.test.mjs` 會擋下下列難詞再次出現在積木與選單中。

## 用詞原則

| 不用 | 改用 | 理由 |
| --- | --- | --- |
| 賦值、設定此變數 | 把 X 設成 Y | 說出動作本身 |
| 區塊 | 積木 | 與工具箱、編輯器一致 |
| 循環 | 迴圈、重複 | 臺灣用語，與「迴圈」分類一致 |
| 陳述式、返回、傳回、true／false | 積木、結果是「是」／「否」 | 程式術語 |
| 真／假 | 是／否 | 條件寫成陳述句，例如「前方有方塊」，接在「如果」「一直重複，只要」「不是」後面都讀得通 |
| 字串、串連 | 文字、接起來 | 程式術語 |
| 再製、添加評論 | 複製一份、加上註解 | 一般用語；積木右鍵只有「複製一份」，馬上在原積木旁放一個副本；用 Cmd+C 複製後可在空白處按右鍵選「貼上」，副本放在按右鍵的位置 |
| 背包 | 物品欄 | Minecraft 官方 `container.inventory` |
| 吸取 | 撿起 | Minecraft 官方 `subtitles.entity.item.pickup` |
| 丟出 | 丟棄 | Minecraft 官方 `key.drop` |
| 海龜 | 機器人 | 物品名稱是「程式機器人」 |
| 教師（給學生看的訊息） | 老師 | 學生的說法；教師專用介面維持原文 |
| 記錄狀態／復原狀態 | 儲存復原點／回到復原點 | 全模組統一一組說法 |

沿用官方名稱、不另改：方塊、物品、挖掘、放置、攻擊、裝備、紅石訊號、鑽石鎬、除錯、世界邊界。

## Blockly 內建積木與選單

| 訊息 | 原本（Blockly zh-hant） | 現在 |
| --- | --- | --- |
| `DUPLICATE_BLOCK` | 再製 | 複製一份 |
| `ADD_COMMENT` | 添加評論 | 加上註解 |
| `DELETE_BLOCK` | 刪除區塊 | 刪除積木 |
| `DELETE_X_BLOCKS` | 刪除%1個區塊 | 刪除 %1 個積木 |
| `DELETE_ALL_BLOCKS` | 刪除全部 %1 個區塊？ | 要刪除全部 %1 個積木嗎？ |
| `CLEAN_UP` | 清理區塊 | 排整齊 |
| `COLLAPSE_BLOCK` | 收合區塊 | 收起積木 |
| `COLLAPSE_ALL` | 收合區塊 | 收起全部積木 |
| `EXPAND_BLOCK` | 展開區塊 | 展開積木 |
| `EXPAND_ALL` | 展開區塊 | 展開全部積木 |
| `DISABLE_BLOCK` | 停用區塊 | 停用積木 |
| `ENABLE_BLOCK` | 啟用區塊 | 啟用積木 |
| `INLINE_INPUTS` | 單行輸入 | 排成一行 |
| `EXTERNAL_INPUTS` | 外部輸入 | 排成多行 |
| `COLLAPSED_WARNINGS_WARNING` | 收合含有警告的區塊。 | 收起來的積木裡有問題。 |
| `WORKSPACE_COMMENT_DEFAULT_TEXT` | 來說些事情... | 在這裡寫下你的想法… |
| `WORKSPACE_ARIA_LABEL` | Blockly工作區 | 積木工作區 |
| `CHANGE_VALUE_TITLE` | 修改值： | 改成： |
| `NEW_VARIABLE_TITLE` | 新變數名稱： | 新變數的名字： |
| `RENAME_VARIABLE` | 重新命名變數... | 幫變數改名字… |
| `RENAME_VARIABLE_TITLE` | 將所有「%1」變數重新命名為： | 把所有「%1」變數改名為： |
| `DELETE_VARIABLE_CONFIRMATION` | 刪除使用%1次的「%2」變數？ | 變數「%2」用在 %1 個積木裡，要全部刪除嗎？ |
| `VARIABLE_ALREADY_EXISTS` | 已存在變數「%1」。 | 已經有叫做「%1」的變數了。 |
| `VARIABLE_ALREADY_EXISTS_FOR_ANOTHER_TYPE` | 已存在名為「%1」，且用於其它類型「%2」的變數。 | 已經有叫做「%1」的變數了，它是「%2」類型。 |
| `VARIABLES_SET` | 賦值 %1 成 %2 | 把 %1 設成 %2 |
| `VARIABLES_SET_TOOLTIP` | 設定此變數，好和輸入結果相等。 | 把這個變數的值換成右邊的值。 |
| `VARIABLES_SET_CREATE_GET` | 建立「取得 %1」 | 建立「%1」積木 |
| `VARIABLES_GET_TOOLTIP` | 返回此變數的值。 | 這個變數現在的值。 |
| `VARIABLES_GET_CREATE_SET` | 建立「賦值 %1」 | 建立「把 %1 設成」積木 |
| `MATH_CHANGE_TITLE` | 修改 %1 自 %2 | 把 %1 加上 %2 |
| `MATH_CHANGE_TOOLTIP` | 將數字加到變數「%1」。 | 把變數「%1」加上一個數字（用負數就是減掉）。 |
| `CONTROLS_IF_MSG_THEN` | 執行 | 就 |
| `CONTROLS_IF_MSG_ELSEIF` | 否則，如果 | 不然，如果 |
| `CONTROLS_IF_MSG_ELSE` | 否則 | 不然 |
| `CONTROLS_IF_ELSEIF_TITLE_ELSEIF` | 否則，如果 | 不然，如果 |
| `CONTROLS_IF_ELSE_TITLE_ELSE` | 否則 | 不然 |
| `CONTROLS_IF_TOOLTIP_1` | 當值為 true 時，執行一些陳述式。 | 如果條件是「是」，就做裡面的積木。 |
| `CONTROLS_IF_TOOLTIP_2` | 如果值為 true 就執行第一塊陳述式，否則執行第二塊。 | 如果條件是「是」，就做第一組積木；不然就做第二組。 |
| `CONTROLS_IF_TOOLTIP_3` | 如果第一個值為 true，則執行第一塊陳述式。否則，當第二個值為 true 時，則執行第二塊陳述式。 | 如果第一個條件是「是」，就做第一組積木；不然，如果第二個條件是「是」，就做第二組。 |
| `CONTROLS_IF_TOOLTIP_4` | 如果第一個值為 true，則執行第一塊陳述式。否則當第二個值為 true 時，則執行第二塊陳述式。如果前幾個敘述都不為 true，則執行最後一塊陳述式。 | 如果第一個條件是「是」，就做第一組積木；不然，如果第二個條件是「是」，就做第二組；都不是的話，就做最後一組。 |
| `CONTROLS_IF_IF_TOOLTIP` | 添加、刪除或重新排列各部份以重新配置這個「如果」區塊。 | 加上、拿掉或換順序，改變這個「如果」積木。 |
| `CONTROLS_IF_ELSEIF_TOOLTIP` | 添加條件到「如果」區塊。 | 在「如果」積木裡多加一個條件。 |
| `CONTROLS_IF_ELSE_TOOLTIP` | 添加一個最終、所有條件都執行的部份到「如果」區塊中。 | 在「如果」積木最後加上「不然」，上面的條件都不是時就做這裡。 |
| `CONTROLS_REPEAT_TITLE` | 重複%1次 | 重複 %1 次 |
| `CONTROLS_REPEAT_INPUT_DO` | 執行 | 做 |
| `CONTROLS_REPEAT_TOOLTIP` | 重複執行某些陳述式多次。 | 把裡面的積木重複做好幾次。 |
| `CONTROLS_WHILEUNTIL_OPERATOR_WHILE` | 重複，當 | 一直重複，只要 |
| `CONTROLS_WHILEUNTIL_OPERATOR_UNTIL` | 重複直到 | 一直重複，直到 |
| `CONTROLS_WHILEUNTIL_INPUT_DO` | 執行 | 做 |
| `CONTROLS_WHILEUNTIL_TOOLTIP_WHILE` | 當值為 true 時，執行一些陳述式。 | 只要條件是「是」，就一直做裡面的積木。 |
| `CONTROLS_WHILEUNTIL_TOOLTIP_UNTIL` | 當值為 false 時，執行一些陳述式。 | 一直做裡面的積木，直到條件變成「是」才停。 |
| `CONTROLS_FOR_TITLE` | 循環計數 %1 從 %2 到 %3 每次增加 %4 | 讓 %1 從 %2 數到 %3 每次加 %4 |
| `CONTROLS_FOR_INPUT_DO` | 執行 | 做 |
| `CONTROLS_FOR_TOOLTIP` | 從起始數到結尾數中取出變數「%1」的值，按指定的時間間隔，執行指定的區塊。 | 讓變數「%1」從第一個數開始，每次加一點，一直數到第二個數；每數一次就做一次裡面的積木。 |
| `CONTROLS_FLOW_STATEMENTS_OPERATOR_BREAK` | 中斷循環 | 跳出迴圈 |
| `CONTROLS_FLOW_STATEMENTS_OPERATOR_CONTINUE` | 繼續下一個循環 | 跳到下一圈 |
| `CONTROLS_FLOW_STATEMENTS_TOOLTIP_BREAK` | 中斷當前的循環。 | 馬上離開這個迴圈。 |
| `CONTROLS_FLOW_STATEMENTS_TOOLTIP_CONTINUE` | 跳過這個循環的其餘步驟，並繼續下一次的循環。 | 這一圈剩下的積木不做了，直接開始下一圈。 |
| `CONTROLS_FLOW_STATEMENTS_WARNING` | 警告：此區塊僅可用於循環內。 | 注意：這個積木只能放在迴圈裡面。 |
| `LOGIC_BOOLEAN_TRUE` | 真 | 是 |
| `LOGIC_BOOLEAN_FALSE` | 假 | 否 |
| `LOGIC_BOOLEAN_TOOLTIP` | 返回真或假。 | 「是」或「否」。 |
| `LOGIC_COMPARE_TOOLTIP_EQ` | 如果兩個輸入的結果相等，返回 true。 | 兩邊一樣時是「是」。 |
| `LOGIC_COMPARE_TOOLTIP_NEQ` | 如果兩個輸入的結果不相等，返回 true。 | 兩邊不一樣時是「是」。 |
| `LOGIC_COMPARE_TOOLTIP_LT` | 如果第一個輸入結果比第二個小，返回 true。 | 左邊比右邊小時是「是」。 |
| `LOGIC_COMPARE_TOOLTIP_LTE` | 如果第一個輸入結果小於或等於第二個，返回 true。 | 左邊比右邊小，或一樣大時是「是」。 |
| `LOGIC_COMPARE_TOOLTIP_GT` | 如果第一個輸入結果大於第二個，返回 true。 | 左邊比右邊大時是「是」。 |
| `LOGIC_COMPARE_TOOLTIP_GTE` | 如果第一個輸入結果大於或等於第二個，返回 true。 | 左邊比右邊大，或一樣大時是「是」。 |
| `LOGIC_OPERATION_AND` | 且 | 而且 |
| `LOGIC_OPERATION_OR` | 或 | 或者 |
| `LOGIC_OPERATION_TOOLTIP_AND` | 如果兩個輸入結果都為 true，則返回 true。 | 兩邊都是「是」，結果才是「是」。 |
| `LOGIC_OPERATION_TOOLTIP_OR` | 如果至少一個輸入結果為 true，返回 true。 | 只要有一邊是「是」，結果就是「是」。 |
| `LOGIC_NEGATE_TITLE` | %1 不成立 | 不是 %1 |
| `LOGIC_NEGATE_TOOLTIP` | 如果輸入結果是 false，則返回 true；如果輸入結果是 true，則返回 false。 | 把「是」變成「否」，把「否」變成「是」。 |
| `MATH_NUMBER_TOOLTIP` | 數字 | 一個數字。 |
| `MATH_ARITHMETIC_TOOLTIP_ADD` | 返回兩個數字的總和。 | 兩個數相加。 |
| `MATH_ARITHMETIC_TOOLTIP_MINUS` | 傳回兩數之差。 | 第一個數減掉第二個數。 |
| `MATH_ARITHMETIC_TOOLTIP_MULTIPLY` | 返回兩個數字的乘積。 | 兩個數相乘。 |
| `MATH_ARITHMETIC_TOOLTIP_DIVIDE` | 傳回兩數之商。 | 第一個數除以第二個數。 |
| `MATH_ARITHMETIC_TOOLTIP_POWER` | 返回以第一個數值為底數，以第二個數值為冪的結果。 | 第一個數乘上自己好幾次（次數是第二個數）。 |
| `MATH_MODULO_TOOLTIP` | 返回兩個數字相除的餘數。 | 兩個數相除後，剩下來的餘數。 |
| `MATH_RANDOM_INT_TITLE` | 隨機取整數 %1 到 %2 | 從 %1 到 %2 隨機選一個數 |
| `MATH_RANDOM_INT_TOOLTIP` | 在指定二個數之間隨機取一個整數（包含）。 | 在兩個數之間（兩頭也算）隨機選一個整數。 |
| `MATH_RANDOM_FLOAT_TITLE_RANDOM` | 隨機取分數 | 隨機小數（0 到 1） |
| `MATH_RANDOM_FLOAT_TOOLTIP` | 在 0.0（包含）和 1.0（不包含）之間隨機取一個分數。 | 隨機選一個 0 到 1 之間的小數（不會剛好是 1）。 |
| `TEXT_TEXT_TOOLTIP` | 一個字母、一個字詞或一行字 | 一個字、一個詞或一句話。 |
| `TEXT_JOIN_TITLE_CREATEWITH` | 字串組合 | 把文字接起來 |
| `TEXT_JOIN_TOOLTIP` | 通過連接任意數量的項目來建立一串文字。 | 把好幾段文字接成一段。 |
| `TEXT_CREATE_JOIN_TITLE_JOIN` | 串連 | 接起來 |
| `TEXT_CREATE_JOIN_ITEM_TITLE_ITEM` | 項目 | 文字 |
| `TEXT_CREATE_JOIN_TOOLTIP` | 添加、刪除或重新排列各部份以重新配置這個文字區塊。 | 加上、拿掉或換順序，改變要接起來的文字。 |
| `TEXT_CREATE_JOIN_ITEM_TOOLTIP` | 添加一個項目到字串中。 | 多接一段文字。 |
| `TEXT_LENGTH_TITLE` | %1的長度 | %1 有幾個字 |
| `TEXT_LENGTH_TOOLTIP` | 返回這串文字的字母數（包含空格）。 | 數一數這段文字有幾個字（空格也算）。 |
| `PROCEDURES_DEFNORETURN_TITLE`／`PROCEDURES_DEFRETURN_TITLE` | 到 | 函式 |
| `PROCEDURES_DEFNORETURN_PROCEDURE` | 做些什麼 | 做一件事 |
| `PROCEDURES_DEFRETURN_PROCEDURE` | 做些什麼 | 算出答案 |
| `PROCEDURES_BEFORE_PARAMS`／`PROCEDURES_CALL_BEFORE_PARAMS` | 與： | 輸入： |
| `PROCEDURES_DEFRETURN_RETURN` | 返回 | 交回答案 |
| `PROCEDURES_ALLOW_STATEMENTS` | 允許陳述式 | 裡面可以放積木 |
| `PROCEDURES_DEFNORETURN_TOOLTIP` | 創建一個無回傳值的函式。 | 把好幾個積木組成一個新的積木，取個名字，之後就能一直拿來用。 |
| `PROCEDURES_DEFRETURN_TOOLTIP` | 創建一個有回傳值的的函式。 | 做完裡面的積木後，交回一個答案。 |
| `PROCEDURES_CALLNORETURN_TOOLTIP` | 執行使用者定義的函式「%1」。 | 做一次函式「%1」裡面的積木。 |
| `PROCEDURES_CALLRETURN_TOOLTIP` | 執行使用者定義的函式「%1」，並使用它的回傳值。 | 做一次函式「%1」，拿回它交回的答案。 |
| `PROCEDURES_CREATE_DO` | 建立「%1」 | 建立「%1」積木 |
| `PROCEDURES_HIGHLIGHT_DEF` | 反白顯示函式定義 | 找到這個函式 |
| `PROCEDURES_IFRETURN_TOOLTIP` | 如果值為 true，則返回第二個值。 | 如果條件是「是」，就馬上結束這個函式，並交回答案。 |
| `PROCEDURES_IFRETURN_WARNING` | 警告：這個區塊只可以在定義函式時使用。 | 注意：這個積木只能放在函式裡面。 |
| `PROCEDURES_DEF_DUPLICATE_WARNING` | 警告: 此函式中有重複的參數。 | 注意：有兩個輸入的名字一樣。 |
| `PROCEDURES_CALL_DISABLED_DEF_WARNING` | 無法執行使用者定義的函式「%1」，因為定義區塊已停用。 | 函式「%1」被停用了，所以這個積木不能用。 |
| `PROCEDURES_MUTATORCONTAINER_TITLE` | 輸入 | 輸入 |
| `PROCEDURES_MUTATORARG_TITLE` | 輸入名稱： | 輸入的名字： |

## 自訂積木與工作台

| 原本 | 現在 |
| --- | --- |
| 海龜動作（分類） | 機器人動作 |
| 背包（分類） | 物品欄 |
| （新分類） | 函式：先列出程式庫裡其他存好的程式（「執行程式」積木），再放「函式」積木（做一件事／算出答案） |
| 丟出 %1 整組物品 | 把選取的整組物品丟棄到 %1 |
| 吸取 %1 的物品 | 撿起 %1 的物品 |
| 嘗試 %1 是否成功 | %1 成功 |
| 物品／方塊 ID %1 | 物品／方塊 %1 |
| 選擇背包格 %1 | 選取物品欄第 %1 格 |
| 移到背包格 %1 數量 %2 | 把選取的物品搬到第 %1 格，搬 %2 個 |
| 背包格 %1 的物品 ID | 第 %1 格是什麼物品 |
| %1 的方塊 ID | %1 是什麼方塊 |
| %1 紅石訊號 | %1 有紅石訊號 |
| 設定 %1 紅石 %2 | 讓 %1 發出紅石訊號 %2 |
| 確認 %1 否則錯誤 %2 | 檢查 %1 不對就停下並顯示 %2 |
| 呼叫程式 %1 | 執行程式 %1 |
| 記錄海龜狀態／復原海龜狀態 | 儲存復原點／回到復原點 |
| 隨機真假 | 隨機選是或否 |
| 舊版動作選單：丟出／吸取前方 | 丟棄到前方／撿起前方 |
| 舊版動作選單：前方相同方塊 | 前方是選取的方塊 |
| 舊版動作選單：剩餘空間／目前格子 | 還能放幾個／選取第幾格 |
| 物品欄選格：選擇機器人背包格 | 選擇機器人物品欄的格子 |
| 物品欄選格：這是這台機器人目前的背包 | 這是這台機器人現在的物品欄 |
| 物品欄選格：沒有連線機器人，只能選格子編號 | 還沒連上機器人，只能選格子號碼 |
| 積木提示：可接上積木：真假 | 可接上積木：是／否 |
| 工作台標題：海龜程式工作台 | 機器人程式工作台 |
| 本機測試工作台 · 尚未連接海龜 | 本機測試工作台 · 還沒連上機器人 |
| 機器人設定：保存復原點／復原狀態 | 儲存復原點／回到復原點 |
| 機器人設定：使用背包格 | 使用物品欄第幾格 |
| 教師已關閉 Lua 程式；請使用積木程式。 | 老師關閉了 Lua 程式；請用積木寫程式。 |
| 教師已停用積木編輯；仍可使用 Lua 程式。 | 老師關閉了積木編輯；還可以用 Lua 寫程式。 |
| 只有教師可鎖定 | 只有老師能鎖定 |
| 已要求保存復原點 | 正在儲存復原點 |
| 已刪除；世界資料內保留備份 | 已刪除；世界存檔裡還留有備份 |
| 已載入本機草稿；若版本衝突，請按「另存副本」。 | 已打開這台電腦裡的草稿；如果和存好的版本不一樣，請按「另存副本」。 |

## 原生控制台與診斷訊息（zh_tw.json）

| 鍵（省略 singularbots） | 原本 | 現在 |
| --- | --- | --- |
| `screen.editor` | ComputerCraftEdu 積木編輯器 | Singular Bot Blocks 積木程式編輯器 |
| `screen.browser_error` | 瀏覽器尚未啟動。請確認已安裝對應平台的原生套件；草稿不會被刪除。 | 編輯器打不開。請老師確認這台電腦已安裝需要的套件；你的草稿不會不見。 |
| `item.remote` | 海龜遙控器 | 機器人遙控器 |
| `message.denied` | 你沒有操作這台機器人的權限，或它已被教師鎖定。 | 你不能操作這台機器人，可能它不是你的，或被老師鎖定了。 |
| `message.owned` | 這台機器人屬於 %s，你不能操作或查看它的程式。 | 這台機器人是 %s 的，你不能操作它，也不能看它的程式。 |
| `message.unavailable` | 海龜未載入、距離太遠，或尚未綁定。 | 找不到機器人：它可能太遠，或所在的區域還沒載入。 |
| `remote.camera` | 海龜視角 | 機器人視角 |
| `remote.name` | 海龜名稱 | 機器人名字 |
| `remote.rename` | 命名 | 改名字 |
| `remote.select` | 切換背包格 | 切換物品欄格子 |
| `remote.program` | 編寫海龜程式 | 寫機器人程式 |
| `message.emergency_stop` | 教師已急停所有教學海龜 | 老師已讓所有機器人緊急停止 |
| `block.build_allow` | 允許海龜建造 | 允許機器人建造 |
| `block.build_deny` | 禁止海龜建造 | 禁止機器人建造 |
| `block.turtle_border` | 海龜邊界 | 機器人邊界 |
| `block.turtle_anti_border` | 海龜邊界隔離 | 取消機器人邊界 |
| `remote.snapshot` | 記錄狀態 | 儲存復原點 |
| `remote.restore` | 復原狀態 | 回到復原點 |
| `message.local_recovery` | 伺服器儲存失敗，程式已保留在本機；下次開啟可按「復原本機草稿」。 | 存到伺服器失敗了，程式先存在這台電腦裡；下次打開時按「復原未存草稿」就能拿回來。 |
| `remote.inventory` | 海龜背包 | 機器人物品欄 |
| `remote.selected_slot` | 目前選取第 %s 格 | 現在選取第 %s 格 |
| `remote.lock_turtle` | 鎖定海龜 | 鎖定機器人 |
| `remote.teacher_only` | 只有 OP2 教師能變更鎖定 | 只有老師（OP2）能鎖定或解鎖 |
| `remote.return_item` | 請先將手上的物品放回格子，再切換頁籤。 | 請先把手上的物品放回格子，再切換頁面。 |
| `remote.choose_program` | 請先在程式頁籤選擇或儲存程式。 | 請先在程式頁面選一個程式，或先儲存程式。 |
| `diagnostic.queue_full` | 操作佇列已滿，請稍後再試 | 動作排太多了，請等一下再試 |
| `diagnostic.movement_obstructed` | 前方有障礙物，無法移動 | 前面被擋住了，不能移動 |
| `diagnostic.movement_failed` | 移動失敗 | 沒辦法移動 |
| `diagnostic.too_low` | 已到達最低高度，無法繼續移動 | 已經到最低的地方，不能再往下 |
| `diagnostic.too_high` | 已到達最高高度，無法繼續移動 | 已經到最高的地方，不能再往上 |
| `diagnostic.outside_world` | 無法離開世界範圍 | 不能離開這個世界 |
| `diagnostic.protected_area` | 無法進入受保護區域 | 不能進入被保護的區域 |
| `diagnostic.unloaded_world` | 無法進入尚未載入的區域 | 不能進入還沒載入的區域 |
| `diagnostic.world_border` | 無法越過世界邊界 | 不能越過世界邊界 |
| `diagnostic.no_items` | 所選格子沒有可放置的物品 | 選取的格子裡沒有可以放置的物品 |
| `diagnostic.place_block` | 無法在此放置方塊 | 這裡不能放置方塊 |
| `diagnostic.place_item` | 無法在此使用物品 | 這裡不能使用這個物品 |
| `diagnostic.no_dig_tool` | 沒有可挖掘的工具 | 沒有可以挖掘的工具 |
| `diagnostic.no_attack_tool` | 沒有可攻擊的工具 | 沒有可以攻擊的工具 |
| `diagnostic.unbreakable` | 無法挖掘不可破壞的方塊 | 這個方塊挖不壞 |
| `diagnostic.mine_unbreakable` | 無法挖掘不可破壞的方塊 | 這個方塊挖不壞 |
| `diagnostic.wrong_tool` | 此工具無法挖掘該方塊 | 這個工具挖不動那個方塊 |
| `diagnostic.nothing_dig` | 前方沒有可挖掘的方塊 | 前面沒有可以挖掘的方塊 |
| `diagnostic.nothing_attack` | 前方沒有可攻擊的對象 | 前面沒有可以攻擊的東西 |
| `diagnostic.protected_block` | 無法破壞受保護的方塊 | 不能破壞被保護的方塊 |
| `diagnostic.invalid_slot` | 無效的背包格子 | 沒有這個物品欄格子 |
| `diagnostic.teacher_required` | 需要教師權限 | 這個要老師才能做 |
| `diagnostic.unknown_command` | 未知的控制指令 | 機器人看不懂這個指令 |
| `diagnostic.control_expired` | 控制權限已失效，請重新開啟機器人 | 已經不能控制了，請重新打開機器人 |
| `diagnostic.cancelled` | 操作已因停止或教師鎖定而取消 | 因為程式停止或老師鎖定，動作取消了 |
| `diagnostic.teacher_stopped` | 教師已急停或停用機器人 | 老師讓機器人停下來了 |
| `diagnostic.teacher_locked` | 教師已鎖定這台機器人 | 老師鎖定了這台機器人 |
| `diagnostic.build_denied` | 教師未允許在此建造或挖掘 | 老師不允許在這裡建造或挖掘 |
| `diagnostic.inventory_denied` | 不允許操作此背包 | 不能動這個物品欄 |
| `diagnostic.boundary` | 教師設定的邊界禁止移動 | 老師設定的邊界不讓機器人過去 |
| `diagnostic.pickaxe_fixed` | 無法移除機器人內建的鑽石鎬 | 機器人內建的鑽石鎬拿不下來 |
| `diagnostic.snapshot_first` | 請先保存復原點 | 請先儲存復原點 |
| `diagnostic.junior_required` | 需要程式機器人 | 要用程式機器人才行 |
| `diagnostic.snapshot_inactive` | 此復原點已失效 | 這個復原點已經不能用了 |
| `diagnostic.restore_changed` | 復原衝突：機器人的位置或背包已改變 | 回不去：機器人的位置或物品欄已經變了 |
| `diagnostic.restore_protected` | 復原目標尚未載入或受到保護 | 回不去：那個地方還沒載入，或被保護了 |
| `diagnostic.restore_drop` | 復原衝突：掉落物已被移動、撿取或改變 | 回不去：掉在地上的物品被移動、撿走或改變了 |
| `diagnostic.restore_occupied` | 復原位置已被占用 | 回不去：原來的位置被占住了 |
| `diagnostic.restore_return` | 無法回到復原點的位置 | 沒辦法回到復原點的位置 |
| `diagnostic.snapshot_restored` | 已復原；繼續前請保存新的復原點 | 已回到復原點；要繼續的話，請先儲存新的復原點 |
| `diagnostic.restore_actor` | 復原衝突：其他玩家已改變記錄中的方塊 | 回不去：有別的玩家改了記住的方塊 |
| `diagnostic.snapshot_limit` | 變更超過復原點上限，無法復原 | 改變的東西太多，回不到復原點 |
| `diagnostic.snapshot_budget` | 復原點超過安全上限 | 復原點記的東西太多了 |
| `diagnostic.restore_container` | 復原衝突：容器在操作之間已被改變 | 回不去：箱子之類的容器被改過了 |
| `diagnostic.restore_item` | 復原衝突：物品在操作之間已被改變 | 回不去：物品被改過了 |
| `diagnostic.restore_inventory` | 復原衝突：背包已被其他操作改變 | 回不去：物品欄被別的動作改過了 |
| `diagnostic.restore_items` | 掉落物過多，無法安全復原 | 地上掉落的物品太多，沒辦法安全地回去 |
| `diagnostic.restore_nearby` | 附近掉落物過多，無法安全復原 | 附近掉落的物品太多，沒辦法安全地回去 |
| `diagnostic.restore_fluid` | 物品使用或液體放置無法安全復原 | 用過物品或倒過水、岩漿，沒辦法安全地回去 |
| `diagnostic.restore_entity` | 生成生物無法安全復原 | 生出了生物，沒辦法安全地回去 |
| `diagnostic.restore_combat` | 戰鬥造成的變更無法安全復原 | 打鬥造成的改變沒辦法安全地回去 |
| `diagnostic.restore_at` | 復原衝突：%s 的方塊已改變 | 回不去：%s 的方塊已經變了 |
| `diagnostic.restore_denied_at` | 教師未允許在 %s 復原 | 老師不允許在 %s 回到復原點 |
| `diagnostic.compile_variables` | （英文）Too many variables | 變數太多了（最多 128 個） |
| `diagnostic.compile_variable` | （英文）Duplicate or invalid variable | 有變數的名字重複了，或不能用 |
| `diagnostic.compile_one_start` | （英文）Only one start block is allowed | 只能有一個「當程式開始」積木 |
| `diagnostic.compile_function_name` | （英文）Duplicate or invalid function name | 有函式的名字重複了，或不能用 |
| `diagnostic.compile_function_input` | （英文）Unknown or repeated function input | 函式的輸入找不到，或重複了 |
| `diagnostic.compile_functions` | （英文）Too many functions | 函式太多了（最多 256 個） |
| `diagnostic.compile_workspace_nesting` | （英文）Workspace nesting exceeds 128 | 積木一層包一層太多了（超過 128 層） |
| `diagnostic.compile_block_id` | （英文）Missing or duplicate stable block ID | 積木的編號不見了或重複了 |
| `diagnostic.compile_blocks` | （英文）Workspace exceeds 4096 blocks | 積木太多了（最多 4096 個） |
| `diagnostic.compile_field_size` | （英文）Field too large | 積木裡的文字太長了 |
| `diagnostic.compile_statement_nesting` | （英文）Too many nested statements | 積木一層包一層太多了（超過 128 層） |
| `diagnostic.compile_command` | （英文）Unknown turtle command | 不認得這個機器人動作 |
| `diagnostic.compile_argument` | （英文）Command does not accept an argument | 這個動作不能加上數字 |
| `diagnostic.compile_choose_program` | （英文）Choose a program to call | 「執行程式」積木還沒選要執行哪個程式 |
| `diagnostic.compile_return` | （英文）Return must be inside a function | 「交回答案」積木要放在函式裡面 |
| `diagnostic.compile_break` | （英文）Break must be inside a loop | 「跳出迴圈」積木要放在迴圈裡面 |
| `diagnostic.compile_only_break` | （英文）Only break is supported | 這裡只能用「跳出迴圈」 |
| `diagnostic.compile_expression_nesting` | （英文）Expression nesting exceeds 64 | 積木一個塞一個太多層了（超過 64 層） |
| `diagnostic.compile_number` | （英文）Number is not finite | 數字太大了，或不是正常的數字 |
| `diagnostic.compile_registry_name` | （英文）Invalid item or block ID | 物品或方塊的 ID 不正確 |
| `diagnostic.compile_text_join` | （英文）Invalid text join size | 「把文字接起來」的段數不正確 |
| `diagnostic.compile_query` | （英文）Invalid turtle query | 不認得這個機器人偵測 |
| `diagnostic.compile_boolean_action` | （英文）Invalid boolean turtle action | 這個動作不能當成「是」或「否」來用 |
| `diagnostic.compile_size` | （英文）Compiled program exceeds 1 MiB | 程式太大了（超過 1 MiB） |
| `diagnostic.compile_variable_id` | （英文）Unknown variable ID | 找不到這個變數 |
| `diagnostic.compile_unknown_function` | （英文）Unknown function: %s | 找不到函式「%s」 |
| `diagnostic.compile_unsupported_block` | （英文）Unsupported block: %s | 不支援這個積木：%s |
| `diagnostic.compile_choice` | （英文）Invalid %s | 積木的選項不正確（%s） |
| `diagnostic.compile_missing_field` | （英文）Missing field %s | 積木少了欄位（%s） |
| `diagnostic.compile_malformed_block` | （英文）Malformed block data: %s | 積木資料有問題：%s |
| `diagnostic.compile_malformed_workspace` | （英文）Malformed workspace: %s | 積木工作區資料有問題：%s |
