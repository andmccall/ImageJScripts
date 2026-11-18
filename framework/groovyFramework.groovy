startTime = java.time.LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
println("Script started at: " + startTime);

/*
 * Description
 */

//Global parameters for user modification:
 
//importStatements

import io.scif.config.SCIFIOConfig;

#@ String (visibility = MESSAGE, value="Message used to force SciJava widget.", required=false) msg
#@ File[] (label="Select input images", style = "files") fileList
#@ File (label="Provide classifier file", style = "file") classifier
#@ boolean (label="Use GPU? (requires NVIDIA gpu and CLIJ)") useGPU
#@ File (label="Set output folder", style="directory") outputDir
#@ UIService uiService
#@ OpService ops
#@ OpEnvironment sciOps
#@ DatasetService datasetService
#@ DatasetIOService datasetioService
#@ IOService ioService
#@ CommandService commandService
#@ ConvertService converter

config = new SCIFIOConfig();
config.writerSetFailIfOverwriting(false);

//body


println("Finished!");
stopTime= java.time.LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
println("Script started at " + startTime):
println("Script finished at " + stopTime);
println("Total runtime (hours): " + (java.time.temporal.ChronoUnit.MINUTES.between(startTime, stopTime)/60.0));
