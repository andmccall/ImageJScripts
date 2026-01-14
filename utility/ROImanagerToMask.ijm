run("Duplicate...", "title=mask ignore duplicate");
run("8-bit");

run("Select All");
setBackgroundColor(0, 0, 0);
run("Clear", "stack");
run("Select None");

roiManager("Fill");
