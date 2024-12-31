/**
 * Script that converts DICOM image sequence stacks to meshes using a LabKit classifier
 * and automatically saves them as a meshed STL file.
 * 
 * @param A directory containing directories of DICOM sequences stacks
 * @param A Labkit classifier that outputs a single foreground label
 * 
 * @return void
 * 
 * @author Andrew McCall
 */

import ij.plugin.FolderOpener;

import net.imagej.Dataset;
import net.imagej.mesh.*;
import net.imagej.mesh.io.stl.STLMeshIO;

import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.roi.labeling.*;

#@ File (label="Select directory of DICOM directories", style = "directory") dicomDirectory
#@ File (label="Classifier file from Labkit", style = "file") classifier
#@ boolean (label="Use GPU? (requires NVIDIA gpu and CLIJ)") useGPU
#@ CommandService commandService
#@ ConvertService converter
#@ OpService ops

File[] folderList = dicomDirectory.listFiles();
STLMeshIO stlIO = new STLMeshIO();

for (int i = 0; i < folderList.length; ++i) {
	if(!folderList[i].isDirectory()){
		continue;
	}
	println("Opening DICOM folder: " + folderList[i].getPath());
	Dataset currentDS = converter.convert(FolderOpener.open(folderList[i].getPath(), ""), net.imagej.Dataset.class);
	
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
	stlIO.save(surface, dicomDirectory.getPath() + File.separator + currentDS.getName() + "-surface.stl");
}
println("All Finished!");
