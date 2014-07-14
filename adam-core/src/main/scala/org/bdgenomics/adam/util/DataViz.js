// Set the dimensions of the canvas / graph
var margin = {top: 30, right: 20, bottom: 30, left: 50},
width = 600 - margin.left - margin.right,
height = 270 - margin.top - margin.bottom;

// Create the SVG viewport
var svgContainer = d3.select("body").append("svg")
                                    .attr("width", 1000)
                                    .attr("height", 400);

// Create the scale for the axis
var axisScale = d3.scale.linear()
                        .domain([0,100])
                        .range([0,1000]);


// Create the axis
var xAxis = d3.svg.axis()
              .scale(axisScale);

// Get the data
d3.tsv("reads.tsv", function(error, data) {
    data.forEach(function(d) {
        d.x = +d.x;
        d.y = +d.y;
        d.w = +d.w;
        d.h = +d.h;
        d.readName = +d.readName;
    });

    // Add the rectangles
    svgContainer.selectAll("rect")
                .data(data)
                .enter()
                .append("rect")
                .attr("x", (function(d) { return d.x; }))
                .attr("y", (function(d) { return d.y; }))
                .attr("width", (function(d) { return d.w; }))
                .attr("height", (function(d) { return d.h; }))
                .attr("fill", "blue");
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

    svgContainer.append("g")
                .attr("class", "x axis")
                .attr("transform", "translate(0," + height + ")")
                .call(xAxis);
});

// Try to move right
function moveRight() {
    alert("Trying to move right")
}

// Hover box for reads
var div = d3.select("body")
            .append("div")
            .attr("class", "tooltip")
            .style("opacity", 0);