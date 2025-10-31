#@ File[] (label="Select images", style="files") images
//#@ String extension (label="Image extension", value="tif")
#@ String (label="Path to QuPath Executable", value="C:\\Users\\406SRVBRB\\AppData\\Local\\QuPath-0.5.1\\QuPath-0.5.1 (console).exe") quPathExe

import groovyx.gpars.GParsPool;
//
//def folder = imagesDirectory
//
//def convertedFolder = new File( folder.getParent(), "converted")
//convertedFolder.mkdirs()
//
//def fileList = folder.listFiles().findAll{ it.getName().endsWith(extension) && !it.getName().contains("Overview") }


GParsPool.withPool(4) {
	images.eachParallel{ file ->
		println("Converting " + file.getPath());
		
		def cmd = quPathExe;
		def outputFile = new File (file.getParent() + File.separator + "OME-tiffs" + File.separator + file.getName() + "_info.txt");
		def outputFolder = new File(file.getParent() + File.separator + "OME-tiffs" + File.separator);
		outputFolder.mkdirs();
		
		def pb = new ProcessBuilder(cmd, "convert-ome", file.getAbsolutePath(), new File( outputFolder.getPath() + File.separator + file.getName() + ".ome.tiff").getAbsolutePath(), "-p", "-y=2")
						.redirectErrorStream(true)
						.redirectOutput(outputFile);
		
		//println (pb.command());
		pb.start().waitFor();
		println("Finished " + file.getPath());
	}
}
println("All files converted");
