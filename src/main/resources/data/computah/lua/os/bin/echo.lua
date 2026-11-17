-- echo [-n] [text...]: prints its arguments.
local args, options = require("shell").parse(...)
io.write(table.concat(args, " "))
if not options.n then io.write("\n") end
