/* This macro generates kymographs from regular radial lines originating from the center of the image, 
 *  allowing the user to then specify a point on the kymograph image to add the intensity
 *  of that point to a table. Designed to work with Thickness maps of cement for crowns.  
 *  
 *  Important: Hit 'space' after setting the point measurement to add it to the table.
 * 
 * @author Andrew McCall
 */

#@ String(value="Select images to analyze", visibility="MESSAGE") msg
#@ File[] (label="Select images", style="files") inputFiles
#@ File (label="Table output directory", style="directory", persist=true) outputDir

Table.create("Marginal Gap Thicknesses");

for (j = 0; j < inputFiles.length; j++) {
	open(inputFiles[j]);
	title = getTitle();

	getDimensions(width, height, channels, slices, frames);
	
	if(width > height){
		longestDim = width;
	}
	else{
		longestDim = height;
	}	
	
	longestDim += longestDim%2;
	run("Canvas Size...", "width="+longestDim+" height="+longestDim+" position=Center zero");
	
	halfDim = (longestDim-1)/2;
	setTool("point");
	run("Set Measurements...", "mean redirect=None decimal=3");
	
	for (i = 0.0; i < 360; i += 20) {
		makeLine(halfDim, halfDim, findX(halfDim, i), findY(halfDim, i));
		run("Multi Kymograph", "linewidth=1");
		while (!isKeyDown("space")) {
		    wait(10);
		}
		getSelectionCoordinates(xpoints, ypoints);
		Table.set(title, i/20, getPixel(xpoints[0],ypoints[0]));
		Table.update;
		close();
		selectImage(title);
	}

	close(title);
}

Table.save(outputDir + File.separator + "MarginalGapMeasurements.csv");

function findX(distance, angle){
	return (distance * cos(Math.toRadians(angle))) + distance;
}

function findY(distance, angle){
	return (distance * sin(Math.toRadians(angle))) + distance;
}

