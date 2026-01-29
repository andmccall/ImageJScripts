/**
 * Script that converts TIFF images to meshed STL files.
 * Requires Labkit, can optionally use with CLIJ + CLIJ2 
 * and an NVIDIA GPU to speed up segmentation. GPU acceleration
 * only affects segmentation and STL conversion will still take
 * a long time.
 * 
 * @input Tiff images
 * @input A Labkit classifier that outputs a single foreground label
 * 
 * @output Automatically saved stl files to provided input folder
 * 
 * @author Andrew McCall
 */

import net.imagej.Dataset;
import net.imagej.mesh.*;
import net.imagej.mesh.io.stl.STLMeshIO;

import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.roi.labeling.*;

#@ File[] (label="Select images", style="files") inputFiles
#@ File (label="Classifier file from Labkit", style = "file") classifier
#@ boolean (label="Use GPU? (requires NVIDIA gpu and CLIJ)") useGPU
#@ CommandService commandService
#@ ConvertService converter
#@ DatasetIOService datasetIOService
#@ OpService ops

STLMeshIO stlIO = new STLMeshIO();

for(File file:inputFiles){
	if(!datasetIOService.canOpen(file.getPath())){
		println(file.getPath() + " - cannot be opened as an image by Fiji, skipping file.");
		continue;
	}
	
	
	println("Opening image: " + file.getPath());
	Dataset currentDS = datasetIOService.open(file.getPath());
	
	println("Segementing image with Labkit");
	ImgLabeling labeledImg = ImgLabeling.fromImageAndLabels(commandService.run("sc.fiji.labkit.ui.plugin.SegmentImageWithLabkitPlugin", false, 
		"input", currentDS,
		"segmenter_file", classifier,
		"use_gpu", useGPU
	).get().getOutput("output"), new ArrayList(["target"]));
		
	println("Creating mesh");
	Mesh surface = ops.geom().marchingCubes(new LabelRegions(labeledImg).getLabelRegion("target"));
	println("Removing duplicate vertices");
	surface = Meshes.removeDuplicateVertices(surface, 1);
	
	//optional step, reduces number of vertices; first number is target percent, second is agressiveness
//	surface = Meshes.simplify(surface, 0.25, 5);
	
	//optional step, enable these and save surfaceNormals if you want your mesh to have accurate normals
//	Mesh surfaceNormals = new NaiveFloatMesh();
//	Meshes.calculateNormals(surface, surfaceNormals);

	println("Saving mesh");
	stlIO.save(surface, file.getPath() + "-surface.stl");
}

println("All Finished: " + (java.time.LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES)));
