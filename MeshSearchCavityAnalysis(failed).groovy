/**
 * This script DOES NOT WORK!
 * 
 * This was an attempt to use surface meshes in order to identify a ring of verticies
 * at a narrow constriction point (a drilled out cavity hole in a tooth). However, I was
 * never able to develop a procedure that actually returned the ring of verticies. It 
 * consistently returned a small patch within the hole. I think this could be done with 
 * a  more tools for mesh processing and analysis (such as local thickness or local 
 * curvature).
 * 
 * I'm posting this anyway as it was my first attempt into working with Meshes and even
 * though I didn't achieve the goal I set out for, I think it will be a good reference for
 * working with Meshes.
 * 
 * Image is assumed to have exactly 3 dimensions, and all axes are assumed to be isometric.
 * 
 * @author Andrew McCall
 */

//Scaled distance from marked point to crop the image (in all dimensions) Note to self: original testing with 0.5, lowering to speed things up
float cropHalfWidth = 0.3;
float thresholdValue = 3500.0;


import ij.IJ;
import ij.gui.Roi;
import java.awt.Point;
import java.util.stream.LongStream;
import customnode.CustomTriangleMesh;
import ij3d.Image3DUniverse;


import net.imagej.axis.Axes;
import net.imagej.mesh.*;
import net.imagej.mesh.naive.NaiveFloatMesh;


import net.imagej.mesh.Triangle;

import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.Interval;
import net.imglib2.FinalInterval;
import net.imglib2.IterableInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealLocalizable;
import net.imglib2.type.numeric.ComplexType;

import org.scijava.vecmath.Color3f;
import org.scijava.vecmath.Point3f;

//#@ File[] (label="Select image[s]", style = "files") fileList
#@ Dataset input
#@ ImagePlus imp
#@ UIService uiService
#@ ConvertService converter
#@ OpService ops
#@ DatasetService dsService
#@ DatasetIOService datasetioService
#@ IOService ioService


def opsMeshToCustomMesh(opsMesh, color) {
    points = [];
    for (t in opsMesh.triangles()) {
        points.add(new Point3f(t.v0xf(), t.v0yf(), t.v0zf()));
        points.add(new Point3f(t.v1xf(), t.v1yf(), t.v1zf()));
        points.add(new Point3f(t.v2xf(), t.v2yf(), t.v2zf()));
    }
    ctm = new CustomTriangleMesh(points);
    ctm.setColor(color);
    return ctm;
}

def showMesh(Mesh inputMesh){	
	mesh3dv = opsMeshToCustomMesh(inputMesh, new Color3f(1, 0, 1));
	
	// Display original image and meshes in 3D Viewer.
	univ = new Image3DUniverse();
	//univ.addVoltex(imp, 1)
	univ.addCustomMesh(mesh3dv, "Surface Mesh");
	univ.show();
}

def showTwoMeshs(Mesh mesh1, Mesh mesh2){
	mesh3dv1 = opsMeshToCustomMesh(mesh1, new Color3f(1, 0, 1));
	mesh3dv2 = opsMeshToCustomMesh(mesh2, new Color3f(0, 1, 0));
	
	univ = new Image3DUniverse();
	//univ.addVoltex(imp, 1)
	univ.addCustomMesh(mesh3dv1, "Full surface");
	univ.addCustomMesh(mesh3dv2, "Ring");
	univ.show();
}

def createNewMeshFromVertices(Mesh srcMesh, Set<Long> vIndices, Mesh destMesh){
	//Modified from Meshes.copy() method
	//Map to keep track of old to new vertex Index
	final Map<Long, Long> vIndexMap = new HashMap<>();
	Vertices sV = srcMesh.vertices();
	Triangles sT = srcMesh.triangles();
	Set<Long> triToAdd = new HashSet<Long>();
	for(final Long vIndex: vIndices){
		 Set triPerVertex = getTrianglesContainingVertex(sT, vIndex);
		 for (Long tIndex:triPerVertex){
		 	triToAdd.add(tIndex);
		 }
	}
	Set vertToAdd = getVerticesFromTriangles(sT, triToAdd);
	
	for(final Long vIndex: vertToAdd){
		long destIndex = destMesh.vertices().add(
			sV.x(vIndex), sV.y(vIndex), sV.z(vIndex),
			sV.nx(vIndex), sV.ny(vIndex), sV.nz(vIndex),
			sV.u(vIndex), sV.v(vIndex));
			
		vIndexMap.put(vIndex, destIndex);
	}
	
	for(final Long tIndex: triToAdd){
        final long v0src = sT.vertex0(tIndex);
        final long v1src = sT.vertex1(tIndex);
        final long v2src = sT.vertex2(tIndex);
        final long v0 = vIndexMap.get(v0src);
        final long v1 = vIndexMap.get(v1src);
        final long v2 = vIndexMap.get(v2src);

        destMesh.triangles().add(v0, v1, v2, sT.nx(tIndex), sT.ny(tIndex), sT.nz(tIndex));
	}
}

