startTime = java.time.LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
println("Script started at: " + startTime);

/*
 * Description
 */
 
//importStatements





//body


println("Finished!");
stopTime= java.time.LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
println("Script started at " + startTime):
println("Script finished at " + stopTime);
println("Total runtime (hours): " + (java.time.temporal.ChronoUnit.MINUTES.between(startTime, stopTime)/60.0));
