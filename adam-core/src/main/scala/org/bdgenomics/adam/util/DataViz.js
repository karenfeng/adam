// Set the dimensions of the canvas
var margin = {bottom: 20},
width = 410,
height = 420;

// Create the SVG viewport
var svgContainer = d3.select("body").append("svg")
                                    .attr("width", width)
                                    .attr("height", height);

// Get the reads data
d3.tsv("reads.tsv", function(error, data) {
    data.forEach(function(d) {
        d.readName = d.readName;
        d.x = +d.x;
        d.y = +d.y;
        d.w = +d.w;
        d.h = +d.h;
    });

    // Add the rectangles
    svgContainer.append("g")
                .selectAll("rect")
                .data(data)
                .enter()
                .append("rect")
                .attr("x", (function(d) { return d.x; }))
                .attr("y", (function(d) { return d.y; }))
                .attr("width", (function(d) { return d.w; }))
                .attr("height", (function(d) { return d.h; }))
                .attr("fill", "blue")
                .on("mouseover", function(d) {
                                    div.transition()
                                    .duration(200)
                                    .style("opacity", .9);
                                    div .html(d.readName)
                                    .style("left", (d3.event.pageX) + "px")
                                    .style("top", (d3.event.pageY - 28) + "px");
                                })
                .on("mouseout", function(d) {
                                    div.transition()
                                    .duration(500)
                                    .style("opacity", 0);
                                });
});

// Get the info data
d3.tsv("info.tsv", function(error, data) {
    console.log(data)
    data.forEach(function(d) {
        d.refName = d.refName;
        d.start = +d.start;
        d.end = +d.end;
    });

    // Print the reference region information
    d3.select("h2")
      .append("text")
      .text(d3.max(data, function(d) { return d.refName; }) + ":" + d3.max(data, function(d) { return d.start; }) + "-" + d3.max(data, function(d) { return d.end; }));

    // Create the scale for the axis
    var axisScale = d3.scale.linear()
                            .domain([d3.max(data, function(d) { return d.start; }), d3.max(data, function(d) { return d.end; })])
                            .range([0, 400]);

    // Create the axis
    var xAxis = d3.svg.axis()
                   .scale(axisScale)
                   .ticks(5);

    // Add the axis to the container
    svgContainer.append("g")
                .attr("class", "axis")
                .attr("transform", "translate(0," + (height-margin.bottom) + ")")
                .call(xAxis);
});

// Try to move right
function moveLeft() {
    alert("Trying to move left")
}

// Try to move right
function moveRight() {
    alert("Trying to move right")
}

// Try to move right
function zoomIn() {
    alert("Trying to zoom in")
}

// Try to zoom out
function zoomOut() {
    alert("Trying to zoom out")
}

// Hover box for reads
var div = d3.select("body")
            .append("div")
            .attr("class", "tooltip")
            .style("opacity", 0);