def vertexCosAlign(Mesh inputMesh, long v1a, long v1b, long v2a, long v2b){
	//v1a-v1b dot v2a-v2b
	Vertices vertices = inputMesh.vertices();
	
	double v1x = vertices.x(v1a) - vertices.x(v1b);
	double v1y = vertices.y(v1a) - vertices.y(v1b);
	double v1z = vertices.z(v1a) - vertices.z(v1b);
	
	double v2x = vertices.x(v2a) - vertices.x(v2b);
	double v2y = vertices.y(v2a) - vertices.y(v2b);
	double v2z = vertices.z(v2a) - vertices.z(v2b);
	
	return ((v1x*v2x)+(v1y*v2y)+(v1z*v2z))/( Math.sqrt(v1x*v1x + v1y*v1y + v1z*v1z) * Math.sqrt(v2x*v2x + v2y*v2y + v2z*v2z));	
}

def getProximityFactor(Mesh mesh, Long vIndex, double[] givenPoint){
	double vx = mesh.vertices().x(vIndex);
	double vy = mesh.vertices().y(vIndex);
	double vz = mesh.vertices().z(vIndex);
	
	double nx = mesh.vertices().nx(vIndex);
	double ny = mesh.vertices().ny(vIndex);
	double nz = mesh.vertices().nz(vIndex);	
	
	double gmvx = givenPoint[0] - vx;
	double gmvy = givenPoint[1] - vy;
	double gmvz = givenPoint[2] - vz;
	
	cosAlign = ((nx*gmvx) + (ny*gmvy) + (nz*gmvz))/( Math.sqrt(nx*nx + ny*ny + nz*nz) * Math.sqrt(gmvx*gmvx + gmvy*gmvy + gmvz*gmvz));
	distance = (Math.sqrt(gmvx*gmvx + gmvy*gmvy + gmvz*gmvz));
	return (cosAlign/Math.pow(distance, 3));
}

def doesTriangleContainVertex(Triangles triangles, long triangleIndex, long vertexIndex){
	if(triangles.vertex0(triangleIndex) == vertexIndex || triangles.vertex1(triangleIndex) == vertexIndex || triangles.vertex2(triangleIndex) == vertexIndex)
		return true;
	return false;
}

def getTrianglesContainingVertex(Triangles triangles, long vertexIndex){	
	Set<Long> inclTriangles = Collections.synchronizedSet(new HashSet());
	LongStream.range(0, triangles.size()).parallel().forEach(tIndex ->{
		if(doesTriangleContainVertex(triangles, tIndex, vertexIndex)){
			synchronized(inclTriangles){
				inclTriangles.add(tIndex);
			}
		}
	});
//
//	Set<Long> inclTriangles = new HashSet();
//	LongStream.range(0, triangles.size()).forEach(tIndex ->{
//		if(doesTriangleContainVertex(triangles, tIndex, vertexIndex)){
//			inclTriangles.add(tIndex);
//		}
//	});
	return inclTriangles;
}

def getVerticesFromTriangles(Triangles triangles, Set inclTriangles){
	Set<Long> inclVertices = new HashSet();
	for(Long tIndex: inclTriangles){
		inclVertices.add(triangles.vertex0(tIndex));
		inclVertices.add(triangles.vertex1(tIndex));
		inclVertices.add(triangles.vertex2(tIndex));
	}
	return inclVertices;
}

def getNeighborhoodVertices(Mesh inputMesh, long vertexIndex){
	Triangles triangles = inputMesh.triangles();
	Set neighborTriangles = getTrianglesContainingVertex(triangles, vertexIndex);
	Set neighborVertices = getVerticesFromTriangles(triangles, neighborTriangles);
	return neighborVertices;
}

