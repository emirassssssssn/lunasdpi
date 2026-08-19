local page = require("page")
local sundial = require("sundial")

function settings_page()
  return page.build(sundial.view())
end
