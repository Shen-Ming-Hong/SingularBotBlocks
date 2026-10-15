# 第三版素材交付報告

已完成78張逐檔驗證PNG（含18格eyes與檢查圖），另加contact_sheet.png、standard.json、validation.json。全部來源重新使用imagegen內建工具生成，沒有沿用第二版貼圖或對refs/v2_contact_sheet.png做濾波。

## 提取標準與來源

out/standard.json 為先建立的標準：8倍輸出、alpha>=128、透明RGB歸零；烤漆灰／黃銅／槍鐵各4階、藍螢幕3階，另允許steel_white的#edf1e8為1階發光高光。色票順序為暗邊、次暗、主色、亮邊（螢幕為暗、中、亮）。完整提示詞：work/prompts/*.md；生成原圖：work/decoded/*.png；SHA256：work/imagegen-jobs.json。

生成後做 BOX 格點取樣、最近色票量化、NEAREST 8倍放大。共用按鈕底座逐像素相同；上下飛行箭頭從新生成前後箭頭作確定性色票替換，不加尾巴。平塗表面孤立量化色合併到最近的接觸色，保留刻意藍色筆畫及透明輪廓；烤漆主色必要時合併最近次色到55%。panel中心標準化為無縫純色，field中段伸縮；修正strip重新生成紅磚（灰白灰縫）與小紅X。機身透明角填最暗槍鐵以確保整面不透明。

## hatch-pet 流程適配

使用 scripts/prepare_pet_run.py 的 create_layout_guide、extract_strip_frames.py 的 extract_slot_frames、inspect_frames.py 的 alpha_nonzero_count、make_contact_sheet.py 的 checker。eyes使用3條連續6格生成strip，逐條固定格位擷取；使用技能connected_components／component_group_image擷取原始生成眼睛或字元，再註冊成指定格點位置、量化、驗證，合成18格橫向strip（字形全部取自生成圖，未用字型或程式描畫）；不是程式手畫眼睛。切眼睛時 fit_to_cell 採viewport原樣回傳，避免192×208寵物padding改變11×8玻璃位置。擷取記錄：work/eyes-extraction.json。

UI生成位置偏離等分格位，因此實際裁切框記錄於work/ui-boxes.json；只調整擷取框，未重畫UI。

缺少 load_workspace_dependencies 工具；依TASK允許的適配說明，使用既有 本機既有 Python 虛擬環境（Pillow11.3.0／NumPy2.4.4），未修改技能scripts。非寵物素材，不輸出8×11 pet atlas、方向測試或pet套件。

## H／I 結構檢查

- moveforward_flight_same_shape: PASS
- moveback_flight_same_shape: PASS
- brick_visible_light_mortar: PASS
- button_common_base: PASS
- body_faces_exact_sheet_crops: PASS
- body_sheet_gaps_transparent: PASS
- love_two_separate_hearts: PASS
- front_screen_matches_open: PASS
- eyes_sheet_order_and_1px_gaps: PASS
- remote_item_matches_tab_remote: PASS
- panel_seamless_center: PASS

body_sheet目標95×62／760×496，面座標與一格透明間隙照TASK；body_check由實際top/front/right組成3/4視圖。controller_check由panel九宮格、左3×3按鈕、下排4個執行鍵、右4個頁籤及另一塊4×4slot拼合。eyes_sheet目標215×8／1720×64，18格按TASK順序排列，格間1目標像素透明。body_front(x7–17,y9–16)與eyes_open完全相同；各eyes11×8／88×64全不透明。

## 逐檔驗證

孤立像素以目標格點、上下左右4鄰居計算。原始數、例外坐標與理由完整見out/validation.json；表中為扣除刻意小點後比例。例外限螢幕眼睛／字元筆畫／高光、10×10小紅X筆畫及四角黃銅鉚釘。機身烤漆比例只在paint_gray像素中計算。

