startTime = getTime();

/*
 * Description
 */

#@ String (visibility = MESSAGE, value="Messsage to force SciJava widget.", required=false) msg
#@ File[] (label="Select images", style="files") inputFiles
#@ File (label="Output directory", style="directory", persist=true) outputDir

setBatchMode("hide");

//body

setBatchMode("exit and display");

stopTime = getTime();
setBatchMode(true);
print("All finshed, total time taken (hours): " + ((stopTime-startTime)/3600000.0));
setBatchMode(false);
