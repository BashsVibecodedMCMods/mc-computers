-- dmesg: shows kernel messages (boot, and errors from threads and event listeners).
for _, line in ipairs(package.kernel.messages) do io.write(line, "\n") end
