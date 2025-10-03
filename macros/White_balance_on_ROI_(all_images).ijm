var delta = newArray(3);
var mean;


run("Set Measurements...", "  mean redirect=None decimal=3");
list = getList("image.titles");	

getBalanceValues();

for (i = 0; i < list.length; i++) {
	selectImage(list[i]);		
	whiteBalance(getTitle());
	run("Save");
}		

function whiteBalance(title){	
	if (bitDepth() != 24) return;
	run("Select None");
	run("RGB Stack");
	for (s=1;s<=3;s++) {
		setSlice(s);
		run("Subtract...", "slice value="+ delta[s-1]);
	}
	run("RGB Color");
}

function getBalanceValues(){
	if (bitDepth() != 24) exit("Active image is not RGB");
	run("RGB Stack");
	val = newArray(3);
	for (s=1;s<=3;s++) {
		setSlice(s);
		val[s-1] = getValue("Median");
	}
	
	Array.getStatistics(val, min, max, mean, stdDev);
	for (i = 0; i <= 2; i++) {
		delta[i] = round(val[i]-mean);
	}
	run("RGB Color");
}