def getHighestNeighbor(Mesh inputMesh, long currentVertex, long previousVertex, double [] givenPoint){
	Set neighborVertices = getNeighborhoodVertices(inputMesh, currentVertex);
	neighborVertices.remove(currentVertex);
	if(previousVertex != -1){
		neighborVertices.remove(previousVertex);
	}
	
	float highestProximity = Float.MIN_NORMAL;
	long highestNeighbor = -1;
	float proximityFactor;
	
	for(long vIndex:neighborVertices){
		proximityFactor = getProximityFactor(inputMesh, vIndex, givenPoint);
		//just proximityFactor forms short rings, need to multiply proximityFactor by the Dot product of (current-previous) and (vIndex-current), this tries to force linearity
		if(previousVertex != -1){
			float correctionFactor = Math.pow(vertexCosAlign(inputMesh, currentVertex, previousVertex, vIndex, currentVertex),3);
			if(proximityFactor >0)
				proximityFactor = proximityFactor*correctionFactor;
			else
				proximityFactor = proximityFactor/correctionFactor;
		}
		if(proximityFactor > highestProximity){
			highestProximity = proximityFactor;
			highestNeighbor = vIndex;
		}
	}
	return highestNeighbor;
}

def getRingOfVertices(Mesh inputMesh, long seedVIndex, double [] givenPoint){
	long maxRange = 5000;
	Set<Long> ringIndices = new HashSet();
	ringIndices.add(seedVIndex);
	long currentVertex = seedVIndex;
	long previousVertex = -1;
	long highestNeighbor;
	
	for(long iter = 0; iter < maxRange; ++iter){
		highestNeighbor = getHighestNeighbor(inputMesh, currentVertex, previousVertex, givenPoint);
		if(highestNeighbor == seedVIndex)
			continue;
		ringIndices.add(highestNeighbor);
		previousVertex = currentVertex;
		currentVertex = highestNeighbor;
	}
	return ringIndices;
}

float scale = input.axis(Axes.X).get().calibratedValue(1);
Point impRoi  = imp.getRoi().getContainedPoints()[0];
double [] givenPoint = new double[] {impRoi.getX(), impRoi.getY(), imp.getSlice()};

long[] min = new long[3];
long[] max = new long[3];

for(int d = 0; d < min.length; ++d){
	min[d] = Math.max(Math.round(givenPoint[d] - Math.round(cropHalfWidth/scale)), input.min(d));
	max[d] = Math.min(Math.round(givenPoint[d] + Math.round(cropHalfWidth/scale)), input.max(d));
	givenPoint[d] = givenPoint[d]-min[d];
}

FinalInterval cropInt = new FinalInterval(min, max);
cropped = ops.transform().crop(input, cropInt);
//uiService.show(cropped);

ComplexType threshold = input.getType();
threshold.setReal(thresholdValue);

Img mask = ops.threshold().apply(cropped, threshold);
Img invertMask = mask.copy();
ops.image().invert(invertMask, mask);
invertMask = ops.morphology().fillHoles(invertMask);
ops.image().invert(mask, invertMask);
mask = ops.morphology().fillHoles(mask);

Mesh surface = ops.geom().marchingCubes(mask);
showMesh(surface);

//Have to remove Duplicate vertices before simplifying. I think marchingCubes makes unique verticies for every triangle
surface = Meshes.removeDuplicateVertices(surface, 1);
surface = Meshes.simplify(surface, 0.25, 10);
showMesh(surface);
Mesh surfaceNormals = new NaiveFloatMesh();
Meshes.calculateNormals(surface, surfaceNormals);
float highestProximity = 0.0;
long closestVertex;
float proximityFactor;

LongStream.range(0, surfaceNormals.vertices().size()).forEach(vIndex ->{
	proximityFactor = getProximityFactor(surfaceNormals,vIndex, givenPoint);
	if(proximityFactor > highestProximity){		
		highestProximity = proximityFactor;
		closestVertex = vIndex;
	}
});
println("Closest vertex: " + closestVertex + " - " + surfaceNormals.vertices().x(closestVertex) + "," + surfaceNormals.vertices().y(closestVertex) + "," + surfaceNormals.vertices().z(closestVertex));

Set<Long> holeRing =  getRingOfVertices(surfaceNormals, closestVertex, givenPoint);
println(holeRing);

Mesh holeRingMesh = new NaiveFloatMesh();
createNewMeshFromVertices(surfaceNormals, holeRing, holeRingMesh);
showTwoMeshs(surfaceNormals, holeRingMesh);

//Set<Long> vertices = getVerticesFromTriangles(surfaceNormals.triangles(), getTrianglesContainingVertex(surfaceNormals.triangles(),  (closestVertex)));
//println(vertices.size());

//println( closestTriangle.v0x() + "," + closestTriangle.v0y() + "," + closestTriangle.v0z() + " - " + highestDiscovery);