|檔案|目標尺寸|PNG尺寸|8×8|色票|全不透明|孤立原數／例外|孤立比例|烤漆主色|結果|
|---|---:|---:|---|---|---|---:|---:|---:|---|
|button_moveforward.png|18×18|144×144|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|button_moveback.png|18×18|144×144|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|button_turnleft.png|18×18|144×144|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|button_turnright.png|18×18|144×144|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|button_moveup.png|18×18|144×144|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|button_movedown.png|18×18|144×144|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|button_dig.png|18×18|144×144|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|button_place.png|18×18|144×144|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|button_show_turtle_view.png|18×18|144×144|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|button_show_normal_view.png|18×18|144×144|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|button_run.png|18×18|144×144|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|button_step.png|18×18|144×144|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|button_pause.png|18×18|144×144|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|button_stop.png|18×18|144×144|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|button_abort.png|18×18|144×144|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|button_recall.png|18×18|144×144|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|button_previous.png|18×18|144×144|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|button_next.png|18×18|144×144|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|button_close.png|10×10|80×80|PASS|PASS|否（允許透明）|17／16|1.02%|—|PASS|
|panel.png|36×36|288×288|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|tab_selected.png|24×24|192×192|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|tab_normal.png|24×24|192×192|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|slot.png|18×18|144×144|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|field.png|36×12|288×96|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|tab_program.png|16×16|128×128|PASS|PASS|否（允許透明）|2／2|0.00%|—|PASS|
|tab_customise.png|16×16|128×128|PASS|PASS|否（允許透明）|1／1|0.00%|—|PASS|
|tab_inventory.png|16×16|128×128|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|tab_remote.png|16×16|128×128|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|body_top.png|24×22|192×176|PASS|PASS|是|0／0|0.00%|79.25% |PASS|
|body_bottom.png|24×22|192×176|PASS|PASS|是|0／0|0.00%|72.73% |PASS|
|body_front.png|24×24|192×192|PASS|PASS|是|0／0|0.00%|65.87% |PASS|
|body_back.png|24×24|192×192|PASS|PASS|是|0／0|0.00%|66.20% |PASS|
|body_left.png|22×24|176×192|PASS|PASS|是|0／0|0.00%|78.48% |PASS|
|body_right.png|22×24|176×192|PASS|PASS|是|0／0|0.00%|64.42% |PASS|
|pack_back.png|20×14|160×112|PASS|PASS|是|0／0|0.00%|—|PASS|
|pack_top.png|20×4|160×32|PASS|PASS|是|0／0|0.00%|—|PASS|
|pack_side.png|4×14|32×112|PASS|PASS|是|0／0|0.00%|—|PASS|
|deco_3dglasses.png|24×24|192×192|PASS|PASS|否（允許透明）|1／1|0.00%|—|PASS|
|deco_bandana_blue.png|24×24|192×192|PASS|PASS|否（允許透明）|1／1|0.00%|—|PASS|
|deco_bandana_purple.png|24×24|192×192|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|deco_bandana_red.png|24×24|192×192|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|deco_bandana_yellow.png|24×24|192×192|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|deco_creeper.png|24×24|192×192|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|deco_crown.png|24×24|192×192|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|deco_elf.png|24×24|192×192|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|deco_flames.png|24×24|192×192|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|deco_frown.png|24×24|192×192|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|deco_moustache.png|24×24|192×192|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|deco_smile.png|24×24|192×192|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|deco_snorkel.png|24×24|192×192|PASS|PASS|否（允許透明）|3／3|0.00%|—|PASS|
|deco_strawhat.png|24×24|192×192|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|deco_sunglasses.png|24×24|192×192|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|deco_teeth.png|24×24|192×192|PASS|PASS|否（允許透明）|1／1|0.00%|—|PASS|
|deco_tuxedo.png|24×24|192×192|PASS|PASS|否（允許透明）|2／2|0.00%|—|PASS|
|turtle_preview.png|36×36|288×288|PASS|PASS|是|0／0|0.00%|—|PASS|
|remote_item.png|16×16|128×128|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|body_sheet.png|95×62|760×496|PASS|PASS|否（允許透明）|0／0|0.00%|—|PASS|
|body_check.png|72×64|576×512|PASS|PASS|否（允許透明）|33／0|1.27%|—|PASS|
|eyes_open.png|11×8|88×64|PASS|PASS|是|0／0|0.00%|—|PASS|
|eyes_half.png|11×8|88×64|PASS|PASS|是|2／2|0.00%|—|PASS|
|eyes_closed.png|11×8|88×64|PASS|PASS|是|6／6|0.00%|—|PASS|
|eyes_look_left.png|11×8|88×64|PASS|PASS|是|2／2|0.00%|—|PASS|
|eyes_look_right.png|11×8|88×64|PASS|PASS|是|1／1|0.00%|—|PASS|
|eyes_happy.png|11×8|88×64|PASS|PASS|是|6／6|0.00%|—|PASS|
|eyes_wink.png|11×8|88×64|PASS|PASS|是|4／4|0.00%|—|PASS|
|eyes_surprised.png|11×8|88×64|PASS|PASS|是|4／4|0.00%|—|PASS|
|eyes_sleepy.png|11×8|88×64|PASS|PASS|是|8／8|0.00%|—|PASS|
|eyes_sad.png|11×8|88×64|PASS|PASS|是|5／5|0.00%|—|PASS|
|eyes_love.png|11×8|88×64|PASS|PASS|是|0／0|0.00%|—|PASS|
|eyes_confused.png|11×8|88×64|PASS|PASS|是|2／2|0.00%|—|PASS|
|eyes_cursor.png|11×8|88×64|PASS|PASS|是|0／0|0.00%|—|PASS|
|eyes_type_1.png|11×8|88×64|PASS|PASS|是|5／5|0.00%|—|PASS|
|eyes_type_2.png|11×8|88×64|PASS|PASS|是|7／7|0.00%|—|PASS|
|eyes_type_3.png|11×8|88×64|PASS|PASS|是|9／9|0.00%|—|PASS|
|eyes_type_heart_1.png|11×8|88×64|PASS|PASS|是|5／5|0.00%|—|PASS|
|eyes_type_heart_2.png|11×8|88×64|PASS|PASS|是|8／8|0.00%|—|PASS|
|eyes_sheet.png|215×8|1720×64|PASS|PASS|否（允許透明）|74／74|0.00%|—|PASS|
|controller_check.png|182×132|1456×1056|PASS|PASS|否（允許透明）|21／3|0.08%|—|PASS|

