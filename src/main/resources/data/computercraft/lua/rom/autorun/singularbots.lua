-- Only Junior turtles receive these APIs. Regular CC computers retain their startup.
if not edu or not turtleedu then return end
for _, suffix in ipairs({"", "Up", "Down"}) do
  local side = suffix == "Up" and "top" or suffix == "Down" and "bottom" or "front"
  turtleedu["checkRedstone" .. suffix] = function() return redstone.getInput(side) end
  turtleedu["setRedstone" .. suffix] = function(value)
    if type(value) ~= "boolean" then error("Expected boolean", 2) end
    redstone.setOutput(side, value)
    return true
  end
end
local calls = 0
local checkpoint = edu.checkpoint
-- A statement is highlighted only after the robot has finished its previous action, and each
-- statement keeps the highlight for one steady beat. Expressions inside a statement are not paced.
edu.checkpoint = function(id, values)
  local ok, wait = edu.pace()
  if ok and wait > 0 then sleep(wait / 20) end
  return checkpoint(id, values)
end
edu.expression = function(id, values, fn)
  checkpoint(id, values)
  local result = table.pack(pcall(fn))
  if not result[1] then
    local message = result[2]
    if type(message) ~= "table" or not message.ccedu then
      message = {ccedu = true, blockId = id, message = tostring(message)}
    end
    error(message, 0)
  end
  return table.unpack(result, 2, result.n)
end
edu.callProgram = function(name)
  calls = calls + 1
  if calls > 64 then error("Program call depth exceeds 64", 2) end
  local source = edu.librarySource(name)
  edu.enterProgram(name)
  local fn, message = load(source, "@edu:" .. name, "t", _ENV)
  if not fn then error(message, 2) end
  fn()
  edu.leaveProgram()
  calls = calls - 1
end
while true do
  local source = edu.takeProgram()
  if source then
    calls = 0
    local fn, message = load(source, "@edu-program", "t", _ENV)
    local ok = false
    if fn then ok, message = pcall(fn) end
    -- CC answers a move when it starts, so the last statement would lose its highlight while the robot
    -- still moves; hold it for its beat like every other statement before reporting the stop.
    if ok then
      local paced, done, wait = pcall(edu.pace)
      if paced and done and wait > 0 then sleep(wait / 20) end
    end
    if ok then edu.setStopped()
    elseif type(message) == "table" and message.ccedu then edu.setErroredAt(message.message, message.blockId)
    else edu.setErrored(tostring(message)) end
  end
  os.pullEvent("ccedu_run")
end
