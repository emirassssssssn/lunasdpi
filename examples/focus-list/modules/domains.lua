local M = {}

function M.parse(raw)
  local out = {}
  for line in string.gmatch(raw or "", "[^\r\n]+") do
    local trimmed = line:match("^%s*(.-)%s*$")
    if trimmed ~= nil and trimmed ~= "" then
      out[#out + 1] = trimmed
    end
  end
  return out
end

return M