## 視覺QA與限制

contact_sheet.png為全部素材的帶標籤棋盤總覽，標籤與棋盤不受遊戲素材色票／透明規則限制。所有原圖為原創，僅參考機械材質風格。最終檢視結論見out/visual_qa.json。未在遊戲引擎載入驗證，16色染色與實際UI縮放仍待整合端檢查。

重跑：以本機 Python 執行 `scripts/finish_v3.py`（先保留work/decoded所有新生成來源）。

機器驗證總結果：PASS。

## 第四版（J／J-2）

更新 tab_customise、tab_inventory、button_show_turtle_view、button_show_normal_view，其餘素材不變。色票與 standard.json 相同；四張都通過 8×8 格、色票與 alpha 檢查，孤立像素扣除刻意小點後 0.88%–2.45%。按鈕底座在圖示區外與其他按鈕逐像素相同。tab_remote 和 remote_item 會在第五版重畫。

## 第五版（J-3／J-4）

重畫 tab_remote（remote_item 為同一張圖）與 deco_elf，色票與 standard.json 相同。遙控器照 J-3 的格點：直立深槍鐵機身、天線配紅球、藍色小螢幕、綠十字鍵和黃紅兩鈕。精靈帽所有不透明像素都在第 0–7 列，不再蓋住眼睛。
