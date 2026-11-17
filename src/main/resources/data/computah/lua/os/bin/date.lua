-- date [format]: prints the real date and time (UTC). The format is like os.date's, e.g. date "%H:%M".
io.write(os.date("!" .. (... or "%Y-%m-%d %H:%M:%S UTC")) .. "\n")
