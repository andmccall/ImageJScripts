#@ File[] (label="Select images", style="files") images
#@ String (label="Path to QuPath Executable", value="C:\\Users\\406SRVBRB\\AppData\\Local\\QuPath-0.5.1\\QuPath-0.5.1 (console).exe") quPathExe
#@ Integer (label="Parallelization number:", value = 4) parallel

import groovyx.gpars.GParsPool;


GParsPool.withPool(parallel) {
	images.eachParallel{ file ->
		println("Converting " + file.getPath());
		
		def cmd = quPathExe;
		def outputFile = new File (file.getParent() + File.separator + "OME-tiffs" + File.separator + file.getName() + "_info.txt");
		def outputFolder = new File(file.getParent() + File.separator + "OME-tiffs" + File.separator);
		outputFolder.mkdirs();
		
		def pb = new ProcessBuilder(cmd, "convert-ome", file.getAbsolutePath(), new File( outputFolder.getPath() + File.separator + file.getName() + ".ome.tiff").getAbsolutePath(), "-p", "-y=2")
						.redirectErrorStream(true)
						.redirectOutput(outputFile);
		
		pb.start().waitFor();
		println("Finished " + file.getPath());
	}
}
println("All files converted");
